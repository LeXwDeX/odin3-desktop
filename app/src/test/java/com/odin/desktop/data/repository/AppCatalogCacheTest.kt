package com.odin.desktop.data.repository

import android.app.Application
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import androidx.room.Room
import com.odin.desktop.data.db.OdinDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowUsageStatsManager.UsageStatsBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppCatalogCacheTest {
    private val context = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java).build()
    private var now = 0L
    private var iconsDrawn = 0
    private var beforePrepare: () -> Unit = {}
    private val repo = AppRepository(context, db, elapsedRealtime = { now }, iconPreparer = { icon, resources ->
        iconsDrawn++
        beforePrepare()
        AppIconPreparation.prepare(icon, resources)
    })

    @After fun close() = db.close()

    @Config(sdk = [32, 35])
    @Test fun warmReturnsReuseMetadataAndExpiryStillRefreshesTheCatalog() = runBlocking {
        install("one", "Original")
        assertEquals("Original", repo.getInstalledLaunchableApps().single().label)
        install("one", "Changed")
        now = 59_999L
        assertEquals("Original", repo.getInstalledLaunchableApps().single().label)
        now = 60_000L
        assertEquals("Changed", repo.getInstalledLaunchableApps().single().label)
        assertEquals(1, iconsDrawn)
    }

    @Test fun packageEventsImmediatelyRefreshNamesEntrypointsAndIcons() = runBlocking {
        install("one", "Old")
        val old = repo.getInstalledLaunchableApps().single()
        install("one", "New", updateTime = 20L, activityName = "one.NewMain")
        repo.invalidateInstalledApps()
        val fresh = repo.getInstalledLaunchableApps().single()
        assertEquals("New", fresh.label)
        assertEquals("one.NewMain", fresh.activityName)
        assertNotSame(old.icon, fresh.icon)
        install("two", "Second")
        repo.invalidateInstalledApps()
        assertEquals("two", repo.getInstalledLaunchableApps().single().packageName)
    }

    @Test fun timedRefreshReusesPreparedIconsButPackageUpdatesReplaceThem() = runBlocking {
        install("one", "One")
        val first = repo.getInstalledLaunchableApps().single()
        now = 60_000L
        val refreshed = repo.getInstalledLaunchableApps().single()
        assertSame(first.icon, refreshed.icon)
        assertEquals(1, iconsDrawn)
        install("one", "Updated", updateTime = 20L)
        now = 120_000L
        assertNotSame(first.icon, repo.getInstalledLaunchableApps().single().icon)
        assertEquals(2, iconsDrawn)
    }

    @Test fun languageAndDensityChangesRefreshCachedLabelsAndArtwork() = runBlocking {
        install("one", "English")
        val first = repo.getInstalledLaunchableApps().single()
        install("one", "日本語")
        RuntimeEnvironment.setQualifiers("ja-rJP-xhdpi")
        val translated = repo.getInstalledLaunchableApps().single()
        assertEquals("日本語", translated.label)
        assertNotSame(first.icon, translated.icon)
        assertEquals(2, iconsDrawn)
    }

    @Test fun concurrentWarmRequestsShareOneCatalogRead() = runBlocking {
        install("one", "One")
        val start = CompletableDeferred<Unit>()
        val requests = (0 until 8).map {
            async(Dispatchers.IO) { start.await(); repo.getInstalledLaunchableApps() }
        }
        start.complete(Unit)
        val results = requests.awaitAll()
        assertEquals(List(8) { "one" }, results.map { it.single().packageName })
        results.forEach { assertSame(results.first().single(), it.single()) }
        assertEquals(1, iconsDrawn)
    }

    @Test fun cancelledScanDoesNotPublishAPartialCatalog() = runBlocking {
        var cancelScan: () -> Unit = {}
        install("one", "Old")
        beforePrepare = { cancelScan() }
        val scan = launch(start = CoroutineStart.LAZY) { repo.getInstalledLaunchableApps() }
        cancelScan = { scan.cancel() }
        scan.start()
        scan.join()
        assertTrue(scan.isCancelled)
        beforePrepare = {}
        install("two", "Fresh")
        assertEquals("two", repo.getInstalledLaunchableApps().single().packageName)
    }

    @Test fun recentUsageRefreshesIndependentlyAndRevocationNeverReusesIt() = runBlocking {
        install("one", "One")
        val ops = context.getSystemService(AppOpsManager::class.java)
        val usage = context.getSystemService(UsageStatsManager::class.java)
        fun permitted(mode: Int) = shadowOf(ops).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), context.packageName, mode)
        val wallNow = System.currentTimeMillis()
        val stats = UsageStatsBuilder.newBuilder().setPackageName("one")
            .setFirstTimeStamp(wallNow - 10_000).setLastTimeStamp(wallNow)
            .setLastTimeUsed(wallNow - 1_000).build()
        shadowOf(usage).addUsageStats(UsageStatsManager.INTERVAL_BEST, stats)
        permitted(AppOpsManager.MODE_ALLOWED)
        assertEquals(wallNow - 1_000, repo.getInstalledLaunchableApps().single().lastTimeUsed)
        assertTrue(repo.usageStatsAvailable)
        shadowOf(usage).addUsageStats(UsageStatsManager.INTERVAL_BEST, UsageStatsBuilder.newBuilder()
            .setPackageName("one").setFirstTimeStamp(wallNow - 1_000).setLastTimeStamp(wallNow)
            .setLastTimeUsed(wallNow).build())
        now = 4_999L
        assertEquals(wallNow - 1_000, repo.getInstalledLaunchableApps().single().lastTimeUsed)
        now = 5_000L
        assertEquals(wallNow, repo.getInstalledLaunchableApps().single().lastTimeUsed)
        permitted(AppOpsManager.MODE_IGNORED)
        assertNull(repo.getInstalledLaunchableApps().single().lastTimeUsed)
        assertFalse(repo.usageStatsAvailable)
        permitted(AppOpsManager.MODE_ALLOWED)
        assertEquals(wallNow, repo.getInstalledLaunchableApps().single().lastTimeUsed)
        assertEquals(1, iconsDrawn)
    }

    @Test fun defaultUsageModeNeedsTheGrantedPermissionAndExplicitDenialWins() = runBlocking {
        install("one", "One")
        val ops = context.getSystemService(AppOpsManager::class.java)
        val usage = context.getSystemService(UsageStatsManager::class.java)
        fun usageMode(mode: Int) = shadowOf(ops).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), context.packageName, mode)
        val wallNow = System.currentTimeMillis()
        shadowOf(usage).addUsageStats(UsageStatsManager.INTERVAL_BEST, UsageStatsBuilder.newBuilder()
            .setPackageName("one").setFirstTimeStamp(wallNow - 10_000).setLastTimeStamp(wallNow)
            .setLastTimeUsed(wallNow - 1_000).build())
        shadowOf(context).denyPermissions(android.Manifest.permission.PACKAGE_USAGE_STATS)
        usageMode(AppOpsManager.MODE_DEFAULT)
        assertNull(repo.getInstalledLaunchableApps().single().lastTimeUsed)
        assertFalse(repo.usageStatsAvailable)
        shadowOf(context).grantPermissions(android.Manifest.permission.PACKAGE_USAGE_STATS)
        assertEquals(wallNow - 1_000, repo.getInstalledLaunchableApps().single().lastTimeUsed)
        assertTrue(repo.usageStatsAvailable)
        usageMode(AppOpsManager.MODE_IGNORED)
        assertNull(repo.getInstalledLaunchableApps().single().lastTimeUsed)
        assertFalse(repo.usageStatsAvailable)
        usageMode(AppOpsManager.MODE_DEFAULT)
        shadowOf(context).denyPermissions(android.Manifest.permission.PACKAGE_USAGE_STATS)
        assertNull(repo.getInstalledLaunchableApps().single().lastTimeUsed)
        assertFalse(repo.usageStatsAvailable)
    }

    private fun install(pkg: String, label: String, updateTime: Long = 10L, activityName: String = "$pkg.Main") {
        val app = ApplicationInfo().apply { packageName = pkg; sourceDir = "/apps/$pkg.apk" }
        val entry = ResolveInfo().apply {
            nonLocalizedLabel = label
            activityInfo = ActivityInfo().apply {
                packageName = pkg; name = activityName; applicationInfo = app
            }
        }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg; firstInstallTime = 1L; lastUpdateTime = updateTime; applicationInfo = app
        })
        @Suppress("DEPRECATION")
        shadowOf(context.packageManager).setResolveInfosForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), listOf(entry))
    }
}
