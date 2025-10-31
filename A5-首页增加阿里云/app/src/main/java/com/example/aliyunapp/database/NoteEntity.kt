package com.example.aliyunapp.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.aliyunapp.FlomoNote

/**
 * 笔记实体类，用于Room数据库存储
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val date: String,
    val type: String,
    val filename: String,
    val path: String,
    val imageUrl: String, // 图片URL，用于下载图片
    val localImagePath: String? = null, // 本地图片路径，下载后保存
    val isDownloaded: Boolean = false, // 是否已下载图片
    val lastUpdated: Long = System.currentTimeMillis() // 最后更新时间
)

/**
 * 将NoteEntity转换为FlomoNote
 */
fun NoteEntity.toFlomoNote(): FlomoNote {
    return FlomoNote(
        id = id,
        content = if (isDownloaded && localImagePath != null) localImagePath else imageUrl,
        date = date,
        type = type,
        filename = filename,
        path = path
    )
}

/**
 * 将FlomoNote转换为NoteEntity
 */
fun FlomoNote.toNoteEntity(): NoteEntity {
    return NoteEntity(
        id = id,
        content = content,
        date = date,
        type = type,
        filename = filename,
        path = path,
        imageUrl = content // 原始content就是图片URL
    )
}

/**
 * 从FlomoNote创建NoteEntity的静态方法
 */
fun fromFlomoNote(flomoNote: FlomoNote): NoteEntity {
    return NoteEntity(
        id = flomoNote.id,
        content = flomoNote.content,
        date = flomoNote.date,
        type = flomoNote.type,
        filename = flomoNote.filename,
        path = flomoNote.path,
        imageUrl = flomoNote.content // 原始content就是图片URL
    )
}