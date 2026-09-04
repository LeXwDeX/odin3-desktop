package com.odin.desktop.hardware

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
            throw HardwareControlException("硬件控制尚未连接，请通过 ADB 重新连接控制服务。", error)
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
                    throw HardwareControlException("硬件控制服务身份验证失败，请通过 ADB 重新连接。")
                }
                val nonce = greeting[1]
                val signed = "$body\t${mac(token, "CLIENT\n$nonce\n$body")}\n"
                socket.getOutputStream().write(signed.toByteArray(Charsets.US_ASCII))
                socket.getOutputStream().flush()
                val wire = readLine(input)
                val cut = wire.lastIndexOf('\t')
                if (cut < 0 || !matches(mac(token, "RESPONSE\n$nonce\n${wire.substring(0, cut)}"), wire.substring(cut + 1))) {
                    throw HardwareControlException("硬件控制返回了无法验证的响应，请刷新状态后重试。")
                }
                val result = wire.substring(0, cut).split('\t')
                if (result.firstOrNull() != "OK") {
                    val reason = when (result.getOrNull(1)) {
                        "ROLLBACK_INCOMPLETE" -> "操作未全部完成，部分旧状态未能恢复，请检查设备设置。"
                        "READBACK_MISMATCH" -> "系统未保持所选设置，已尝试恢复原状态。"
                        "READ_UNAVAILABLE" -> "硬件状态暂时无法确认，请稍后刷新。"
                        "BAD_REQUEST" -> "此硬件操作不在允许范围内。"
                        "AUTH_FAILED" -> "硬件控制认证失败，请通过 ADB 重新连接。"
                        else -> "系统拒绝了硬件操作，已尝试恢复原状态。"
                    }
                    throw HardwareControlException(reason)
                }
                return result.drop(1)
            }
        } catch (error: HardwareControlException) {
            throw error
        } catch (error: Exception) {
            throw HardwareControlException("硬件控制服务未连接或响应超时，请通过 ADB 重新连接并刷新状态。", error)
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
