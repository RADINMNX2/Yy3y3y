package com.example.adb.engine

import android.content.Context
import android.os.Build

class FrameworkPlugin : CommandAnalyzerPlugin {
    override val name: String = "AndroidFrameworkPlugin"

    override fun canAnalyze(command: String): Boolean {
        val firstToken = command.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""
        return firstToken in listOf("am", "pm", "settings", "wm", "input", "media", "telecom", "shortcut", "display")
    }

    override fun analyze(command: String, context: Context): PreExecutionAnalysis {
        val tokens = command.trim().split(Regex("\\s+"))
        val firstToken = tokens.firstOrNull()?.lowercase() ?: ""
        val secondToken = tokens.getOrNull(1)?.lowercase() ?: ""

        var layer = ExecutionLayer.ANDROID_FRAMEWORK
        var requiredPrivilege = RequiredPrivilege.SHELL
        var riskLevel = RiskLevel.LOW
        var riskDescription = "Safe reading/diagnostic command."
        var expectedEffect = "Interacts with SystemServer daemon."
        var isIgnoredBySystem = false
        var requiresReboot = false
        var isPersistent = false
        var estimatedTime = 100L
        var compatibility = "Android 5.0+ (API 21+)"
        var deviceSupport = "Standard AOSP implementation"

        when (firstToken) {
            "am" -> {
                layer = ExecutionLayer.ACTIVITY_MANAGER
                expectedEffect = "Dispatches intents or controls task stacks via ActivityManagerService (AMS)."
                estimatedTime = 150L
                when (secondToken) {
                    "start", "start-activity" -> {
                        riskLevel = RiskLevel.MEDIUM
                        riskDescription = "Launches an activity. Could cause screen flickering, overlay, or system state shifts."
                        expectedEffect = "Invokes startActivity() in AMS."
                    }
                    "force-stop" -> {
                        riskLevel = RiskLevel.MEDIUM
                        riskDescription = "Kills background applications abruptly, potentially causing unsaved user data loss."
                        expectedEffect = "Kills processes and cleans up task record trees."
                    }
                    "kill", "kill-all" -> {
                        riskLevel = RiskLevel.MEDIUM
                        riskDescription = "Kills background app processes."
                    }
                    "broadcast" -> {
                        riskLevel = RiskLevel.MEDIUM
                        riskDescription = "Dispatches arbitrary system broadcasts; could trigger security hooks or background handlers."
                    }
                }
            }
            "pm" -> {
                layer = ExecutionLayer.PACKAGE_MANAGER
                expectedEffect = "Interacts with PackageManagerService (PMS) to query or modify packages."
                estimatedTime = 250L
                when (secondToken) {
                    "install", "install-existing" -> {
                        riskLevel = RiskLevel.HIGH
                        riskDescription = "Modifies application packages. Can overwrite critical system components or install malicious software."
                        isPersistent = true
                    }
                    "uninstall", "clear" -> {
                        riskLevel = RiskLevel.HIGH
                        riskDescription = "Wipes package data or removes applications, resulting in irreversible user data loss."
                        isPersistent = true
                    }
                    "enable", "disable", "disable-user", "disable-until-used" -> {
                        riskLevel = RiskLevel.HIGH
                        riskDescription = "Toggles system component state. Disabling critical packages can trigger soft-reboots or bootloops."
                        requiredPrivilege = RequiredPrivilege.SYSTEM
                        isPersistent = true
                    }
                    "grant", "revoke" -> {
                        riskLevel = RiskLevel.HIGH
                        riskDescription = "Directly bypasses runtime permission prompts to grant/revoke app permissions."
                        isPersistent = true
                    }
                }
            }
            "settings" -> {
                layer = ExecutionLayer.SETTINGS_PROVIDER
                expectedEffect = "Modifies or queries the system SettingsProvider database."
                estimatedTime = 120L
                when (secondToken) {
                    "put" -> {
                        riskLevel = RiskLevel.MEDIUM
                        riskDescription = "Writes values directly to Settings database. High risk of breaking system UI, accessibility, or security policy."
                        isPersistent = true
                        
                        val table = tokens.getOrNull(2)?.lowercase() ?: ""
                        if (table == "global") {
                            requiredPrivilege = RequiredPrivilege.SYSTEM
                        }
                    }
                    "delete" -> {
                        riskLevel = RiskLevel.HIGH
                        riskDescription = "Deletes persistent system settings. Could corrupt specific framework services dependent on those records."
                        isPersistent = true
                    }
                }
            }
            "wm" -> {
                layer = ExecutionLayer.ANDROID_FRAMEWORK
                expectedEffect = "Queries or overrides window and display settings via WindowManagerService (WMS)."
                estimatedTime = 80L
                when (secondToken) {
                    "size", "density", "overscan" -> {
                        riskLevel = RiskLevel.HIGH
                        riskDescription = "Alters screen resolution or density parameters. Could lead to UI rendering freezes or system-wide layout issues."
                        isPersistent = true
                    }
                }
            }
            "input" -> {
                layer = ExecutionLayer.ANDROID_FRAMEWORK
                expectedEffect = "Injects low-level touchscreen, mouse, or hardware key events."
                estimatedTime = 100L
                riskLevel = RiskLevel.MEDIUM
                riskDescription = "Simulates physical interaction. Potential to trigger unexpected app operations or perform operations on the user's behalf."
            }
        }

        // Adjust for Android version check
        if (Build.VERSION.SDK_INT >= 34 && firstToken == "settings") {
            compatibility = "Android 14+ (Enforced strict namespace isolation in SettingsProvider)"
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
