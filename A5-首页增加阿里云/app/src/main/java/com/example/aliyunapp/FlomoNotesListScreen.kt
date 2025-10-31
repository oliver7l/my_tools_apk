package com.example.aliyunapp

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import kotlinx.coroutines.launch

/**
 * Flomo笔记列表界面
 * 展示所有笔记列表，支持搜索和筛选
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlomoNotesListScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    onNoteClick: (FlomoNote) -> Unit,
    flomoNotesRepository: FlomoNotesRepository,
    favoritesRepository: FavoritesRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<FlomoNote>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("all") } // all, text, image
    var showFilterDialog by remember { mutableStateOf(false) }
    var favoriteNotes by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // 加载笔记列表
    fun loadNotes() {
        isLoading = true
        coroutineScope.launch {
            try {
                val allNotes = flomoNotesRepository.getAllNotes()
                notes = allNotes
                
                // 加载收藏状态
                val favoriteIds = mutableSetOf<String>()
                allNotes.forEach { note ->
                    if (favoritesRepository.isFavorite(note.id)) {
                        favoriteIds.add(note.id)
                    }
                }
                favoriteNotes = favoriteIds
            } catch (e: Exception) {
                Toast.makeText(context, "加载笔记失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }
    
    // 搜索笔记
    fun searchNotes() {
        isLoading = true
        coroutineScope.launch {
            try {
                val type = if (filterType == "all") null else filterType
                val searchResults = flomoNotesRepository.searchNotes(searchQuery, type)
                notes = searchResults
                
                // 更新收藏状态
                val favoriteIds = mutableSetOf<String>()
                searchResults.forEach { note ->
                    if (favoritesRepository.isFavorite(note.id)) {
                        favoriteIds.add(note.id)
                    }
                }
                favoriteNotes = favoriteIds
            } catch (e: Exception) {
                Toast.makeText(context, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }
    
    // 切换收藏状态
    fun toggleFavorite(noteId: String) {
        coroutineScope.launch {
            try {
                val note = notes.find { it.id == noteId } ?: return@launch
                val success = favoritesRepository.toggleFavorite(note)
                if (success) {
                    val newFavoriteNotes = if (favoriteNotes.contains(noteId)) {
                        favoriteNotes - noteId
                    } else {
                        favoriteNotes + noteId
                    }
                    favoriteNotes = newFavoriteNotes
                    Toast.makeText(
                        context,
                        if (newFavoriteNotes.contains(noteId)) "已添加到收藏" else "已从收藏中移除",
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
    
    // 初始化加载
    LaunchedEffect(Unit) {
        loadNotes()
    }
    
    // 当搜索查询或筛选类型变化时执行搜索
    LaunchedEffect(searchQuery, filterType) {
        if (searchQuery.isNotEmpty() || filterType != "all") {
            searchNotes()
        } else {
            loadNotes()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flomo笔记列表") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 筛选按钮
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "筛选")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("搜索笔记") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            
            // 筛选标签
            if (filterType != "all") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "筛选: ${if (filterType == "text") "文本笔记" else "图片笔记"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { filterType = "all" },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "清除筛选",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            // 笔记列表
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (notes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty() || filterType != "all") "没有找到匹配的笔记" else "没有笔记",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { loadNotes() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("重新加载")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes) { note ->
                        NoteItem(
                            note = note,
                            isFavorite = favoriteNotes.contains(note.id),
                            onNoteClick = { onNoteClick(note) },
                            onFavoriteClick = { toggleFavorite(note.id) }
                        )
                    }
                }
            }
        }
        
        // 筛选对话框
        if (showFilterDialog) {
            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { Text("筛选笔记") },
                text = {
                    Column {
                        Text("选择笔记类型:")
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { filterType = "all"; showFilterDialog = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = filterType == "all",
                                onClick = { filterType = "all"; showFilterDialog = false }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("全部笔记")
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { filterType = "text"; showFilterDialog = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = filterType == "text",
                                onClick = { filterType = "text"; showFilterDialog = false }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("文本笔记")
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { filterType = "image"; showFilterDialog = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = filterType == "image",
                                onClick = { filterType = "image"; showFilterDialog = false }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("图片笔记")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showFilterDialog = false }) {
                        Text("确定")
                    }
                }
            )
        }
    }
}

/**
 * 笔记列表项组件
 */
@Composable
fun NoteItem(
    note: FlomoNote,
    isFavorite: Boolean,
    onNoteClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNoteClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 笔记内容预览
            if (note.type == FlomoNote.TYPE_IMAGE) {
                // 图片笔记
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    // 判断是本地文件路径还是URL
                    val imageUrl = if (note.content.startsWith("http")) {
                        // API返回的URL
                        note.content
                    } else if (note.filename.isNotEmpty()) {
                        // 使用文件名构建API URL
                        "https://elizabet-isotheral-janeth.ngrok-free.dev/api/files/${note.filename}"
                    } else {
                        // 如果没有文件名，从内容中提取文件名
                        val fileName = note.content.substringAfterLast("/")
                        "https://elizabet-isotheral-janeth.ngrok-free.dev/api/files/$fileName"
                    }
                    
                    // 添加日志记录
                    android.util.Log.d("FlomoNotesListScreen", "图片URL: $imageUrl")
                    android.util.Log.d("FlomoNotesListScreen", "笔记内容: ${note.content}")
                    android.util.Log.d("FlomoNotesListScreen", "文件名: ${note.filename}")
                    android.util.Log.d("FlomoNotesListScreen", "文件路径: ${note.path}")
                    
                    Image(
                        painter = rememberImagePainter(
                            data = imageUrl,
                            builder = {
                                crossfade(true)
                                placeholder(android.R.drawable.ic_menu_gallery)
                                error(android.R.drawable.ic_menu_report_image)
                            }
                        ),
                        contentDescription = "笔记图片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // 文本笔记
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Note,
                        contentDescription = "文本笔记",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 笔记信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 笔记内容预览
                Text(
                    text = if (note.type == FlomoNote.TYPE_TEXT) {
                        note.content.take(50).let { 
                            if (it.length < note.content.length) "$it..." else it 
                        }
                    } else {
                        note.filename
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 笔记日期和类型
                Row {
                    Text(
                        text = note.date,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = if (note.type == FlomoNote.TYPE_TEXT) "文本" else "图片",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 收藏按钮
            IconButton(
                onClick = onFavoriteClick
            ) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}