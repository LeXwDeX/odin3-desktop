package com.odin.desktop.dashboard

import com.odin.desktop.R
import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/** Collection owns the samplers; cancelling collection stops all refresh loops. */
class DashboardRepository(context: Context) {
    private val app = context.applicationContext
    private val storageLock = Mutex()
    @Volatile private var storageCache: CachedStorage? = null

    fun observe(): Flow<DashboardState> = channelFlow {
        var state = DashboardState(storage = storageCache?.value ?: StorageUsage())
        val stateLock = Mutex()
        val sampler = LiveSampler(app)
        val dumpSampler = SystemDumpSampler(app)
        var pss = PssSample(null, app.getString(R.string.text_reading_non_system_app_pss))
        send(state)
        suspend fun publish(change: (DashboardState) -> DashboardState) {
            stateLock.withLock {
                state = change(state)
                send(state)
            }
        }

        // UID storage queries can take seconds. They never hold up the live metrics.
        launch(Dispatchers.IO) {
            while (isActive) {
                val storage = storageSnapshot()
                publish { it.copy(storage = storage) }
                delay(STORAGE_INTERVAL_MS)
            }
        }
        launch(Dispatchers.IO) {
            while (isActive) {
                val external = readExternalStorage()
                publish { it.copy(externalStorage = external) }
                delay(LIVE_INTERVAL_MS)
            }
        }
        launch(Dispatchers.IO) {
            while (isActive) {
                val sample = sampler.read()
                publish {
                    sample.copy(
                        storage = it.storage,
                        externalStorage = it.externalStorage,
                        memory = sample.memory.copy(nonSystemAppBytes = pss.bytes, note = notes(sample.memory.note, pss.note))
                    )
                }
                delay(LIVE_INTERVAL_MS)
            }
        }
        launch(Dispatchers.IO) {
            while (isActive) {
                val value = dumpSampler.nonSystemPss()
                publish {
                    pss = value
                    it.copy(memory = it.memory.copy(nonSystemAppBytes = value.bytes, note = value.note))
                }
                delay(DUMP_INTERVAL_MS)
            }
        }
    }.conflate()

    private suspend fun storageSnapshot(): StorageUsage = storageLock.withLock {
        val access = hasUsageAccess(app)
        val now = SystemClock.elapsedRealtime()
        storageCache?.takeIf { it.hasAccess == access && now - it.time < STORAGE_INTERVAL_MS }
            ?.let { return@withLock it.value }
        val value = readStorage(access)
        currentCoroutineContext().ensureActive()
        storageCache = CachedStorage(value, SystemClock.elapsedRealtime(), access)
        value
    }

