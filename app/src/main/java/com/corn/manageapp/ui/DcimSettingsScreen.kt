package com.corn.manageapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corn.manageapp.data.*
import com.corn.manageapp.network.DcimApi
import kotlinx.coroutines.launch

@Composable
fun DcimSettingsScreen(
    repo: DcimConfigRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val cfg by repo.configFlow.collectAsState(initial = DcimConfig("", "", ""))

    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var testMsg by remember { mutableStateOf("") }

    // DataStore → UI
    LaunchedEffect(cfg) {
        host = cfg.host
        user = cfg.username
        pass = cfg.password
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()) // ✅ 小屏 240x320 也能滑
    ) {
        TextButton(onClick = onBack) { Text("← 返回") }
        Text("DCIM 配置", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("DCIM 地址（HTTP，例：http://192.168.10.71/）") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("API 用户名") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("API 密码") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    repo.saveConfig(host, user, pass)
                    testMsg = "✅ 配置已保存"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存配置") }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    try {
                        val api = DcimApi.create(host)
                        val r = api.testConnection(user, pass, listpages = 1)
                        testMsg = if (r.status == "success") "✅ 连接成功"
                        else "❌ 连接失败：${r.msg ?: "未知原因"}"
                    } catch (e: Exception) {
                        testMsg = "❌ 请求失败：${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("测试连通性") }

        if (testMsg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(testMsg, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
