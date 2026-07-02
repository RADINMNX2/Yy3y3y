package com.example.adb.engine

import android.content.Context
import android.os.Build

object CommandExecutionEngine {
    private val plugins = mutableListOf<CommandAnalyzerPlugin>()

    init {
        // Register default modular core plugins
        registerPlugin(FrameworkPlugin())
        registerPlugin(SystemPropertiesPlugin())
        registerPlugin(KernelAndVfsPlugin())
        registerPlugin(SELinuxPlugin())
        registerPlugin(BinderAndHardwarePlugin())
        registerPlugin(BootAndFastbootPlugin())
    }

    fun registerPlugin(plugin: CommandAnalyzerPlugin) {
        synchronized(plugins) {
            plugins.add(plugin)
        }
    }

    fun getPlugins(): List<CommandAnalyzerPlugin> = synchronized(plugins) { plugins.toList() }

    fun analyzePreExecution(command: String, context: Context): PreExecutionAnalysis {
        val trimmed = command.trim()
        
        // Find matching plugin
        val matchingPlugin = synchronized(plugins) {
            plugins.find { it.canAnalyze(trimmed) }
        }

        if (matchingPlugin != null) {
            return matchingPlugin.analyze(trimmed, context)
        }

        // Default fallback analysis for basic standard commands or unknown commands
        val isAdbShell = trimmed.startsWith("adb shell ")
        return PreExecutionAnalysis(
            command = trimmed,
            layer = if (isAdbShell) ExecutionLayer.ADB_SHELL else ExecutionLayer.UNKNOWN,
            requiredPrivilege = RequiredPrivilege.SHELL,
            androidVersionCompatibility = "Compatible with Android 5.0+ (API 21+)",
            deviceSpecificSupport = "Universal generic shell compatibility",
            expectedEffect = "Runs as a sub-process shell command within the container sandboxed namespace.",
            isIgnoredBySystem = false,
            requiresReboot = false,
            isPersistent = false,
            riskLevel = RiskLevel.LOW,
            riskDescription = "Standard terminal shell execution.",
            estimatedExecutionTimeMs = 50L
        )
    }

    fun analyzePostExecution(
        command: String, 
        output: String, 
        exitCode: Int, 
        timeMs: Long
    ): PostExecutionAnalysis {
        val trimmed = command.trim()
        val outLower = output.lowercase()

        val result: PostExecutionResult
        val details: String

        when {
            // Permission issues
            outLower.contains("permission denied") || outLower.contains("not allowed") || outLower.contains("secure exception") || outLower.contains("securityexception") || outLower.contains("security exception") -> {
                if (outLower.contains("requires root") || outLower.contains("su: not found") || outLower.contains("only root") || outLower.contains("uid 0")) {
                    result = PostExecutionResult.REQUIRES_ROOT
                    details = "Execution blocked. This command directly modifies protected namespaces or low-level configurations only accessible by Superuser UID 0."
                } else if (outLower.contains("signature")) {
                    result = PostExecutionResult.REQUIRES_SYSTEM_SIGNATURE
                    details = "Execution failed due to lack of Platform Certificate matching. Command requires android.uid.system signature."
                } else {
                    result = PostExecutionResult.PERMISSION_DENIED
                    details = "Operation rejected by SELinux MAC policy or Android framework sandbox."
                }
            }
            // Unsupported / Missing binary or interface
            outLower.contains("not found") || outLower.contains("no such file") || outLower.contains("unknown command") || outLower.contains("invalid command") || outLower.contains("is not recognized") -> {
                result = PostExecutionResult.UNSUPPORTED
                details = "Command binary, service handle, or shell wrapper is missing on this vendor build or Android API."
            }
            // Deprecated APIs
            outLower.contains("deprecated") || outLower.contains("obsolete") || outLower.contains("no longer supported") -> {
                result = PostExecutionResult.DEPRECATED
                details = "API or shell utility has been retired in modern Android versions."
            }
            // Require userdebug/eng build
            outLower.contains("only on userdebug") || outLower.contains("userdebug/eng build") || outLower.contains("not supported on production") || outLower.contains("not supported on user builds") -> {
                result = PostExecutionResult.REQUIRES_ENGINEERING_BUILD
                details = "Blocked. Security configurations restrict this action to engineering/userdebug OS compilations."
            }
            // Executed but ignored / No observable effect
            outLower.contains("ignored") || outLower.contains("no-op") || outLower.contains("nothing to do") || (exitCode == 0 && output.trim().isEmpty() && (trimmed.startsWith("setprop") || trimmed.startsWith("settings put"))) -> {
                if (trimmed.startsWith("setprop ro.")) {
                    result = PostExecutionResult.EXECUTED_BUT_IGNORED
                    details = "Write request complete, but system properties starting with 'ro.' are read-only and locked at boot phase."
                } else {
                    result = PostExecutionResult.NO_OBSERVABLE_EFFECT
                    details = "Execution completed with code 0 but returned an empty response. Verify configuration parameters."
                }
            }
            exitCode == 0 -> {
                result = PostExecutionResult.SUCCESSFULLY_EXECUTED
                details = "Command executed successfully. Handshook with target kernel/framework modules."
            }
            else -> {
                result = PostExecutionResult.FAILED_UNKNOWN
                details = "Execution completed with non-zero exit status ($exitCode). Error details: ${output.take(150)}"
            }
        }

        return PostExecutionAnalysis(result, details, timeMs)
    }