    private suspend fun readExternalStorage(): List<ExternalStorageUsage> {
        val manager = app.getSystemService(StorageManager::class.java) ?: return emptyList()
        val volumes = readOrNull { manager.storageVolumes } ?: return emptyList()
        return volumes.mapIndexedNotNull { index, volume ->
            currentCoroutineContext().ensureActive()
            val state = volume.state
            if (volume.isPrimary || state !in setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)) {
                return@mapIndexedNotNull null
            }
            val label = readOrNull { volume.getDescription(app) }?.takeIf { it.isNotBlank() } ?: app.getString(R.string.text_external_storage)
            // API 29 has no public volume-directory accessor. Do not guess a mount path or
            // call getExternalFilesDirs(), which can create directories on the user's volume.
            val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) readOrNull { volume.directory } else null
            val id = volume.uuid?.let { "uuid:$it" } ?: directory?.absolutePath?.let { "path:$it" }
                ?: "external:$index:$label"
            val base = ExternalStorageUsage(id = id, label = label, readOnly = state == Environment.MEDIA_MOUNTED_READ_ONLY)
            if (directory == null) {
                return@mapIndexedNotNull base.copy(note = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    app.getString(R.string.text_this_android_version_does_not_provide_a)
                } else app.getString(R.string.text_volume_mounted_but_its_path_is_unavailable))
            }
            // Only filesystem capacity metadata is read; file contents and directories are never scanned.
            val sizes = readOrNull {
                val fs = StatFs(directory.absolutePath)
                val total = fs.totalBytes
                val free = fs.availableBytes
                if (total <= 0L || free !in 0L..total) null else total to free
            }
            if (sizes == null) base.copy(note = app.getString(R.string.text_volume_mounted_but_its_capacity_is_unavailable))
            else base.copy(totalBytes = sizes.first, freeBytes = sizes.second)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun readStorage(hasAccess: Boolean): StorageUsage {
        val fs = readOrNull { StatFs(Environment.getDataDirectory().absolutePath) }
            ?: return StorageUsage(loading = false, note = app.getString(R.string.text_internal_storage_is_unavailable))
        val dataTotal = fs.totalBytes
        val free = fs.availableBytes.coerceIn(0L, dataTotal)
        val manager = app.getSystemService(StorageStatsManager::class.java)
        val physicalTotal = readOrNull { manager?.getTotalBytes(StorageManager.UUID_DEFAULT) }
            ?.takeIf { it >= dataTotal }
        val system = physicalTotal?.minus(dataTotal)
        val base = StorageUsage(
            totalBytes = physicalTotal ?: dataTotal,
            freeBytes = free,
            systemBytes = system,
            loading = false,
            needsUsageAccess = !hasAccess
        )
        if (!hasAccess) return base.copy(note = app.getString(R.string.text_allow_usage_access_to_measure_apps_system))
        if (manager == null) return base.copy(note = app.getString(R.string.text_app_storage_statistics_are_unavailable))
        if (Build.VERSION.SDK_INT >= 30 && !hasPermission(app, Manifest.permission.QUERY_ALL_PACKAGES)) {
            return base.copy(note = app.getString(R.string.text_the_app_list_is_restricted_total_app))
        }
        val installed = readOrNull { app.packageManager.getInstalledApplications(0) }
            ?: return base.copy(note = app.getString(R.string.text_cannot_read_the_app_list))
        if (installed.isEmpty()) return base.copy(note = app.getString(R.string.text_app_list_unavailable))
        var appsBytes = 0L
        for (uid in installed.map { it.uid }.distinct()) {
            currentCoroutineContext().ensureActive()
            val stats = readOrNull { manager.queryStatsForUid(StorageManager.UUID_DEFAULT, uid) }
                ?: return base.copy(
                    needsUsageAccess = !hasUsageAccess(app),
                    note = app.getString(R.string.text_some_app_storage_is_unreadable_an_incomplete)
                )
            // dataBytes already includes cacheBytes. Shared-UID packages are queried only once.
            appsBytes += stats.appBytes + stats.dataBytes
        }
        val dataUsed = dataTotal - free
        if (appsBytes !in 0L..dataUsed) {
            return base.copy(note = app.getString(R.string.text_storage_snapshots_differ_waiting_for_the_next))
        }
        return base.copy(
            appsBytes = appsBytes,
            otherBytes = dataUsed - appsBytes,
            note = if (system == null) app.getString(R.string.text_data_partition_only_total_system_usage_is)
            else app.getString(R.string.text_system_includes_partitions_and_reserves_apps_include)
        )
    }

    private data class CachedStorage(val value: StorageUsage, val time: Long, val hasAccess: Boolean)

    private companion object {
        const val LIVE_INTERVAL_MS = 2_000L
        const val STORAGE_INTERVAL_MS = 60_000L
        const val DUMP_INTERVAL_MS = 15_000L
    }
}

private class LiveSampler(private val app: Context) {
    private val activity = app.getSystemService(ActivityManager::class.java)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val temperatures = TemperatureSampler()
    private var lastWifi: NetworkCounter? = null

