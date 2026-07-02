package com.example.adb.engine

import android.content.Context

class BinderAndHardwarePlugin : CommandAnalyzerPlugin {
    override val name: String = "BinderAndHardwarePlugin"

    override fun canAnalyze(command: String): Boolean {
        val firstToken = command.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""
        return firstToken in listOf("service", "dumpsys", "lshal", "hal")
    }

    override fun analyze(command: String, context: Context): PreExecutionAnalysis {
        val tokens = command.trim().split(Regex("\\s+"))
        val firstToken = tokens.firstOrNull()?.lowercase() ?: ""
        val secondToken = tokens.getOrNull(1)?.lowercase() ?: ""

        var layer = ExecutionLayer.BINDER_SERVICES
        var requiredPrivilege = RequiredPrivilege.SHELL
        var riskLevel = RiskLevel.LOW
        var riskDescription = "Queries service state or queries IPC registers."
        var expectedEffect = "Talks to servicemanager to fetch system dump or list service interfaces."
        var isIgnoredBySystem = false
        var requiresReboot = false
        var isPersistent = false
        var estimatedTime = 200L
        var compatibility = "All Android APIs"
        var deviceSupport = "Standard IPC / HAL Infrastructure"

        when (firstToken) {
            "service" -> {
                layer = ExecutionLayer.BINDER_SERVICES
                expectedEffect = "Interacts with binder servicemanager registry."
                when (secondToken) {
                    "list" -> {
                        expectedEffect = "Lists all binder interfaces registered within servicemanager."
                        estimatedTime = 150L
                    }
                    "call" -> {
                        riskLevel = RiskLevel.HIGH
                        requiredPrivilege = RequiredPrivilege.SYSTEM
                        riskDescription = "Performs raw binder transaction on target interface index. Sending mismatched arguments can cause target process crashes, system restart, or system failure."
                        expectedEffect = "Dispatches a raw binder transactional call payload."
                        estimatedTime = 300L
                    }
                }
            }
            "dumpsys" -> {
                layer = ExecutionLayer.BINDER_SERVICES
                expectedEffect = "Requests a complete diagnostic text dump from registered binder services."
                estimatedTime = 1500L // Dumpsys takes much longer to resolve because it waits on all services
                if (secondToken.isNotEmpty()) {
                    expectedEffect = "Requests specific service diagnostic report: $secondToken"
                    estimatedTime = 300L
                }
                
                when (secondToken) {
                    "battery" -> {
                        riskDescription = "Queries battery metrics and registers. Safe to read, but 'dumpsys battery set level' commands are risky."
                        if (command.contains("set") || command.contains("reset")) {
                            riskLevel = RiskLevel.MEDIUM
                            riskDescription = "Overrides system-wide battery fuel gauge values and thermal measurements. Can lead to incorrect charging speed regulation or thermal shutdown."
                        }
                    }
                    "wifi", "connectivity", "telephony.registry" -> {
                        riskDescription = "Queries radio, baseband, and network service telemetry data. Possibility of revealing private location coordinates or network IDs."
                    }
                }
            }
            "lshal" -> {
                layer = ExecutionLayer.HAL
                expectedEffect = "Lists active Hardware Abstraction Layer implementations registered in hwservicemanager."
                estimatedTime = 250L
                riskLevel = RiskLevel.LOW
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
