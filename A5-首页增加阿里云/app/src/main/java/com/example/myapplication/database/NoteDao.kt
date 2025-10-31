package com.example.myapplication.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 笔记数据访问对象
 */
@Dao
interface NoteDao {
    
    /**
     * 插入或更新笔记
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNote(note: NoteEntity)
    
    /**
     * 批量插入或更新笔记
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNotes(notes: List<NoteEntity>)
    
    /**
     * 获取所有笔记
     */
    @Query("SELECT * FROM notes ORDER BY date DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>
    
    /**
     * 获取所有图片笔记
     */
    @Query("SELECT * FROM notes WHERE type = 'image' ORDER BY date DESC")
    suspend fun getAllImageNotes(): List<NoteEntity>
    
    /**
     * 获取已下载的图片笔记
     */
    @Query("SELECT * FROM notes WHERE type = 'image' AND isDownloaded = 1 ORDER BY date DESC")
    suspend fun getDownloadedImageNotes(): List<NoteEntity>
    
    /**
     * 根据ID获取笔记
     */
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): NoteEntity?
    
    /**
     * 更新笔记的本地图片路径和下载状态
     */
    @Query("UPDATE notes SET localImagePath = :localImagePath, isDownloaded = 1, lastUpdated = :lastUpdated WHERE id = :id")
    suspend fun updateNoteLocalPath(id: String, localImagePath: String, lastUpdated: Long = System.currentTimeMillis())
    
    /**
     * 删除笔记
     */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: String)
    
    /**
     * 清空所有笔记
     */
    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
    
    /**
     * 获取笔记数量
     */
    @Query("SELECT COUNT(*) FROM notes")
    suspend fun getNotesCount(): Int
    
    /**
     * 获取图片笔记数量
     */
    @Query("SELECT COUNT(*) FROM notes WHERE type = 'image'")
    suspend fun getImageNotesCount(): Int
    
    /**
     * 获取已下载图片笔记数量
     */
    @Query("SELECT COUNT(*) FROM notes WHERE type = 'image' AND isDownloaded = 1")
    suspend fun getDownloadedImageNotesCount(): Int
}