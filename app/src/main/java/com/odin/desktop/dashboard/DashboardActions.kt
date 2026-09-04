package com.odin.desktop.dashboard

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import com.odin.desktop.shader.control.ShaderControlActivity

/** Navigation for the dashboard's four explicit actions. */
class DashboardActions(private val activity: Activity) {
    fun execute(action: DashboardAction) {
        if (activity.isFinishing || activity.isDestroyed) return
        when (action) {
            DashboardAction.FILES -> openFiles()
            DashboardAction.SYSTEM_SETTINGS -> openOrExplain(Intent(Settings.ACTION_SETTINGS))
            DashboardAction.ODIN_SETTINGS -> openOrExplain(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .setComponent(ComponentName(
                        "com.odin.settings",
                        "com.ro.settings.activity.MainSettingsActivity"
                    ))
            )
            DashboardAction.FILTERS -> openOrExplain(
                Intent(activity, ShaderControlActivity::class.java)
                    .putExtra(ShaderControlActivity.EXTRA_PREVIEW_ONLY, true)
            )
        }
    }

    private fun openFiles() {
        val filesPackage = "com.android.documentsui"
        val launcher = activity.packageManager.getLaunchIntentForPackage(filesPackage)
        val browseRoot = Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary"),
                DocumentsContract.Root.MIME_TYPE_ITEM
            )
            .setPackage(filesPackage)
        openOrExplain(
            *listOfNotNull(launcher, browseRoot).toTypedArray()
        )
    }

    private fun openOrExplain(vararg intents: Intent) {
        for (intent in intents) {
            if (intent.resolveActivity(activity.packageManager) == null) continue
            try {
                activity.startActivity(intent)
                return
            } catch (_: RuntimeException) {
                // Try the next standard destination when a vendor activity is unavailable.
            }
        }
        toast("无法打开这个系统页面。")
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

}
