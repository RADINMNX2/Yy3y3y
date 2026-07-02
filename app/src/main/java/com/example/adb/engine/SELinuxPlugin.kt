package com.example.adb.engine

import android.content.Context

class SELinuxPlugin : CommandAnalyzerPlugin {
    override val name: String = "SELinuxPlugin"

    override fun canAnalyze(command: String): Boolean {
        val trimmed = command.trim()
        val firstToken = trimmed.split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""
        return firstToken in listOf("getenforce", "setenforce", "chcon", "restorecon") || trimmed.contains(" -Z")
    }

    override fun analyze(command: String, context: Context): PreExecutionAnalysis {
        val tokens = command.trim().split(Regex("\\s+"))
        val firstToken = tokens.firstOrNull()?.lowercase() ?: ""

        var layer = ExecutionLayer.SELINUX
        var requiredPrivilege = RequiredPrivilege.SHELL
        var riskLevel = RiskLevel.LOW
        var riskDescription = "Queries MAC labels or security status."
        var expectedEffect = "Queries or interacts with the kernel's SELinux subsystem."
        var isIgnoredBySystem = false
        var requiresReboot = false
        var isPersistent = false
        var estimatedTime = 30L
        var compatibility = "Android 4.3+ (Fully Enforcing from 5.0)"
        var deviceSupport = "Mandatory Access Control System"

        when (firstToken) {
            "setenforce" -> {
                layer = ExecutionLayer.ROOT_ONLY
                requiredPrivilege = RequiredPrivilege.ROOT
                riskLevel = RiskLevel.HIGH
                riskDescription = "Alters global SELinux mode (0 for Permissive, 1 for Enforcing). This relaxes all MAC security boundaries, posing extreme system-wide vulnerability risk."
                expectedEffect = "Updates runtime SELinux policy state to Permissive or Enforcing."
                
                val level = tokens.getOrNull(1) ?: ""
                if (level == "0" || level.equals("permissive", ignoreCase = true)) {
                    isPersistent = false // Resets to enforcing upon reboot unless boot script overrides
                }
            }
            "chcon" -> {
                requiredPrivilege = RequiredPrivilege.ROOT
                riskLevel = RiskLevel.HIGH
                riskDescription = "Directly modifies security context of a file. Could make system components unreadable to vital system processes."
                expectedEffect = "Updates context label."
            }
            "restorecon" -> {
                requiredPrivilege = RequiredPrivilege.SHELL
                riskLevel = RiskLevel.MEDIUM
                riskDescription = "Restores files to default security context based on policy files. Safe if restoring standard user files, risky on system folders."
                expectedEffect = "Reverts contexts back to policy defaults."
            }
            "getenforce" -> {
                expectedEffect = "Queries SELinux MAC policy mode (Enforcing, Permissive, or Disabled)."
            }
        }

        return PreExecutionAnalysis(
            command = command,
            layer = layer,
            requiredPrivilege = requiredPrivilege,
            androidVersionCompatibility = compatibility,
            deviceSpecificSupport = deviceSupport,
            expectedEffect = expectedEffect,
            isIgnoredBySystem = isIgnoredBySystem,
            requiresReboot = requiresReboot,
            isPersistent = isPersistent,
            riskLevel = riskLevel,
            riskDescription = riskDescription,
            estimatedExecutionTimeMs = estimatedTime
        )
    }
}
