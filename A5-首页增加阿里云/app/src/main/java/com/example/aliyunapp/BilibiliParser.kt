package com.example.aliyunapp

import android.content.Context
import android.util.Log
import com.example.aliyunapp.entity.BilibiliFavoriteStatusEntity
import com.example.aliyunapp.repository.BilibiliFavoriteStatusRepository
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

/**
 * 哔哩哔哩收藏数据解析器
 */
class BilibiliParser(private val context: Context) {
    
    companion object {
        private const val TAG = "BilibiliParser"
        private const val BILIBILI_FOLDER = "bilibili"
    }
    
    /**
     * 获取所有哔哩哔哩收藏数据
     */
    suspend fun getAllFavorites(): List<BilibiliFavoriteItem> = withContext(Dispatchers.IO) {
        try {
            val jsonFiles = getBilibiliJsonFiles()
            if (jsonFiles.isEmpty()) {
                Log.w(TAG, "未找到哔哩哔哩收藏JSON文件")
                return@withContext emptyList()
            }
            
            val allFavorites = mutableListOf<BilibiliFavoriteItem>()
            
            jsonFiles.forEach { file ->
                try {
                    val favorites = parseJsonFile(file)
                    allFavorites.addAll(favorites)
                } catch (e: Exception) {
                    Log.e(TAG, "解析文件 ${file.name} 失败: ${e.message}")
                }
            }
            
            Log.d(TAG, "成功解析 ${allFavorites.size} 个收藏项")
            allFavorites
        } catch (e: Exception) {
            Log.e(TAG, "获取哔哩哔哩收藏数据失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 获取随机一个收藏项
     */
    suspend fun getRandomFavorite(repository: BilibiliFavoriteStatusRepository? = null): BilibiliFavoriteItem? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始获取随机收藏项")
            val allFavorites = getAllFavorites()
            Log.d(TAG, "总共找到 ${allFavorites.size} 个收藏项")
            
            // 如果有repository，过滤掉已删除的收藏项
            val availableFavorites = if (repository != null) {
                // 获取所有已删除的收藏项
                val deletedStatuses = repository.getAllDeleted()
                val firstStatusList = deletedStatuses.first()
                val deletedBvids = firstStatusList.map { status: BilibiliFavoriteStatusEntity -> status.bvid }.toSet<String>()
                
                allFavorites.filter { favorite ->
                    !deletedBvids.contains(favorite.bvid)
                }
            } else {
                allFavorites
            }
            
            Log.d(TAG, "过滤后可用收藏项: ${availableFavorites.size} 个")
            
            if (availableFavorites.isNotEmpty()) {
                val randomFavorite = availableFavorites.random()
                Log.d(TAG, "随机选择收藏项: ${randomFavorite.title}")
                randomFavorite
            } else {
                Log.w(TAG, "未找到任何可用收藏项")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取随机收藏项失败: ${e.message}")
            null
        }
    }
    
    /**
     * 获取JSON文件的键信息
     */
    suspend fun getJsonKeys(): String = withContext(Dispatchers.IO) {
        try {
            val jsonFiles = getBilibiliJsonFiles()
            if (jsonFiles.isEmpty()) {
                "未找到JSON文件"
            } else {
                val firstFile = jsonFiles.first()
                val jsonString = firstFile.readText(Charset.forName("UTF-8"))
                
                if (jsonString.isEmpty()) {
                    "JSON文件为空"
                } else {
                    val gson = Gson()
                    val jsonObject = gson.fromJson(jsonString, com.google.gson.JsonObject::class.java)
                    val keys = jsonObject.keySet()
                    "JSON文件包含的键: ${keys.joinToString(", ")}"
                }
            }
        } catch (e: Exception) {
            "获取JSON键失败: ${e.message}"
        }
    }
    
    /**
     * 获取bilibili文件夹下的所有JSON文件名
     */
    fun getBilibiliJsonFileNames(): List<String> {
        return try {
            val fileNames = mutableListOf<String>()
            
            // 首先尝试从assets读取（这是APK打包后的文件位置）
            val assetManager = context.assets
            try {
                val files = assetManager.list(BILIBILI_FOLDER)
                if (files != null && files.isNotEmpty()) {
                    files.forEach { fileName ->
                        if (fileName.endsWith(".json")) {
                            fileNames.add(fileName)
                        }
                    }
                    Log.d(TAG, "从assets找到 ${fileNames.size} 个JSON文件")
                }
            } catch (e: Exception) {
                Log.w(TAG, "从assets读取bilibili文件夹失败: ${e.message}")
            }
            
            // 如果assets中没有，尝试从项目根目录的bilibili文件夹读取（开发调试用）
            if (fileNames.isEmpty()) {
                val projectBilibiliDir = File("/Users/grit/Downloads/01-我的github项目/my_tools_apk/A5-首页增加阿里云/bilibili")
                if (projectBilibiliDir.exists()) {
                    val projectJsonFiles = projectBilibiliDir.listFiles { file ->
                        file.isFile && file.name.endsWith(".json")
                    } ?: emptyArray()
                    projectJsonFiles.forEach { file ->
                        fileNames.add(file.name)
                    }
                    Log.d(TAG, "从项目目录找到 ${projectJsonFiles.size} 个JSON文件")
                }
            }
            
            Log.d(TAG, "总共找到 ${fileNames.size} 个JSON文件")
            fileNames
        } catch (e: Exception) {
            Log.e(TAG, "获取bilibili文件夹失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 获取bilibili文件夹下的所有JSON文件
     */
    private fun getBilibiliJsonFiles(): List<File> {
        return try {
            val jsonFiles = mutableListOf<File>()
            
            // 首先尝试从assets读取（这是APK打包后的文件位置）
            val assetManager = context.assets
            try {
                val files = assetManager.list(BILIBILI_FOLDER)
                if (files != null && files.isNotEmpty()) {
                    files.forEach { fileName ->
                        if (fileName.endsWith(".json")) {
                            // 创建临时文件来读取assets中的内容
                            val tempFile = File.createTempFile("bilibili_", ".json")
                            assetManager.open("$BILIBILI_FOLDER/$fileName").use { inputStream ->
                                tempFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            jsonFiles.add(tempFile)
                        }
                    }
                    Log.d(TAG, "从assets找到 ${jsonFiles.size} 个JSON文件")
                }
            } catch (e: Exception) {
                Log.w(TAG, "从assets读取bilibili文件夹失败: ${e.message}")
            }
            
            // 如果assets中没有，尝试从项目根目录的bilibili文件夹读取（开发调试用）
            if (jsonFiles.isEmpty()) {
                val projectBilibiliDir = File("/Users/grit/Downloads/01-我的github项目/my_tools_apk/A5-首页增加阿里云/bilibili")
                if (projectBilibiliDir.exists()) {
                    val projectJsonFiles = projectBilibiliDir.listFiles { file ->
                        file.isFile && file.name.endsWith(".json")
                    } ?: emptyArray()
                    jsonFiles.addAll(projectJsonFiles)
                    Log.d(TAG, "从项目目录找到 ${projectJsonFiles.size} 个JSON文件")
                }
            }
            
            Log.d(TAG, "总共找到 ${jsonFiles.size} 个JSON文件")
            jsonFiles
        } catch (e: Exception) {
            Log.e(TAG, "获取bilibili文件夹失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 解析JSON文件
     */
    private fun parseJsonFile(file: File): List<BilibiliFavoriteItem> {
        return try {
            Log.d(TAG, "开始解析JSON文件: ${file.name}")
            val jsonString = file.readText(Charset.forName("UTF-8"))
            
            // 检查JSON文件内容
            if (jsonString.isEmpty()) {
                Log.w(TAG, "JSON文件为空")
                return emptyList()
            }
            
            // 打印JSON文件的前500个字符用于调试
            Log.d(TAG, "JSON文件内容预览: ${jsonString.take(500)}")
            
            // 使用Gson解析为JsonObject来检查键
            val gson = Gson()
            val jsonObject = gson.fromJson(jsonString, com.google.gson.JsonObject::class.java)
            
            // 打印JSON对象的所有键
            Log.d(TAG, "JSON文件包含的键: ${jsonObject.keySet()}")
            
            // 检查是否有favorites键
            if (!jsonObject.has("favorites")) {
                Log.e(TAG, "JSON文件中缺少'favorites'键，实际键为: ${jsonObject.keySet()}")
                return emptyList()
            }
            
            // 检查favorites字段的类型
            val favoritesElement = jsonObject.get("favorites")
            Log.d(TAG, "favorites字段类型: ${favoritesElement.javaClass.simpleName}")
            
            val response = gson.fromJson(jsonString, BilibiliFavoritesResponse::class.java)
            
            // 检查解析结果
            if (response.favorites.isEmpty()) {
                Log.w(TAG, "解析结果中favorites字段为空")
                // 尝试直接解析favorites数组
                try {
                    val favoritesArray = gson.fromJson(favoritesElement, Array<BilibiliFavorite>::class.java)
                    Log.d(TAG, "直接解析favorites数组成功，找到 ${favoritesArray.size} 个收藏夹")
                } catch (e: Exception) {
                    Log.e(TAG, "直接解析favorites数组失败: ${e.message}")
                }
                return emptyList()
            }
            
            Log.d(TAG, "找到 ${response.favorites.size} 个收藏夹")
            
            // 提取所有收藏项
            val favoriteItems = mutableListOf<BilibiliFavoriteItem>()
            
            response.favorites.forEachIndexed { index, favorite ->
                Log.d(TAG, "收藏夹 ${index + 1}: ${favorite.title}, 包含 ${favorite.medias.size} 个媒体项")
                
                if (favorite.medias.isEmpty()) {
                    Log.w(TAG, "收藏夹 ${favorite.title} 中没有媒体项")
                }
                
                favorite.medias.forEach { media ->
                    if (!media.invalid) {
                        val favoriteItem = BilibiliFavoriteItem(
                            title = media.title,
                            link = media.link,
                            bvid = media.bvid,
                            cover = media.cover,
                            duration = media.duration,
                            favTime = media.favTime
                        )
                        favoriteItems.add(favoriteItem)
                        Log.d(TAG, "添加收藏项: ${media.title}, BV号: ${media.bvid}")
                    } else {
                        Log.d(TAG, "跳过无效媒体项: ${media.title}")
                    }
                }
            }
            
            Log.d(TAG, "从文件 ${file.name} 解析出 ${favoriteItems.size} 个收藏项")
            favoriteItems
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON语法错误: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "解析JSON文件失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 获取收藏项数量
     */
    suspend fun getFavoriteCount(): Int = withContext(Dispatchers.IO) {
        try {
            val allFavorites = getAllFavorites()
            allFavorites.size
        } catch (e: Exception) {
            Log.e(TAG, "获取收藏项数量失败: ${e.message}")
            0
        }
    }
    
    /**
     * 根据BV号获取收藏项
     */
    suspend fun getFavoriteByBvid(bvid: String): BilibiliFavoriteItem? = withContext(Dispatchers.IO) {
        try {
            val allFavorites = getAllFavorites()
            allFavorites.find { it.bvid == bvid }
        } catch (e: Exception) {
            Log.e(TAG, "根据BV号获取收藏项失败: ${e.message}")
            null
        }
    }
}