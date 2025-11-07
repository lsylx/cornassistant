package com.corn.manageapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.corn.manageapp.NfcReadResult
import com.corn.manageapp.NfcWriteResult
import com.corn.manageapp.utils.VCardVerifier

/**
 * 人员管理（离线签名版）
 * - 写入：仅收集 name/phone/emailPrefix，实际写卡在 MainActivity 中完成（使用私钥签名 UID）
 * - 读取：收到 UID + vCard，解析 NOTE 验真伪、显示 UID 和 10位门禁卡号
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleManagementScreen(
    modifier: Modifier = Modifier,
    onWriteRequest: (String, String, String) -> Unit,
    onWriteStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onTagRead: ((NfcReadResult) -> Unit) -> Unit
) {
    val ctx = LocalContext.current

    val tabs = listOf("写入", "读取", "人员", "门禁")
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    // 写入表单
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var emailPrefix by rememberSaveable { mutableStateOf("") }

    // 读取结果
    var lastUid by remember { mutableStateOf("") }
    var lastDoorNum by remember { mutableStateOf("") }
    var verifyOk by remember { mutableStateOf<Boolean?>(null) }
    var verifyMessage by remember { mutableStateOf<String?>(null) }
    var parsedLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var writeStatus by remember { mutableStateOf<NfcWriteResult?>(null) }

    // 注册 NFC 读取回调（组件进入时）
    LaunchedEffect(Unit) {
        onWriteStatus { status ->
            writeStatus = status
        }
        onTagRead { res ->
            lastUid = res.uidHex
            lastDoorNum = calcDoorNum10(res.uidHex)
            val noteLine = res.vcard.lines()
                .firstOrNull { it.startsWith("NOTE:", ignoreCase = true) }
                ?.substringAfter("NOTE:", "")
                ?.trim()

            if (noteLine.isNullOrEmpty()) {
                verifyOk = false
                verifyMessage = "❌ vCard 缺少 NOTE 签名字段"
            } else {
                val ok = VCardVerifier.verifyNoteWithStoredPublic(ctx, res.uidHex, noteLine)
                verifyOk = ok
                verifyMessage = if (ok) "✅ 签名验证通过" else "❌ 签名验证失败"
            }

            parsedLines = parseVCard(res.vcard)
            selectedTab = 1 // 自动切到“读取”
        }
    }

    LaunchedEffect(writeStatus) {
        if (writeStatus != null) {
            selectedTab = 0
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("人员管理") }) }
    ) { inner ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
        }

        when (selectedTab) {
            // 写入
            0 -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("写入 vCard（NOTE 含 UID 的 Ed25519 签名）", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("姓名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("手机号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = emailPrefix,
                        onValueChange = { emailPrefix = it },
                        label = { Text("邮箱前半部分（如：john）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val n = name.trim()
                            val p = phone.trim()
                            val e = emailPrefix.trim()
                            if (n.isEmpty() || p.isEmpty() || e.isEmpty()) return@Button
                            // 通知 Activity：等待贴卡，届时会读取 UID 并进行签名写卡
                            onWriteRequest(n, p, e)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = writeStatus !is NfcWriteResult.Waiting
                    ) {
                        Text(if (writeStatus is NfcWriteResult.Waiting) "请贴卡" else "写入 NFC")
                    }

                        Button(
                            onClick = {
                                val n = name.trim()
                                val p = phone.trim()
                                val e = emailPrefix.trim()
                                if (n.isEmpty() || p.isEmpty() || e.isEmpty()) return@Button
                                // 通知 Activity：等待贴卡，届时会读取 UID 并进行签名写卡
                                onWriteRequest(n, p, e)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = writeStatus !is NfcWriteResult.Waiting
                        ) {
                            Text(if (writeStatus is NfcWriteResult.Waiting) "请贴卡" else "写入 NFC")
                        }

                    when (val status = writeStatus) {
                        NfcWriteResult.Waiting -> {
                            Text("请将卡片靠近 NFC 写入…", color = MaterialTheme.colorScheme.primary)
                        }
                        is NfcWriteResult.Success -> {
                            val door = calcDoorNum10(status.uidHex)
                            Text(
                                buildString {
                                    append("✅ 写卡成功 (UID: ")
                                    append(status.uidHex)
                                    append(')')
                                    if (door.isNotEmpty()) {
                                        append(" 门禁号：")
                                        append(door)
                                    }
                                },
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        when (val status = writeStatus) {
                            NfcWriteResult.Waiting -> {
                                Text("请将卡片靠近 NFC 写入…", color = MaterialTheme.colorScheme.primary)
                            }
                            is NfcWriteResult.Success -> {
                                val door = calcDoorNum10(status.uidHex)
                                Text(
                                    buildString {
                                        append("✅ 写卡成功 (UID: ")
                                        append(status.uidHex)
                                        append(')')
                                        if (door.isNotEmpty()) {
                                            append(" 门禁号：")
                                            append(door)
                                        }
                                    },
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is NfcWriteResult.Failure -> {
                                Text(status.reason, color = MaterialTheme.colorScheme.error)
                            }
                            null -> {}
                        }
                    }
                }
            }

            // 读取
            1 -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("读取 vCard 并离线验证", style = MaterialTheme.typography.titleMedium)

                    InfoLine("卡片 UID", lastUid.takeIf { it.isNotEmpty() })
                    InfoLine("门禁卡号（10位）", lastDoorNum.takeIf { it.isNotEmpty() })

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("验证结果：")
                        Spacer(Modifier.width(6.dp))
                        when (verifyOk) {
                            true -> {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "真",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("真", color = MaterialTheme.colorScheme.primary)
                            }
                            false -> {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "假",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("假", color = MaterialTheme.colorScheme.error)
                            }
                            null -> {
                                Text("（请刷卡）")
                            }
                        }
                    }

                    verifyMessage?.let {
                        Text(
                            it,
                            color = if (verifyOk == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }

                    if (parsedLines.isNotEmpty()) {
                        Divider()
                        parsedLines.forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text("请贴卡读取信息", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 人员
            2 -> {
                Text("人员信息管理（可扩展）", style = MaterialTheme.typography.bodyMedium)
            }

            // 门禁
            3 -> {
                Text("门禁管理（可扩展）", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/* ----------------- 小工具 & 解析 ----------------- */

