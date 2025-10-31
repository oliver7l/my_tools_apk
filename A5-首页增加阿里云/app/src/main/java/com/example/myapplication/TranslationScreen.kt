package com.example.myapplication

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var chineseText by remember { mutableStateOf("") }
    var englishText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // 添加测试日志
    LaunchedEffect(Unit) {
        println("=== TranslationScreen 已加载 ===")
        Log.d("TranslationScreen", "TranslationScreen 已加载")
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "中文转英文",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // 输入区域
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "中文输入",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = chineseText,
                    onValueChange = { chineseText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text("请输入中文文本") },
                    enabled = !isLoading
                )
            }
        }
        
        // 翻译按钮
        Button(
            onClick = {
                if (chineseText.isBlank()) {
                    Toast.makeText(context, "请输入要翻译的中文文本", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                Toast.makeText(context, "翻译功能已调用，文本：$chineseText", Toast.LENGTH_SHORT).show()
                isLoading = true
                coroutineScope.launch {
                    try {
                        val result = translateChineseToEnglish(chineseText, context)
                        englishText = result
                    } catch (e: Exception) {
                        Toast.makeText(context, "翻译失败: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && chineseText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Translate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("翻译")
            }
        }
        
        // 输出区域
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "英文翻译",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = englishText,
                    onValueChange = { englishText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text("翻译结果将显示在这里") },
                    enabled = false
                )
            }
        }
    }
}

suspend fun translateChineseToEnglish(chineseText: String, context: Context): String = withContext(Dispatchers.IO) {
    val apiKey = "sk-6c20421d99db41cebd6f06c4999683c6"
    val url = URL("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation")
    
    println("=== 开始翻译: $chineseText ===")
    Log.d("Translation", "开始翻译: $chineseText")
    
    val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    val requestBody = JSONObject().apply {
        put("model", "qwen-plus")
        put("input", JSONObject().apply {
            put("messages", arrayOf(
                JSONObject().apply {
                    put("role", "user")
                    put("content", "请将以下中文翻译成英文，只返回翻译结果，不要添加任何解释：$chineseText")
                }
            ))
        })
        put("parameters", JSONObject().apply {
            put("temperature", 0.1)
            put("result_format", "message")
        })
    }.toString()
    
    println("=== 请求体: $requestBody ===")
    Log.d("Translation", "请求体: $requestBody")
    
    val request = Request.Builder()
        .url(url)
        .addHeader("Content-Type", "application/json")
        .addHeader("Authorization", "Bearer $apiKey")
        .post(requestBody.toRequestBody("application/json".toMediaType()))
        .build()
    
    try {
        println("=== 发送请求 ===")
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        
        println("=== 响应状态码: ${response.code} ===")
        println("=== 响应内容: $responseBody ===")
        Log.d("Translation", "响应状态码: ${response.code}")
        Log.d("Translation", "响应内容: $responseBody")
        
        if (response.isSuccessful) {
            val jsonResponse = JSONObject(responseBody)
            val output = jsonResponse.getJSONObject("output")
            val choices = output.getJSONArray("choices")
            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                val result = message.getString("content").trim()
                println("=== 翻译结果: $result ===")
                Log.d("Translation", "翻译结果: $result")
                result
            } else {
                val errorMsg = "翻译失败：未返回有效结果"
                println("=== 错误: $errorMsg ===")
                Log.e("Translation", errorMsg)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                }
                errorMsg
            }
        } else {
            val errorMsg = "翻译失败：HTTP ${response.code} - $responseBody"
            println("=== 错误: $errorMsg ===")
            Log.e("Translation", errorMsg)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
            errorMsg
        }
    } catch (e: IOException) {
        val errorMsg = "翻译失败：网络错误 - ${e.message}"
        println("=== 网络错误: $errorMsg ===")
        Log.e("Translation", errorMsg, e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
        errorMsg
    } catch (e: Exception) {
        val errorMsg = "翻译失败：${e.message}"
        println("=== 异常: $errorMsg ===")
        Log.e("Translation", errorMsg, e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
        errorMsg
    }
}