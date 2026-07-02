package com.example.adb.engine

import android.content.Context
import android.os.Build

class SystemPropertiesPlugin : CommandAnalyzerPlugin {
    override val name: String = "SystemPropertiesPlugin"

    override fun canAnalyze(command: String): Boolean {
        val firstToken = command.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""
        return firstToken in listOf("getprop", "setprop")
    }

    override fun analyze(command: String, context: Context): PreExecutionAnalysis {
        val tokens = command.trim().split(Regex("\\s+"))
        val firstToken = tokens.firstOrNull()?.lowercase() ?: ""
        val propertyName = tokens.getOrNull(1) ?: ""

        var layer = ExecutionLayer.SYSTEM_PROPERTIES
        var requiredPrivilege = RequiredPrivilege.SHELL
        var riskLevel = RiskLevel.LOW
        var riskDescription = "Safe property read command."
        var expectedEffect = "Interacts with init's shared memory property area."
        var isIgnoredBySystem = false
        var requiresReboot = false
        var isPersistent = false
        var estimatedTime = 30L
        var compatibility = "All Android APIs"
        var deviceSupport = "Standard property registry"

        if (firstToken == "setprop") {
            estimatedTime = 60L
            expectedEffect = "Writes to a system property key. If starting with persist.*, it is written to /data/property."
            
            when {
                propertyName.startsWith("ro.") -> {
                    riskLevel = RiskLevel.LOW
                    isIgnoredBySystem = true
                    riskDescription = "Properties starting with 'ro.' are read-only and loaded at initial boot sequence. Changing them has no effect after boot."
                    expectedEffect = "Ignored by the system because read-only boot properties are immutable."
                }
                propertyName.startsWith("persist.") -> {
                    riskLevel = RiskLevel.HIGH
                    isPersistent = true
                    riskDescription = "Alters dynamic settings that persist across reboots. High risk of causing dynamic driver crashes or boot freezes if critical keys are altered."
                    requiredPrivilege = RequiredPrivilege.SYSTEM
                }
                propertyName.startsWith("sys.") || propertyName.startsWith("ctl.") -> {
                    riskLevel = RiskLevel.MEDIUM
                    riskDescription = "Controls runtime system state or triggers init services (e.g., stopping zygote or starting logd)."
                    requiredPrivilege = RequiredPrivilege.SYSTEM
                }
                else -> {
                    riskLevel = RiskLevel.MEDIUM
                    riskDescription = "Standard runtime volatile property override."
                }
            }
        } else if (firstToken == "getprop") {
            if (propertyName.isNotEmpty()) {
                expectedEffect = "Retrieves value for property key: $propertyName"
                if (propertyName == "ro.build.type" || propertyName == "ro.debuggable") {
                    expectedEffect = "Checks system build characteristic (user, userdebug, eng)."
                }
            } else {
                expectedEffect = "Dumps entire key-value property map of the active Android system."
                estimatedTime = 150L
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
