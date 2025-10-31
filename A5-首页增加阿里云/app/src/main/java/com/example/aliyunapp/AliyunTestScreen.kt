package com.example.aliyunapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliyunTestScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // API配置状态
    val apiKey = "sk-6c20421d99db41cebd6f06c4999683c6" // 固定API Key
    var modelName by remember { mutableStateOf("qwen-turbo") }
    var inputText by remember { mutableStateOf("你好，请介绍一下你自己") }
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("通用大语言模型") }
    
    // 不同类型模型的输入参数
    var codeLanguage by remember { mutableStateOf("python") } // 代码模型的语言选择
    var mathProblem by remember { mutableStateOf("") } // 数学模型的问题输入
    var audioPrompt by remember { mutableStateOf("") } // 音频模型的提示词
    var audioFormat by remember { mutableStateOf("wav") } // 音频格式
    var audioSampleRate by remember { mutableStateOf("16000") } // 音频采样率
    
    // 模型分类
    val modelCategories = listOf(
        "通用大语言模型",
        "代码模型",
        "多模态模型",
        "音频模型",
        "数学模型",
        "特化模型"
    )
    
    // 按分类组织的模型列表
    val modelOptionsByCategory = mapOf(
        "通用大语言模型" to listOf(
            "qwen-turbo",
            "qwen-plus",
            "qwen-max",
            "qwen-max-longcontext",
            "qwen-max-1201"
        ),
        "代码模型" to listOf(
            "qwen-coder-plus",
            "qwen-coder-turbo"
        ),
        "多模态模型" to listOf(
            "qwen-vl-plus",
            "qwen-vl-max",
            "qwen-vl-plus-latest"
        ),
        "音频模型" to listOf(
            "qwen-audio-turbo",
            "qwen-audio-chat"
        ),
        "数学模型" to listOf(
            "qwen-math-plus",
            "qwen-math-turbo"
        ),
        "特化模型" to listOf(
            "qwen-long",
            "qwen2-72b-instruct",
            "qwen2-57b-a14b-instruct",
            "qwen2-7b-instruct",
            "qwen2-1.5b-instruct",
            "qwen2-0.5b-instruct"
        )
    )
    
    // 获取当前选中的模型列表
    val currentModelOptions = modelOptionsByCategory[selectedCategory] ?: emptyList()
    
    // 响应状态
    var responseText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // 滚动状态
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = "阿里云百炼API测试",
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
        
        // API配置区域
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "API配置",
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium
                )
                
                // 模型分类下拉菜单
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = { },
                    label = { Text("模型分类") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { isCategoryDropdownExpanded = true }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "展开分类菜单")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                DropdownMenu(
                    expanded = isCategoryDropdownExpanded,
                    onDismissRequest = { isCategoryDropdownExpanded = false }
                ) {
                    modelCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                selectedCategory = category
                                isCategoryDropdownExpanded = false
                                // 当切换分类时，自动选择该分类下的第一个模型
                                val firstModel = modelOptionsByCategory[category]?.firstOrNull()
                                if (firstModel != null) {
                                    modelName = firstModel
                                }
                            }
                        )
                    }
                }
                
                // 模型选择下拉菜单
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { },
                    label = { Text("模型名称") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { isModelDropdownExpanded = true }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "展开模型菜单")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                DropdownMenu(
                    expanded = isModelDropdownExpanded,
                    onDismissRequest = { isModelDropdownExpanded = false }
                ) {
                    currentModelOptions.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                modelName = model
                                isModelDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }
        
        // 输入区域
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = when (selectedCategory) {
                        "通用大语言模型" -> "文本输入"
                        "代码模型" -> "代码输入"
                        "多模态模型" -> "多模态输入"
                        "音频模型" -> "音频输入"
                        "数学模型" -> "数学问题"
                        "特化模型" -> "输入内容"
                        else -> "输入内容"
                    },
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium
                )
                
                // 根据模型类型显示不同的输入界面
                when (selectedCategory) {
                    "通用大语言模型", "多模态模型", "特化模型" -> {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = { Text("请输入要测试的文本") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                    
                    "代码模型" -> {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = { Text("请输入代码需求或问题") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        
                        // 代码语言选择
                        OutlinedTextField(
                            value = codeLanguage,
                            onValueChange = { },
                            label = { Text("编程语言") },
                            readOnly = true,
                            trailingIcon = {
                                var expanded by remember { mutableStateOf(false) }
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "展开语言选择")
                                }
                                
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    listOf("python", "java", "javascript", "cpp", "c", "go", "rust", "swift", "kotlin").forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text(lang) },
                                            onClick = {
                                                codeLanguage = lang
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    "音频模型" -> {
                        OutlinedTextField(
                            value = audioPrompt,
                            onValueChange = { audioPrompt = it },
                            label = { Text("请输入音频描述或文本") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        
                        // 音频格式选择
                        OutlinedTextField(
                            value = audioFormat,
                            onValueChange = { },
                            label = { Text("音频格式") },
                            readOnly = true,
                            trailingIcon = {
                                var expanded by remember { mutableStateOf(false) }
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "展开格式选择")
                                }
                                
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    listOf("wav", "mp3", "pcm").forEach { format ->
                                        DropdownMenuItem(
                                            text = { Text(format) },
                                            onClick = {
                                                audioFormat = format
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // 采样率选择
                        OutlinedTextField(
                            value = audioSampleRate,
                            onValueChange = { },
                            label = { Text("采样率") },
                            readOnly = true,
                            trailingIcon = {
                                var expanded by remember { mutableStateOf(false) }
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "展开采样率选择")
                                }
                                
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    listOf("16000", "22050", "44100", "48000").forEach { rate ->
                                        DropdownMenuItem(
                                            text = { Text(rate) },
                                            onClick = {
                                                audioSampleRate = rate
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    "数学模型" -> {
                        OutlinedTextField(
                            value = mathProblem,
                            onValueChange = { mathProblem = it },
                            label = { Text("请输入数学问题") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                }
                
                Button(
                    onClick = {
                        // 根据模型类型验证输入
                        val isValidInput = when (selectedCategory) {
                            "通用大语言模型", "多模态模型", "特化模型", "代码模型" -> inputText.isNotBlank()
                            "音频模型" -> audioPrompt.isNotBlank()
                            "数学模型" -> mathProblem.isNotBlank()
                            else -> false
                        }
                        
                        if (!isValidInput) {
                            val message = when (selectedCategory) {
                                "音频模型" -> "请输入音频描述或文本"
                                "数学模型" -> "请输入数学问题"
                                else -> "请输入测试文本"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        // 清理API Key，去除换行符和多余空格
                        val cleanApiKey = apiKey.trim().replace("\n", "").replace("\r", "")
                        
                        isLoading = true
                        errorMessage = ""
                        responseText = ""
                        
                        // 根据模型类型准备输入内容
                        val finalInputText = when (selectedCategory) {
                            "代码模型" -> "请使用${codeLanguage}语言完成以下任务：${inputText}"
                            "音频模型" -> audioPrompt
                            "数学模型" -> mathProblem
                            else -> inputText
                        }
                        
                        coroutineScope.launch {
                            try {
                                val result = callAliyunAPI(
                                    cleanApiKey, 
                                    modelName, 
                                    finalInputText,
                                    audioFormat,
                                    audioSampleRate.toInt()
                                )
                                responseText = result
                                Toast.makeText(context, "API调用成功", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                errorMessage = "API调用失败: ${e.message}"
                                Toast.makeText(context, "API调用失败", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("调用中...")
                    } else {
                        Text("调用API")
                    }
                }
            }
        }
        
        // 响应区域
        if (responseText.isNotBlank() || errorMessage.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "API响应",
                            fontSize = 18.sp,
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        // 复制按钮
                        IconButton(
                            onClick = {
                                val textToCopy = if (errorMessage.isNotBlank()) {
                                    "错误信息:\n$errorMessage\n\n响应内容:\n$responseText"
                                } else {
                                    responseText
                                }
                                
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("API响应", textToCopy)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "复制响应"
                            )
                        }
                    }
                    
                    if (errorMessage.isNotBlank()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    if (responseText.isNotBlank()) {
                        Text(
                            text = responseText,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        
        // 使用说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "使用说明",
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Text(
                    text = "1. API Key已固定配置\n2. 先选择模型分类，再选择具体模型\n3. 根据模型类型输入相应内容\n4. 点击调用API查看结果\n\n模型分类说明：\n- 通用大语言模型：文本对话和生成\n- 代码模型：代码生成和问题解答\n- 多模态模型：图文理解与生成\n- 音频模型：音频理解和语音识别\n- 数学模型：数学问题解答\n- 特化模型：特定领域任务",
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * 调用阿里云百炼API
 */
private suspend fun callAliyunAPI(
    apiKey: String, 
    modelName: String, 
    inputText: String,
    audioFormat: String = "wav",
    audioSampleRate: Int = 16000
): String = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    
    // 根据模型类型选择不同的API端点和请求格式
    val isAudioModel = modelName.startsWith("qwen-audio")
    
    val requestBody = if (isAudioModel) {
        // 音频模型使用不同的请求格式 - Qwen-Audio是音频理解模型，不是音频生成模型
        JSONObject().apply {
            put("model", modelName)
            put("input", JSONObject().apply {
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", inputText)
                            })
                        })
                    })
                })
            })
            put("parameters", JSONObject().apply {
                put("result_format", "message")
            })
        }.toString().toRequestBody("application/json".toMediaType())
    } else {
        // 其他模型使用标准格式
        JSONObject().apply {
            put("model", modelName)
            put("input", JSONObject().apply {
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", inputText)
                    })
                })
            })
            put("parameters", JSONObject().apply {
                put("result_format", "message")
            })
        }.toString().toRequestBody("application/json".toMediaType())
    }
    
    // 根据模型类型选择不同的API端点
    val apiUrl = if (isAudioModel) {
        "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
    } else {
        "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
    }
    
    // 构建请求
    val request = Request.Builder()
        .url(apiUrl)
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "application/json")
        .post(requestBody)
        .build()
    
    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.body?.string()}")
            }
            
            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            
            // 解析响应
            val jsonResponse = JSONObject(responseBody)
            
            // 检查是否有错误
            if (jsonResponse.has("code") && jsonResponse.getString("code") != "200") {
                val errorMsg = if (jsonResponse.has("message")) {
                    jsonResponse.getString("message")
                } else {
                    "API调用失败"
                }
                throw IOException("API错误: $errorMsg")
            }
            
            // 根据模型类型提取不同的响应内容
            if (isAudioModel) {
                // 音频理解模型返回文本响应，格式与其他模型类似
                if (jsonResponse.has("output")) {
                    val output = jsonResponse.getJSONObject("output")
                    if (output.has("choices")) {
                        val choices = output.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            if (choice.has("message")) {
                                val message = choice.getJSONObject("message")
                                if (message.has("content")) {
                                    return@withContext message.getString("content")
                                }
                            }
                        }
                    }
                }
            } else {
                // 文本模型提取回复内容
                if (jsonResponse.has("output")) {
                    val output = jsonResponse.getJSONObject("output")
                    if (output.has("choices")) {
                        val choices = output.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            if (choice.has("message")) {
                                val message = choice.getJSONObject("message")
                                if (message.has("content")) {
                                    return@withContext message.getString("content")
                                }
                            }
                        }
                    }
                }
            }
            
            // 如果无法解析回复内容，返回完整响应
            return@withContext responseBody
        }
    } catch (e: Exception) {
        throw e
    }
}