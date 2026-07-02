package com.example.adb.engine

import android.content.Context

class BootAndFastbootPlugin : CommandAnalyzerPlugin {
    override val name: String = "BootAndFastbootPlugin"

    override fun canAnalyze(command: String): Boolean {
        val firstToken = command.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""
        return firstToken in listOf("reboot", "fastboot")
    }

    override fun analyze(command: String, context: Context): PreExecutionAnalysis {
        val tokens = command.trim().split(Regex("\\s+"))
        val firstToken = tokens.firstOrNull()?.lowercase() ?: ""
        val secondToken = tokens.getOrNull(1)?.lowercase() ?: ""

        var layer = ExecutionLayer.ADB_SHELL
        var requiredPrivilege = RequiredPrivilege.SHELL
        var riskLevel = RiskLevel.MEDIUM
        var riskDescription = "Triggers hardware reboot cycles."
        var expectedEffect = "Instructs system power manager to gracefully or abruptly recycle runtime state."
        var isIgnoredBySystem = false
        var requiresReboot = true
        var isPersistent = true
        var estimatedTime = 2000L
        var compatibility = "All Android APIs"
        var deviceSupport = "Standard Bootloader/Kernel Interface"

        when (firstToken) {
            "reboot" -> {
                layer = ExecutionLayer.READ_ONLY_BOOT_PROPERTIES
                riskLevel = RiskLevel.HIGH
                riskDescription = "Initiates instant warm or cold system restart. Terminal connection will sever immediately."
                
                when (secondToken) {
                    "recovery" -> {
                        riskDescription = "Reboots device directly into recovery partition mode (e.g., TWRP or AOSP Recovery)."
                        expectedEffect = "Executes sys.powerctl = reboot,recovery."
                    }
                    "bootloader" -> {
                        riskDescription = "Reboots device directly into the fastboot bootloader screen to edit flash partitions."
                        expectedEffect = "Executes sys.powerctl = reboot,bootloader."
                    }
                }
            }
            "fastboot" -> {
                layer = ExecutionLayer.FASTBOOT
                riskLevel = RiskLevel.LOW
                isIgnoredBySystem = true
                riskDescription = "Fastboot protocol operates in bootloader pre-OS state. Sending this inside a running ADB shell does not execute; listed purely for instructional awareness."
                expectedEffect = "None inside shell. Re-run command in computer terminal after booting into fastboot."
                requiresReboot = false
                isPersistent = false
                estimatedTime = 10L
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
