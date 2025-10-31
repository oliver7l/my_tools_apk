package com.example.myapplication.network

import com.example.myapplication.FlomoNote
import kotlinx.coroutines.delay

/**
 * Flomo API的模拟实现
 * 用于测试和开发阶段
 */
class MockFlomoApi : FlomoApi {
    
    override suspend fun getNotes(): ApiResponse<List<FlomoNote>> {
        // 模拟网络延迟
        delay(1000)
        
        return try {
            // 模拟返回一些测试数据
            val notes = listOf(
                FlomoNote(
                    id = "1",
                    date = "2023-10-30",
                    content = "这是第一条测试笔记",
                    type = FlomoNote.TYPE_TEXT,
                    filename = "",
                    path = ""
                ),
                FlomoNote(
                    id = "2",
                    date = "2023-10-30",
                    content = "这是第二条测试笔记",
                    type = FlomoNote.TYPE_TEXT,
                    filename = "",
                    path = ""
                ),
                FlomoNote(
                    id = "3",
                    date = "2023-10-30",
                    content = "https://example.com/image1.jpg",
                    type = FlomoNote.TYPE_IMAGE,
                    filename = "image1.jpg",
                    path = "https://example.com/image1.jpg"
                )
            )
            
            ApiResponse(
                success = true,
                message = "获取笔记成功",
                data = notes
            )
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                message = "获取笔记失败: ${e.message}",
                data = null
            )
        }
    }
}