package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 图片缓存管理器
 * 负责下载和缓存图片，提高图片加载速度
 */
class ImageCacheManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageCacheManager"
        private const val CACHE_DIR_NAME = "image_cache"
        private const val MAX_CACHE_SIZE_MB = 100 // 最大缓存大小：100MB
        private const val PRELOAD_COUNT = 20 // 预加载图片数量
    }
    
    private val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
    private val memoryCache = ConcurrentHashMap<String, Bitmap>()
    private val httpClient = OkHttpClient()
    
    init {
        // 确保缓存目录存在
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    /**
     * 获取缓存中的图片
     * @param imageUrl 图片URL
     * @return Bitmap对象，如果缓存中没有则返回null
     */
    suspend fun getCachedImage(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        // 首先检查内存缓存
        memoryCache[imageUrl]?.let { return@withContext it }
        
        // 然后检查磁盘缓存
        val cacheFile = getCacheFile(imageUrl)
        if (cacheFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bitmap != null) {
                    // 将图片加载到内存缓存
                    memoryCache[imageUrl] = bitmap
                    return@withContext bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载缓存图片失败: $imageUrl", e)
                // 删除损坏的缓存文件
                cacheFile.delete()
            }
        }
        
        return@withContext null
    }
    
    /**
     * 下载并缓存图片
     * @param imageUrl 图片URL
     * @return Bitmap对象，如果下载失败则返回null
     */
    suspend fun downloadAndCacheImage(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始下载图片: $imageUrl")
            
            val request = Request.Builder()
                .url(imageUrl)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                val inputStream = response.body?.byteStream() ?: throw IOException("Empty response body")
                val bitmap = BitmapFactory.decodeStream(inputStream)
                
                if (bitmap != null) {
                    // 保存到内存缓存
                    memoryCache[imageUrl] = bitmap
                    
                    // 保存到磁盘缓存
                    val cacheFile = getCacheFile(imageUrl)
                    try {
                        FileOutputStream(cacheFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        Log.d(TAG, "图片缓存成功: $imageUrl -> ${cacheFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "保存图片到缓存失败: $imageUrl", e)
                    }
                    
                    return@withContext bitmap
                } else {
                    Log.e(TAG, "无法解码图片: $imageUrl")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载图片失败: $imageUrl", e)
        }
        
        return@withContext null
    }
    
    /**
     * 预加载图片列表
     * @param imageUrls 图片URL列表
     * @param count 预加载的数量，默认为PRELOAD_COUNT
     */
    suspend fun preloadImages(imageUrls: List<String>, count: Int = PRELOAD_COUNT) {
        withContext(Dispatchers.IO) {
            val urlsToPreload = imageUrls.take(count)
            Log.d(TAG, "开始预加载 ${urlsToPreload.size} 张图片")
            
            urlsToPreload.forEach { url ->
                if (!isImageCached(url)) {
                    downloadAndCacheImage(url)
                }
            }
            
            Log.d(TAG, "预加载完成")
        }
    }
    
    /**
     * 检查图片是否已缓存
     * @param imageUrl 图片URL
     * @return 如果已缓存返回true，否则返回false
     */
    fun isImageCached(imageUrl: String): Boolean {
        // 检查内存缓存
        if (memoryCache.containsKey(imageUrl)) {
            return true
        }
        
        // 检查磁盘缓存
        val cacheFile = getCacheFile(imageUrl)
        return cacheFile.exists()
    }
    
    /**
     * 清理缓存，删除最旧的文件直到缓存大小小于限制
     */
    suspend fun cleanCache() = withContext(Dispatchers.IO) {
        try {
            val cacheFiles = cacheDir.listFiles() ?: return@withContext
            if (cacheFiles.isEmpty()) return@withContext
            
            // 计算当前缓存大小
            var totalSize = cacheFiles.sumOf { it.length() }
            val maxSizeBytes = MAX_CACHE_SIZE_MB * 1024 * 1024L
            
            Log.d(TAG, "当前缓存大小: ${totalSize / (1024 * 1024)}MB")
            
            // 如果缓存大小超过限制，删除最旧的文件
            if (totalSize > maxSizeBytes) {
                // 按修改时间排序，最旧的在前
                cacheFiles.sortBy { it.lastModified() }
                
                var deletedSize = 0L
                for (file in cacheFiles) {
                    if (totalSize - deletedSize <= maxSizeBytes) break
                    
                    val fileSize = file.length()
                    if (file.delete()) {
                        deletedSize += fileSize
                        Log.d(TAG, "删除缓存文件: ${file.name}")
                    }
                }
                
                Log.d(TAG, "清理完成，删除了 ${deletedSize / (1024 * 1024)}MB")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理缓存失败", e)
        }
    }
    
    /**
     * 清空所有缓存
     */
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        try {
            // 清空内存缓存
            memoryCache.clear()
            
            // 清空磁盘缓存
            val cacheFiles = cacheDir.listFiles()
            if (cacheFiles != null) {
                for (file in cacheFiles) {
                    file.delete()
                }
            }
            
            Log.d(TAG, "清空所有缓存完成")
        } catch (e: Exception) {
            Log.e(TAG, "清空缓存失败", e)
        }
    }
    
    /**
     * 获取缓存文件
     * @param imageUrl 图片URL
     * @return 缓存文件对象
     */
    private fun getCacheFile(imageUrl: String): File {
        // 使用URL的MD5哈希作为文件名，避免特殊字符问题
        val hash = md5(imageUrl)
        return File(cacheDir, "$hash.jpg")
    }
    
    /**
     * 计算字符串的MD5哈希值
     * @param str 输入字符串
     * @return MD5哈希值
     */
    private fun md5(str: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(str.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}