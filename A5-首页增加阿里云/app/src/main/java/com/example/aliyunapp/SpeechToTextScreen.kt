package com.example.aliyunapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * 语音转文字屏幕组件
 * 提供录音和语音转文字功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechToTextScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 录音相关状态
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var recordingTime by remember { mutableStateOf(0) }
    
    // MediaRecorder实例
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    
    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 在权限授予后，通过状态变量触发录音
            isRecording = true
        } else {
            Toast.makeText(context, "需要录音权限才能使用语音转文字功能", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 检查录音权限
    fun checkRecordPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 6.0以下默认授予权限
        }
    }
    
    // 开始录音
    fun startRecording() {
        try {
            // 创建录音文件
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val audioFileName = "AUDIO_$timeStamp.mp3"
            val storageDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "AudioRecords")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            audioFile = File(storageDir, audioFileName)
            
            // 初始化MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            
            isRecording = true
            recordingTime = 0
            recognizedText = "" // 清空之前的识别结果
            
            // 更新录音时间
            coroutineScope.launch {
                while (isRecording) {
                    delay(1000)
                    recordingTime++
                }
            }
            
            Toast.makeText(context, "开始录音", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(context, "录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
    
    // 处理语音转文字（使用阿里云MCP服务）
    fun processSpeechToText() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                audioFile?.let { file ->
                    // 这里应该调用阿里云MCP进行语音转文字
                    // 以下是示例代码结构，实际使用时需要替换为真实的API调用
                    
                    /*
                    // 1. 准备请求参数
                    val apiKey = "your_ali_cloud_api_key"
                    val appKey = "your_app_key"
                    
                    // 2. 读取音频文件
                    val audioBytes = file.readBytes()
                    
                    // 3. 构建请求
                    val client = OkHttpClient()
                    
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("audio", file.name, 
                            file.readBytes().toRequestBody("audio/mpeg".toMediaType()))
                        .addFormDataPart("format", "mp3")
                        .addFormDataPart("sample_rate", "16000")
                        .build()
                    
                    val request = Request.Builder()
                        .url("https://nls-gateway.cn-shanghai.aliyuncs.com/stream/v1/asr")
                        .addHeader("X-NLS-Token", apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build()
                    
                    // 4. 发送请求
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    
                    // 5. 解析响应
                    if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                        val jsonResponse = JSONObject(responseBody)
                        val text = jsonResponse.optString("text", "无法识别语音内容")
                        withContext(Dispatchers.Main) {
                            recognizedText = text
                            isProcessing = false
                            Toast.makeText(context, "语音转文字完成", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            recognizedText = "语音识别失败: ${response.message}"
                            isProcessing = false
                        }
                    }
                    */
                    
                    // 目前使用模拟结果
                    delay(2000) // 模拟网络请求延迟
                    
                    // 模拟识别结果
                    val mockResults = listOf(
                        "这是一段测试语音转文字的内容",
                        "今天天气真不错，适合出去走走",
                        "语音识别技术已经越来越成熟了",
                        "通过语音输入可以大大提高效率"
                    )
                    
                    val randomResult = mockResults.random()
                    
                    withContext(Dispatchers.Main) {
                        recognizedText = randomResult
                        isProcessing = false
                        Toast.makeText(context, "语音转文字完成", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    Toast.makeText(context, "语音转文字失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // 停止录音
    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            
            // 开始处理语音转文字
            if (audioFile != null && audioFile!!.exists()) {
                isProcessing = true
                processSpeechToText()
            } else {
                Toast.makeText(context, "录音文件不存在", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Toast.makeText(context, "停止录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
    
    // 格式化录音时间
    fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
    
    // 监听isRecording状态变化，当权限授予后自动开始录音
    LaunchedEffect(isRecording) {
        if (isRecording && mediaRecorder == null) {
            // 如果isRecording为true但mediaRecorder为null，说明是权限授予后的触发
            startRecording()
        }
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音转文字") },
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 录音状态显示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isRecording) {
                        Text(
                            text = "录音中...",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatTime(recordingTime),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isProcessing) {
                        Text(
                            text = "正在转换语音为文字...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "点击下方按钮开始录音",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // 识别结果区域
            if (recognizedText.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "识别结果:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = recognizedText,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
            
            // 录音控制按钮
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(
                        if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(40.dp)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { 
                                // 检查权限
                                if (checkRecordPermission()) {
                                    // 开始录音
                                    startRecording()
                                    
                                    // 等待用户松开按钮
                                    val success = tryAwaitRelease()
                                    if (success) {
                                        // 松开按钮后停止录音
                                        stopRecording()
                                    }
                                } else {
                                    // 请求权限
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "停止录音" else "开始录音",
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 使用说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "使用说明:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "1. 按住麦克风按钮开始录音\n2. 松开按钮停止录音\n3. 系统将自动将语音转换为文字\n4. 识别结果将显示在上方",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}