    // Helper to format ANSI Box for output stream
    fun formatPreExecutionAnsi(analysis: PreExecutionAnalysis): String {
        val colorCode = analysis.riskLevel.colorCode
        val headerColor = "35" // Magenta
        return """
[1;${headerColor}m┌────────────────── PRE-FLIGHT ANALYSIS ──────────────────┐[0m
[1m│ Command:[0m ${analysis.command.take(45).padEnd(45)} │
[1m│ Layer:[0m ${analysis.layer.displayName.padEnd(47)} │
[1m│ Privileges:[0m ${analysis.requiredPrivilege.displayName.padEnd(42)} │
[1m│ Version:[0m ${analysis.androidVersionCompatibility.padEnd(45)} │
[1m│ Specifics:[0m ${analysis.deviceSpecificSupport.padEnd(43)} │
[1m│ Risk Level:[0m [1;${colorCode}m${analysis.riskLevel.displayName.padEnd(41)}[0m │
[1m│ Reboot:[0m ${(if (analysis.requiresReboot) "Required" else "No").padEnd(46)} │
[1m│ Persistence:[0m ${(if (analysis.isPersistent) "Persistent" else "Temporary").padEnd(41)} │
[1m│ Est Time:[0m ${(analysis.estimatedExecutionTimeMs.toString() + " ms").padEnd(44)} │
[1;${headerColor}m└─────────────────────────────────────────────────────────┘[0m
""".trimIndent() + "\n"
    }

    fun formatPostExecutionAnsi(post: PostExecutionAnalysis): String {
        val statusColor = when (post.result) {
            PostExecutionResult.SUCCESSFULLY_EXECUTED -> "32" // Green
            PostExecutionResult.EXECUTED_BUT_IGNORED, PostExecutionResult.NO_OBSERVABLE_EFFECT -> "33" // Yellow
            else -> "31" // Red
        }
        val headerColor = "36" // Cyan
        return """
[1;${headerColor}m┌────────────────── POST-FLIGHT AUDIT ────────────────────┐[0m
[1m│ Status:[0m [1;${statusColor}m${post.result.name.padEnd(44)}[0m │
[1m│ Details:[0m ${post.details.take(44).padEnd(44)} │
[1m│ Act Time:[0m ${(post.actualExecutionTimeMs.toString() + " ms").padEnd(43)} │
[1;${headerColor}m└─────────────────────────────────────────────────────────┘[0m
""".trimIndent() + "\n"
    }
}
