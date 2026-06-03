package com.projectstripe.manager

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VectorConnectionChecker(
    private val context: Context,
    private val magiskStatusChecker: MagiskStatusChecker = MagiskStatusChecker(),
) {
    suspend fun check(): ProjectStripeStatus =
        withContext(Dispatchers.IO) {
            ProjectStripeLogger.log("Starting Project Stripe connection checks.")
            val magisk = magiskStatusChecker.check()
            ProjectStripeLogger.log("Root status: ${magisk.root.label}.")
            ProjectStripeLogger.log("Magisk status: ${magisk.magiskVersion.label}.")
            ProjectStripeLogger.log("Zygisk status: ${magisk.zygisk.label}.")

            val moduleCheck = checkModuleFolders(magisk.root)
            val managers = findManagerPackages()
            val nextStep = resolveNextStep(magisk, moduleCheck)

            ProjectStripeLogger.log("Vector module status: ${moduleCheck.status.label}.")
            ProjectStripeLogger.log("Installed manager package matches: ${managers.ifEmpty { listOf("none") }.joinToString()}.")
            ProjectStripeLogger.log("Next setup step: $nextStep")

            ProjectStripeStatus(
                magiskStatus = magisk,
                moduleCheck = moduleCheck,
                managerPackages = managers,
                nextStep = nextStep,
            )
        }

    private suspend fun checkModuleFolders(root: StatusValue): VectorModuleCheck {
        if (root is StatusValue.Missing) {
            return VectorModuleCheck(StatusValue.Unknown("Root missing; module folders cannot be checked."), emptyList())
        }

        val result = magiskStatusChecker.runRootCommand("ls -1 ${ProjectStripeConfig.MODULE_ROOT}")
        if (!result.success || result.output.isBlank()) {
            return VectorModuleCheck(StatusValue.Missing("Install Vector as a Magisk Zygisk module."), emptyList())
        }

        val folders = result.output.lines().map { it.trim() }.filter { it.isNotBlank() }
        val matches =
            folders.filter { folder ->
                ProjectStripeConfig.moduleNameHints.any { hint -> folder.contains(hint, ignoreCase = true) }
            }

        return if (matches.isNotEmpty()) {
            VectorModuleCheck(StatusValue.Present("Vector connected."), matches)
        } else {
            VectorModuleCheck(StatusValue.Missing("Install Vector as a Magisk Zygisk module."), folders)
        }
    }

    private fun findManagerPackages(): List<String> {
        return try {
            val packages =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getInstalledPackages(0)
                }

            packages
                .map { it.packageName }
                .filter { packageName ->
                    ProjectStripeConfig.managerPackageHints.any { hint ->
                        packageName.contains(hint, ignoreCase = true)
                    }
                }
                .sorted()
        } catch (error: Throwable) {
            ProjectStripeLogger.log("Manager package scan failed: ${error.message ?: "unknown error"}.")
            emptyList()
        }
    }

    private fun resolveNextStep(magisk: MagiskStatus, moduleCheck: VectorModuleCheck): String {
        return when {
            magisk.root is StatusValue.Missing -> "Root is required."
            magisk.magiskVersion is StatusValue.Missing -> "Install Magisk first."
            magisk.zygisk is StatusValue.Missing -> "Enable Zygisk in Magisk and reboot."
            moduleCheck.status is StatusValue.Missing -> "Install Vector as a Magisk Zygisk module."
            moduleCheck.status is StatusValue.Present -> "Vector connected."
            else -> "Review setup status."
        }
    }
}

data class ProjectStripeStatus(
    val magiskStatus: MagiskStatus,
    val moduleCheck: VectorModuleCheck,
    val managerPackages: List<String>,
    val nextStep: String,
) {
    companion object {
        fun initial(): ProjectStripeStatus =
            ProjectStripeStatus(
                magiskStatus =
                    MagiskStatus(
                        root = StatusValue.Unknown("Not checked yet."),
                        magiskVersion = StatusValue.Unknown("Not checked yet."),
                        zygisk = StatusValue.Unknown("Not checked yet."),
                        androidSdk = Build.VERSION.SDK_INT,
                        deviceCodename = "Unknown",
                        commandResults = emptyList(),
                    ),
                moduleCheck = VectorModuleCheck(StatusValue.Unknown("Not checked yet."), emptyList()),
                managerPackages = emptyList(),
                nextStep = "Run setup checks.",
            )
    }
}

data class VectorModuleCheck(
    val status: StatusValue,
    val detectedFolders: List<String>,
)
