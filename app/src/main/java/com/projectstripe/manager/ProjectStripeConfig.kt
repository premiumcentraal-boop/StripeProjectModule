package com.projectstripe.manager

object ProjectStripeConfig {
    const val APP_NAME = "Project Stripe"
    const val PACKAGE_NAME = "com.projectstripe.manager"
    const val VECTOR_REFERENCE_NAME = "Vector"
    const val MODULE_ROOT = "/data/adb/modules"

    val moduleNameHints = listOf("vector", "lsposed", "libxposed")
    val managerPackageHints = listOf("vector", "lsposed", "libxposed")

    val tools =
        listOf(
            ProjectStripeTool(
                title = "Refresh connection checks",
                description = "Run root, Magisk, Zygisk, Vector, and manager package checks again.",
                enabled = true,
            ),
            ProjectStripeTool(
                title = "Inspect local module config",
                description = "Read-only check for Vector or LSPosed-style folders under /data/adb/modules.",
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
