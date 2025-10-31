package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

/**
 * WiFi状态检测和自动同步管理器
 * 监听WiFi连接状态，在连接到WiFi时自动同步笔记
 */
class WifiSyncManager(
    private val context: Context,
    private val flomoNotesRepository: FlomoNotesRepository
) {
    companion object {
        private const val TAG = "WifiSyncManager"
        private const val SYNC_INTERVAL_MS = 60 * 60 * 1000L // 1小时同步一次
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private var isWifiConnected = false
    private var lastSyncTime = 0L
    private var syncJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    checkWifiConnection()
                }
            }
        }
    }
    
    init {
        registerWifiReceiver()
        checkWifiConnection()
    }
    
    /**
     * 注册WiFi状态广播接收器
     */
    private fun registerWifiReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        context.registerReceiver(wifiReceiver, filter)
    }
    
    /**
     * 检查当前WiFi连接状态
     */
    private fun checkWifiConnection() {
        val wasConnected = isWifiConnected
        isWifiConnected = isWifiConnected()
        
        Log.d(TAG, "WiFi状态检查: $isWifiConnected (之前: $wasConnected)")
        
        // 如果从断开变为连接，触发同步
        if (!wasConnected && isWifiConnected) {
            Log.d(TAG, "WiFi已连接，检查是否需要同步")
            checkAndSync()
        }
    }
    
    /**
     * 检查是否连接到WiFi
     */
    private fun isWifiConnected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
            networkInfo?.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
        }
    }
    
    /**
     * 检查是否需要同步，如果需要则执行同步
     */
    private fun checkAndSync() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSyncTime > SYNC_INTERVAL_MS) {
            Log.d(TAG, "距离上次同步已超过${SYNC_INTERVAL_MS / 1000 / 60}分钟，开始同步")
            syncNotes()
        } else {
            Log.d(TAG, "距离上次同步不足${SYNC_INTERVAL_MS / 1000 / 60}分钟，跳过同步")
        }
    }
    
    /**
     * 同步笔记
     */
    fun syncNotes() {
        // 取消之前的同步任务
        syncJob?.cancel()
        
        syncJob = scope.launch {
            try {
                Log.d(TAG, "开始同步笔记...")
                val success = flomoNotesRepository.syncNotesFromNetwork()
                if (success) {
                    lastSyncTime = System.currentTimeMillis()
                    Log.d(TAG, "笔记同步成功")
                } else {
                    Log.e(TAG, "笔记同步失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步笔记时发生异常", e)
            }
        }
    }
    
    /**
     * 手动触发同步，忽略时间间隔限制
     */
    fun forceSync() {
        Log.d(TAG, "强制同步笔记")
        syncNotes()
    }
    
    /**
     * 获取当前WiFi连接状态
     */
    fun getWifiConnectionStatus(): Boolean {
        return isWifiConnected
    }
    
    /**
     * 获取上次同步时间
     */
    fun getLastSyncTime(): Long {
        return lastSyncTime
    }
    
    /**
     * 获取距离下次同步的剩余时间（毫秒）
     */
    fun getTimeUntilNextSync(): Long {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSync = currentTime - lastSyncTime
        return if (timeSinceLastSync >= SYNC_INTERVAL_MS) {
            0L
        } else {
            SYNC_INTERVAL_MS - timeSinceLastSync
        }
    }
    
    /**
     * 销毁管理器，取消所有任务并注销广播接收器
     */
    fun destroy() {
        syncJob?.cancel()
        scope.cancel()
        try {
            context.unregisterReceiver(wifiReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "注销WiFi广播接收器失败", e)
        }
    }
}