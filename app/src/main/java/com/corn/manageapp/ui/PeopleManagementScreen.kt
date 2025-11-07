package com.corn.manageapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    onWriteRequest: (String, String, String, Boolean) -> Unit,
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
    var enableCounter by rememberSaveable { mutableStateOf(true) }

    // 读取结果
    var lastUid by remember { mutableStateOf("") }
    var lastDoorNum by remember { mutableStateOf("") }
    var lastTagType by remember { mutableStateOf("") }
    var lastCounter by remember { mutableStateOf<String?>(null) }
    var verifyOk by remember { mutableStateOf<Boolean?>(null) }
    var hasNoteSignature by remember { mutableStateOf<Boolean?>(null) }
    var parsedLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var writeStatus by remember { mutableStateOf<NfcWriteResult?>(null) }

    val people = remember { mutableStateListOf<PersonInfo>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var detailPerson by remember { mutableStateOf<PersonInfo?>(null) }

    // 注册 NFC 读取回调（组件进入时）
    LaunchedEffect(Unit) {
        onWriteStatus { status ->
            writeStatus = status
        }
        onTagRead { res ->
            lastUid = res.uidHex
            lastDoorNum = calcDoorNum10(res.uidHex)
            lastTagType = res.tagType.orEmpty()
            lastCounter = res.counter?.let { it.toString() }
            // 从 vCard 找 NOTE 行
            val noteLine = res.vcard.lines().firstOrNull { it.startsWith("NOTE:", ignoreCase = true) }
                ?.substringAfter("NOTE:", "")
                ?.trim()
                ?: ""
            val noteExists = noteLine.isNotEmpty()
            hasNoteSignature = noteExists
            verifyOk = if (noteExists) {
                VCardVerifier.verifyNoteWithStoredPublic(ctx, res.uidHex, noteLine)
            } else false
            parsedLines = parseVCard(res.vcard)
            selectedTab = 1 // 自动切到“读取”
        }
    }

    LaunchedEffect(writeStatus) {
        if (writeStatus != null) {
            selectedTab = 0
        }
    }

    val containerScroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(containerScroll)
            .padding(16.dp)
    ) {
        Text("人员管理", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
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

        when (selectedTab) {
            // 写入
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = enableCounter, onCheckedChange = { enableCounter = it })
                        Spacer(Modifier.width(8.dp))
                        Text("启用 NTAG NFC 计数器")
                    }

                    Button(
                        onClick = {
                            val n = name.trim()
                            val p = phone.trim()
                            val e = emailPrefix.trim()
                            if (n.isEmpty() || p.isEmpty() || e.isEmpty()) return@Button
                            // 通知 Activity：等待贴卡，届时会读取 UID 并进行签名写卡
                            onWriteRequest(n, p, e, enableCounter)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = writeStatus !is NfcWriteResult.Waiting
                    ) {
                        Text(if (writeStatus is NfcWriteResult.Waiting) "请贴卡" else "写入 NFC")
                    }

                    // 预览（纯文本预览，不含 NOTE）
                    val preview = buildString {
                        appendLine("BEGIN:VCARD")
                        appendLine("VERSION:3.0")
                        appendLine("FN:${name.ifBlank { "张三" }}")
                        appendLine("ORG:COMCORN")
                        appendLine("EMAIL:${(emailPrefix.ifBlank { "john" })}@comcorn.cn")
                        appendLine("TEL:${phone.ifBlank { "13800000000" }}")
                        appendLine("NOTE:(刷卡写入时生成 UID 签名)")
                        appendLine("END:VCARD")
                    }
                    Text("预览", style = MaterialTheme.typography.titleSmall)
                    Surface(tonalElevation = 1.dp) {
                        Text(preview, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp))
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
                            status.tagType?.takeIf { it.isNotEmpty() }?.let {
                                Text("卡片类型：$it", style = MaterialTheme.typography.bodySmall)
                            }
                            if (status.counterConfigured != null) {
                                val tip = if (status.counterConfigured == true) "已尝试开启 NFC 计数器" else "未能开启 NFC 计数器"
                                Text(tip, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is NfcWriteResult.Failure -> {
                            Text(status.reason, color = MaterialTheme.colorScheme.error)
                        }
                        null -> {}
                    }
                }
            }

            // 读取
            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("读取 vCard 并离线验证", style = MaterialTheme.typography.titleMedium)

                    InfoLine("卡片 UID", lastUid.takeIf { it.isNotEmpty() })
                    InfoLine("门禁卡号（10位）", lastDoorNum.takeIf { it.isNotEmpty() })
                    InfoLine("卡片类型", lastTagType.takeIf { it.isNotEmpty() })
                    InfoLine("NFC 计数器", lastCounter)

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
                                val msg = if (hasNoteSignature == false) "假（缺少 NOTE 签名）" else "假"
                                Text(msg, color = MaterialTheme.colorScheme.error)
                            }
                            null -> {
                                Text("（请刷卡）")
                            }
                        }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (people.isEmpty()) {
                        Text("暂无人员，请点击下方按钮添加。", style = MaterialTheme.typography.bodySmall)
                    }
                    people.forEach { person ->
                        PersonCard(person = person, onClick = { detailPerson = person })
                    }
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("添加人员")
                    }
                }
            }

            // 门禁
            3 -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("门禁管理（可扩展）", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (showAddDialog) {
        AddPersonDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { person ->
                people.add(0, person)
                showAddDialog = false
            }
        )
    }

    detailPerson?.let { person ->
        PersonDetailDialog(
            person = person,
            onDismiss = { detailPerson = null },
            onFill = {
                name = person.name
                phone = person.phone
                emailPrefix = person.email.substringBefore('@', person.email)
                selectedTab = 0
            }
        )
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

@Composable
private fun PersonCard(person: PersonInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(person.name, style = MaterialTheme.typography.titleMedium)
            Text("性别：${person.gender.display}", style = MaterialTheme.typography.bodySmall)
            Text("手机号：${person.phone}", style = MaterialTheme.typography.bodySmall)
            Text("邮箱：${person.email}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AddPersonDialog(
    onDismiss: () -> Unit,
    onConfirm: (PersonInfo) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(PersonGender.Male) }
    var idNumber by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val birthday = remember(idNumber) { extractBirthdayFromId(idNumber) }

    val enableConfirm = name.isNotBlank() && phone.isNotBlank() && email.isNotBlank() && idNumber.length >= 15

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val person = PersonInfo(
                        id = System.currentTimeMillis(),
                        name = name.trim(),
                        gender = gender,
                        idNumber = idNumber.trim(),
                        birthday = birthday,
                        phone = phone.trim(),
                        email = email.trim()
                    )
                    onConfirm(person)
                },
                enabled = enableConfirm
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("添加人员") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GenderChip(option = PersonGender.Male, selected = gender == PersonGender.Male) { gender = PersonGender.Male }
                    GenderChip(option = PersonGender.Female, selected = gender == PersonGender.Female) { gender = PersonGender.Female }
                }
                OutlinedTextField(
                    value = idNumber,
                    onValueChange = { idNumber = it.uppercase() },
                    label = { Text("身份证号") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = birthday,
                    onValueChange = {},
                    label = { Text("出生年月日") },
                    singleLine = true,
                    enabled = false
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("手机号") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱") },
                    singleLine = true
                )
            }
        }
    )
}

@Composable
private fun PersonDetailDialog(
    person: PersonInfo,
    onDismiss: () -> Unit,
    onFill: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onFill()
                onDismiss()
            }) {
                Text("填充写卡信息")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = { Text(person.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("性别：${person.gender.display}")
                Text("身份证号：${person.idNumber}")
                Text("出生年月日：${person.birthday.ifBlank { "未知" }}")
                Text("手机号：${person.phone}")
                Text("邮箱：${person.email}")
            }
        }
    )
}

@Composable
private fun GenderChip(option: PersonGender, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(option.display) }
    )
}

data class PersonInfo(
    val id: Long,
    val name: String,
    val gender: PersonGender,
    val idNumber: String,
    val birthday: String,
    val phone: String,
    val email: String
)

enum class PersonGender(val display: String) {
    Male("男"),
    Female("女");
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

private fun extractBirthdayFromId(idNumber: String): String {
    val digits = idNumber.filter { it.isDigit() }
    if (digits.length >= 14) {
        val datePart = digits.substring(6, 14)
        val year = datePart.substring(0, 4)
        val month = datePart.substring(4, 6)
        val day = datePart.substring(6, 8)
        return "$year-$month-$day"
    }
    return ""
}
