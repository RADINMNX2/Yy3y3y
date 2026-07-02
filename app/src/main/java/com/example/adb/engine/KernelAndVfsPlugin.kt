package com.example.adb.engine

import android.content.Context

class KernelAndVfsPlugin : CommandAnalyzerPlugin {
    override val name: String = "KernelAndVfsPlugin"

    override fun canAnalyze(command: String): Boolean {
        val trimmed = command.trim()
        val firstToken = trimmed.split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""
        return firstToken in listOf("dmesg", "uname", "sysctl", "lsmod", "insmod", "rmmod") ||
                trimmed.contains("/proc") ||
                trimmed.contains("/sys/") ||
                trimmed.startsWith("cat ") && (trimmed.contains("proc/") || trimmed.contains("sys/"))
    }

    override fun analyze(command: String, context: Context): PreExecutionAnalysis {
        val trimmed = command.trim()
        val firstToken = trimmed.split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""

        var layer = ExecutionLayer.LINUX_KERNEL
        var requiredPrivilege = RequiredPrivilege.SHELL
        var riskLevel = RiskLevel.LOW
        var riskDescription = "Normal query on virtual file systems or kernel properties."
        var expectedEffect = "Interacts with Linux kernel interfaces directly."
        var isIgnoredBySystem = false
        var requiresReboot = false
        var isPersistent = false
        var estimatedTime = 40L
        var compatibility = "All Android Linux Kernels"
        var deviceSupport = "Standard Linux Virtual File System"

        if (trimmed.contains("/proc") || trimmed.contains("/sys")) {
            layer = ExecutionLayer.VIRTUAL_FILE_SYSTEMS
            expectedEffect = "Reads or writes volatile states of drivers, hardware registers, or memory mappings via sysfs/procfs."
            
            if (trimmed.startsWith("echo") || trimmed.contains(">")) {
                // Attempting to write
                riskLevel = RiskLevel.HIGH
                requiredPrivilege = RequiredPrivilege.ROOT
                riskDescription = "Modifying sysfs/procfs nodes bypasses safety limits and directly writes to kernel variables or hardware controls. High risk of immediate crash or damage."
            } else {
                // Reading
                if (trimmed.contains("/proc/kallsyms") || trimmed.contains("/proc/slabinfo") || trimmed.contains("/proc/config.gz")) {
                    requiredPrivilege = RequiredPrivilege.ROOT
                    riskDescription = "Accessing sensitive kernel mappings or configuration layouts. Usually blocked on modern user builds via SELinux."
                }
            }
        }

        when (firstToken) {
            "dmesg" -> {
                requiredPrivilege = RequiredPrivilege.ROOT
                riskLevel = RiskLevel.MEDIUM
                riskDescription = "Accessing ring buffer log. Typically blocked for shell users unless in userdebug/root configuration due to potential information leaks."
                expectedEffect = "Dumps dmesg log buffer."
            }
            "insmod", "rmmod" -> {
                layer = ExecutionLayer.ROOT_ONLY
                requiredPrivilege = RequiredPrivilege.ROOT
                riskLevel = RiskLevel.HIGH
                riskDescription = "Direct insertion or removal of Kernel Modules. Dangerous and could cause immediate kernel panic."
                expectedEffect = "Modifies kernel runtime memory."
                isPersistent = false
            }
            "uname" -> {
                expectedEffect = "Prints kernel version, compiler build, architecture, and timestamp."
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
