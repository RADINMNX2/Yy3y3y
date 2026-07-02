package com.example.adb.engine

import androidx.compose.ui.graphics.Color

enum class ExecutionLayer(val displayName: String) {
    ANDROID_FRAMEWORK("Android Framework"),
    ART("Android Runtime (ART)"),
    ACTIVITY_MANAGER("Activity Manager"),
    PACKAGE_MANAGER("Package Manager"),
    SETTINGS_PROVIDER("Settings Provider"),
    SYSTEM_PROPERTIES("System Properties"),
    BINDER_SERVICES("Binder Services"),
    VENDOR_SERVICES("Vendor Services"),
    HAL("Hardware Abstraction Layer (HAL)"),
    LINUX_KERNEL("Linux Kernel"),
    VIRTUAL_FILE_SYSTEMS("Virtual File Systems (/proc, /sys)"),
    SELINUX("SELinux"),
    ROOT_ONLY("Root-only Operations"),
    READ_ONLY_BOOT_PROPERTIES("Read-only Boot Properties"),
    ADB_SHELL("ADB Shell"),
    FASTBOOT("Fastboot (Informational Only)"),
    UNKNOWN("Generic Shell Command")
}

enum class RequiredPrivilege(val displayName: String) {
    NONE("Normal User"),
    SHELL("ADB Shell Privilege"),
    SYSTEM("System Privilege"),
    SIGNATURE("System Signature"),
    ROOT("Superuser (Root)")
}

enum class RiskLevel(val displayName: String, val colorCode: String) {
    LOW("Low Risk", "32"), // Green
    MEDIUM("Medium Risk", "33"), // Yellow
    HIGH("High Risk / Dangerous", "31") // Red
}

data class PreExecutionAnalysis(
    val command: String,
    val layer: ExecutionLayer,
    val requiredPrivilege: RequiredPrivilege,
    val androidVersionCompatibility: String,
    val deviceSpecificSupport: String,
    val expectedEffect: String,
    val isIgnoredBySystem: Boolean,
    val requiresReboot: Boolean,
    val isPersistent: Boolean,
    val riskLevel: RiskLevel,
    val riskDescription: String,
    val estimatedExecutionTimeMs: Long
)

enum class PostExecutionResult {
    SUCCESSFULLY_EXECUTED,
    EXECUTED_BUT_IGNORED,
    UNSUPPORTED,
    PERMISSION_DENIED,
    DEPRECATED,
    NO_OBSERVABLE_EFFECT,
    REQUIRES_ROOT,
    REQUIRES_SYSTEM_SIGNATURE,
    REQUIRES_ENGINEERING_BUILD,
    FAILED_UNKNOWN
}

data class PostExecutionAnalysis(
    val result: PostExecutionResult,
    val details: String,
    val actualExecutionTimeMs: Long
)
