package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 收藏仓库类
 * 负责管理用户收藏的flomo笔记
 */
class FavoritesRepository(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "favorites_prefs"
        private const val FAVORITES_KEY = "favorite_notes"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 添加笔记到收藏
     */
    suspend fun addToFavorites(note: FlomoNote): Boolean = withContext(Dispatchers.IO) {
        try {
            val favorites = getFavorites().toMutableList()
            
            // 检查是否已经收藏
            if (favorites.any { it.id == note.id }) {
                return@withContext false // 已经收藏，不需要重复添加
            }
            
            // 创建收藏副本，标记为已收藏
            val favoriteNote = note.copy(isFavorite = true)
            favorites.add(favoriteNote)
            
            // 保存到SharedPreferences
            val json = gson.toJson(favorites)
            prefs.edit()
                .putString(FAVORITES_KEY, json)
                .apply()
                
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 从收藏中移除笔记
     */
    suspend fun removeFromFavorites(noteId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val favorites = getFavorites().toMutableList()
            val removed = favorites.removeAll { it.id == noteId }
            
            if (removed) {
                // 保存更新后的收藏列表
                val json = gson.toJson(favorites)
                prefs.edit()
                    .putString(FAVORITES_KEY, json)
                    .apply()
            }
            
            removed
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 获取所有收藏的笔记
     */
    suspend fun getFavorites(): List<FlomoNote> = withContext(Dispatchers.IO) {
        try {
            val json = prefs.getString(FAVORITES_KEY, null)
            if (json == null) {
                return@withContext emptyList()
            }
            
            val type = object : TypeToken<List<FlomoNote>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 检查笔记是否已收藏
     */
    suspend fun isFavorite(noteId: String): Boolean = withContext(Dispatchers.IO) {
        val favorites = getFavorites()
        favorites.any { it.id == noteId }
    }
    
    /**
     * 切换笔记的收藏状态
     */
    suspend fun toggleFavorite(note: FlomoNote): Boolean = withContext(Dispatchers.IO) {
        if (note.isFavorite) {
            removeFromFavorites(note.id)
        } else {
            addToFavorites(note)
        }
    }
    
    /**
     * 清空所有收藏
     */
    suspend fun clearFavorites(): Boolean = withContext(Dispatchers.IO) {
        try {
            prefs.edit()
                .remove(FAVORITES_KEY)
                .apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}