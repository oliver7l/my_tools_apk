package com.example.aliyunapp

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 简单的错误日志记录器，替代Firebase Crashlytics
 */
class ErrorLogger {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * 记录异常到本地文件
     */
    fun recordException(context: Context, throwable: Throwable) {
        try {
            // 记录到Logcat
            Log.e("PhotoMoveApp", "Exception occurred", throwable)
            
            // 记录到本地文件
            val errorLog = File(context.getExternalFilesDir(null), "error_log.txt")
            val timestamp = dateFormat.format(Date())
            
            FileWriter(errorLog, true).use { writer ->
                PrintWriter(writer).use { printer ->
                    printer.println("=== Error at $timestamp ===")
                    throwable.printStackTrace(printer)
                    printer.println()
                    printer.flush()
                }
            }
        } catch (e: Exception) {
            Log.e("PhotoMoveApp", "Failed to log error", e)
        }
    }
    
    /**
     * 记录自定义消息到本地文件
     */
    fun log(context: Context, message: String) {
        try {
            // 记录到Logcat
            Log.d("PhotoMoveApp", message)
            
            // 记录到本地文件
            val errorLog = File(context.getExternalFilesDir(null), "error_log.txt")
            val timestamp = dateFormat.format(Date())
            
            FileWriter(errorLog, true).use { writer ->
                writer.append("=== Log at $timestamp ===\n")
                writer.append("$message\n\n")
                writer.flush()
            }
        } catch (e: Exception) {
            Log.e("PhotoMoveApp", "Failed to log message", e)
        }
    }
    
    /**
     * 获取错误日志内容
     */
    fun getErrorLogContent(context: Context): String {
        return try {
            val errorLog = File(context.getExternalFilesDir(null), "error_log.txt")
            if (errorLog.exists()) {
                errorLog.readText()
            } else {
                "暂无错误日志"
            }
        } catch (e: Exception) {
            "读取错误日志失败: ${e.message}"
        }
    }
    
    /**
     * 清除错误日志
     */
    fun clearErrorLog(context: Context) {
        try {
            val errorLog = File(context.getExternalFilesDir(null), "error_log.txt")
            if (errorLog.exists()) {
                errorLog.delete()
            }
        } catch (e: Exception) {
            Log.e("PhotoMoveApp", "Failed to clear error log", e)
        }
    }
}