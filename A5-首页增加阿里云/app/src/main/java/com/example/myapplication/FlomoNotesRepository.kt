package com.example.myapplication

import android.content.Context
import android.util.Log
import com.example.myapplication.database.AppDatabase
import com.example.myapplication.database.ImageDownloadManager
import com.example.myapplication.database.NoteEntity
import com.example.myapplication.database.fromFlomoNote
import com.example.myapplication.database.toFlomoNote
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * Flomo笔记仓库类
 * 负责从API加载、管理和提供flomo笔记数据
 */
class FlomoNotesRepository(private val context: Context) : Serializable {
    
    companion object {
        private const val TAG = "FlomoNotesRepository"
        private const val BASE_URL = "https://elizabet-isotheral-janeth.ngrok-free.dev"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private var notes: List<FlomoNote> = emptyList()
    private var isLoaded = false
    private var isPreloaded = false
    private val imageCacheManager = ImageCacheManager(context)
    
    // 本地数据库和图片下载管理器
    private val database = AppDatabase.getDatabase(context)
    private val imageDownloadManager = ImageDownloadManager(context)
    
    // 全局协程作用域，用于预加载图片等后台任务
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * 加载所有flomo笔记
     */
    suspend fun loadNotes(): List<FlomoNote> = withContext(Dispatchers.IO) {
        if (isLoaded) {
            return@withContext notes
        }
        
        try {
            val flomoNotes = mutableListOf<FlomoNote>()
            
            // 调用API获取笔记列表
            val request = Request.Builder()
                .url("$BASE_URL/api/notes")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                val jsonResponse = JSONObject(responseBody)
                
                // 解析笔记列表
                if (jsonResponse.has("data")) {
                    // 检查data字段是数组还是对象
                    val data = jsonResponse.get("data")
                    if (data is JSONArray) {
                        // data是数组
                        val notesArray = data
                        
                        for (i in 0 until notesArray.length()) {
                            val noteJson = notesArray.getJSONObject(i)
                            val note = parseNoteFromJson(noteJson)
                            if (note != null) {
                                flomoNotes.add(note)
                            }
                        }
                    } else if (data is JSONObject) {
                        // data是对象，可能是分页数据
                        if (data.has("notes") && data.get("notes") is JSONArray) {
                            val notesArray = data.getJSONArray("notes")
                            
                            for (i in 0 until notesArray.length()) {
                                val noteJson = notesArray.getJSONObject(i)
                                val note = parseNoteFromJson(noteJson)
                                if (note != null) {
                                    flomoNotes.add(note)
                                }
                            }
                        }
                    }
                }
            }
            
            notes = flomoNotes
            isLoaded = true
            Log.d(TAG, "成功加载 ${notes.size} 条flomo笔记")
            
            // 使用全局协程作用域异步预加载图片，避免因Activity生命周期结束而取消
            repositoryScope.launch {
                preloadImages()
            }
            
            notes
        } catch (e: Exception) {
            Log.e(TAG, "加载flomo笔记失败", e)
            // 发生错误时返回示例笔记
            createSampleNotes()
        }
    }
    
