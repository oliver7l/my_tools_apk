package com.example.myapplication

import java.io.Serializable

/**
 * Flomo笔记数据类
 * 用于表示一个flomo笔记，可以是文本或图片
 */
data class FlomoNote(
    val id: String, // 唯一标识符
    val date: String, // 笔记日期
    val content: String, // 笔记内容（文本内容或图片路径）
    val type: String, // 笔记类型：text 或 image
    val filename: String, // 文件名
    val path: String, // 文件完整路径
    val isFavorite: Boolean = false, // 是否已收藏
    val localImagePath: String? = null // 本地图片路径（用于缓存）
) : Serializable {
    
    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
    }
    
    /**
     * 获取笔记的简短描述
     */
    fun getShortDescription(maxLength: Int = 50): String {
        return if (type == TYPE_TEXT) {
            if (content.length > maxLength) {
                "${content.take(maxLength)}..."
            } else {
                content
            }
        } else {
            "图片笔记: $filename"
        }
    }
    
    /**
     * 判断是否为图片笔记
     */
    fun isImageNote(): Boolean {
        return type == TYPE_IMAGE
    }
    
    /**
     * 获取图片URL
     */
    fun getImageUrl(): String? {
        return if (isImageNote()) {
            path
        } else {
            null
        }
    }
}