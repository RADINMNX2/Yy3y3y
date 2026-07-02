package com.example.adb.engine

import android.content.Context

interface CommandAnalyzerPlugin {
    val name: String
    
    /**
     * Determines whether this plugin can analyze the given command.
     */
    fun canAnalyze(command: String): Boolean
    
    /**
     * Analyzes the given command under the specified Android system context.
     */
    fun analyze(command: String, context: Context): PreExecutionAnalysis
}
