package com.example.aliyunapp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.aliyunapp.FlomoNote
import com.example.aliyunapp.database.AppDatabase
import com.example.aliyunapp.database.ImageDownloadManager
import com.example.aliyunapp.database.NoteEntity
import com.example.aliyunapp.database.fromFlomoNote
import com.example.aliyunapp.network.FlomoApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WiFi状态检测和自动同步管理器
 * 负责检测WiFi连接状态并在连接WiFi时自动同步笔记和下载图片
 */
class WifiSyncManager(
    private val context: Context,
    private val flomoApi: FlomoApi,
    private val database: AppDatabase,
    private val imageDownloadManager: ImageDownloadManager
) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "WifiSyncManager"
        private const val PREF_NAME = "wifi_sync_prefs"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000 // 24小时
        private const val MAX_DOWNLOAD_IMAGES = 50 // 最大下载数量
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                Log.d(TAG, "WiFi已连接")
                _isWifiConnected.value = true
                checkAndStartSync()
            }
        }
        
        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "网络连接断开")
            _isWifiConnected.value = false
        }
    }
    
    init {
        // 注册生命周期观察者
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        // 初始化网络状态
        checkCurrentNetworkStatus()
        
        // 初始化上次同步时间
        _lastSyncTime.value = sharedPrefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }
    
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // 应用进入前台时检查网络状态
        checkCurrentNetworkStatus()
    }
    
    /**
     * 检查当前网络状态
     */
    private fun checkCurrentNetworkStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        _isWifiConnected.value = isConnected
        
        if (isConnected) {
            checkAndStartSync()
        }
    }
    
    /**
     * 开始监听网络状态变化
     */
    fun startNetworkMonitoring() {
        Log.d(TAG, "开始监听网络状态变化")
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }
    
    /**
     * 停止监听网络状态变化
     */
    fun stopNetworkMonitoring() {
        Log.d(TAG, "停止监听网络状态变化")
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
    
    /**
     * 检查并开始同步
     */
    private fun checkAndStartSync() {
        if (!_isWifiConnected.value) return
        
        val currentTime = System.currentTimeMillis()
        val lastSync = _lastSyncTime.value
        
        // 如果距离上次同步时间超过设定间隔，则开始同步
        if (currentTime - lastSync > SYNC_INTERVAL_MS) {
            startSync()
        } else {
            Log.d(TAG, "距离上次同步时间不足，跳过本次同步")
        }
    }
    
    /**
     * 手动触发同步
     */
    fun startSync() {
        if (_isSyncing.value) {
            Log.d(TAG, "同步正在进行中，跳过本次同步")
            return
        }
        
        _isSyncing.value = true
        Log.d(TAG, "开始同步笔记和图片")
        
        syncScope.launch {
            try {
                // 1. 同步笔记
                val syncResult = syncNotes()
                
                // 2. 下载图片
                if (syncResult) {
                    downloadImages()
                }
                
                // 更新同步时间
                val currentTime = System.currentTimeMillis()
                _lastSyncTime.value = currentTime
                sharedPrefs.edit().putLong(KEY_LAST_SYNC_TIME, currentTime).apply()
                
                Log.d(TAG, "同步完成")
            } catch (e: Exception) {
                Log.e(TAG, "同步失败", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    /**
     * 同步笔记
     */
    private suspend fun syncNotes(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始同步笔记")
            
            // 从API获取所有笔记
            val response = flomoApi.getNotes()
            if (!response.success) {
                Log.e(TAG, "获取笔记失败: ${response.message}")
                return@withContext false
            }
            
            val notes = response.data ?: emptyList()
            Log.d(TAG, "获取到 ${notes.size} 条笔记")
            
            // 转换为实体类
            val noteEntities: List<NoteEntity> = notes.map { note: FlomoNote ->
                fromFlomoNote(note)
            }
            
            // 保存到本地数据库
            database.noteDao().insertOrUpdateNotes(noteEntities)
            
            Log.d(TAG, "笔记同步完成")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "同步笔记失败", e)
            return@withContext false
        }
    }
    
    /**
     * 下载图片
     */
    private suspend fun downloadImages() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始下载图片")
            
            // 获取所有未下载的图片笔记
            val imageNotes = database.noteDao().getAllImageNotes()
            val notDownloadedNotes = imageNotes.filter { it.localImagePath.isNullOrEmpty() }
            
            Log.d(TAG, "需要下载 ${notDownloadedNotes.size} 张图片")
            
            var downloadCount = 0
            for (note in notDownloadedNotes.take(MAX_DOWNLOAD_IMAGES)) {
                try {
                    val imageUrl = note.content
                    val localPath = imageDownloadManager.downloadImage(imageUrl)
                    
                    if (localPath != null) {
                        // 更新数据库中的本地路径
                        database.noteDao().updateNoteLocalPath(note.id, localPath)
                        downloadCount++
                        Log.d(TAG, "图片下载成功: ${note.id}")
                    } else {
                        Log.w(TAG, "图片下载失败: ${note.id}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "下载图片失败: ${note.id}", e)
                }
            }
            
            Log.d(TAG, "图片下载完成，成功下载 $downloadCount 张图片")
        } catch (e: Exception) {
            Log.e(TAG, "下载图片失败", e)
        }
    }
    
    /**
     * 获取同步统计信息
     */
    suspend fun getSyncStats(): SyncStats = withContext(Dispatchers.IO) {
        val totalNotes = database.noteDao().getNotesCount()
        val imageNotes = database.noteDao().getImageNotesCount()
        val downloadedImages = database.noteDao().getDownloadedImageNotesCount()
        val localImagesSize = imageDownloadManager.getLocalImagesSize()
        
        SyncStats(
            totalNotes = totalNotes,
            imageNotes = imageNotes,
            downloadedImages = downloadedImages,
            localImagesSize = localImagesSize,
            lastSyncTime = _lastSyncTime.value
        )
    }
    
    /**
     * 清理所有本地数据
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        try {
            // 清理数据库
            database.noteDao().deleteAllNotes()
            
            // 清理图片
            imageDownloadManager.clearAllImages()
            
            // 重置同步时间
            _lastSyncTime.value = 0L
            sharedPrefs.edit().remove(KEY_LAST_SYNC_TIME).apply()
            
            Log.d(TAG, "清理所有本地数据完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理本地数据失败", e)
        }
    }
    
    /**
     * 同步统计数据
     */
    data class SyncStats(
        val totalNotes: Int,
        val imageNotes: Int,
        val downloadedImages: Int,
        val localImagesSize: Long,
        val lastSyncTime: Long
    )
}