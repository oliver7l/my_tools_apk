package com.example.myapplication.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.Album

// 清爽风格按钮组件
@Composable
fun CleanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "",
    icon: ImageVector? = null,
    iconPosition: ButtonIconPosition = ButtonIconPosition.START
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF1F8E9),
            contentColor = Color(0xFF2E7D32)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        border = BorderStroke(1.dp, Color(0xFF81C784)),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (icon != null && iconPosition == ButtonIconPosition.START) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        if (icon != null && iconPosition == ButtonIconPosition.END) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// 清爽风格轮廓按钮组件
@Composable
fun CleanOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "",
    icon: ImageVector? = null,
    iconPosition: ButtonIconPosition = ButtonIconPosition.START
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFF2E7D32)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF81C784)),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (icon != null && iconPosition == ButtonIconPosition.START) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        if (icon != null && iconPosition == ButtonIconPosition.END) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// 清爽风格文本按钮组件
@Composable
fun CleanTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "",
    icon: ImageVector? = null,
    iconPosition: ButtonIconPosition = ButtonIconPosition.START
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            containerColor = Color(0xFFF1F8E9),
            contentColor = Color(0xFF2E7D32)
        ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (icon != null && iconPosition == ButtonIconPosition.START) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        if (icon != null && iconPosition == ButtonIconPosition.END) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// 清爽风格图标按钮组件
@Composable
fun CleanIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector,
    contentDescription: String? = null
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF1F8E9)),
        enabled = enabled
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(24.dp)
        )
    }
}

// 清爽风格按钮行组件
@Composable
fun CleanButtonRow(
    modifier: Modifier = Modifier,
    onRefreshClick: () -> Unit,
    onMoveClick: () -> Unit,
    onAlbumChange: (String) -> Unit,
    moveToAlbum: String,
    albumsList: List<Album>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 随机切换按钮
        CleanIconButton(
            onClick = onRefreshClick,
            icon = Icons.Default.Shuffle,
            contentDescription = "随机切换",
            modifier = Modifier.size(48.dp)
        )
        
        // 相册选择下拉菜单
        Box {
            CleanIconButton(
                onClick = { onExpandedChange(true) },
                icon = Icons.Default.Folder,
                contentDescription = "选择相册",
                modifier = Modifier.size(48.dp)
            )
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                albumsList.forEach { album ->
                    DropdownMenuItem(
                        text = { 
                            Column {
                                Text(album.name)
                                if (album.fullPath.isNotEmpty() && album.fullPath != album.name) {
                                    Text(
                                        text = album.fullPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = {
                            onAlbumChange(album.name)
                            onExpandedChange(false)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("新建相册") },
                    onClick = {
                        onExpandedChange(false)
                    }
                )
            }
        }
        
        // 移动按钮
        CleanIconButton(
            onClick = onMoveClick,
            enabled = moveToAlbum.isNotBlank(),
            icon = Icons.Default.Save,
            contentDescription = "移动",
            modifier = Modifier.size(48.dp)
        )
    }
}

// 按钮图标位置枚举
enum class ButtonIconPosition {
    START, END
}