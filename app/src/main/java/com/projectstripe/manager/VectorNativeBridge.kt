package com.projectstripe.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VectorNativeBridge(
    private val magiskStatusChecker: MagiskStatusChecker = MagiskStatusChecker(),
) {
    suspend fun inspect(root: StatusValue): VectorNativeSnapshot =
        withContext(Dispatchers.IO) {
            if (root is StatusValue.Missing) {
                ProjectStripeLogger.log("Vector native inspection skipped because root is missing.")
                return@withContext VectorNativeSnapshot.rootMissing()
            }

            val paths =
                VectorNativePaths(
                    base = checkPath(ProjectStripeConfig.VECTOR_BASE_PATH, PathCheck.Directory),
                    cli = checkPath(ProjectStripeConfig.VECTOR_CLI_PATH, PathCheck.File),
                    socket = checkPath(ProjectStripeConfig.VECTOR_SOCKET_PATH, PathCheck.Socket),
                    config = checkPath(ProjectStripeConfig.VECTOR_CONFIG_PATH, PathCheck.Directory),
                    database = checkPath(ProjectStripeConfig.VECTOR_DATABASE_PATH, PathCheck.File),
                    logs = checkPath(ProjectStripeConfig.VECTOR_LOG_PATH, PathCheck.Directory),
                    moduleData = checkPath(ProjectStripeConfig.VECTOR_MODULE_DATA_PATH, PathCheck.Directory),
                )
            val moduleMetadata = readModuleMetadata()
            val cliAvailable = paths.cli is StatusValue.Present
            val cliStatus = if (cliAvailable) readCliStatus() else emptyMap()
            val modules = if (cliAvailable) readModules() else emptyList()
            val statusNotification = if (cliAvailable) readConfigValue("status-notification") else StatusValue.Unknown("Vector CLI is missing.")
            val verboseLog = if (cliAvailable) readConfigValue("verbose-log") else StatusValue.Unknown("Vector CLI is missing.")
            val nativeConnection = resolveNativeConnection(paths, cliStatus)

            ProjectStripeLogger.log("Vector native base path: ${paths.base.label}.")
            ProjectStripeLogger.log("Vector native CLI: ${paths.cli.label}.")
            ProjectStripeLogger.log("Vector daemon socket: ${paths.socket.label}.")
            ProjectStripeLogger.log("Vector CLI modules found: ${modules.size}.")

            VectorNativeSnapshot(
                connection = nativeConnection,
                paths = paths,
                moduleMetadata = moduleMetadata,
                cliStatus = cliStatus,
                modules = modules,
                statusNotification = statusNotification,
                verboseLog = verboseLog,
            )
        }

    suspend fun refreshCliStatus(): VectorActionResult =
        withContext(Dispatchers.IO) {
            val response = runCli("status")
            val data = response.data
            val body =
                when {
                    response.success && data is JSONObject -> data.toPrettyStringMap().toDisplayText()
                    response.success -> response.rawOutput.ifBlank { "Vector CLI status returned successfully." }
                    else -> response.error.ifBlank { response.rawOutput.ifBlank { "Vector CLI status failed." } }
                }
            ProjectStripeLogger.log("Vector CLI status refresh: ${body.takeForLog()}")
            VectorActionResult(response.success, "Vector CLI status", body)
        }

    suspend fun readModulesForLog(): VectorActionResult =
        withContext(Dispatchers.IO) {
            val response = runCli("modules ls")
            val data = response.data
            if (!response.success || data !is JSONArray) {
                val body = response.error.ifBlank { response.rawOutput.ifBlank { "Vector module list is unavailable." } }
                ProjectStripeLogger.log("Vector module list failed: ${body.takeForLog()}")
                return@withContext VectorActionResult(false, "Vector modules", body)
            }

            val modules = parseModuleRecords(data)
            val body =
                if (modules.isEmpty()) {
                    "No modules reported by the Vector CLI."
                } else {
                    modules.joinToString("\n") { "${it.packageName} - ${it.status} - uid ${it.uid}" }
                }
            ProjectStripeLogger.log("Vector module list: ${body.takeForLog()}")
            VectorActionResult(true, "Vector modules", body)
        }

    suspend fun readVectorLog(verbose: Boolean): VectorActionResult =
        withContext(Dispatchers.IO) {
            val command = if (verbose) "log cat -v" else "log cat"
            val result = magiskStatusChecker.runRootCommand("${ProjectStripeConfig.VECTOR_CLI_PATH} --json $command", timeoutSeconds = 12)
            val label = if (verbose) "Vector verbose log" else "Vector module log"
            val fallback =
                if (result.output.isBlank()) {
                    readLogFiles(verbose)
                } else {
                    null
                }
            val body =
                result.output.ifBlank {
                    fallback?.output?.ifBlank { fallback.error } ?: result.error.ifBlank { "No log output returned." }
                }
            ProjectStripeLogger.log("$label read: ${body.takeForLog()}")
            VectorActionResult(result.success || fallback?.success == true, label, body)
        }

    suspend fun setConfigValue(key: String, enabled: Boolean): VectorActionResult =
        withContext(Dispatchers.IO) {
            if (key !in ProjectStripeConfig.safeVectorConfigKeys) {
                val message = "Unsupported Vector config key: $key"
                ProjectStripeLogger.log(message)
                return@withContext VectorActionResult(false, "Vector config", message)
            }

            val response = runCli("config set $key ${enabled.toString()}")
            val body =
                when {
                    response.success && response.rawOutput.isNotBlank() -> response.rawOutput
                    response.success -> "Successfully set $key to $enabled."
                    else -> response.error.ifBlank { response.rawOutput.ifBlank { "Vector config update failed." } }
                }
            ProjectStripeLogger.log("Vector config update: $key=$enabled. ${body.takeForLog()}")
            VectorActionResult(response.success, "Vector config", body)
        }

    private suspend fun checkPath(path: String, type: PathCheck): StatusValue {
        val operator =
            when (type) {
                PathCheck.Directory -> "-d"
                PathCheck.File -> "-f"
                PathCheck.Socket -> "-S"
            }
        val result = magiskStatusChecker.runRootCommand("if [ $operator ${shellQuote(path)} ]; then echo present; else echo missing; fi")
        return when {
            result.success && result.output.trim() == "present" -> StatusValue.Present(path)
            result.success -> StatusValue.Missing(path)
            else -> StatusValue.Unknown(result.error.ifBlank { "Unable to check $path." })
        }
    }

    private suspend fun readModuleMetadata(): List<VectorModuleMetadata> {
        val foldersResult = magiskStatusChecker.runRootCommand("ls -1 ${ProjectStripeConfig.MODULE_ROOT}")
        if (!foldersResult.success || foldersResult.output.isBlank()) return emptyList()

        return foldersResult.output
            .lines()
            .map { it.trim() }
            .filter { it.matches(Regex("[A-Za-z0-9._-]+")) }
            .mapNotNull { folder ->
                val propPath = "${ProjectStripeConfig.MODULE_ROOT}/$folder/module.prop"
                val propResult = magiskStatusChecker.runRootCommand("cat ${shellQuote(propPath)}")
                if (!propResult.success || propResult.output.isBlank()) return@mapNotNull null

                val props = parseModuleProp(propResult.output)
                val haystack = (listOf(folder) + props.keys + props.values).joinToString(" ")
                if (ProjectStripeConfig.moduleNameHints.none { haystack.contains(it, ignoreCase = true) }) {
                    return@mapNotNull null
                }

                val disabledResult =
                    magiskStatusChecker.runRootCommand(
                        "if [ -f ${shellQuote("${ProjectStripeConfig.MODULE_ROOT}/$folder/disable")} ]; then echo disabled; else echo enabled; fi"
                    )

                VectorModuleMetadata(
                    folder = folder,
                    id = props["id"],
                    name = props["name"],
                    version = props["version"],
                    versionCode = props["versionCode"],
                    description = props["description"],
                    enabled = disabledResult.output.trim() != "disabled",
                )
            }
    }

    private suspend fun readCliStatus(): Map<String, String> {
        val response = runCli("status")
        val data = response.data
        return if (response.success && data is JSONObject) {
            data.toPrettyStringMap()
        } else {
            emptyMap()
        }
    }

    private suspend fun readModules(): List<VectorModuleRecord> {
        val response = runCli("modules ls")
        val data = response.data
        if (!response.success || data !is JSONArray) return emptyList()

        return parseModuleRecords(data)
    }

    private fun parseModuleRecords(data: JSONArray): List<VectorModuleRecord> {
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                add(
                    VectorModuleRecord(
                        packageName = item.optString("PACKAGE", "Unknown"),
                        uid = item.optString("UID", "Unknown"),
                        status = item.optString("STATUS", "Unknown"),
                    )
                )
            }
        }
    }

    private suspend fun readLogFiles(verbose: Boolean): CommandResult {
        val pattern = if (verbose) "verbose*.log" else "modules*.log"
        val command =
            "for f in ${ProjectStripeConfig.VECTOR_LOG_PATH}/$pattern; do " +
                "[ -f \"\$f\" ] && echo \"--- \$f ---\" && tail -n 200 \"\$f\"; " +
                "done"
        return magiskStatusChecker.runRootCommand(command, timeoutSeconds = 12)
    }

    private suspend fun readConfigValue(key: String): StatusValue {
        val response = runCli("config get $key")
        val data = response.data
        if (!response.success || data !is JSONObject) {
            return StatusValue.Unknown(response.error.ifBlank { response.rawOutput.ifBlank { "Vector config value unavailable." } })
        }

        val value = data.opt("VALUE")?.toString()?.takeIf { it.isNotBlank() }
        return if (value != null) {
            StatusValue.Present("$key = $value")
        } else {
            StatusValue.Unknown("$key is unavailable.")
        }
    }

    private suspend fun runCli(arguments: String): VectorCliResult {
        val result = magiskStatusChecker.runRootCommand("${ProjectStripeConfig.VECTOR_CLI_PATH} --json $arguments", timeoutSeconds = 8)
        val raw = result.output.trim()
        if (!result.success) {
            return VectorCliResult(false, null, result.error.ifBlank { raw }, raw)
        }
        if (!raw.startsWith("{")) {
            return VectorCliResult(true, raw, "", raw)
        }

        return runCatching {
            val json = JSONObject(raw)
            val data = json.opt("data").takeUnless { it == JSONObject.NULL }
            VectorCliResult(
                success = json.optBoolean("success", false),
                data = data,
                error = json.optString("error", "").takeIf { it.isNotBlank() && it != "null" } ?: "",
                rawOutput = raw,
            )
        }.getOrElse { error ->
            VectorCliResult(false, null, error.message ?: "Unable to parse Vector CLI response.", raw)
        }
    }

    private fun resolveNativeConnection(paths: VectorNativePaths, cliStatus: Map<String, String>): StatusValue {
        return when {
            paths.base is StatusValue.Missing -> StatusValue.Missing("Vector base folder is missing.")
            paths.cli is StatusValue.Missing -> StatusValue.Missing("Vector CLI is missing at ${ProjectStripeConfig.VECTOR_CLI_PATH}.")
            paths.socket is StatusValue.Missing -> StatusValue.Unknown("Vector CLI exists, but daemon socket is missing.")
            cliStatus.isNotEmpty() -> StatusValue.Present("Vector connected.")
            else -> StatusValue.Unknown("Vector native files exist, but CLI status did not respond.")
        }
    }

    private fun parseModuleProp(output: String): Map<String, String> =
        output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0].trim() to parts[1].trim()
            }

    private fun JSONObject.toPrettyStringMap(): Map<String, String> =
        keys().asSequence().associateWith { key -> opt(key)?.toString() ?: "Unknown" }

    private fun Map<String, String>.toDisplayText(): String =
        entries.joinToString("\n") { "${it.key}: ${it.value}" }

    private fun String.takeForLog(): String =
        replace("\n", " ").let { if (it.length > 300) it.take(300) + "..." else it }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private enum class PathCheck {
        Directory,
        File,
        Socket,
    }
}

