package com.example.aliyunapp.database

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
import java.util.concurrent.TimeUnit

/**
 * 图片下载和本地存储管理器
 * 负责从网络下载图片并保存到本地存储
 */
class ImageDownloadManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageDownloadManager"
        private const val IMAGE_DIR_NAME = "downloaded_images"
        private const val MAX_IMAGE_SIZE = 10 * 1024 * 1024 // 10MB
    }
    
    private val imageDir = File(context.filesDir, IMAGE_DIR_NAME)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    init {
        // 确保图片目录存在
        if (!imageDir.exists()) {
            imageDir.mkdirs()
        }
    }
    
    /**
     * 下载图片并保存到本地
     * @param imageUrl 图片URL
     * @return 本地图片路径，下载失败返回null
     */
    suspend fun downloadImage(imageUrl: String): String? = withContext(Dispatchers.IO) {
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
                
                // 检查响应大小
                val contentLength = response.body?.contentLength() ?: 0
                if (contentLength > MAX_IMAGE_SIZE) {
                    throw IOException("图片文件过大: $contentLength bytes")
                }
                
                // 生成唯一文件名
                val fileName = generateFileName(imageUrl)
                val imageFile = File(imageDir, fileName)
                
                // 保存图片到本地
                FileOutputStream(imageFile).use { output ->
                    inputStream.copyTo(output)
                }
                
                Log.d(TAG, "图片下载成功: $imageUrl -> ${imageFile.absolutePath}")
                return@withContext imageFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载图片失败: $imageUrl", e)
            return@withContext null
        }
    }
    
    /**
     * 获取本地图片
     * @param localImagePath 本地图片路径
     * @return Bitmap对象，如果图片不存在或加载失败返回null
     */
    suspend fun getLocalImage(localImagePath: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val imageFile = File(localImagePath)
            if (!imageFile.exists()) {
                Log.w(TAG, "本地图片文件不存在: $localImagePath")
                return@withContext null
            }
            
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (bitmap == null) {
                Log.w(TAG, "无法解码本地图片: $localImagePath")
                // 删除损坏的图片文件
                imageFile.delete()
            }
            
            return@withContext bitmap
        } catch (e: Exception) {
            Log.e(TAG, "加载本地图片失败: $localImagePath", e)
            return@withContext null
        }
    }
    
    /**
     * 检查本地图片是否存在
     * @param localImagePath 本地图片路径
     * @return 如果图片存在返回true，否则返回false
     */
    fun isLocalImageExists(localImagePath: String): Boolean {
        return File(localImagePath).exists()
    }
    
    /**
     * 删除本地图片
     * @param localImagePath 本地图片路径
     * @return 删除成功返回true，否则返回false
     */
    fun deleteLocalImage(localImagePath: String): Boolean {
        return try {
            val imageFile = File(localImagePath)
            if (imageFile.exists()) {
                val result = imageFile.delete()
                if (result) {
                    Log.d(TAG, "删除本地图片成功: $localImagePath")
                } else {
                    Log.w(TAG, "删除本地图片失败: $localImagePath")
                }
                result
            } else {
                Log.w(TAG, "本地图片文件不存在，无需删除: $localImagePath")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除本地图片失败: $localImagePath", e)
            false
        }
    }
    
    /**
     * 清理所有本地图片
     */
    suspend fun clearAllImages() = withContext(Dispatchers.IO) {
        try {
            val imageFiles = imageDir.listFiles() ?: return@withContext
            var deletedCount = 0
            
            imageFiles.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }
            
            Log.d(TAG, "清理完成，删除了 $deletedCount 张图片")
        } catch (e: Exception) {
            Log.e(TAG, "清理本地图片失败", e)
        }
    }
    
    /**
     * 获取本地图片存储大小
     * @return 存储大小（字节）
     */
    fun getLocalImagesSize(): Long {
        return try {
            val imageFiles = imageDir.listFiles() ?: return 0
            imageFiles.sumOf { it.length() }
        } catch (e: Exception) {
            Log.e(TAG, "获取本地图片存储大小失败", e)
            0
        }
    }
    
    /**
     * 根据URL生成唯一文件名
     */
    private fun generateFileName(url: String): String {
        val hash = md5(url)
        val extension = getFileExtension(url)
        return "${hash}.${extension}"
    }
    
    /**
     * 获取URL的文件扩展名
     */
    private fun getFileExtension(url: String): String {
        return try {
            val lastSlashIndex = url.lastIndexOf('/')
            val lastDotIndex = url.lastIndexOf('.', lastSlashIndex)
            if (lastDotIndex != -1 && lastDotIndex > lastSlashIndex) {
                url.substring(lastDotIndex + 1)
            } else {
                "jpg" // 默认扩展名
            }
        } catch (e: Exception) {
            "jpg" // 默认扩展名
        }
    }
    
    /**
     * MD5哈希
     */
    private fun md5(str: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            digest.digest(str.toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            str.hashCode().toString()
        }
    }
}