/** 信息行（左标签右值；当值为空时不显示） */
@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(onClick = {}, label = { Text(label) })
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** 解析 vCard 的常见字段 */
private fun parseVCard(vcard: String): List<String> {
    val lines = vcard.replace("\r\n", "\n").split("\n")
    val out = mutableListOf<String>()
    for (raw in lines) {
        val l = raw.trim()
        when {
            l.startsWith("FN:", true) -> out.add("姓名：${l.substringAfter("FN:", "")}")
            l.startsWith("ORG:", true) -> out.add("公司：${l.substringAfter("ORG:", "")}")
            l.startsWith("TEL", true) -> out.add("电话：${l.substringAfter(':', "")}")
            l.startsWith("EMAIL", true) -> out.add("邮箱：${l.substringAfter(':', "")}")
        }
    }
    return out
}

/**
 * 计算“门禁卡号”：取 UID 的前 4 个字节（8个HEX字符），按大端解析成无符号整型，
 * 再格式化为 10 位十进制，不足左侧补 0。
 */
private fun calcDoorNum10(uidHex: String): String {
    val clean = uidHex.replace("[^0-9A-Fa-f]".toRegex(), "")
    if (clean.length < 8) return ""
    val first4 = clean.substring(0, 8)
    val value = first4.toULong(16).toLong() // 0..0xFFFFFFFF
    return value.toString().padStart(10, '0')
}