data class VectorNativeSnapshot(
    val connection: StatusValue,
    val paths: VectorNativePaths,
    val moduleMetadata: List<VectorModuleMetadata>,
    val cliStatus: Map<String, String>,
    val modules: List<VectorModuleRecord>,
    val statusNotification: StatusValue,
    val verboseLog: StatusValue,
) {
    companion object {
        fun initial(): VectorNativeSnapshot =
            VectorNativeSnapshot(
                connection = StatusValue.Unknown("Not checked yet."),
                paths = VectorNativePaths.initial(),
                moduleMetadata = emptyList(),
                cliStatus = emptyMap(),
                modules = emptyList(),
                statusNotification = StatusValue.Unknown("Not checked yet."),
                verboseLog = StatusValue.Unknown("Not checked yet."),
            )

        fun rootMissing(): VectorNativeSnapshot =
            initial().copy(
                connection = StatusValue.Unknown("Root missing; Vector native paths cannot be checked."),
                paths = VectorNativePaths.rootMissing(),
                statusNotification = StatusValue.Unknown("Root missing."),
                verboseLog = StatusValue.Unknown("Root missing."),
            )
    }
}

data class VectorNativePaths(
    val base: StatusValue,
    val cli: StatusValue,
    val socket: StatusValue,
    val config: StatusValue,
    val database: StatusValue,
    val logs: StatusValue,
    val moduleData: StatusValue,
) {
    companion object {
        fun initial(): VectorNativePaths =
            VectorNativePaths(
                base = StatusValue.Unknown("Not checked yet."),
                cli = StatusValue.Unknown("Not checked yet."),
                socket = StatusValue.Unknown("Not checked yet."),
                config = StatusValue.Unknown("Not checked yet."),
                database = StatusValue.Unknown("Not checked yet."),
                logs = StatusValue.Unknown("Not checked yet."),
                moduleData = StatusValue.Unknown("Not checked yet."),
            )

        fun rootMissing(): VectorNativePaths =
            VectorNativePaths(
                base = StatusValue.Unknown("Root missing."),
                cli = StatusValue.Unknown("Root missing."),
                socket = StatusValue.Unknown("Root missing."),
                config = StatusValue.Unknown("Root missing."),
                database = StatusValue.Unknown("Root missing."),
                logs = StatusValue.Unknown("Root missing."),
                moduleData = StatusValue.Unknown("Root missing."),
            )
    }
}

data class VectorModuleMetadata(
    val folder: String,
    val id: String?,
    val name: String?,
    val version: String?,
    val versionCode: String?,
    val description: String?,
    val enabled: Boolean,
) {
    val displayName: String
        get() = name ?: id ?: folder

    val summary: String
        get() =
            buildList {
                add("Folder: $folder")
                id?.let { add("ID: $it") }
                version?.let { add("Version: $it") }
                versionCode?.let { add("Version code: $it") }
                add("Magisk state: ${if (enabled) "enabled" else "disabled"}")
                description?.let { add(it) }
            }.joinToString("\n")
}

data class VectorModuleRecord(
    val packageName: String,
    val uid: String,
    val status: String,
)

data class VectorCliResult(
    val success: Boolean,
    val data: Any?,
    val error: String,
    val rawOutput: String,
)

data class VectorActionResult(
    val success: Boolean,
    val title: String,
    val body: String,
)
