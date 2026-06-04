package com.projectstripe.manager

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MagiskStatusChecker {
    suspend fun check(): MagiskStatus =
        withContext(Dispatchers.IO) {
            val root = runRootCommand("id")
            val magisk = runRootCommand("magisk -v")
            val zygisk = runRootCommand("magisk --sqlite \"SELECT value FROM settings WHERE key='zygisk';\"")
            val device = runCommand("getprop", "ro.product.device")

            val rootState =
                when {
                    root.success && root.output.contains("uid=0") -> StatusValue.Present(root.output)
                    root.success -> StatusValue.Unknown(root.output.ifBlank { "su returned without uid=0" })
                    else -> StatusValue.Missing(root.error.ifBlank { "Root is required." })
                }

            val magiskState =
                when {
                    magisk.success && magisk.output.isNotBlank() -> StatusValue.Present(magisk.output)
                    rootState is StatusValue.Missing -> StatusValue.Unknown("Root missing; Magisk cannot be checked.")
                    else -> StatusValue.Missing("Install Magisk first.")
                }

            val zygiskState =
                when {
                    zygisk.success && zygisk.output.trim() == "1" -> StatusValue.Present("Enabled")
                    zygisk.success && zygisk.output.isNotBlank() -> StatusValue.Missing("Enable Zygisk in Magisk and reboot.")
                    magiskState is StatusValue.Missing -> StatusValue.Unknown("Magisk missing; Zygisk cannot be checked.")
                    else -> StatusValue.Unknown("Zygisk status is not available from Magisk.")
                }

            MagiskStatus(
                root = rootState,
                magiskVersion = magiskState,
                zygisk = zygiskState,
                androidSdk = Build.VERSION.SDK_INT,
                deviceCodename = device.output.ifBlank { "Unknown" },
                commandResults = listOf(root, magisk, zygisk, device),
            )
        }

    suspend fun runRootCommand(command: String, timeoutSeconds: Long = 5): CommandResult =
        runCommandWithTimeout(timeoutSeconds, "su", "-c", command)

    suspend fun runCommand(vararg command: String): CommandResult =
        runCommandWithTimeout(5, *command)

    private suspend fun runCommandWithTimeout(timeoutSeconds: Long, vararg command: String): CommandResult =
        withContext(Dispatchers.IO) {
            val display = command.joinToString(" ")
            try {
                val process = ProcessBuilder(*command).redirectErrorStream(true).start()
                val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return@withContext CommandResult(display, false, "", "Timed out")
                }
                val output = process.inputStream.bufferedReader().readText().trim()
                CommandResult(display, process.exitValue() == 0, output, if (process.exitValue() == 0) "" else output)
            } catch (error: Throwable) {
                CommandResult(display, false, "", error.message ?: "Command failed")
            }
        }
}

data class MagiskStatus(
    val root: StatusValue,
    val magiskVersion: StatusValue,
    val zygisk: StatusValue,
    val androidSdk: Int,
    val deviceCodename: String,
    val commandResults: List<CommandResult>,
)

data class CommandResult(
    val command: String,
    val success: Boolean,
    val output: String,
    val error: String,
)

sealed class StatusValue {
    data class Present(val message: String) : StatusValue()
    data class Missing(val reason: String) : StatusValue()
    data class Unknown(val reason: String) : StatusValue()

    val label: String
        get() =
            when (this) {
                is Present -> "Present"
                is Missing -> "Missing"
                is Unknown -> "Unknown"
            }

    val detail: String
        get() =
            when (this) {
                is Present -> message
                is Missing -> reason
                is Unknown -> reason
            }
}
