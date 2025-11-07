package com.corn.manageapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleManagementScreen(
    modifier: Modifier = Modifier,
    onWriteRequest: (String) -> Unit,
    onTagRead: ((String) -> Unit) -> Unit
) {
    val tabs = listOf("写入", "读取", "人员", "门禁")
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    // 写入字段
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var emailPrefix by rememberSaveable { mutableStateOf("") }

    // 读取数据
    var rawRead by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf(ParsedVCard(emptyList(), false)) }

    val snackHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 注册读取回调
    LaunchedEffect(Unit) {
        onTagRead { text ->
            rawRead = text
            parsed = parseVCard(text)
            selectedTab = 1
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("人员管理") }) },
        snackbarHost = { SnackbarHost(snackHost) }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(12.dp)
        ) {

            /* ---------------- Tabs 顶部栏 ---------------- */
            item {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            /* =====================================================
             * 写入页面
             * ===================================================== */
            if (selectedTab == 0) {
                item { Text("写入 vCard 到 NFC", style = MaterialTheme.typography.titleMedium) }

                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("姓名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("手机号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = emailPrefix,
                        onValueChange = { emailPrefix = it },
                        label = { Text("邮箱前半部分（例如 john）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (name.isBlank() || phone.isBlank() || emailPrefix.isBlank()) {
                                scope.launch {
                                    snackHost.showSnackbar("请填写姓名、手机号、邮箱前半部分")
                                }
                                return@Button
                            }

                            val vcard = buildVCard(
                                fullName = name,
                                org = "COMCORN",
                                email = "${emailPrefix}@comcorn.cn",
                                tel = phone
                            )
                            onWriteRequest(vcard)

                            scope.launch {
                                snackHost.showSnackbar("请将 NFC 卡靠近设备完成写入…")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("写入 NFC")
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Text("预览（vCard）", style = MaterialTheme.typography.titleSmall)
                }

                item {
                    Surface(tonalElevation = 2.dp) {
                        Text(
                            text = buildVCard(
                                name.ifBlank { "张三" },
                                "COMCORN",
                                (emailPrefix.ifBlank { "john" }) + "@comcorn.cn",
                                phone.ifBlank { "13800000000" }
                            ),
                            modifier = Modifier.padding(8.dp),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            /* =====================================================
             * 读取页面
             * ===================================================== */
            if (selectedTab == 1) {

                item { Text("读取 vCard", style = MaterialTheme.typography.titleMedium) }

                item {
                    AssistLine(
                        label = "公司校验（ORG 是否包含 COMCORN）",
                        value = if (parsed.isComcorn) "真" else "假"
                    )
                }

                if (parsed.lines.isNotEmpty()) {
                    items(parsed.lines) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                    }
                } else {
                    item { Text("请将 NFC 卡靠近设备读取信息") }
                }

                if (rawRead.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Text("原始文本（调试）", style = MaterialTheme.typography.titleSmall)
                    }
                    item {
                        Surface(tonalElevation = 2.dp) {
                            Text(
                                text = rawRead,
                                modifier = Modifier.padding(8.dp),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            /* =====================================================
             * 人员
             * ===================================================== */
            if (selectedTab == 2) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("人员信息管理（可扩展）")
                    }
                }
            }

            /* =====================================================
             * 门禁
             * ===================================================== */
            if (selectedTab == 3) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("门禁管理（可扩展）")
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

/* -------------------- 工具函数 -------------------- */

private fun buildVCard(fullName: String, org: String, email: String, tel: String): String =
    """
        BEGIN:VCARD
        VERSION:3.0
        FN:$fullName
        ORG:$org
        EMAIL:$email
        TEL:$tel
        END:VCARD
    """.trimIndent().replace("\n", "\r\n")

private fun parseVCard(text: String): ParsedVCard {
    val lines = text.replace("\r\n", "\n").split("\n")
    val list = mutableListOf<String>()
    var isComcorn = false

    for (l in lines.map { it.trim() }) {
        when {
            l.startsWith("FN:", true) ->
                list.add("姓名：${l.substringAfter("FN:")}")

            l.startsWith("ORG:", true) -> {
                val org = l.substringAfter("ORG:")
                list.add("公司：$org")
                if (org.contains("COMCORN", true)) isComcorn = true
            }

            l.startsWith("TEL", true) ->
                list.add("电话：${l.substringAfter(":")}")

            l.startsWith("EMAIL", true) ->
                list.add("邮箱：${l.substringAfter(":")}")
        }
    }

    return ParsedVCard(
        lines = list.ifEmpty { listOf("未识别到有效 vCard") },
        isComcorn = isComcorn
    )
}

private data class ParsedVCard(
    val lines: List<String>,
    val isComcorn: Boolean
)

@Composable
private fun AssistLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = {}, label = { Text(label) })
        Spacer(modifier = Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
