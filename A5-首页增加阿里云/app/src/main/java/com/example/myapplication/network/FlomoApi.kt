package com.example.myapplication.network

import com.example.myapplication.FlomoNote

/**
 * Flomo API接口
 * 用于从远程服务器获取笔记数据
 */
interface FlomoApi {
    /**
     * 获取所有笔记
     * @return API响应，包含成功状态、消息和笔记数据
     */
    suspend fun getNotes(): ApiResponse<List<FlomoNote>>
}

/**
 * API响应数据类
 * @param T 响应数据的类型
 */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?
)