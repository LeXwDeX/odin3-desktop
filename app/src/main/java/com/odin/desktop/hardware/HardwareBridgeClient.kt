package com.odin.desktop.hardware

import com.odin.desktop.R
import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HardwareControlException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** Authenticated connection to the ADB-started shell bridge. Call from an IO dispatcher. */
internal object HardwareBridgeClient {
    private const val PORT = 18889

    fun request(context: Context, body: String): List<String> {
        require(body.length <= 384 && '\n' !in body && '\r' !in body)
        val token = try {
            File(context.noBackupFilesDir, "hardware_bridge/token").readText(Charsets.US_ASCII).trim()
                .also { check(it.matches(Regex("[0-9a-f]{64}"))) }
        } catch (error: Exception) {
            throw HardwareControlException(context.getString(R.string.text_hardware_control_is_not_connected_reconnect_the), error)
        }
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", PORT), 600)
                socket.soTimeout = 15_000
                val input = socket.getInputStream()
                val greeting = readLine(input).split('\t')
                if (greeting.size != 3 || greeting[0] != "ODIN1" ||
                    !greeting[1].matches(Regex("[0-9a-f]{64}")) ||
                    !matches(mac(token, "SERVER\n${greeting[1]}"), greeting[2])) {
                    throw HardwareControlException(context.getString(R.string.text_the_hardware_service_could_not_be_authenticated))
                }
                val nonce = greeting[1]
                val signed = "$body\t${mac(token, "CLIENT\n$nonce\n$body")}\n"
                socket.getOutputStream().write(signed.toByteArray(Charsets.US_ASCII))
                socket.getOutputStream().flush()
                val wire = readLine(input)
                val cut = wire.lastIndexOf('\t')
                if (cut < 0 || !matches(mac(token, "RESPONSE\n$nonce\n${wire.substring(0, cut)}"), wire.substring(cut + 1))) {
                    throw HardwareControlException(context.getString(R.string.text_the_hardware_response_could_not_be_verified))
                }
                val result = wire.substring(0, cut).split('\t')
                if (result.firstOrNull() != "OK") {
                    val reason = when (result.getOrNull(1)) {
                        "ROLLBACK_INCOMPLETE" -> context.getString(R.string.text_the_operation_was_incomplete_and_some_settings)
                        "READBACK_MISMATCH" -> context.getString(R.string.text_the_system_did_not_keep_the_selected)
                        "READ_UNAVAILABLE" -> context.getString(R.string.text_hardware_status_is_temporarily_unavailable_refresh_it)
                        "BAD_REQUEST" -> context.getString(R.string.text_this_hardware_operation_is_not_allowed)
                        "AUTH_FAILED" -> context.getString(R.string.text_hardware_authentication_failed_reconnect_through_adb)
                        else -> context.getString(R.string.text_the_system_rejected_the_operation_restoration_was)
                    }
                    throw HardwareControlException(reason)
                }
                return result.drop(1)
            }
        } catch (error: HardwareControlException) {
            throw error
        } catch (error: Exception) {
            throw HardwareControlException(context.getString(R.string.text_the_hardware_service_is_offline_or_timed), error)
        }
    }

    private fun readLine(input: InputStream): String {
        val bytes = java.io.ByteArrayOutputStream()
        repeat(1024) {
            val value = input.read()
            check(value >= 0) { "Incomplete bridge response" }
            if (value == 10) return bytes.toString("US-ASCII")
            check(value in 32..126 || value == 9) { "Invalid bridge response" }
            bytes.write(value)
        }
        error("Bridge response too long")
    }

    private fun mac(token: String, payload: String): String {
        val key = token.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val signer = Mac.getInstance("HmacSHA256")
        signer.init(SecretKeySpec(key, "HmacSHA256"))
        return signer.doFinal(payload.toByteArray(Charsets.US_ASCII)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun matches(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), actual.toByteArray(Charsets.US_ASCII))
}
