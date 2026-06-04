package com.projectstripe.manager

object ProjectStripeConfig {
    const val APP_NAME = "Project Stripe"
    const val PACKAGE_NAME = "com.projectstripe.manager"
    const val VECTOR_REFERENCE_NAME = "Vector"
    const val MODULE_ROOT = "/data/adb/modules"
    const val VECTOR_BASE_PATH = "/data/adb/lspd"
    const val VECTOR_CLI_PATH = "/data/adb/lspd/cli"
    const val VECTOR_SOCKET_PATH = "/data/adb/lspd/.cli_sock"
    const val VECTOR_CONFIG_PATH = "/data/adb/lspd/config"
    const val VECTOR_DATABASE_PATH = "/data/adb/lspd/config/modules_config.db"
    const val VECTOR_LOG_PATH = "/data/adb/lspd/log"
    const val VECTOR_MODULE_DATA_PATH = "/data/adb/lspd/modules"

    val moduleNameHints = listOf("vector", "lsposed", "libxposed")
    val managerPackageHints = listOf("vector", "lsposed", "libxposed")
    val safeVectorConfigKeys = setOf("status-notification", "verbose-log")

    val tools =
        listOf(
            ProjectStripeTool(
                title = "Refresh connection checks",
                description = "Run root, Magisk, Zygisk, Vector native path, CLI, module, and manager checks again.",
                enabled = true,
            ),
            ProjectStripeTool(
                title = "Inspect Vector native status",
                description = "Read Vector daemon status through /data/adb/lspd/cli when the CLI is available.",
                enabled = true,
            ),
            ProjectStripeTool(
                title = "Read Vector modules",
                description = "List Vector-reported Xposed modules without changing enabled state or scopes.",
                enabled = true,
            ),
            ProjectStripeTool(
                title = "Read Vector logs",
                description = "Pull module and verbose log output through the Vector CLI.",
                enabled = true,
            ),
            ProjectStripeTool(
                title = "Scope controls",
                description = "Status-only view. Project Stripe does not auto-enable external app scopes.",
                enabled = false,
            ),
            ProjectStripeTool(
                title = "Module installer",
                description = "Disabled by design. Install Magisk/Zygisk modules manually in Magisk.",
                enabled = false,
            ),
        )
}

data class ProjectStripeTool(
    val title: String,
    val description: String,
    val enabled: Boolean,
)
