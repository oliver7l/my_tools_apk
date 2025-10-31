package com.example.aliyunapp

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
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    onNavigateToSpeechToText: () -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToAliyunTest: () -> Unit,
    onNavigateToAliyunMCPTest: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToRandomNote: () -> Unit,
    onNavigateToBilibiliFavorites: () -> Unit,
    onNavigateToBilibiliLiked: () -> Unit,
    onNavigateToDatabaseStats: () -> Unit
) {
    val context = LocalContext.current
    
    // 收藏数量（由于删除了flomo功能，暂时设为0）
    val favoriteCount = 0
    
    // 功能列表 - 重新组织，将哔哩哔哩收藏放在更显眼的位置
    val features = remember {
        listOf(
            FeatureItem(
                title = "随机照片",
                description = "随机浏览您的照片集",
                icon = Icons.Default.Shuffle,
                action = onNavigateToRandomPhoto
            ),
            FeatureItem(
                title = "哔哩哔哩收藏",
                description = "浏览收藏的视频内容",
                icon = Icons.Default.PlayArrow,
                action = onNavigateToBilibiliFavorites
            ),
            FeatureItem(
                title = "哔哩哔哩喜欢",
                description = "浏览喜欢的视频内容",
                icon = Icons.Default.Favorite,
                action = onNavigateToBilibiliLiked
            ),
            FeatureItem(
                title = "随机笔记",
                description = "随机阅读您的笔记",
                icon = Icons.Default.Article,
                action = onNavigateToRandomNote
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
                title = "收藏笔记",
                description = "查看收藏的笔记列表",
                icon = Icons.Default.Favorite,
                action = onNavigateToFavorites
            ),
            FeatureItem(
                title = "数据统计",
                description = "查看数据库统计信息",
                icon = Icons.Default.DataUsage,
                action = onNavigateToDatabaseStats
            ),
            FeatureItem(
                title = "阿里云测试",
                description = "测试阿里云百炼API",
                icon = Icons.Default.Cloud,
                action = onNavigateToAliyunTest
            ),
            FeatureItem(
                title = "阿里云MCP测试",
                description = "测试阿里云MCP功能",
                icon = Icons.Default.Cloud,
                action = onNavigateToAliyunMCPTest
            ),
            FeatureItem(
                title = "照片管理",
                description = "管理和整理您的照片",
                icon = Icons.Default.PhotoLibrary,
                action = {
                    Toast.makeText(context, "功能开发中...", Toast.LENGTH_SHORT).show()
                }
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
        
        // 功能列表 - 网格布局（每行5个）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            features.chunked(5).forEach { rowFeatures ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowFeatures.forEach { feature ->
                        SmallFeatureCard(feature = feature)
                    }
                    // 如果一行不满5个，添加空位保持对齐
                    if (rowFeatures.size < 5) {
                        repeat(5 - rowFeatures.size) {
                            Spacer(modifier = Modifier.width(120.dp))
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun SmallFeatureCard(
    feature: FeatureItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(120.dp)
            .height(90.dp)
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = feature.action
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = feature.title,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = feature.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = feature.description,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
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