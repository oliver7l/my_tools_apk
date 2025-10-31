package com.example.aliyunapp

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import kotlinx.coroutines.launch
import androidx.compose.ui.ExperimentalComposeUiApi

/**
 * Flomo笔记查看器组件
 * 用于展示随机flomo笔记，支持收藏和删除功能
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FlomoNoteViewer(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    flomoNotesRepository: FlomoNotesRepository,
    favoritesRepository: FavoritesRepository,
    initialNote: FlomoNote? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentNote by remember { mutableStateOf<FlomoNote?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // 获取MainActivity实例以检查权限
    val activity = context as? MainActivity
    
    // 加载随机笔记
    fun loadRandomNote() {
        isLoading = true
        coroutineScope.launch {
            try {
                val note = flomoNotesRepository.getRandomNote()
                if (note != null) {
                    currentNote = note
                    isFavorite = favoritesRepository.isFavorite(note.id)
                } else {
                    Toast.makeText(context, "没有找到笔记", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "加载笔记失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }
    
    // 初始化加载
    LaunchedEffect(initialNote) {
        if (initialNote != null) {
            currentNote = initialNote
            isFavorite = favoritesRepository.isFavorite(initialNote.id)
            isLoading = false
        } else {
            loadRandomNote()
        }
    }
    
    // 切换收藏状态
    fun toggleFavorite() {
        val note = currentNote ?: return
        coroutineScope.launch {
            try {
                val success = favoritesRepository.toggleFavorite(note)
                if (success) {
                    isFavorite = !isFavorite
                    // 更新笔记的收藏状态
                    currentNote = note.copy(isFavorite = isFavorite)
                    Toast.makeText(
                        context, 
                        if (isFavorite) "已添加到收藏" else "已从收藏中移除", 
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 删除笔记（从收藏中删除，API不支持删除笔记）
    fun deleteNote() {
        val note = currentNote ?: return
        
        // 显示删除确认对话框
        showDeleteDialog = true
    }
    
    // 确认删除笔记（仅从收藏中删除）
    fun confirmDeleteNote() {
        val note = currentNote ?: return
        coroutineScope.launch {
            try {
                // 从收藏中移除（如果是收藏的）
                if (isFavorite) {
                    favoritesRepository.removeFromFavorites(note.id)
                    isFavorite = false
                    currentNote = note.copy(isFavorite = false)
                    Toast.makeText(context, "已从收藏中移除", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "笔记不在收藏中", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showDeleteDialog = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flomo笔记") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (currentNote == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "没有找到笔记",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { loadRandomNote() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("重新加载")
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 笔记内容区域
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // 笔记日期
                            Text(
                                text = currentNote!!.date,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.End)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 笔记内容
                            if (currentNote!!.type == FlomoNote.TYPE_IMAGE) {
                                // 图片笔记
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // 判断是本地文件路径还是URL
                                    val imageUrl = if (currentNote!!.content.startsWith("http")) {
                                        // API返回的URL
                                        currentNote!!.content
                                    } else {
                                        // 本地文件路径，需要转换为API URL
                                        "https://elizabet-isotheral-janeth.ngrok-free.dev/api/files/${currentNote!!.filename}"
                                    }
                                    
                                    Image(
                                        painter = rememberImagePainter(
                                            data = imageUrl,
                                            builder = {
                                                crossfade(true)
                                                placeholder(android.R.drawable.ic_menu_gallery)
                                            }
                                        ),
                                        contentDescription = "笔记图片",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // 文件名
                                Text(
                                    text = "文件名: ${currentNote!!.filename}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                // 文本笔记
                                Text(
                                    text = currentNote!!.content,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 操作按钮区域
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 收藏按钮
                        FloatingActionButton(
                            onClick = { toggleFavorite() },
                            modifier = Modifier.size(48.dp),
                            containerColor = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(
                                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // 从收藏移除按钮
                        FloatingActionButton(
                            onClick = { deleteNote() },
                            modifier = Modifier.size(48.dp),
                            containerColor = Color.Red,
                            contentColor = Color.White
                        ) {
                            Icon(
                                Icons.Default.RemoveCircle,
                                contentDescription = "从收藏移除",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // 刷新按钮
                        FloatingActionButton(
                            onClick = { loadRandomNote() },
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "随机笔记",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        
        // 删除确认对话框
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("从收藏中移除") },
                text = { Text("确定要从收藏中移除这条笔记吗？") },
                confirmButton = {
                    Button(
                        onClick = { confirmDeleteNote() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("移除", color = Color.White)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showDeleteDialog = false }
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}