    fun read(): DashboardState {
        val (cpuTemperature, gpuTemperature) = temperatures.read()
        return DashboardState(
            memory = memory(),
            cpu = ProcessorUsage(cpuTemperature, if (cpuTemperature == null) app.getString(R.string.text_cpu_temperature_unavailable) else app.getString(R.string.text_highest_cpu_sensor_temperature)),
            gpu = ProcessorUsage(gpuTemperature, if (gpuTemperature == null) app.getString(R.string.text_gpu_temperature_unavailable) else app.getString(R.string.text_highest_gpu_sensor_temperature)),
            wifi = wifi(),
            loading = false,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    private fun memory(): MemoryUsage {
        val memory = readOrNull {
            ActivityManager.MemoryInfo().also { requireNotNull(activity).getMemoryInfo(it) }
        } ?: return MemoryUsage(note = app.getString(R.string.text_system_memory_information_unavailable))
        return MemoryUsage(
            totalBytes = memory.totalMem,
            usedBytes = (memory.totalMem - memory.availMem).coerceIn(0L, memory.totalMem)
        )
    }

    @Suppress("DEPRECATION")
    private fun wifi(): WifiUsage {
        if (!hasPermission(app, Manifest.permission.ACCESS_NETWORK_STATE) || connectivity == null) {
            lastWifi = null
            return WifiUsage(note = app.getString(R.string.text_network_state_permission_is_missing))
        }
        val candidates = readOrNull {
            connectivity.allNetworks.mapNotNull { network ->
                val caps = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                ) network to caps else null
            }
        } ?: run {
            lastWifi = null
            return WifiUsage(note = app.getString(R.string.text_wi_fi_network_status_unavailable))
        }
        val active = readOrNull { connectivity.activeNetwork }
        val selected = candidates.firstOrNull { it.first == active } ?: candidates.firstOrNull()
        if (selected == null) {
            lastWifi = null
            return WifiUsage(note = app.getString(R.string.text_wi_fi_disconnected))
        }
        val (network, caps) = selected
        val iface = readOrNull { connectivity.getLinkProperties(network)?.interfaceName }
        val fineLocation = hasPermission(app, Manifest.permission.ACCESS_FINE_LOCATION)
        val locationOn = readOrNull { app.getSystemService(LocationManager::class.java)?.isLocationEnabled } == true
        val transportSsid = cleanSsid((caps.transportInfo as? WifiInfo)?.ssid)
        // Legacy WifiManager refers to the primary connection; don't attach it to a different STA.
        val ssid = transportSsid ?: if (candidates.size == 1 && hasPermission(app, Manifest.permission.ACCESS_WIFI_STATE)) {
            readOrNull { cleanSsid(app.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid) }
        } else null
        val rx = iface?.let { interfaceBytes(it, received = true) }
        val tx = iface?.let { interfaceBytes(it, received = false) }
        val sample = if (iface != null && rx != null && tx != null) {
            NetworkCounter("${network.networkHandle}:$iface", SystemClock.elapsedRealtime(), rx, tx)
        } else null
        val previous = lastWifi
        lastWifi = sample
        val elapsed = if (sample != null && previous != null && sample.key == previous.key) sample.time - previous.time else 0L
        val ratesValid = sample != null && previous != null && elapsed > 0 &&
            sample.rx >= previous.rx && sample.tx >= previous.tx
        val needsLocation = ssid == null && (!fineLocation || !locationOn)
        return WifiUsage(
            connected = true,
            ssid = ssid,
            rxBytesPerSecond = if (ratesValid) ((sample!!.rx - previous!!.rx) * (1000.0 / elapsed)).toLong() else null,
            txBytesPerSecond = if (ratesValid) ((sample!!.tx - previous!!.tx) * (1000.0 / elapsed)).toLong() else null,
            needsLocationAccess = needsLocation,
            note = notes(
                if (ssid == null) {
                    when {
                        !fineLocation && !locationOn -> app.getString(R.string.text_wi_fi_name_requires_precise_location_access)
                        !fineLocation -> app.getString(R.string.text_wi_fi_name_requires_precise_location_access_2)
                        !locationOn -> app.getString(R.string.text_system_location_is_off_wi_fi_name)
                        else -> app.getString(R.string.text_wi_fi_name_unavailable)
                    }
                } else null,
                if (sample == null) app.getString(R.string.text_wi_fi_traffic_counters_unavailable)
                else if (!ratesValid) app.getString(R.string.text_sampling_wi_fi_traffic) else app.getString(R.string.text_wi_fi_interface_value, iface)
            )
        )
    }

    private fun interfaceBytes(iface: String, received: Boolean): Long? = readOrNull {
        if (!iface.matches(Regex("[A-Za-z0-9_.:-]+")) || iface.contains("..")) return@readOrNull null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val count = if (received) TrafficStats.getRxBytes(iface) else TrafficStats.getTxBytes(iface)
            if (count >= 0L) return@readOrNull count
        }
        // Older Android versions may permit these exact interface counters. Never use total traffic.
        File("/sys/class/net/$iface/statistics/${if (received) "rx" else "tx"}_bytes")
            .readText().trim().toLong().takeIf { it >= 0L }
    }

    private data class NetworkCounter(val key: String, val time: Long, val rx: Long, val tx: Long)
}

private class TemperatureSampler {
    private var sensors = emptyList<Pair<File, Boolean>>()
    private var scannedAt: Long? = null

    fun read(): Pair<Float?, Float?> {
        val now = SystemClock.elapsedRealtime()
        if (scannedAt == null || now - scannedAt!! >= 60_000L) {
            sensors = readOrNull {
                File("/sys/class/thermal").listFiles().orEmpty().filter { it.name.startsWith("thermal_zone") }
                    .mapNotNull { zone ->
                        val type = readOrNull { File(zone, "type").readText().trim().lowercase() }
                            ?: return@mapNotNull null
                        when {
                            type.startsWith("cpu") -> File(zone, "temp") to true
                            type.startsWith("gpu") -> File(zone, "temp") to false
                            else -> null
                        }
                    }
            }.orEmpty()
            scannedAt = now
        }
        var cpu: Float? = null
        var gpu: Float? = null
        sensors.forEach { (file, isCpu) ->
            val value = readOrNull { (file.readText().trim().toFloat() / 1000f).takeIf { it.isFinite() && it in -10f..150f } }
                ?: return@forEach
            if (isCpu) cpu = cpu?.let { maxOf(it, value) } ?: value
            else gpu = gpu?.let { maxOf(it, value) } ?: value
        }
        return cpu to gpu
    }
}