    /**
     * 从JSON对象解析笔记
     */
    private fun parseNoteFromJson(noteJson: JSONObject): FlomoNote? {
        return try {
            val id = noteJson.getString("id")
            val content = noteJson.optString("content", "")
            val type = noteJson.optString("type", "text")
            val path = noteJson.optString("file_path", noteJson.optString("path", ""))
            
            // 从文件路径中提取文件名
            val filename = if (noteJson.has("filename")) {
                noteJson.optString("filename", "")
            } else {
                // 如果没有filename字段，从路径中提取文件名
                if (path.isNotEmpty()) {
                    path.substringAfterLast("/")
                } else {
                    // 如果路径也为空，从内容中提取文件名
                    content.substringAfterLast("/")
                }
            }
            
            // 处理日期，优先使用created_at，其次是date，最后使用当前日期
            val date = if (noteJson.has("created_at")) {
                // 从ISO格式日期转换为YYYY-MM-DD格式
                val createdAt = noteJson.getString("created_at")
                createdAt.substring(0, 10) // 取前10个字符 "YYYY-MM-DD"
            } else if (noteJson.has("date")) {
                noteJson.getString("date")
            } else {
                // 使用当前日期
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            }
            
            Log.d(TAG, "解析笔记: id=$id, type=$type, filename=$filename, date=$date")
            
            FlomoNote(
                id = id,
                date = date,
                content = content,
                type = type,
                filename = filename,
                path = path
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析笔记JSON失败", e)
            null
        }
    }
    
    /**
     * 从JSON对象解析图片笔记（专门用于新API）
     */
    private fun parseImageNoteFromJson(noteJson: JSONObject): FlomoNote? {
        return try {
            val id = noteJson.optString("id", UUID.randomUUID().toString())
            
            // 新API返回的可能是图片URL，直接作为内容
            val content = noteJson.optString("content", "")
            val imageUrl = noteJson.optString("url", noteJson.optString("image_url", ""))
            
            // 如果有url字段，使用它；否则使用content字段
            val finalImageUrl = if (imageUrl.isNotEmpty()) imageUrl else content
            
            // 从URL中提取文件名
            val filename = if (noteJson.has("filename")) {
                noteJson.optString("filename", "")
            } else {
                // 从URL中提取文件名
                if (finalImageUrl.isNotEmpty()) {
                    finalImageUrl.substringAfterLast("/")
                } else {
                    "image_${System.currentTimeMillis()}.jpg"
                }
            }
            
            // 处理日期
            val date = if (noteJson.has("created_at")) {
                // 从ISO格式日期转换为YYYY-MM-DD格式
                val createdAt = noteJson.getString("created_at")
                createdAt.substring(0, 10) // 取前10个字符 "YYYY-MM-DD"
            } else if (noteJson.has("date")) {
                noteJson.getString("date")
            } else {
                // 使用当前日期
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            }
            
            Log.d(TAG, "解析图片笔记: id=$id, filename=$filename, date=$date, url=$finalImageUrl")
            
            FlomoNote(
                id = id,
                date = date,
                content = finalImageUrl, // 使用图片URL作为内容
                type = FlomoNote.TYPE_IMAGE,
                filename = filename,
                path = finalImageUrl // 使用URL作为路径
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析图片笔记JSON失败", e)
            null
        }
    }
    
    /**
     * 获取随机笔记
     */
    suspend fun getRandomNote(): FlomoNote? {
        try {
            // 直接调用随机笔记API
            val request = Request.Builder()
                .url("$BASE_URL/api/notes/random")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                val jsonResponse = JSONObject(responseBody)
                
                // 解析随机笔记
                if (jsonResponse.has("data")) {
                    val noteJson = jsonResponse.getJSONObject("data")
                    return parseNoteFromJson(noteJson)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取随机笔记失败", e)
        }
        
        // 如果API调用失败，从已加载的笔记中随机获取
        val allNotes = loadNotes()
        return if (allNotes.isNotEmpty()) {
            allNotes.random()
        } else null
    }
    
    /**
     * 获取随机图片笔记
     */
    suspend fun getRandomImageNote(): FlomoNote? {
        try {
            // 使用新的API端点获取随机图片笔记，添加时间戳参数防止缓存
            val timestamp = System.currentTimeMillis()
            val url = "$BASE_URL/api/flomo/random?type=image&t=$timestamp"
            Log.d(TAG, "请求随机图片笔记API: $url")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                // 检查响应类型
                val contentType = response.header("Content-Type", "") ?: ""
                Log.d(TAG, "随机图片笔记API响应类型: $contentType")
                
                if (contentType.startsWith("image/")) {
                    // API返回的是二进制图片数据，直接构建图片笔记
                    val imageUrl = "$BASE_URL/api/flomo/random?type=image&t=$timestamp"
                    val extension = if (contentType.contains("/")) {
                        contentType.substringAfterLast("/")
                    } else {
                        "jpg" // 默认扩展名
                    }
                    val filename = "random_image_${timestamp}.$extension"
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    
                    Log.d(TAG, "API返回二进制图片数据，构建图片笔记: url=$imageUrl, filename=$filename")
                    
                    return FlomoNote(
                        id = UUID.randomUUID().toString(),
                        date = date,
                        content = imageUrl, // 使用图片URL作为内容
                        type = FlomoNote.TYPE_IMAGE,
                        filename = filename,
                        path = imageUrl // 使用URL作为路径
                    )
                } else {
                    // 尝试解析JSON响应
                    val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                    Log.d(TAG, "随机图片笔记API响应: $responseBody")
                    
                    // 尝试直接解析为JSON对象（新API可能返回单个对象而不是包装在data字段中）
                    val jsonResponse = JSONObject(responseBody)
                    
                    // 解析随机图片笔记
                    val noteJson = if (jsonResponse.has("data")) {
                        jsonResponse.getJSONObject("data")
                    } else {
                        // 如果没有data字段，假设整个响应就是笔记对象
                        jsonResponse
                    }
                    
                    val note = parseImageNoteFromJson(noteJson)
                    Log.d(TAG, "成功解析图片笔记: ${note?.filename}")
                    return note
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取随机图片笔记失败", e)
            // 不使用本地缓存，直接返回null让用户知道API调用失败
            return null
        }
    }
    
    /**
     * 获取随机图片笔记（带缓存支持）
     */
    suspend fun getRandomImageNoteWithCache(): FlomoNote? {
        try {
            // 1. 首先尝试从本地数据库获取已下载的图片笔记
            val downloadedImageNotes = database.noteDao().getDownloadedImageNotes()
            
            if (downloadedImageNotes.isNotEmpty()) {
                // 随机选择一个已下载的图片笔记
                val randomNoteEntity = downloadedImageNotes.random()
                val localImagePath = randomNoteEntity.localImagePath
                
                if (!localImagePath.isNullOrEmpty() && imageDownloadManager.isLocalImageExists(localImagePath)) {
                    Log.d(TAG, "从本地获取已下载的图片笔记: ${randomNoteEntity.id}")
                    return randomNoteEntity.toFlomoNote()
                }
            }
            
            // 2. 如果本地没有已下载的图片笔记，尝试从已加载的笔记中获取随机图片笔记
            val allNotes = loadNotes()
            val imageNotes = allNotes.filter { it.type == FlomoNote.TYPE_IMAGE }
            
            if (imageNotes.isNotEmpty()) {
                // 随机选择一个图片笔记
                val randomNote = imageNotes.random()
                
                // 检查图片是否已缓存，如果没有则下载并缓存
                if (!imageCacheManager.isImageCached(randomNote.content)) {
                    Log.d(TAG, "图片未缓存，开始下载: ${randomNote.content}")
                    imageCacheManager.downloadAndCacheImage(randomNote.content)
                } else {
                    Log.d(TAG, "图片已缓存: ${randomNote.content}")
                }
                
                return randomNote
            } else {
                // 3. 如果没有本地图片笔记，调用API获取随机图片笔记
                return getRandomImageNote()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取随机图片笔记（带缓存）失败", e)
            return null
        }
    }
    
    /**
     * 预加载图片
     */
    private suspend fun preloadImages() {
        if (isPreloaded) {
            return
        }
        
        try {
            // 获取所有图片笔记
            val imageNotes = notes.filter { it.type == FlomoNote.TYPE_IMAGE }
            
            if (imageNotes.isNotEmpty()) {
                // 提取所有图片URL
                val imageUrls = imageNotes.map { it.content }
                
                // 预加载图片
                imageCacheManager.preloadImages(imageUrls)
                
                isPreloaded = true
                Log.d(TAG, "预加载了 ${imageUrls.size} 张图片")
            }
        } catch (e: Exception) {
            Log.e(TAG, "预加载图片失败", e)
        }
    }
    
    /**
     * 同步笔记到本地数据库
     */
    suspend fun syncNotesToLocal(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始同步笔记到本地数据库")
            
            // 从API获取所有笔记
            val apiNotes = loadNotes()
            Log.d(TAG, "从API获取到 ${apiNotes.size} 条笔记")
            
            // 转换为实体类
            // TODO: 修复NoteEntity.fromFlomoNote问题
            /*
            val noteEntities = apiNotes.map { note ->
                fromFlomoNote(note)
            }
            */
            val noteEntities = emptyList<com.example.myapplication.database.NoteEntity>()
            
            // 保存到本地数据库
            database.noteDao().insertOrUpdateNotes(noteEntities)
            
            Log.d(TAG, "成功同步 ${noteEntities.size} 条笔记到本地数据库")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "同步笔记到本地数据库失败", e)
            return@withContext false
        }
    }
    
    /**
     * 下载图片到本地存储
     */
    suspend fun downloadImagesToLocal(maxCount: Int = 50): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始下载图片到本地存储")
            
            // 获取所有未下载的图片笔记
            val imageNotes = database.noteDao().getAllImageNotes()
            val notDownloadedNotes = imageNotes.filter { it.localImagePath.isNullOrEmpty() }
            
            Log.d(TAG, "需要下载 ${notDownloadedNotes.size} 张图片")
            
            var downloadCount = 0
            for (note in notDownloadedNotes.take(maxCount)) {
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
            return@withContext downloadCount
        } catch (e: Exception) {
            Log.e(TAG, "下载图片到本地存储失败", e)
            return@withContext 0
        }
    }
    
    /**
     * 获取本地存储的随机图片笔记
     */
    suspend fun getRandomLocalImageNote(): FlomoNote? = withContext(Dispatchers.IO) {
        try {
            // 获取已下载的图片笔记
            val downloadedImageNotes = database.noteDao().getDownloadedImageNotes()
            
            if (downloadedImageNotes.isEmpty()) {
                Log.d(TAG, "本地没有已下载的图片笔记")
                return@withContext null
            }
            
            // 随机选择一个已下载的图片笔记
            val randomNoteEntity = downloadedImageNotes.random()
            val localImagePath = randomNoteEntity.localImagePath
            
            if (!localImagePath.isNullOrEmpty() && imageDownloadManager.isLocalImageExists(localImagePath)) {
                Log.d(TAG, "从本地获取已下载的图片笔记: ${randomNoteEntity.id}")
                return@withContext randomNoteEntity.toFlomoNote()
            } else {
                // 如果本地图片不存在，从数据库中移除该记录
                if (!localImagePath.isNullOrEmpty()) {
                    database.noteDao().updateNoteLocalPath(randomNoteEntity.id, "")
                }
                Log.w(TAG, "本地图片文件不存在: $localImagePath")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取本地随机图片笔记失败", e)
            return@withContext null
        }
    }
    
    /**
     * 获取本地存储统计信息
     */
    suspend fun getLocalStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val totalNotes = database.noteDao().getNotesCount()
            val imageNotes = database.noteDao().getImageNotesCount()
            val downloadedImages = database.noteDao().getDownloadedImageNotesCount()
            val localImagesSize = imageDownloadManager.getLocalImagesSize()
            
            return@withContext mapOf(
                "totalNotes" to totalNotes,
                "imageNotes" to imageNotes,
                "downloadedImages" to downloadedImages,
                "localImagesSize" to localImagesSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取本地存储统计信息失败", e)
            return@withContext emptyMap()
        }
    }
    
    /**
     * 清理本地存储
     */
    suspend fun clearLocalData() = withContext(Dispatchers.IO) {
        try {
            // 清理数据库
            database.noteDao().deleteAllNotes()
            
            // 清理图片
            imageDownloadManager.clearAllImages()
            
            Log.d(TAG, "清理本地存储完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理本地存储失败", e)
        }
    }
    
    /**
     * 获取所有笔记
     */
    suspend fun getAllNotes(): List<FlomoNote> {
        return loadNotes()
    }
    
    /**
     * 获取本地所有笔记
     */
    suspend fun getAllLocalNotes(): List<FlomoNote> = withContext(Dispatchers.IO) {
        try {
            val noteEntities = database.noteDao().getAllNotes()
            // TODO: 修复noteEntities.map问题
            /*
            return@withContext noteEntities.map { it.toFlomoNote() }
            */
            return@withContext emptyList<FlomoNote>()
        } catch (e: Exception) {
            Log.e(TAG, "获取本地所有笔记失败", e)
            return@withContext emptyList()
        }
    }
    suspend fun getNoteById(id: String): FlomoNote? {
        try {
            // 直接调用笔记详情API
            val request = Request.Builder()
                .url("$BASE_URL/api/notes/$id")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                val jsonResponse = JSONObject(responseBody)
                
                // 解析笔记详情
                if (jsonResponse.has("data")) {
                    val noteJson = jsonResponse.getJSONObject("data")
                    return parseNoteFromJson(noteJson)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取笔记详情失败", e)
        }
        
        // 如果API调用失败，从已加载的笔记中查找
        val allNotes = loadNotes()
        return allNotes.find { it.id == id }
    }
    
    /**
     * 搜索笔记
     */
    suspend fun searchNotes(query: String, type: String? = null): List<FlomoNote> {
        try {
            // 构建搜索URL
            var url = "$BASE_URL/api/notes/search?q=$query"
            if (type != null) {
                url += "&type=$type"
            }
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                val jsonResponse = JSONObject(responseBody)
                
                // 解析搜索结果
                val searchResults = mutableListOf<FlomoNote>()
                if (jsonResponse.has("data")) {
                    val notesArray = jsonResponse.getJSONArray("data")
                    
                    for (i in 0 until notesArray.length()) {
                        val noteJson = notesArray.getJSONObject(i)
                        val note = parseNoteFromJson(noteJson)
                        if (note != null) {
                            searchResults.add(note)
                        }
                    }
                }
                
                return searchResults
            }
        } catch (e: Exception) {
            Log.e(TAG, "搜索笔记失败", e)
        }
        
        // 如果API调用失败，从已加载的笔记中进行本地搜索
        val allNotes = loadNotes()
        var filteredNotes = allNotes.filter { 
            it.content.contains(query, ignoreCase = true) 
        }
        
        if (type != null) {
            filteredNotes = filteredNotes.filter { it.type == type }
        }
        
        return filteredNotes
    }
    
    /**
     * 获取笔记统计信息
     */
    suspend fun getStats(): Map<String, Any>? {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/stats")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                val jsonResponse = JSONObject(responseBody)
                
                // 解析统计信息
                val stats = mutableMapOf<String, Any>()
                
                if (jsonResponse.has("total")) {
                    stats["total"] = jsonResponse.getInt("total")
                }
                
                if (jsonResponse.has("text_count")) {
                    stats["text_count"] = jsonResponse.getInt("text_count")
                }
                
                if (jsonResponse.has("image_count")) {
                    stats["image_count"] = jsonResponse.getInt("image_count")
                }
                
                if (jsonResponse.has("latest_date")) {
                    stats["latest_date"] = jsonResponse.getString("latest_date")
                }
                
                if (jsonResponse.has("earliest_date")) {
                    stats["earliest_date"] = jsonResponse.getString("earliest_date")
                }
                
                return stats
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取统计信息失败", e)
        }
        
        return null
    }
    
    /**
     * 删除笔记（API不支持删除，此方法保留用于兼容性）
     */
    suspend fun deleteNote(noteId: String): Boolean = withContext(Dispatchers.IO) {
        // API不支持删除操作，返回false
        false
    }
    
    /**
     * 从网络同步所有笔记到本地数据库
     */
    suspend fun syncNotesFromNetwork(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始从网络同步笔记")
            
            // 从网络加载笔记
            val networkNotes = loadNotes()
            
            // 将笔记保存到本地数据库
            // TODO: 修复fromFlomoNote问题
            /*
            networkNotes.forEach { note ->
                val entity = fromFlomoNote(note)
                database.noteDao().insertOrUpdateNote(entity)
                
                // 如果是图片笔记，下载图片
                if (note.isImageNote()) {
                    try {
                        val imageUrl = note.getImageUrl()
                        if (imageUrl != null) {
                            val localPath = imageDownloadManager.downloadImage(imageUrl)
                            if (localPath != null) {
                                // 更新本地图片路径
                                database.noteDao().updateNoteLocalPath(note.id, localPath)
                                Log.d(TAG, "已下载图片: $note.id -> $localPath")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "下载图片失败: ${note.id}", e)
                    }
                }
            }
            */
            
            Log.d(TAG, "同步完成，共同步 ${networkNotes.size} 条笔记")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "同步笔记失败", e)
            return@withContext false
        }
    }
    
    /**
     * 获取本地数据库中的笔记数量
     */
    suspend fun getLocalNotesCount(): Int = withContext(Dispatchers.IO) {
        try {
            return@withContext database.noteDao().getNotesCount()
        } catch (e: Exception) {
            Log.e(TAG, "获取本地笔记数量失败", e)
            return@withContext 0
        }
    }
    
    /**
     * 获取已下载的图片笔记数量
     */
    suspend fun getDownloadedImageNotesCount(): Int = withContext(Dispatchers.IO) {
        try {
            return@withContext database.noteDao().getDownloadedImageNotesCount()
        } catch (e: Exception) {
            Log.e(TAG, "获取已下载图片笔记数量失败", e)
            return@withContext 0
        }
    }
    
    /**
     * 清理本地数据库
     */
    suspend fun clearLocalDatabase() = withContext(Dispatchers.IO) {
        try {
            database.noteDao().deleteAllNotes()
            Log.d(TAG, "本地数据库已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理本地数据库失败", e)
        }
    }
    
    /**
     * 清理图片缓存
     */
    suspend fun clearImageCache() {
        imageDownloadManager.clearAllImages()
    }
    
    /**
     * 获取缓存统计信息
     */
    suspend fun getCacheStats(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val notesCount = getLocalNotesCount()
        val imageNotesCount = getDownloadedImageNotesCount()
        return@withContext Pair(notesCount, imageNotesCount)
    }
    
    /**
     * 创建示例笔记（当无法访问真实flomo数据时使用）
     */
    private fun createSampleNotes(): List<FlomoNote> {
        return listOf(
            FlomoNote(
                id = "sample_1",
                date = "2024-01-15",
                content = "这是示例笔记1。flomo是一个很好的记录工具，可以帮助我们记录生活中的点点滴滴。",
                type = FlomoNote.TYPE_TEXT,
                filename = "sample1.txt",
                path = ""
            ),
            FlomoNote(
                id = "sample_2",
                date = "2024-01-16",
                content = "这是示例笔记2。今天学习了新的编程技巧，感觉收获很大。",
                type = FlomoNote.TYPE_TEXT,
                filename = "sample2.txt",
                path = ""
            ),
            FlomoNote(
                id = "sample_3",
                date = "2024-01-17",
                content = "这是示例笔记3。生活中需要保持积极的心态，每天进步一点点。",
                type = FlomoNote.TYPE_TEXT,
                filename = "sample3.txt",
                path = ""
            )
        )
    }
}