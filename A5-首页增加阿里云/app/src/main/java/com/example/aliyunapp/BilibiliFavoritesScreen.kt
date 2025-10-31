package com.example.aliyunapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aliyunapp.repository.BilibiliFavoriteStatusRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliFavoritesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bilibiliParser = remember { BilibiliParser(context) }
    val repository = remember { BilibiliFavoriteStatusRepository(context) }
    
    // 当前显示的随机收藏项
    var currentFavorite by remember { mutableStateOf<BilibiliFavoriteItem?>(null) }
    var totalFavoritesCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var jsonFileContent by remember { mutableStateOf<String?>(null) }
    var jsonKeysInfo by remember { mutableStateOf<String?>(null) }
    
    // 加载随机收藏项
    fun loadRandomFavorite() {
        isLoading = true
        jsonFileContent = null
        jsonKeysInfo = null
        coroutineScope.launch {
            try {
                // 先获取JSON文件内容
                val jsonFileNames = bilibiliParser.getBilibiliJsonFileNames()
                if (jsonFileNames.isNotEmpty()) {
                    val firstFileName = jsonFileNames.first()
                    val content = context.assets.open("bilibili/$firstFileName").bufferedReader().use { it.readText() }
                    jsonFileContent = content
                }
                
                // 获取JSON键信息
                jsonKeysInfo = bilibiliParser.getJsonKeys()
                
                // 获取总收藏数量
                totalFavoritesCount = bilibiliParser.getFavoriteCount()
                
                val randomFavorite = bilibiliParser.getRandomFavorite(repository)
                if (randomFavorite != null) {
                    // 从数据库获取收藏状态
                    val currentStatus = repository.getFavoriteStatus(randomFavorite.bvid)
                    currentFavorite = randomFavorite.copy(
                        isFavorite = currentStatus?.isFavorite ?: false,
                        isDeleted = currentStatus?.isDeleted ?: false
                    )
                } else {
                    currentFavorite = null
                    Toast.makeText(context, "未找到收藏内容", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }
    
    // 处理喜欢操作
    fun handleFavoriteAction(favorite: BilibiliFavoriteItem) {
        coroutineScope.launch {
            try {
                val newFavoriteState = !favorite.isFavorite
                repository.setFavorite(favorite.bvid, newFavoriteState)
                currentFavorite = favorite.copy(isFavorite = newFavoriteState)
                val message = if (newFavoriteState) "已添加到喜欢" else "已取消喜欢"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 处理删除操作
    fun handleDeleteAction(favorite: BilibiliFavoriteItem) {
        coroutineScope.launch {
            try {
                val newDeleteState = !favorite.isDeleted
                repository.setDeleted(favorite.bvid, newDeleteState)
                currentFavorite = favorite.copy(isDeleted = newDeleteState)
                val message = if (newDeleteState) "已标记为删除" else "已取消删除标记"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 首次加载
    LaunchedEffect(Unit) {
        loadRandomFavorite()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("哔哩哔哩随机收藏")
                        if (totalFavoritesCount > 0) {
                            Text(
                                text = "总收藏: ${totalFavoritesCount}条",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 刷新按钮
                    IconButton(
                        onClick = { loadRandomFavorite() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                // 加载中状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (currentFavorite == null) {
                // 无数据状态
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "暂无收藏内容",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "请检查bilibili文件夹下的JSON文件",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    // 显示JSON键信息
                    if (jsonKeysInfo != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "JSON文件键信息:",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = jsonKeysInfo!!,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    // 显示JSON文件内容用于调试
                    if (jsonFileContent != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "JSON文件内容（前500字符）:",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = jsonFileContent!!.take(500) + if (jsonFileContent!!.length > 500) "..." else "",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                // 显示收藏内容
                currentFavorite?.let { favorite ->
                    // 标题
                    Text(
                        text = favorite.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // 视频信息
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // BV号
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "BV号:",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = favorite.bvid,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // 收藏时间
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "收藏时间:",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatFavTime(favorite.favTime),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            // 视频时长
                            if (favorite.duration > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "视频时长:",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatDuration(favorite.duration),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    
                    // 操作按钮
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 第一行：主要操作按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // 打开视频按钮
                            Button(
                                onClick = {
                                    openVideoInBrowser(context, favorite.link)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "播放",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("打开视频")
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // 分享按钮
                            Button(
                                onClick = {
                                    shareVideo(context, favorite.title, favorite.link)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "分享",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("分享")
                            }
                        }
                        
                        // 第二行：喜欢和删除按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // 喜欢按钮
                            Button(
                                onClick = {
                                    handleFavoriteAction(favorite)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (favorite.isFavorite) MaterialTheme.colorScheme.primaryContainer 
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = if (favorite.isFavorite) Icons.Default.Favorite 
                                    else Icons.Default.FavoriteBorder,
                                    contentDescription = "喜欢",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (favorite.isFavorite) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (favorite.isFavorite) "已喜欢" else "喜欢",
                                    color = if (favorite.isFavorite) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // 删除按钮
                            Button(
                                onClick = {
                                    handleDeleteAction(favorite)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (favorite.isDeleted) MaterialTheme.colorScheme.errorContainer 
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = if (favorite.isDeleted) Icons.Default.Delete 
                                    else Icons.Default.DeleteOutline,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (favorite.isDeleted) MaterialTheme.colorScheme.error 
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (favorite.isDeleted) "已删除" else "删除",
                                    color = if (favorite.isDeleted) MaterialTheme.colorScheme.error 
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    // 链接信息
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "视频链接:",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = favorite.link,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 格式化收藏时间
 */
private fun formatFavTime(timestamp: Long): String {
    val date = java.util.Date(timestamp * 1000)
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return formatter.format(date)
}

/**
 * 格式化视频时长
 */
private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
}

/**
 * 在浏览器中打开视频
 */
private fun openVideoInBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 分享视频
 */
private fun shareVideo(context: Context, title: String, url: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享视频"))
    } catch (e: Exception) {
        Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
    }
}