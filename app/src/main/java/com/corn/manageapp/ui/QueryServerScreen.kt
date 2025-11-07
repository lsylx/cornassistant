package com.corn.manageapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding

// ✅ 最新 Compose 输入法 API（非常重要，否则报错）
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager

import com.corn.manageapp.data.DcimConfig
import com.corn.manageapp.data.DcimConfigRepository
import com.corn.manageapp.network.DcimApi
import com.corn.manageapp.network.HardwareItem
import kotlinx.coroutines.launch


@Composable
fun QueryServerScreen(
    repo: DcimConfigRepository,
    onBack: () -> Unit,
    onOpenDetail: (HardwareItem) -> Unit
) {
    val cfg by repo.configFlow.collectAsState(initial = DcimConfig("", "", ""))
    var key by rememberSaveable { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<List<HardwareItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // ✅ 控制输入法（关闭键盘必须加这个）
    val focusManager = LocalFocusManager.current

    fun doSearch() {
        if (cfg.host.isEmpty()) {
            msg = "请先在设置中配置 DCIM 地址/账号/密码"
            result = emptyList()
            return
        }

        scope.launch {
            try {
                val api = DcimApi.create(cfg.host)
                val r = api.queryServers(
                    username = cfg.username,
                    password = cfg.password,
                    search = "key",
                    key = key,
                    listpages = 50
                )

                if (r.status == "success") {
                    result = r.listing ?: emptyList()
                    msg = "共 ${result.size} 台"

                    // ✅ 若只有 1 台，自动跳转
                    if (result.size == 1) {
                        onOpenDetail(result.first())
                    }
                } else {
                    result = emptyList()
                    msg = "❌ 查询失败"
                }
            } catch (e: Exception) {
                result = emptyList()
                msg = "❌ 请求异常：${e.message}"
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),   // ✅ 解决键盘遮挡
        contentPadding = PaddingValues(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← 返回") }
                Spacer(Modifier.width(8.dp))
                Text("查询服务器", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
        }

        // ✅ 输入框
        item {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("统一搜索（内部标签 / 物理标签 / IP 等）") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        doSearch()
                        focusManager.clearFocus()   // ✅ 自动关闭输入法
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        // ✅ 查询按钮
        item {
            Button(
                onClick = {
                    doSearch()
                    focusManager.clearFocus()       // ✅ 按按钮也关闭键盘
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("查询")
            }

            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(msg)
            }
            Spacer(Modifier.height(12.dp))
        }

        // ✅ 列表项
        items(result) { item ->
            ServerRowSimple(
                item = item,
                onClick = { onOpenDetail(item) }
            )
            Divider()
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}


/* ✅ 简洁列表行 + 电源状态点 */
@Composable
private fun ServerRowSimple(
    item: HardwareItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(item.power)
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.nbtag ?: item.wltag ?: "(无标签)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(2.dp))

            val sub = buildString {
                if (!item.wltag.isNullOrEmpty()) append("物理: ${item.wltag}  ")
                if (!item.zhuip.isNullOrEmpty()) append("IP: ${item.zhuip}  ")
                if (!item.power.isNullOrEmpty()) append("状态: ${item.power}")
            }.ifBlank { "-" }

            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}


/* ✅ 电源状态颜色点 */
@Composable
private fun StatusDot(status: String?) {
    val color = when (status?.lowercase()) {
        "on" -> Color(0xFF2ECC71)
        "off" -> Color(0xFFE74C3C)
        "error" -> Color(0xFFF39C12)
        "nonsupport" -> Color(0xFF95A5A6)
        else -> Color(0xFFB0B0B0)
    }

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}
