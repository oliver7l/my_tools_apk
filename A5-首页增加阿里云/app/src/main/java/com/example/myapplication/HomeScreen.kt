package com.example.myapplication

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class FeatureItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val action: () -> Unit
)

@Composable
fun HomeScreen(
    onNavigateToRandomPhoto: () -> Unit,
    onNavigateToFlomoNotes: () -> Unit,
    onNavigateToFlomoNotesList: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSpeechToText: () -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToAliyunTest: () -> Unit
) {
    val context = LocalContext.current
    val favoritesRepository = remember { FavoritesRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // 获取收藏的笔记数量
    var favoriteCount by remember { mutableStateOf(0) }
    
    // 在组件加载时获取收藏数量
    LaunchedEffect(Unit) {
        favoriteCount = favoritesRepository.getFavorites().size
    }
    
    // 功能列表
    val features = remember {
        listOf(
            FeatureItem(
                title = "我的收藏",
                description = "已收藏 $favoriteCount 条笔记",
                icon = Icons.Default.Favorite,
                action = onNavigateToFavorites
            ),
            FeatureItem(
                title = "随机照片",
                description = "随机浏览您的照片集",
                icon = Icons.Default.Shuffle,
                action = onNavigateToRandomPhoto
            ),
            FeatureItem(
                title = "Flomo笔记",
                description = "浏览和管理您的flomo笔记",
                icon = Icons.Default.Note,
                action = onNavigateToFlomoNotes
            ),
            FeatureItem(
                title = "笔记列表",
                description = "查看所有flomo笔记",
                icon = Icons.Default.Photo,
                action = onNavigateToFlomoNotesList
            ),
            FeatureItem(
                title = "照片管理",
                description = "管理和整理您的照片",
                icon = Icons.Default.PhotoLibrary,
                action = {
                    Toast.makeText(context, "功能开发中...", Toast.LENGTH_SHORT).show()
                }
            ),
            FeatureItem(
                title = "语音转文字",
                description = "说话自动转为文本",
                icon = Icons.Default.Mic,
                action = onNavigateToSpeechToText
            ),
            FeatureItem(
                title = "中文转英文",
                description = "使用AI翻译中文为英文",
                icon = Icons.Default.Translate,
                action = onNavigateToTranslation
            ),
            FeatureItem(
                title = "阿里云测试",
                description = "测试阿里云百炼API",
                icon = Icons.Default.Cloud,
                action = onNavigateToAliyunTest
            )
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 应用标题
        Text(
            text = "照片管理应用",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        
        // 功能列表 - 水平排列
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            features.forEach { feature ->
                SmallFeatureCard(feature = feature)
            }
        }
    }
}

@Composable
fun FavoritesSection(
    favoriteCount: Int,
    onNavigateToFavorites: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = onNavigateToFavorites
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图标
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "我的收藏",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            // 文本内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "我的收藏",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "已收藏 $favoriteCount 条笔记",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SmallFeatureCard(
    feature: FeatureItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(80.dp)
            .height(100.dp)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标按钮
        IconButton(
            onClick = feature.action,
            modifier = Modifier.size(60.dp)
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = feature.title,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 标题文本
        Text(
            text = feature.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun FeatureCard(
    feature: FeatureItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = feature.action
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图标
            Icon(
                imageVector = feature.icon,
                contentDescription = feature.title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            // 文本内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = feature.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = feature.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}