package random.photos

import android.Manifest
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import random.photos.ui.components.CleanButton
import random.photos.ui.components.CleanButtonRow
import random.photos.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random as KotlinRandom

data class Photo(
    val id: Long,
    val displayName: String,
    val uri: Uri,
    val album: String?
)

data class Album(
    val name: String,
    val id: Long = 0,
    val fullPath: String = ""
)

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "PhotoMoveApp"
        private const val REQUEST_MANAGE_EXTERNAL_STORAGE = 1001
    }
    
    // 简单的错误日志记录器，替代Firebase Crashlytics
    private val errorLogger = ErrorLogger()
    private var photosList = mutableListOf<Photo>()
    private var albumsList = mutableListOf<Album>()
    private var currentPhotoIndex by mutableStateOf(-1)
    private var moveToAlbum by mutableStateOf("")
    private var expanded by mutableStateOf(false)
    private var pendingDeleteUri: Uri? = null
    private var pendingNewUri: Uri? = null
    var showVerificationDialog by mutableStateOf(false)
    var verificationMessage by mutableStateOf("")
    
    // 权限请求启动器 (Android 13+)
    private val requestPhotoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadPhotosAndAlbums()
        } else {
            Toast.makeText(this, "需要照片访问权限才能访问照片", Toast.LENGTH_LONG).show()
        }
    }
    
    // 写入照片权限请求启动器 (Android 13+)
    private val requestWriteMediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限已授予，重新尝试移动照片操作
            if (currentPhotoIndex >= 0 && currentPhotoIndex < photosList.size && moveToAlbum.isNotBlank()) {
                moveCurrentPhotoToAlbum(moveToAlbum)
            }
        } else {
            Toast.makeText(this, "需要写入照片权限才能移动照片", Toast.LENGTH_LONG).show()
        }
    }
    
    // 权限请求启动器 (Android 12及以下)
    private val requestStoragePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            loadPhotosAndAlbums()
        } else {
            Toast.makeText(this, "需要存储权限才能访问照片", Toast.LENGTH_LONG).show()
        }
    }
    
    // 移动照片需要的写入权限 (Android 10及以下)
    private val requestWritePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限已授予，可以继续移动照片操作
        } else {
            Toast.makeText(this, "需要写入权限才能移动照片", Toast.LENGTH_LONG).show()
        }
    }
    
    // 用于处理安全异常的Activity结果启动器
    private lateinit var recoverableSecurityExceptionLauncher: ActivityResultLauncher<IntentSenderRequest>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 初始化恢复安全异常的启动器
        recoverableSecurityExceptionLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                // 用户已授权，可以重新尝试删除操作
                pendingDeleteUri?.let { uri ->
                    pendingNewUri?.let { newUri ->
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val deleted = contentResolver.delete(uri, null, null)
                                Log.d(TAG, "授权后删除成功: $deleted")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "照片移动完成", Toast.LENGTH_LONG).show()
                                    // 重新加载照片列表
                                    loadPhotosAndAlbums()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "授权后删除仍然失败", e)
                                // 记录错误到本地日志
                                errorLogger.recordException(this@MainActivity, e)
                                // 即使删除失败，也要删除新创建的文件，避免重复
                                contentResolver.delete(newUri, null, null)
                                val errorMsg = "授权后删除仍然失败: ${e.message}\n${Log.getStackTraceString(e)}"
                                withContext(Dispatchers.Main) {
                                    copyErrorToClipboard(errorMsg)
                                    Toast.makeText(this@MainActivity, "照片移动失败，请查看日志", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            } else {
                // 用户拒绝授权，删除新创建的文件，避免重复
                pendingNewUri?.let { newUri ->
                    CoroutineScope(Dispatchers.IO).launch {
                        contentResolver.delete(newUri, null, null)
                    }
                }
                Toast.makeText(this@MainActivity, "权限被拒绝，无法移动照片", Toast.LENGTH_LONG).show()
            }
            pendingDeleteUri = null
            pendingNewUri = null
        }
        
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PhotoViewer(
                        modifier = Modifier.padding(innerPadding),
                        onRefreshClick = { loadRandomPhoto() },
                        onMoveClick = { moveCurrentPhotoToAlbum(moveToAlbum) },
                        onAlbumChange = { moveToAlbum = it },
                        currentPhotoIndex = currentPhotoIndex,
                        photosList = photosList,
                        moveToAlbum = moveToAlbum,
                        albumsList = albumsList,
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        onCopyErrorToClipboard = { errorText ->
                            copyErrorToClipboard(errorText)
                        },
                        showVerificationDialog = showVerificationDialog,
                        verificationMessage = verificationMessage,
                        onDismissDialog = { showVerificationDialog = false }
                    )
                }
            }
        }
        
        // 检查并请求权限
        checkPermissionsAndLoadPhotos()
    }
    
    private fun checkPermissionsAndLoadPhotos() {
        when {
            // Android 13及以上版本
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val permission = Manifest.permission.READ_MEDIA_IMAGES
                if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                    loadPhotosAndAlbums()
                } else {
                    // 请求权限时添加说明
                    requestPhotoPermissionLauncher.launch(permission)
                }
            }
            // Android 6.0到Android 12
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val permissions = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
                    loadPhotosAndAlbums()
                } else {
                    requestStoragePermissionsLauncher.launch(permissions)
                }
            }
            // Android 6.0以下版本，权限在安装时授予
            else -> {
                loadPhotosAndAlbums()
            }
        }
    }
    
    private fun checkWritePermission(): Boolean {
        return when {
            // Android 13及以上版本需要READ_MEDIA_IMAGES和在运行时处理写入权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // 在Android 13+上，检查是否有管理所有文件权限
                hasManageExternalStoragePermission() ||
                // 如果没有管理权限，依赖系统在操作时抛出RecoverableSecurityException并处理
                true
            }
            // Android 10到Android 12使用MediaStore API不需要写入权限
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // 在Android 10-12上，也检查是否有管理所有文件权限
                hasManageExternalStoragePermission() || true
            }
            // Android 10以下版本需要写入权限
            else -> {
                val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                    true
                } else {
                    requestWritePermissionLauncher.launch(permission)
                    false
                }
            }
        }
    }
    
    // 检查是否有管理所有文件的权限
    private fun hasManageExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Android 10以下不需要此权限
            false
        }
    }
    
    // 请求管理所有文件的权限
    private fun requestManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE)
            } catch (e: Exception) {
                Log.e(TAG, "无法启动管理存储权限设置页面", e)
                Toast.makeText(this, "请手动前往系统设置授予应用管理所有文件的权限", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun loadPhotosAndAlbums() {
        CoroutineScope(Dispatchers.IO).launch {
            photosList.clear()
            albumsList.clear()
            
            // 加载照片
            val photoProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            
            val selection = MediaStore.Images.Media.SIZE + " > 0"
            
            try {
                val photoCursor = contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    photoProjection,
                    selection,
                    null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
                )
                
                photoCursor?.use { c ->
                    val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val displayNameColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val bucketColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    
                    while (c.moveToNext()) {
                        val id = c.getLong(idColumn)
                        val displayName = c.getString(displayNameColumn)
                        val bucketName = c.getString(bucketColumn)
                        
                        val uri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        
                        photosList.add(Photo(id, displayName, uri, bucketName))
                    }
                }
                
                // 加载相册
                val albumProjection = arrayOf(
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.RELATIVE_PATH
                )
                
                val albumCursor = contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    albumProjection,
                    selection,
                    null,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " ASC"
                )
                
                albumCursor?.use { c ->
                    val bucketNameColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val bucketIdColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                    val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    } else {
                        null
                    }
                    
                    val uniqueAlbums = mutableMapOf<String, Album>()
                    
                    while (c.moveToNext()) {
                        val bucketName = c.getString(bucketNameColumn)
                        val bucketId = c.getLong(bucketIdColumn)
                        
                        if (bucketName != null) {
                            // 获取相册的完整路径
                            val fullPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && relativePathColumn != null && relativePathColumn >= 0) {
                                // Android Q及以上版本，使用RELATIVE_PATH
                                val relativePath = c.getString(relativePathColumn)
                                if (relativePath != null && relativePath.isNotEmpty()) {
                                    // 确保路径以相册名结尾
                                    if (relativePath.endsWith("/$bucketName") || relativePath.endsWith("/$bucketName/")) {
                                        relativePath.trimEnd('/')
                                    } else {
                                        "$relativePath$bucketName"
                                    }
                                } else {
                                    // 如果没有RELATIVE_PATH，构造默认路径
                                    "${Environment.DIRECTORY_PICTURES}/$bucketName"
                                }
                            } else {
                                // Android Q以下版本，构造默认路径
                                "${Environment.DIRECTORY_PICTURES}/$bucketName"
                            }
                            
                            // 使用相册名作为键，确保每个相册只添加一次
                            if (!uniqueAlbums.containsKey(bucketName)) {
                                uniqueAlbums[bucketName] = Album(bucketName, bucketId, fullPath)
                            }
                        }
                    }
                    
                    // 将所有相册添加到列表
                    albumsList.addAll(uniqueAlbums.values)
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载照片和相册失败", e)
                e.printStackTrace()
            }
            
            withContext(Dispatchers.Main) {
                if (photosList.isNotEmpty()) {
                    loadRandomPhoto()
                } else {
                    Toast.makeText(this@MainActivity, "未找到照片", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // 查找匹配的相册，支持完整路径和相册名匹配
    private fun findMatchingAlbum(albumName: String): Album? {
        Log.d(TAG, "查找匹配的相册: $albumName")
        
        // 标准化输入的相册名
        val normalizedInputName = try {
            java.text.Normalizer.normalize(albumName.trim(), java.text.Normalizer.Form.NFC)
        } catch (e: Exception) {
            Log.w(TAG, "标准化输入相册名时出错，使用原始值", e)
            albumName.trim()
        }
        
        // 构造可能的完整路径
        val possiblePaths = listOf(
            // 如果已经是完整路径，直接使用
            if (normalizedInputName.contains("/")) normalizedInputName else null,
            // 构造Pictures下的路径
            "${Environment.DIRECTORY_PICTURES}/$normalizedInputName",
            // 构造DCIM下的路径
            "DCIM/$normalizedInputName"
        ).filterNotNull()
        
        // 首先尝试精确匹配完整路径
        albumsList.forEach { album ->
            val albumPath = album.fullPath.trim()
            if (possiblePaths.any { path -> path.equals(albumPath, ignoreCase = true) }) {
                Log.d(TAG, "找到精确匹配的相册: ${album.name}, 路径: ${album.fullPath}")
                return album
            }
        }
        
        // 如果没有找到精确匹配，尝试相册名匹配
        albumsList.forEach { album ->
            val normalizedAlbumName = try {
                java.text.Normalizer.normalize(album.name.trim(), java.text.Normalizer.Form.NFC)
            } catch (e: Exception) {
                Log.w(TAG, "标准化相册名时出错，使用原始值", e)
                album.name.trim()
            }
            
            if (normalizedAlbumName.equals(normalizedInputName, ignoreCase = true)) {
                Log.d(TAG, "找到名称匹配的相册: ${album.name}, 路径: ${album.fullPath}")
                return album
            }
        }
        
        Log.d(TAG, "未找到匹配的相册: $albumName")
        return null
    }

    private fun loadRandomPhoto() {
        if (photosList.isNotEmpty()) {
            currentPhotoIndex = KotlinRandom.nextInt(0, photosList.size)
        }
    }
    
    private fun moveCurrentPhotoToAlbum(albumName: String) {
        if (currentPhotoIndex < 0 || currentPhotoIndex >= photosList.size) {
            Toast.makeText(this, "没有选中的照片", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (albumName.isBlank()) {
            Toast.makeText(this, "请输入目标相册名称", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 检查写入权限
        if (!checkWritePermission()) {
            Toast.makeText(this, "请先授予写入权限", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 对于Android 13+，确保我们有读取照片的权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val readPermission = Manifest.permission.READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, readPermission) != PackageManager.PERMISSION_GRANTED) {
                requestPhotoPermissionLauncher.launch(readPermission)
                return
            }
        }
        
        // 尝试查找匹配的相册
        val matchingAlbum = findMatchingAlbum(albumName)
        val targetAlbumName = if (matchingAlbum != null) {
            // 如果找到匹配的相册，使用其完整路径
            Log.d(TAG, "使用现有相册: ${matchingAlbum.name}, 完整路径: ${matchingAlbum.fullPath}")
            matchingAlbum.fullPath
        } else {
            // 如果没有找到匹配的相册，使用输入的名称（将构造为新相册）
            Log.d(TAG, "未找到匹配的相册，将创建新相册: $albumName")
            albumName
        }
        
        val photo = photosList[currentPhotoIndex]
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "开始移动照片: ${photo.displayName} 到相册: $targetAlbumName")
                val moved = movePhotoToAlbum(photo, targetAlbumName)
                withContext(Dispatchers.Main) {
                    if (moved) {
                        val displayAlbumName = if (matchingAlbum != null) {
                            matchingAlbum.name
                        } else {
                            getAlbumDisplayName(targetAlbumName)
                        }
                        Toast.makeText(this@MainActivity, "照片已移至 $displayAlbumName 相册", Toast.LENGTH_LONG).show()
                        Log.d(TAG, "照片移动成功")
                        // 重新加载照片列表
                        loadPhotosAndAlbums()
                    } else {
                        val errorMsg = "移动照片失败"
                        Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                        Log.e(TAG, errorMsg)
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "移动照片时出错: ${e.message}\n${Log.getStackTraceString(e)}"
                Log.e(TAG, "移动照片时出错", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "移动照片时出错，请查看日志", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private suspend fun movePhotoToAlbum(photo: Photo, albumName: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            // 使用MediaStore API移动照片，适配所有Android版本
            movePhotoWithMediaStore(photo, albumName)
        } catch (e: Exception) {
            Log.e(TAG, "移动照片异常", e)
            val errorMsg = "移动照片异常: ${e.message}\n${Log.getStackTraceString(e)}"
            withContext(Dispatchers.Main) {
                copyErrorToClipboard(errorMsg)
            }
            e.printStackTrace()
            false
        }
    }
    
    private suspend fun movePhotoWithMediaStore(photo: Photo, albumName: String): Boolean {
        // 先在主线程检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasManageExternalStoragePermission()) {
            Log.w(TAG, "需要管理所有文件权限才能移动照片")
            // 切换到主线程显示Toast提示用户需要权限
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "需要额外权限才能移动照片，请在系统设置中授予应用管理所有文件的权限", Toast.LENGTH_LONG).show()
            }
            
            // 请求权限
            requestManageExternalStoragePermission()
            
            // 返回false表示操作未完成
            return false
        }
        
        // 切换到IO线程继续操作
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始使用MediaStore移动照片，目标相册: $albumName")
                
                // 获取原始照片的详细信息
                val originalProjection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATE_MODIFIED
                )
                
                contentResolver.query(photo.uri, originalProjection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE))
                        val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                        val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                        val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED))
                        
                        // 构造目标相册路径
                        val relativePath = constructRelativePath(albumName)
                        Log.d(TAG, "目标相对路径: $relativePath")
                        
                        // 读取原始文件内容
                        val inputStream = contentResolver.openInputStream(photo.uri)
                        if (inputStream == null) {
                            Log.e(TAG, "无法打开原始文件输入流")
                            withContext(Dispatchers.Main) {
                                copyErrorToClipboard("无法打开原始文件输入流")
                            }
                            return@withContext false
                        }
                        
                        // 准备新文件的ContentValues
                        // 确保相册名经过Unicode标准化处理
                        val normalizedAlbumName = try {
                            java.text.Normalizer.normalize(albumName, java.text.Normalizer.Form.NFC)
                        } catch (e: Exception) {
                            Log.w(TAG, "相册名Unicode标准化失败: ${e.message}")
                            albumName
                        }
                        
                        val newValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                            put(MediaStore.Images.Media.SIZE, size)
                            put(MediaStore.Images.Media.DATE_ADDED, dateAdded)
                            put(MediaStore.Images.Media.DATE_MODIFIED, dateModified)
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                // 确保使用正确的相对路径
                                val relativePath = constructRelativePath(normalizedAlbumName)
                                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                                put(MediaStore.Images.Media.IS_PENDING, 1)
                                
                                // 设置BUCKET_DISPLAY_NAME为相册的显示名称（不包含路径）
                                val bucketDisplayName = getAlbumDisplayName(normalizedAlbumName)
                                put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, bucketDisplayName)
                                
                                Log.d(TAG, "设置相对路径: $relativePath, 相册显示名: $bucketDisplayName")
                            } else {
                                // Android Q以下版本使用DATA字段
                                val externalStorage = Environment.getExternalStorageDirectory()
                                val targetDir = File(externalStorage, constructRelativePath(normalizedAlbumName))
                                if (!targetDir.exists()) {
                                    targetDir.mkdirs()
                                }
                                val targetFile = File(targetDir, displayName)
                                put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
                                
                                // 也设置BUCKET_DISPLAY_NAME以确保兼容性
                                val bucketDisplayName = getAlbumDisplayName(normalizedAlbumName)
                                put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, bucketDisplayName)
                                
                                Log.d(TAG, "设置数据路径: ${targetFile.absolutePath}, 相册显示名: $bucketDisplayName")
                            }
                        }
                        
                        // 插入新文件到MediaStore
                        val newUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newValues)
                        if (newUri == null) {
                            Log.e(TAG, "无法创建新文件在MediaStore中")
                            inputStream.close()
                            withContext(Dispatchers.Main) {
                                copyErrorToClipboard("无法创建新文件在MediaStore中")
                            }
                            return@withContext false
                        }
                        
                        // 写入文件内容
                        var success = false
                        try {
                            contentResolver.openOutputStream(newUri)?.use { outputStream ->
                                inputStream.copyTo(outputStream)
                                success = true
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "写入新文件失败", e)
                            errorLogger.recordException(this@MainActivity, e)
                            errorLogger.log(this@MainActivity, "写入新文件失败: ${e.message}")
                        } finally {
                            inputStream.close()
                        }
                        
                        if (!success) {
                            // 删除已创建的空文件
                            contentResolver.delete(newUri, null, null)
                            withContext(Dispatchers.Main) {
                                copyErrorToClipboard("写入新文件失败")
                            }
                            return@withContext false
                        }
                        
                        // 对于Android Q及以上版本，完成文件写入
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // 确保相册名经过Unicode标准化处理
                            val normalizedAlbumName = try {
                                java.text.Normalizer.normalize(albumName, java.text.Normalizer.Form.NFC)
                            } catch (e: Exception) {
                                Log.w(TAG, "完成文件写入时相册名Unicode标准化失败: ${e.message}")
                                albumName
                            }
                            
                            newValues.clear()
                            newValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            val updatedRows = contentResolver.update(newUri, newValues, null, null)
                            Log.d(TAG, "完成文件写入，更新行数: $updatedRows")
                            
                            // 增强媒体扫描，确保系统识别新文件
                            try {
                                // 方法1: 使用MediaScannerConnection扫描文件
                                MediaScannerConnection.scanFile(
                                    this@MainActivity,
                                    arrayOf(getRealPathFromURI(newUri)),
                                    arrayOf("image/jpeg", "image/png")
                                ) { path, uri ->
                                    Log.d(TAG, "媒体扫描完成: path=$path, uri=$uri")
                                }
                                
                                // 方法2: 发送广播通知系统扫描文件（适用于旧版Android）
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                    scanIntent.data = newUri
                                    this@MainActivity.sendBroadcast(scanIntent)
                                    Log.d(TAG, "已发送媒体扫描广播")
                                }
                                
                                // 等待更长时间，让系统有时间处理扫描
                                Thread.sleep(1000)
                                
                                // 方法3: 更新相册信息，确保BUCKET_DISPLAY_NAME正确设置
                                newValues.clear()
                                val bucketDisplayName = getAlbumDisplayName(normalizedAlbumName)
                                newValues.put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, bucketDisplayName)
                                val updateResult = contentResolver.update(newUri, newValues, null, null)
                                Log.d(TAG, "更新相册显示名结果: $updateResult, 名称: $bucketDisplayName")
                                
                                // 方法4: 再次更新RELATIVE_PATH确保路径正确
                                newValues.clear()
                                val relativePath = constructRelativePath(normalizedAlbumName)
                                newValues.put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                                val pathUpdateResult = contentResolver.update(newUri, newValues, null, null)
                                Log.d(TAG, "更新相对路径结果: $pathUpdateResult, 路径: $relativePath")
                            } catch (e: Exception) {
                                Log.e(TAG, "媒体扫描过程中出错", e)
                                errorLogger.recordException(this@MainActivity, e)
                            }
                        }
                        
                        // 验证新文件是否存在
                        contentResolver.query(newUri, arrayOf(MediaStore.Images.Media._ID), null, null, null)?.use { newCursor ->
                            if (!newCursor.moveToFirst()) {
                                Log.e(TAG, "新文件创建后无法查询到")
                                contentResolver.delete(newUri, null, null)
                                withContext(Dispatchers.Main) {
                                    copyErrorToClipboard("新文件创建后无法查询到")
                                }
                                return@withContext false
                            }
                        }
                        
                        // 额外验证：检查文件是否在目标相册中可见
                        Log.d(TAG, "验证照片是否在目标相册中可见...")
                        var isVisibleInAlbum = false
                        var retryCount = 0
                        val maxRetries = 8  // 增加重试次数
                        
                        // 详细记录照片信息
                        logPhotoDetails(newUri, "新创建的照片")
                        
                        // 多次尝试验证，因为MediaStore更新可能需要时间
                        while (!isVisibleInAlbum && retryCount < maxRetries) {
                            isVisibleInAlbum = strictVerifyPhotoInAlbum(newUri, albumName)
                            Log.d(TAG, "严格验证尝试 ${retryCount + 1}/$maxRetries: 照片是否在目标相册中: $isVisibleInAlbum")
                            
                            if (!isVisibleInAlbum) {
                                // 等待更长时间后重试，给系统更多时间处理
                                val waitTime = 1500L * (retryCount + 1)  // 递增等待时间，从1.5秒开始
                                Log.d(TAG, "等待 ${waitTime}ms 后重试验证")
                                Thread.sleep(waitTime)
                                retryCount++
                                
                                // 在重试过程中，再次尝试更新BUCKET_DISPLAY_NAME和RELATIVE_PATH
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    try {
                                        // 确保相册名经过Unicode标准化处理
                                        val normalizedAlbumName = try {
                                            java.text.Normalizer.normalize(albumName, java.text.Normalizer.Form.NFC)
                                        } catch (e: Exception) {
                                            Log.w(TAG, "重试中相册名Unicode标准化失败: ${e.message}")
                                            albumName
                                        }
                                        
                                        // 更新BUCKET_DISPLAY_NAME
                                        newValues.clear()
                                        val bucketDisplayName = getAlbumDisplayName(normalizedAlbumName)
                                        newValues.put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, bucketDisplayName)
                                        val updateResult = contentResolver.update(newUri, newValues, null, null)
                                        Log.d(TAG, "重试中更新相册显示名结果: $updateResult, 名称: $bucketDisplayName")
                                        
                                        // 更新RELATIVE_PATH
                                        newValues.clear()
                                        val relativePath = constructRelativePath(normalizedAlbumName)
                                        newValues.put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                                        val pathUpdateResult = contentResolver.update(newUri, newValues, null, null)
                                        Log.d(TAG, "重试中更新相对路径结果: $pathUpdateResult, 路径: $relativePath")
                                        
                                        // 再次触发媒体扫描
                                        MediaScannerConnection.scanFile(
                                            this@MainActivity,
                                            arrayOf(getRealPathFromURI(newUri)),
                                            arrayOf("image/jpeg", "image/png")
                                        ) { path, uri ->
                                            Log.d(TAG, "重试中媒体扫描完成: path=$path, uri=$uri")
                                        }
                                        
                                        // 记录更新后的照片信息
                                        logPhotoDetails(newUri, "更新后的照片")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "重试中更新相册信息出错", e)
                                    }
                                }
                            }
                        }
                        
                        if (!isVisibleInAlbum) {
                            Log.e(TAG, "照片未在目标相册中可见，经过多次尝试后仍失败")
                            contentResolver.delete(newUri, null, null)
                            withContext(Dispatchers.Main) {
                                copyErrorToClipboard("照片未在目标相册中可见，移动失败")
                            }
                            return@withContext false
                        }
                        
                        // 删除原始文件
                        try {
                            val deletedRows = contentResolver.delete(photo.uri, null, null)
                            Log.d(TAG, "删除原始文件结果: $deletedRows")
                            
                            if (deletedRows > 0) {
                                Log.d(TAG, "照片移动成功")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "照片已成功移至 $albumName 相册", Toast.LENGTH_SHORT).show()
                                }
                                return@withContext true
                            } else {
                                Log.e(TAG, "删除原始文件失败")
                                // 删除新创建的文件，避免重复
                                contentResolver.delete(newUri, null, null)
                                withContext(Dispatchers.Main) {
                                    copyErrorToClipboard("删除原始文件失败")
                                }
                                return@withContext false
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "删除原始文件时发生安全异常", e)
                            errorLogger.recordException(this@MainActivity, e)
                            errorLogger.log(this@MainActivity, "删除原始文件时发生安全异常: ${e.message}")
                            
                            // 保存URI以便后续处理
                            pendingDeleteUri = photo.uri
                            pendingNewUri = newUri
                            
                            // 尝试处理安全异常
                            handleSecurityException(e, photo.uri, newUri)
                            return@withContext false
                        }
                    } else {
                        Log.e(TAG, "无法获取原始照片信息")
                        withContext(Dispatchers.Main) {
                            copyErrorToClipboard("无法获取原始照片信息")
                        }
                        return@withContext false
                    }
                } ?: run {
                    Log.e(TAG, "无法查询原始照片")
                    withContext(Dispatchers.Main) {
                        copyErrorToClipboard("无法查询原始照片")
                    }
                    return@withContext false
                }
            } catch (e: Exception) {
                val errorMessage = "移动照片过程中出现异常: ${e.message}\n${Log.getStackTraceString(e)}"
                Log.e(TAG, "移动照片过程中出现异常", e)
                errorLogger.recordException(this@MainActivity, e)
                errorLogger.log(this@MainActivity, "移动照片过程中出现异常: ${e.message}")
                withContext(Dispatchers.Main) {
                    copyErrorToClipboard(errorMessage)
                }
                false
            }
        }
    }
    
    private fun getRealPathFromURI(uri: Uri): String? {
        return try {
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                if (cursor.moveToFirst()) {
                    cursor.getString(dataIndex)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取URI实际路径失败", e)
            null
        }
    }
    
    // 详细记录照片信息
    private fun logPhotoDetails(uri: Uri, description: String) {
        try {
            Log.d(TAG, "=== $description 详细信息 ===")
            
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED
            )
            
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val bucketDisplayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                    val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
                    val dataPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                    val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                    val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED))
                    
                    Log.d(TAG, "ID: $id")
                    Log.d(TAG, "文件名: $displayName")
                    Log.d(TAG, "相册显示名: $bucketDisplayName")
                    Log.d(TAG, "相对路径: $relativePath")
                    Log.d(TAG, "完整路径: $dataPath")
                    Log.d(TAG, "大小: ${size / 1024} KB")
                    Log.d(TAG, "添加时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(dateAdded * 1000))}")
                    Log.d(TAG, "修改时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(dateModified * 1000))}")
                } else {
                    Log.d(TAG, "无法查询照片信息")
                }
            }
            
            // 检查文件系统中的实际路径
            val realPath = getRealPathFromURI(uri)
            Log.d(TAG, "文件系统路径: $realPath")
            
            // 检查文件是否存在
            if (realPath != null) {
                val file = java.io.File(realPath)
                Log.d(TAG, "文件系统检查: 文件${if (file.exists()) "存在" else "不存在"}")
                if (file.exists()) {
                    Log.d(TAG, "文件大小: ${file.length() / 1024} KB")
                }
            }
            
            Log.d(TAG, "=== $description 详细信息结束 ===")
        } catch (e: Exception) {
            Log.e(TAG, "记录照片详细信息时出错", e)
        }
    }
    
    private fun constructRelativePath(albumName: String): String {
        // 确保相册路径正确
        Log.d(TAG, "构造相对路径，原始相册名: $albumName")
        
        val cleanAlbumName = albumName.trim()
        
        // 标准化相册名，确保中文和特殊符号的一致性
        val normalizedAlbumName = try {
            java.text.Normalizer.normalize(cleanAlbumName, java.text.Normalizer.Form.NFC)
        } catch (e: Exception) {
            Log.w(TAG, "标准化相册名时出错，使用原始值", e)
            cleanAlbumName
        }
        
        // 处理路径中的特殊字符，确保文件系统兼容性
        val sanitizedAlbumName = try {
            // 替换可能导致文件系统问题的字符
            var path = normalizedAlbumName
                .replace(Regex("""[<>:"|?*]"""), "_") // Windows不支持的字符
                .replace(Regex("""\.\."""), ".") // 避免连续的点
                .replace(Regex("""^\.+"""), "") // 避免以点开头
                .replace(Regex("""\s+$"""), "") // 去除末尾空格
            
            // 确保路径不以斜杠开头（除非是绝对路径）
            if (path.startsWith("/") && !path.startsWith("/storage/")) {
                path = path.substring(1)
            }
            
            path
        } catch (e: Exception) {
            Log.w(TAG, "清理路径字符时出错，使用标准化值", e)
            normalizedAlbumName
        }
        
        val result = when {
            // 如果已经以Pictures开头，直接使用
            sanitizedAlbumName.startsWith(Environment.DIRECTORY_PICTURES) -> {
                sanitizedAlbumName
            }
            // 如果以DCIM开头，保持不变
            sanitizedAlbumName.startsWith("DCIM/") -> {
                sanitizedAlbumName
            }
            // 如果包含路径分隔符但不以Pictures开头，添加前缀
            sanitizedAlbumName.contains("/") -> {
                "${Environment.DIRECTORY_PICTURES}/$sanitizedAlbumName"
            }
            // 否则构造完整路径
            else -> {
                "${Environment.DIRECTORY_PICTURES}/$sanitizedAlbumName"
            }
        }
        
        Log.d(TAG, "构造后的相对路径: $result")
        return result
    }
    
    // 获取相册的显示名称（不包含路径）
    private fun getAlbumDisplayName(albumName: String): String {
        Log.d(TAG, "获取相册显示名称，原始相册名: $albumName")
        
        val cleanAlbumName = albumName.trim()
        
        val result = when {
            // 如果包含路径分隔符，取最后一部分作为显示名称
            cleanAlbumName.contains("/") -> {
                cleanAlbumName.substringAfterLast("/")
            }
            // 否则直接使用相册名
            else -> {
                cleanAlbumName
            }
        }
        
        // 确保相册名使用UTF-8编码，并处理特殊字符
        val normalizedResult = try {
            // 标准化Unicode字符，确保中文和特殊符号的一致性
            java.text.Normalizer.normalize(result, java.text.Normalizer.Form.NFC)
        } catch (e: Exception) {
            Log.w(TAG, "标准化相册名时出错，使用原始值", e)
            result
        }
        
        Log.d(TAG, "提取的相册显示名称: $normalizedResult")
        return normalizedResult
    }
    
    private fun copyErrorToClipboard(errorText: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("错误信息", errorText)
        clipboard.setPrimaryClip(clip)
        
        // 记录错误到本地日志
        errorLogger.log(this, "错误信息: $errorText")
        
        Toast.makeText(this, "错误信息已复制到剪贴板", Toast.LENGTH_LONG).show()
        Log.d(TAG, "错误信息已复制到剪贴板: $errorText")
    }
    
    // 用户自定义错误日志功能
    fun logCustomError(message: String, throwable: Throwable? = null) {
        // 记录自定义错误到本地日志
        Log.e(TAG, "用户自定义错误: $message", throwable)
        
        // 记录到本地日志
        errorLogger.log(this, "用户自定义错误: $message")
        
        if (throwable != null) {
            errorLogger.recordException(this, throwable)
        } else {
            // 如果没有异常对象，记录为普通消息
            errorLogger.log(this, "用户自定义错误详情: $message")
        }
        
        // 复制到剪贴板
        val fullMessage = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        copyErrorToClipboard(fullMessage)
        
        Toast.makeText(this, "自定义错误已记录并复制到剪贴板", Toast.LENGTH_LONG).show()
    }
    
    private fun handleSecurityException(e: SecurityException, originalUri: Uri? = null, newUri: Uri? = null) {
        Log.e(TAG, "处理安全异常", e)
        errorLogger.recordException(this, e)
        
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && e.message?.contains("MANAGE_EXTERNAL_STORAGE") == true -> {
                // Android 11+ 需要管理所有文件权限
                Log.w(TAG, "需要管理所有文件权限")
                errorLogger.log(this, "需要管理所有文件权限")
                Toast.makeText(this, "需要管理所有文件权限，请在设置中授予", Toast.LENGTH_LONG).show()
                requestManageExternalStoragePermission()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e.message?.contains("RecoverableSecurityException") == true -> {
                // Android 10+ 可恢复的安全异常
                Log.w(TAG, "处理可恢复的安全异常")
                errorLogger.log(this, "处理可恢复的安全异常")
                handleRecoverableSecurityException(e, originalUri, newUri)
            }
            else -> {
                // 其他安全异常
                Log.w(TAG, "普通安全异常: ${e.message}")
                errorLogger.log(this, "普通安全异常: ${e.message}")
                Toast.makeText(this, "权限不足: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun handleRecoverableSecurityException(e: SecurityException, originalUri: Uri? = null, newUri: Uri? = null) {
        Log.w(TAG, "处理可恢复的安全异常", e)
        errorLogger.recordException(this, e)
        
        try {
            // 使用反射获取RecoverableSecurityException
            val recoverableExceptionClass = Class.forName("android.app.RecoverableSecurityException")
            if (recoverableExceptionClass.isInstance(e)) {
                // 获取userAction方法
                val userActionMethod = recoverableExceptionClass.getMethod("getUserAction")
                val userAction = userActionMethod.invoke(e)
                
                // 获取actionIntent PendingIntent
                val actionIntentMethod = userAction.javaClass.getMethod("getActionIntent")
                val actionIntent = actionIntentMethod.invoke(userAction) as PendingIntent
                
                // 保存URI，以便在权限授予后继续处理
                if (originalUri != null && newUri != null) {
                    pendingDeleteUri = originalUri
                    pendingNewUri = newUri
                }
                
                // 发送权限请求
                try {
                    actionIntent.send(this, 0, null)
                    Toast.makeText(this, "请授予照片删除权限", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "发送权限请求失败", e)
                    errorLogger.recordException(this, e)
                    Toast.makeText(this, "权限请求失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "不是RecoverableSecurityException类型")
                errorLogger.log(this, "不是RecoverableSecurityException类型")
                Toast.makeText(this, "权限异常类型不匹配", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理可恢复安全异常时出错", e)
            errorLogger.recordException(this, e)
            Toast.makeText(this, "处理权限异常失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 严格验证照片是否在目标相册中可见
    private fun strictVerifyPhotoInAlbum(photoUri: Uri, albumName: String): Boolean {
        return try {
            Log.d(TAG, "=== 开始严格验证照片是否在目标相册中可见 ===")
            Log.d(TAG, "目标相册: $albumName")
            
            // 获取相册的显示名称
            val albumDisplayName = getAlbumDisplayName(albumName)
            val expectedPath = constructRelativePath(albumName)
            
            Log.d(TAG, "相册显示名: $albumDisplayName")
            Log.d(TAG, "期望路径: $expectedPath")
            
            // 标准化相册名，确保中文和特殊符号的一致性
            val normalizedAlbumName = try {
                java.text.Normalizer.normalize(albumDisplayName, java.text.Normalizer.Form.NFC)
            } catch (e: Exception) {
                Log.w(TAG, "标准化相册名时出错，使用原始值", e)
                albumDisplayName
            }
            
            // 方法1: 检查照片的详细信息
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            
            var photoInfoMatch = false
            contentResolver.query(photoUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val bucketDisplayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                    val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
                    val dataPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    
                    Log.d(TAG, "照片详细信息: BUCKET_DISPLAY_NAME=$bucketDisplayName, RELATIVE_PATH=$relativePath, DATA=$dataPath, DISPLAY_NAME=$displayName")
                    
                    // 标准化从数据库获取的相册名，确保比较的一致性
                    val normalizedBucketName = try {
                        bucketDisplayName?.let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFC) }
                    } catch (e: Exception) {
                        Log.w(TAG, "标准化数据库相册名时出错，使用原始值", e)
                        bucketDisplayName
                    }
                    
                    // 检查照片信息是否匹配
                    photoInfoMatch = when {
                        // Android Q及以上版本
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                            val pathMatch = relativePath != null && (
                                relativePath.equals(expectedPath, ignoreCase = true) || 
                                relativePath.contains("/$normalizedAlbumName/", ignoreCase = true) ||
                                relativePath.endsWith("/$normalizedAlbumName", ignoreCase = true)
                            )
                            
                            val bucketMatch = normalizedBucketName != null && normalizedBucketName.equals(normalizedAlbumName, ignoreCase = true)
                            
                            Log.d(TAG, "路径匹配: $pathMatch, 相册名匹配: $bucketMatch")
                            pathMatch && bucketMatch
                        }
                        // Android Q以下版本
                        else -> {
                            val bucketMatch = normalizedBucketName != null && normalizedBucketName.equals(normalizedAlbumName, ignoreCase = true)
                            val pathMatch = dataPath != null && (
                                dataPath.contains("/$normalizedAlbumName/", ignoreCase = true) ||
                                dataPath.endsWith("/$normalizedAlbumName", ignoreCase = true)
                            )
                            
                            Log.d(TAG, "路径匹配: $pathMatch, 相册名匹配: $bucketMatch")
                            bucketMatch || pathMatch
                        }
                    }
                }
            }
            
            // 方法2: 查询目标相册中是否包含该照片
            var photoInAlbumQuery = false
            try {
                // 先尝试精确匹配
                val selection = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                    }
                    else -> {
                        "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                    }
                }
                
                val selectionArgs = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        arrayOf("%$normalizedAlbumName%", getPhotoDisplayName(photoUri))
                    }
                    else -> {
                        arrayOf(normalizedAlbumName, getPhotoDisplayName(photoUri))
                    }
                }
                
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    photoInAlbumQuery = cursor.count > 0
                    Log.d(TAG, "目标相册查询结果: 找到 ${cursor.count} 张匹配照片")
                }
                
                // 如果精确匹配失败，尝试模糊匹配
                if (!photoInAlbumQuery) {
                    val fuzzySelection = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                        }
                        else -> {
                            "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?"
                        }
                    }
                    
                    val fuzzySelectionArgs = arrayOf("%$normalizedAlbumName%")
                    
                    contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                        fuzzySelection,
                        fuzzySelectionArgs,
                        null
                    )?.use { cursor ->
                        if (cursor.count > 0) {
                            Log.d(TAG, "模糊匹配找到 ${cursor.count} 张照片，检查是否包含目标照片")
                            val displayName = getPhotoDisplayName(photoUri)
                            while (cursor.moveToNext()) {
                                val cursorDisplayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                                
                                // 对于模糊匹配，还需要检查相册名是否真正匹配
                                if (cursorDisplayName.equals(displayName, ignoreCase = true)) {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                        val cursorBucketName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                                        val normalizedCursorBucketName = try {
                                            cursorBucketName?.let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFC) }
                                        } catch (e: Exception) {
                                            Log.w(TAG, "标准化查询结果相册名时出错，使用原始值", e)
                                            cursorBucketName
                                        }
                                        
                                        // 确保相册名真正匹配，而不仅仅是包含
                                        if (normalizedCursorBucketName != null && normalizedCursorBucketName.equals(normalizedAlbumName, ignoreCase = true)) {
                                            photoInAlbumQuery = true
                                            Log.d(TAG, "通过模糊匹配找到目标照片，相册名匹配: $normalizedCursorBucketName")
                                            break
                                        }
                                    } else {
                                        photoInAlbumQuery = true
                                        Log.d(TAG, "通过模糊匹配找到目标照片")
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "查询目标相册时出错", e)
            }
            
            // 方法3: 检查文件系统中的路径（如果可用）
            var fileSystemMatch = false
            try {
                val dataPath = getRealPathFromURI(photoUri)
                if (dataPath != null) {
                    fileSystemMatch = dataPath.contains("/$normalizedAlbumName/", ignoreCase = true) ||
                            dataPath.endsWith("/$normalizedAlbumName", ignoreCase = true)
                    Log.d(TAG, "文件系统路径匹配: $fileSystemMatch, 路径: $dataPath")
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查文件系统路径时出错", e)
            }
            
            // 综合判断
            val finalResult = photoInfoMatch && (photoInAlbumQuery || fileSystemMatch)
            
            Log.d(TAG, "=== 验证结果汇总 ===")
            Log.d(TAG, "照片信息匹配: ${if (photoInfoMatch) "✓" else "✗"}")
            Log.d(TAG, "相册查询结果: ${if (photoInAlbumQuery) "✓ 找到匹配照片" else "✗ 照片不在目标相册中"}")
            Log.d(TAG, "文件系统检查: ${if (fileSystemMatch) "✓ 路径匹配" else "✗ 路径不匹配"}")
            Log.d(TAG, "总体结果: ${if (finalResult) "✓ 成功" else "✗ 失败"}")
            Log.d(TAG, "=== 严格验证结束 ===")
            
            finalResult
        } catch (e: Exception) {
            Log.e(TAG, "严格验证照片位置时出错", e)
            errorLogger.recordException(this@MainActivity, e)
            false
        }
    }
    
    // 获取照片的显示名称
    private fun getPhotoDisplayName(photoUri: Uri): String {
        return try {
            val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
            contentResolver.query(photoUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                } else ""
            } ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "获取照片显示名称失败", e)
            ""
        }
    }
    
    // 验证照片是否在目标相册中可见
    private fun isPhotoVisibleInAlbum(photoUri: Uri, albumName: String): Boolean {
        return try {
            Log.d(TAG, "验证照片是否在目标相册中可见: $albumName")
            
            // 获取照片的详细信息
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATA
            )
            
            contentResolver.query(photoUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val bucketDisplayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                    val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
                    val dataPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    
                    Log.d(TAG, "照片相册信息: BUCKET_DISPLAY_NAME=$bucketDisplayName, RELATIVE_PATH=$relativePath, DATA=$dataPath")
                    
                    // 获取相册的显示名称（不包含路径）
                    val albumDisplayName = getAlbumDisplayName(albumName)
                    val expectedPath = constructRelativePath(albumName)
                    
                    // 检查照片是否在目标相册中
                    val isInCorrectAlbum = when {
                        // Android Q及以上版本使用RELATIVE_PATH和BUCKET_DISPLAY_NAME
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                            // 检查相对路径是否匹配
                            val pathMatch = relativePath != null && (
                                relativePath.equals(expectedPath, ignoreCase = true) || 
                                relativePath.contains("/$albumDisplayName/", ignoreCase = true) ||
                                relativePath.endsWith("/$albumDisplayName", ignoreCase = true)
                            )
                            
                            // 检查相册显示名称是否匹配
                            val bucketMatch = bucketDisplayName != null && bucketDisplayName.equals(albumDisplayName, ignoreCase = true)
                            
                            Log.d(TAG, "路径匹配: $pathMatch, 相册名匹配: $bucketMatch")
                            
                            // 两者都匹配才认为成功
                            pathMatch && bucketMatch
                        }
                        // Android Q以下版本使用BUCKET_DISPLAY_NAME或DATA路径
                        else -> {
                            val bucketMatch = bucketDisplayName != null && bucketDisplayName.equals(albumDisplayName, ignoreCase = true)
                            val pathMatch = dataPath != null && (
                                dataPath.contains("/$albumDisplayName/", ignoreCase = true) ||
                                dataPath.endsWith("/$albumDisplayName", ignoreCase = true)
                            )
                            
                            Log.d(TAG, "路径匹配: $pathMatch, 相册名匹配: $bucketMatch")
                            
                            bucketMatch || pathMatch
                        }
                    }
                    
                    Log.d(TAG, "照片是否在目标相册中: $isInCorrectAlbum")
                    isInCorrectAlbum
                } else {
                    Log.e(TAG, "无法查询照片信息")
                    false
                }
            } ?: run {
                Log.e(TAG, "无法查询照片")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "验证照片位置时出错", e)
            errorLogger.recordException(this@MainActivity, e)
            false
        }
    }
    
    fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Photo Verification", text)
        clipboard.setPrimaryClip(clip)
    }
    
    // 获取照片的完整验证信息，用于显示给用户
    fun getPhotoVerificationInfo(photo: Photo, albumName: String): String {
        return getPhotoVerificationInfo(photo.uri, albumName)
    }
    
    fun getPhotoVerificationInfo(photoUri: Uri, albumName: String): String {
        return try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED
            )
            
            val info = StringBuilder()
            info.append("=== 照片验证信息 ===\n")
            
            contentResolver.query(photoUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val bucketDisplayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                    val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
                    val dataPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                    val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                    
                    info.append("照片ID: $id\n")
                    info.append("文件名: $displayName\n")
                    info.append("大小: ${size / 1024} KB\n")
                    info.append("添加时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(dateAdded * 1000))}\n\n")
                    
                    info.append("=== 位置信息 ===\n")
                    info.append("相册显示名: $bucketDisplayName\n")
                    info.append("相对路径: $relativePath\n")
                    info.append("完整路径: $dataPath\n\n")
                    
                    val albumDisplayName = getAlbumDisplayName(albumName)
                    val expectedPath = constructRelativePath(albumName)
                    
                    info.append("=== 验证结果 ===\n")
                    info.append("目标相册: $albumName (显示名: $albumDisplayName)\n")
                    info.append("期望路径: $expectedPath\n\n")
                    
                    // 检查匹配情况
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                            val pathMatch = relativePath != null && (
                                relativePath.equals(expectedPath, ignoreCase = true) || 
                                relativePath.contains("/$albumDisplayName/", ignoreCase = true) ||
                                relativePath.endsWith("/$albumDisplayName", ignoreCase = true)
                            )
                            
                            val bucketMatch = bucketDisplayName != null && bucketDisplayName.equals(albumDisplayName, ignoreCase = true)
                            
                            info.append("路径匹配: ${if (pathMatch) "✓" else "✗"}\n")
                            info.append("相册名匹配: ${if (bucketMatch) "✓" else "✗"}\n")
                            info.append("总体结果: ${if (pathMatch && bucketMatch) "✓ 成功" else "✗ 失败"}\n")
                        }
                        else -> {
                            val bucketMatch = bucketDisplayName != null && bucketDisplayName.equals(albumDisplayName, ignoreCase = true)
                            val pathMatch = dataPath != null && (
                                dataPath.contains("/$albumDisplayName/", ignoreCase = true) ||
                                dataPath.endsWith("/$albumDisplayName", ignoreCase = true)
                            )
                            
                            info.append("路径匹配: ${if (pathMatch) "✓" else "✗"}\n")
                            info.append("相册名匹配: ${if (bucketMatch) "✓" else "✗"}\n")
                            info.append("总体结果: ${if (bucketMatch || pathMatch) "✓ 成功" else "✗ 失败"}\n")
                        }
                    }
                    
                    // 检查文件是否真实存在
                    val fileExists = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Android Q+ 使用相对路径检查
                            val externalDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                            relativePath != null && File(externalDir, relativePath).exists()
                        } else {
                            // 旧版本使用完整路径检查
                            dataPath != null && File(dataPath).exists()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "检查文件存在性时出错", e)
                        false
                    }
                    
                    info.append("\n文件系统检查: ${if (fileExists) "✓ 文件存在" else "✗ 文件不存在"}\n")
                    
                    // 查询该相册中是否包含此照片
                    try {
                        val selection = when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                            }
                            else -> {
                                "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                            }
                        }
                        
                        val selectionArgs = when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                                arrayOf("%$albumDisplayName%", displayName)
                            }
                            else -> {
                                arrayOf(albumDisplayName, displayName)
                            }
                        }
                        
                        contentResolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            arrayOf(MediaStore.Images.Media._ID),
                            selection,
                            selectionArgs,
                            null
                        )?.use { cursor ->
                            info.append("相册查询结果: 找到 ${cursor.count} 张匹配照片\n")
                            if (cursor.count > 0) {
                                info.append("相册查询: ✓ 照片在目标相册中\n")
                            } else {
                                info.append("相册查询: ✗ 照片不在目标相册中\n")
                            }
                        }
                    } catch (e: Exception) {
                        info.append("相册查询: 查询失败 - ${e.message}\n")
                    }
                }
            }
            
            info.toString()
        } catch (e: Exception) {
            Log.e(TAG, "获取照片验证信息时出错", e)
            "获取验证信息失败: ${e.message}"
        }
    }
    
    // 处理权限请求结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_MANAGE_EXTERNAL_STORAGE -> {
                if (resultCode == RESULT_OK) {
                    // 权限已授予，重新尝试移动照片
                    Log.d(TAG, "MANAGE_EXTERNAL_STORAGE权限已授予")
                    Toast.makeText(this, "权限已授予，请重新尝试移动照片", Toast.LENGTH_SHORT).show()
                    
                    // 如果有待处理的移动操作，重新执行
                    if (currentPhotoIndex >= 0 && currentPhotoIndex < photosList.size && moveToAlbum.isNotBlank()) {
                        val photo = photosList[currentPhotoIndex]
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                Log.d(TAG, "重新尝试移动照片: ${photo.displayName} 到相册: $moveToAlbum")
                                val moved = movePhotoWithMediaStore(photo, moveToAlbum)
                                withContext(Dispatchers.Main) {
                                    if (moved) {
                                        Toast.makeText(this@MainActivity, "照片已移至 $moveToAlbum 相册", Toast.LENGTH_LONG).show()
                                        Log.d(TAG, "照片移动成功")
                                        // 重新加载照片列表
                                        loadPhotosAndAlbums()
                                    } else {
                                        Toast.makeText(this@MainActivity, "移动照片失败", Toast.LENGTH_LONG).show()
                                        Log.e(TAG, "照片移动失败")
                                    }
                                }
                            } catch (e: Exception) {
                                val errorMsg = "重新尝试移动照片时出错: ${e.message}"
                                Log.e(TAG, errorMsg, e)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                } else {
                    // 权限被拒绝
                    Log.w(TAG, "MANAGE_EXTERNAL_STORAGE权限被拒绝")
                    Toast.makeText(this, "需要管理所有文件的权限才能移动照片，请在系统设置中手动授予", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

}

@Composable
fun PhotoViewer(
    modifier: Modifier = Modifier,
    onRefreshClick: () -> Unit,
    onMoveClick: () -> Unit,
    onAlbumChange: (String) -> Unit,
    currentPhotoIndex: Int,
    photosList: List<Photo>,
    moveToAlbum: String,
    albumsList: List<Album>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCopyErrorToClipboard: (String) -> Unit,
    showVerificationDialog: Boolean,
    verificationMessage: String,
    onDismissDialog: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 图片显示区域 - 扩大高度，让图片占据更多空间
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentPhotoIndex >= 0 && currentPhotoIndex < photosList.size) {
                val photo = photosList[currentPhotoIndex]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 使用LaunchedEffect处理异步加载
                    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                    var loadError by remember { mutableStateOf<String?>(null) }
                    
                    // 使用key(currentPhotoIndex)确保每次切换照片时都会重新加载
                    androidx.compose.runtime.LaunchedEffect(key1 = currentPhotoIndex) {
                        bitmap = null // 重置bitmap，确保显示加载状态
                        loadError = null // 重置错误状态
                        
                        try {
                            context.contentResolver.openInputStream(photo.uri)?.use { inputStream ->
                                // 限制图片大小以提高加载性能
                                val options = android.graphics.BitmapFactory.Options().apply {
                                    inSampleSize = 2 // 将图片缩小为原来的1/4
                                }
                                val loadedBitmap = android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                                bitmap = loadedBitmap
                            }
                        } catch (e: Exception) {
                            loadError = e.message
                        }
                    }
                    
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = photo.displayName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                        )
                    } else if (loadError != null) {
                        Text("无法加载图片: $loadError")
                    } else {
                        Text("加载中...")
                    }
                }
            } else if (photosList.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "未找到照片",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }
        
        // 按钮区域 - 使用清爽风格按钮
        CleanButtonRow(
            onRefreshClick = onRefreshClick,
            onMoveClick = onMoveClick,
            onAlbumChange = onAlbumChange,
            moveToAlbum = moveToAlbum,
            albumsList = albumsList,
            expanded = expanded,
            onExpandedChange = onExpandedChange
        )
        
        // 验证结果对话框
        if (showVerificationDialog) {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = {
                    Text("照片移动验证结果")
                },
                text = {
                    Text(verificationMessage)
                },
                confirmButton = {
                    CleanButton(
                        onClick = onDismissDialog,
                        text = "确定"
                    )
                },
                dismissButton = {
                    CleanButton(
                        onClick = {
                            val activity = context as MainActivity
                            activity.copyToClipboard(verificationMessage)
                            Toast.makeText(context, "验证信息已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            onDismissDialog()
                        },
                        text = "复制到剪贴板"
                    )
                }
            )
        }
    }
}