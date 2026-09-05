package com.odin.desktop.hardware

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import com.odin.desktop.R
import com.odin.hardware.HardwareOperations
import com.odin.hardware.OemCommandCodec
import java.io.IOException

class HardwareControlException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** Calls the firmware's own service from our app UID. No pairing, socket or shell bridge. */
internal object HardwareControlClient {
    private val operations = HardwareOperations(OemStore())

    // HardwareOperations serializes complete transactions including rollback. Always use an IO thread.
    fun request(context: Context, body: String): List<String> {
        try {
            service() // Report missing firmware support separately from rejected writes.
            val result = operations.execute(body).split('\t')
            if (result.firstOrNull() != "OK") {
                val reason = when (result.getOrNull(1)) {
                    "ROLLBACK_INCOMPLETE" -> R.string.text_the_operation_was_incomplete_and_some_settings
                    "READBACK_MISMATCH" -> R.string.text_the_system_did_not_keep_the_selected
                    "READ_UNAVAILABLE" -> R.string.text_hardware_status_is_temporarily_unavailable_refresh_it
                    "BAD_REQUEST" -> R.string.text_this_hardware_operation_is_not_allowed
                    else -> R.string.text_the_system_rejected_the_operation_restoration_was
                }
                throw HardwareControlException(context.getString(reason))
            }
            return result.drop(1)
        } catch (error: HardwareControlException) {
            throw error
        } catch (error: Exception) {
            throw HardwareControlException(context.getString(R.string.hardware_oem_service_unavailable), error)
        }
    }

    private fun service(): IBinder =
        (Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "PServerBinder") as? IBinder)?.takeIf { it.isBinderAlive }
            ?: throw IOException("OEM hardware service unavailable")

    private class OemStore : HardwareOperations.CommandStore() {
        override fun putAll(entries: MutableMap<String, String>) {
            // Firmware truncates commands at 255 bytes. Keep each Settings write bounded;
            // HardwareOperations still verifies and rolls back the complete transaction.
            entries.forEach { (key, value) -> put(key, value) }
        }

        override fun command(vararg arguments: String): String {
            // Only fixed HardwareOperations commands reach this private transport.
            val command = OemCommandCodec.encode(*arguments)
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                // OEM protocol: transaction 0, string array [command, wait-for-result], byte-array reply.
                data.writeStringArray(arrayOf(command, "1"))
                check(service().transact(0, data, reply, 0)) { "OEM operation unhandled" }
                return OemCommandCodec.decode(reply.createByteArray())
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}
