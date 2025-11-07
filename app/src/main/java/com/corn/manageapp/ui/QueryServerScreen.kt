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
import com.corn.manageapp.data.DcimConfig
import com.corn.manageapp.data.DcimConfigRepository
import com.corn.manageapp.network.DcimApi
import com.corn.manageapp.network.HardwareItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("查询服务器") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← 返回") }
                }
            )
        }
    ) { inner ->
        // ✅ 全部使用一个 LazyColumn，整页可滑动；加 imePadding 防止被键盘遮挡
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("统一搜索（内部标签 / 物理标签 / IP 等）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                Button(
                    onClick = {
                        if (cfg.host.isEmpty()) {
                            msg = "请先在设置中配置 DCIM 地址/账号/密码"
                            return@Button
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
                                } else {
                                    result = emptyList()
                                    msg = "❌ 查询失败"
                                }
                            } catch (e: Exception) {
                                result = emptyList()
                                msg = "❌ 请求异常：${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("查询") }

                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(msg)
                }
                Spacer(Modifier.height(8.dp))
            }

            // ✅ 结果列表（同一个 LazyColumn 里，页面整体都能滑动）
            items(result) { item ->
                ServerRowSimple(
                    item = item,
                    onClick = { onOpenDetail(item) }
                )
                Divider()
            }

            // 防止底部被系统栏/小屏裁掉的一点缓冲
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

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
        StatusDot(status = item.power)
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.nbtag ?: item.wltag ?: "(无标签)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(2.dp))
            val line = buildString {
                if (!item.wltag.isNullOrEmpty()) append("物理: ${item.wltag}  ")
                if (!item.zhuip.isNullOrEmpty()) append("IP: ${item.zhuip}  ")
                if (!item.power.isNullOrEmpty()) append("状态: ${item.power}")
            }.ifEmpty { "-" }
            Text(line, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(4.dp))
    }
}

/** 电源状态颜色点：on=绿、off=红、error=橙、nonsupport/未知=灰 */
@Composable
private fun StatusDot(status: String?) {
    val color: Color = when (status?.lowercase()) {
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