private data class PssSample(val bytes: Long?, val note: String)

/** Uses platform DUMP and usage permissions with the app's own UID; no privileged backend. */
private class SystemDumpSampler(private val app: Context) {
    @Suppress("DEPRECATION")
    suspend fun nonSystemPss(): PssSample {
        diagnosticPermissionNote(app)?.let { return PssSample(null, app.getString(R.string.text_app_pss_value, it)) }
        if (Build.VERSION.SDK_INT >= 30 && !hasPermission(app, Manifest.permission.QUERY_ALL_PACKAGES)) {
            return PssSample(null, app.getString(R.string.text_the_app_list_is_restricted_pss_cannot))
        }
        val apps = readOrNull { app.packageManager.getInstalledApplications(0) }
            ?: return PssSample(null, app.getString(R.string.text_cannot_identify_non_system_apps))
        val uidSystem = apps.groupBy { it.uid }.mapValues { (_, packages) ->
            packages.any { it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
        }
        val processes = boundedDump(app, "activity", "processes")
            ?: return PssSample(null, app.getString(R.string.text_process_diagnostics_unavailable_or_timed_out))
        val records = parseProcesses(processes)
            ?: return PssSample(null, app.getString(R.string.text_process_list_incomplete_waiting_for_the_next))
        val memory = boundedDump(app, "meminfo", "--oom")
            ?: return PssSample(null, app.getString(R.string.text_memory_diagnostics_unavailable_or_timed_out))
        val pss = parsePss(memory)
            ?: return PssSample(null, app.getString(R.string.text_the_system_returned_an_incomplete_pss_section))
        // A process can start/exit between the two snapshots. Never report a partial total.
        if (pss.keys != records.keys || pss.any { (pid, value) -> records[pid]?.name != value.name }) {
            return PssSample(null, app.getString(R.string.text_processes_are_changing_waiting_for_a_consistent))
        }
        val user = Process.myUid() / 100_000
        var sum = 0L
        for ((pid, process) in records) {
            if (process.uid / 100_000 != user || process.uid % 100_000 < Process.FIRST_APPLICATION_UID) continue
            val system = uidSystem[process.uid]
                ?: return PssSample(null, app.getString(R.string.text_some_processes_cannot_be_classified_an_incomplete))
            if (!system) {
                val bytes = pss.getValue(pid).bytes
                if (bytes <= 0L) return PssSample(null, app.getString(R.string.text_some_app_pss_readings_are_unavailable))
                // Each PID is counted once. Multiple packages sharing its UID add no duplicates.
                sum += bytes
            }
        }
        return PssSample(sum, app.getString(R.string.text_non_system_app_pss_for_this_user))
    }

    private fun parseProcesses(text: String): Map<Int, ProcessIdentity>? {
        if (!text.startsWith("ACTIVITY MANAGER RUNNING PROCESSES")) return null
        val expected = Regex("Process LRU list \\(sorted by oom_adj, (\\d+) total")
            .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val header = Regex("(?m)^\\s*\\*\\w+\\* UID (\\d+) ProcessRecord\\{\\S+ (\\d+):([^/]+)/[^}]+\\}")
        val rows = mutableMapOf<Int, ProcessIdentity>()
        for (match in header.findAll(text)) {
            val uid = match.groupValues[1].toIntOrNull() ?: return null
            val pid = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
            if (rows.put(pid, ProcessIdentity(uid, match.groupValues[3])) != null) return null
        }
        return rows.takeIf { it.size == expected && it.isNotEmpty() }
    }

    private fun parsePss(text: String): Map<Int, ProcessPss>? {
        // Android 15 compact output repeats proc rows for RSS then PSS with identical tags.
        // Select the explicit PSS section so RSS can never be mistaken for PSS or double-counted.
        val start = text.indexOf("Total PSS by OOM adjustment:")
        val end = text.indexOf("\nTotal RAM:", start.coerceAtLeast(0))
        if (start < 0 || end <= start) return null
        val processRow = Regex("^\\s*([0-9,]+)K:\\s+(.+?) \\(pid (\\d+)(?: / [^)]*)?\\)\\s*$")
        val groupRow = Regex("^\\s*[0-9,]+K:\\s+(.+?)\\s*$")
        var group: String? = null
        val rows = mutableMapOf<Int, ProcessPss>()
        for (line in text.substring(start, end).lineSequence().drop(1)) {
            if (line.isBlank()) continue
            val process = processRow.matchEntire(line)
            if (process != null) {
                if (group == null) return null
                // Native daemons are not Android application ProcessRecords.
                if (group == "Native") continue
                val bytes = process.groupValues[1].replace(",", "").toLongOrNull()?.times(1024L) ?: return null
                val pid = process.groupValues[3].toIntOrNull() ?: return null
                if (rows.put(pid, ProcessPss(process.groupValues[2], bytes)) != null) return null
            } else {
                group = groupRow.matchEntire(line)?.groupValues?.get(1) ?: return null
            }
        }
        return rows.takeIf { it.isNotEmpty() }
    }

    private data class ProcessIdentity(val uid: Int, val name: String)
    private data class ProcessPss(val name: String, val bytes: Long)
}

private fun diagnosticPermissionNote(context: Context): String? = when {
    !hasPermission(context, Manifest.permission.DUMP) -> context.getString(R.string.text_system_diagnostics_permission_not_granted)
    // AppOps access alone permits storage stats, but the diagnostic service also checks this grant.
    !hasPermission(context, Manifest.permission.PACKAGE_USAGE_STATS) -> context.getString(R.string.text_system_usage_statistics_permission_not_granted)
    !hasUsageAccess(context) -> context.getString(R.string.text_usage_access_is_off)
    else -> null
}

/** A bounded, cancelled-with-collection subprocess. Output is never written to disk or logged. */
private suspend fun boundedDump(context: Context, vararg arguments: String): String? = coroutineScope {
    val service = arguments.firstOrNull() ?: return@coroutineScope null
    fun diagnose(reason: String) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d("DashboardDump", "$service: $reason")
        }
    }
    val process = try {
        ProcessBuilder("/system/bin/dumpsys", "-t", "4", *arguments).redirectErrorStream(true).start()
    } catch (error: Exception) {
        diagnose("start failed: ${error.javaClass.simpleName}")
        return@coroutineScope null
    }
    val output = async(Dispatchers.IO) {
        try {
            process.inputStream.use { input ->
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > 1_048_576) {
                        diagnose("output exceeds 1 MiB")
                        return@use null
                    }
                    bytes.write(buffer, 0, count)
                }
                bytes.toString(Charsets.UTF_8.name())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnose("read failed: ${error.javaClass.simpleName}")
            null
        }
    }
    try {
        val completed = runInterruptible(Dispatchers.IO) { process.waitFor(6, TimeUnit.SECONDS) }
        if (!completed) {
            diagnose("process timed out after 6 seconds")
            return@coroutineScope null
        }
        val text = output.await()
        val denial = text?.contains("Permission Denial", ignoreCase = true) == true
        val timeout = text?.contains("DUMP TIMEOUT", ignoreCase = true) == true
        if (process.exitValue() != 0 || denial || timeout) {
            // Log only classification, never diagnostic content, package lists or process names.
            val permission = when {
                text?.contains("missing android.permission.PACKAGE_USAGE_STATS") == true -> "PACKAGE_USAGE_STATS"
                text?.contains("missing android.permission.DUMP") == true -> "DUMP"
                text?.contains("app-op not allowed") == true -> "usage app-op"
                denial -> "other"
                else -> "none"
            }
            diagnose("exit=${process.exitValue()} denial=$permission timeout=$timeout")
            null
        } else text
    } finally {
        process.destroy()
        readOrNull { process.inputStream.close() }
        output.cancel()
    }
}

private fun cleanSsid(value: String?): String? = value?.removeSurrounding("\"")
    ?.takeUnless { it.isBlank() || it == WifiManager.UNKNOWN_SSID || it == "0x" }

private fun notes(vararg parts: String?): String? = parts.filterNotNull().takeIf { it.isNotEmpty() }?.joinToString("；")

private fun hasPermission(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun hasUsageAccess(context: Context): Boolean = readOrNull {
    val manager = context.getSystemService(AppOpsManager::class.java) ?: return@readOrNull false
    when (manager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)) {
        AppOpsManager.MODE_ALLOWED -> true
        AppOpsManager.MODE_DEFAULT -> hasPermission(context, Manifest.permission.PACKAGE_USAGE_STATS)
        else -> false
    }
} ?: false

private inline fun <T> readOrNull(read: () -> T): T? = try {
    read()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
