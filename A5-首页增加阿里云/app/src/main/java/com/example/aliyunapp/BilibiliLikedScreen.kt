package com.example.aliyunapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
fun BilibiliLikedScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bilibiliParser = remember { BilibiliParser(context) }
    val repository = remember { BilibiliFavoriteStatusRepository(context) }
    
    // 喜欢的收藏列表
    var likedFavorites by remember { mutableStateOf<List<BilibiliFavoriteItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    // 加载喜欢的收藏
    fun loadLikedFavorites() {
        isLoading = true
        coroutineScope.launch {
            try {
                val allFavorites = bilibiliParser.getAllFavorites()
                val likedItems = allFavorites.filter { favorite ->
                    val status = repository.getFavoriteStatus(favorite.bvid)
                    status?.isFavorite == true
                }
                likedFavorites = likedItems
                
                if (likedItems.isEmpty()) {
                    Toast.makeText(context, "暂无喜欢的收藏", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                likedFavorites = emptyList()
            } finally {
                isLoading = false
            }
        }
    }
    
    // 处理取消喜欢操作
    fun handleUnfavoriteAction(favorite: BilibiliFavoriteItem) {
        coroutineScope.launch {
            try {
                repository.setFavorite(favorite.bvid, false)
                // 从列表中移除
                likedFavorites = likedFavorites.filter { it.bvid != favorite.bvid }
                Toast.makeText(context, "已取消喜欢", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 打开视频链接
    fun openVideoLink(bvid: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bilibili.com/video/$bvid"))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 分享视频
    fun shareVideo(favorite: BilibiliFavoriteItem) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, favorite.title)
            shareIntent.putExtra(Intent.EXTRA_TEXT, "${favorite.title}\nhttps://www.bilibili.com/video/${favorite.bvid}")
            context.startActivity(Intent.createChooser(shareIntent, "分享视频"))
        } catch (e: Exception) {
            Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 首次加载
    LaunchedEffect(Unit) {
        loadLikedFavorites()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("哔哩哔哩喜欢")
                        Text(
                            text = "已喜欢: ${likedFavorites.size}条",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        onClick = { loadLikedFavorites() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
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
        ) {
            if (isLoading) {
                // 加载中状态
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (likedFavorites.isEmpty()) {
                // 无数据状态
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "暂无喜欢",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "暂无喜欢的收藏",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "在哔哩哔哩收藏页面标记喜欢的视频",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                // 显示喜欢的收藏列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(likedFavorites, key = { it.bvid }) { favorite ->
                        LikedFavoriteItem(
                            favorite = favorite,
                            onUnfavorite = { handleUnfavoriteAction(favorite) },
                            onPlay = { openVideoLink(favorite.bvid) },
                            onShare = { shareVideo(favorite) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 格式化时长（秒数转换为时分秒格式）
 */
private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
    } else {
        "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
    }
}

@Composable
fun LikedFavoriteItem(
    favorite: BilibiliFavoriteItem,
    onUnfavorite: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题
            Text(
                text = favorite.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 描述信息（暂时使用标题作为描述，后续可以根据需要添加描述字段）
            Text(
                text = favorite.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 视频信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BV${favorite.bvid}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (favorite.duration > 0) {
                    Text(
                        text = formatDuration(favorite.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 取消喜欢按钮
                IconButton(onClick = onUnfavorite) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "取消喜欢",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 播放按钮
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放"
                    )
                }
                
                // 分享按钮
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "分享"
                    )
                }
            }
        }
    }
}