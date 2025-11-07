package com.corn.manageapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateListOf
import com.corn.manageapp.NfcReadResult
import com.corn.manageapp.NfcWriteResult
import com.corn.manageapp.utils.VCardVerifier
import java.util.Locale


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
    var lastEnableCounterRequest by remember { mutableStateOf(true) }

    // 读取结果
    var lastUid by remember { mutableStateOf("") }
    var lastDoorNum by remember { mutableStateOf("") }
    var verifyOk by remember { mutableStateOf<Boolean?>(null) }
    var hasNoteSignature by remember { mutableStateOf<Boolean?>(null) }
    var parsedLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var writeStatus by remember { mutableStateOf<NfcWriteResult?>(null) }
    var lastTagType by remember { mutableStateOf("") }
    var lastCounter by remember { mutableStateOf<Int?>(null) }
    var lastCounterEnabled by remember { mutableStateOf<Boolean?>(null) }
    var lastCounterSupported by remember { mutableStateOf<Boolean?>(null) }

    val people = remember { mutableStateListOf<Person>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedPerson by remember { mutableStateOf<Person?>(null) }

    // 注册 NFC 读取回调（组件进入时）
    LaunchedEffect(Unit) {
        onWriteStatus { status ->
            writeStatus = status
        }
        onTagRead { res ->
            lastUid = res.uidHex
            lastDoorNum = calcDoorNum10(res.uidHex)
            lastTagType = res.tagType.orEmpty()
            lastCounter = res.nfcCounter
            lastCounterEnabled = res.counterEnabled
            lastCounterSupported = res.counterSupported
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

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = enableCounter, onCheckedChange = { enableCounter = it })
                            Spacer(Modifier.width(6.dp))
                            Text("写卡时开启 NFC 计数器", style = MaterialTheme.typography.bodyMedium)
                        }

                        Button(
                            onClick = {
                                val n = name.trim()
                                val p = phone.trim()
                                val e = emailPrefix.trim()
                                if (n.isEmpty() || p.isEmpty() || e.isEmpty()) return@Button
                                lastEnableCounterRequest = enableCounter
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
                                val pieces = mutableListOf<String>()
                                pieces += "UID: ${status.uidHex}"
                                status.tagType?.takeIf { it.isNotBlank() }?.let { pieces += "类型：$it" }
                                if (door.isNotEmpty()) {
                                    pieces += "门禁号：$door"
                                }
                                if (status.counterSupported) {
                                    val counterText = if (status.counterEnabled) "计数器：已启用" else "计数器：未启用"
                                    pieces += counterText
                                } else if (lastEnableCounterRequest) {
                                    pieces += "计数器：不支持"
                                }
                                Text(
                                    "✅ 写卡成功 (${pieces.joinToString(" / ")})",
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (status.counterSupported && !status.counterEnabled) {
                                    Text(
                                        "提示：请确认标签支持并已启用 NFC 计数器",
                                        color = MaterialTheme.colorScheme.tertiary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
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
                        InfoLine("标签类型", lastTagType.takeIf { it.isNotEmpty() })
                        when (lastCounterSupported) {
                            true -> {
                                val counterText = lastCounter?.toString() ?: "读取失败"
                                InfoLine("NFC 计数器", counterText)
                                lastCounterEnabled?.let { enabled ->
                                    Text(
                                        if (enabled) "计数器已启用" else "计数器未启用",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            false -> {
                                if (lastTagType.isNotEmpty()) {
                                    Text(
                                        "该标签不支持 NFC 计数器",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }

                            null -> {}
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("验证结果：")
                            Spacer(Modifier.width(6.dp))
                            when (verifyOk) {
                                true -> {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "真", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("真", color = MaterialTheme.colorScheme.primary)
                                }
                                false -> {
                                    Icon(Icons.Filled.Close, contentDescription = "假", tint = MaterialTheme.colorScheme.error)
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
                            .padding(12.dp)
                    ) {
                        if (people.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无人员，请点击下方按钮添加", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(people) { person ->
                                    PersonRow(person = person, onClick = { selectedPerson = person })
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
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

        if (showAddDialog) {
            AddPersonDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { person ->
                    people.add(person)
                    showAddDialog = false
                }
            )
        }

        selectedPerson?.let { person ->
            PersonDetailDialog(
                person = person,
                onDismiss = { selectedPerson = null },
                onFillWrite = {
                    name = it.name
                    phone = it.phone
                    emailPrefix = it.email.substringBefore('@')
                    selectedTab = 0
                    selectedPerson = null
                }
            )
        }
    }
}

@Composable
private fun PersonRow(person: Person, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "${person.name} (${person.gender.label})",
                style = MaterialTheme.typography.titleMedium
            )
            Text("身份证：${person.idNumber}", style = MaterialTheme.typography.bodySmall)
            if (person.birthDate.isNotEmpty()) {
                Text("出生日期：${person.birthDate}", style = MaterialTheme.typography.bodySmall)
            }
            Text("手机号：${person.phone}", style = MaterialTheme.typography.bodySmall)
            Text("邮箱：${person.email}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AddPersonDialog(
    onDismiss: () -> Unit,
    onConfirm: (Person) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<Gender?>(null) }

    val birthDate = parseBirthDateFromId(idNumber)
    val canSubmit = name.isNotBlank() && idNumber.isNotBlank() && phone.isNotBlank() && email.isNotBlank() && gender != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加人员") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = idNumber,
                    onValueChange = {
                        idNumber = it
                        if (gender == null) {
                            deriveGenderFromId(it)?.let { derived -> gender = derived }
                        }
                    },
                    label = { Text("身份证号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (birthDate.isNotEmpty()) "出生日期：$birthDate" else "出生日期：自动识别失败",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("性别", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gender.values().forEach { option ->
                        FilterChip(
                            selected = gender == option,
                            onClick = { gender = option },
                            label = { Text(option.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("手机号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalGender = gender ?: return@TextButton
                    onConfirm(
                        Person(
                            name = name.trim(),
                            gender = finalGender,
                            idNumber = idNumber.trim(),
                            birthDate = parseBirthDateFromId(idNumber.trim()),
                            phone = phone.trim(),
                            email = email.trim()
                        )
                    )
                },
                enabled = canSubmit
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun PersonDetailDialog(
    person: Person,
    onDismiss: () -> Unit,
    onFillWrite: (Person) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("人员详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("姓名：${person.name}", style = MaterialTheme.typography.bodyMedium)
                Text("性别：${person.gender.label}", style = MaterialTheme.typography.bodyMedium)
                Text("身份证号：${person.idNumber}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "出生日期：${person.birthDate.ifEmpty { "-" }}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("手机号：${person.phone}", style = MaterialTheme.typography.bodyMedium)
                Text("邮箱：${person.email}", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = { onFillWrite(person) }) { Text("填入写卡") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

private data class Person(
    val name: String,
    val gender: Gender,
    val idNumber: String,
    val birthDate: String,
    val phone: String,
    val email: String
)

private enum class Gender(val label: String) {
    Male("男"),
    Female("女")
}

private fun parseBirthDateFromId(idNumber: String): String {
    val clean = idNumber.trim()
    return when {
        clean.length >= 18 -> {
            val birth = clean.substring(6, 14)
            if (birth.all { it.isDigit() }) {
                val year = birth.substring(0, 4)
                val month = birth.substring(4, 6)
                val day = birth.substring(6, 8)
                "$year-$month-$day"
            } else {
                ""
            }
        }

        clean.length == 15 -> {
            val birth = clean.substring(6, 12)
            if (birth.all { it.isDigit() }) {
                val year = "19${birth.substring(0, 2)}"
                val month = birth.substring(2, 4)
                val day = birth.substring(4, 6)
                "$year-$month-$day"
            } else {
                ""
            }
        }

        else -> ""
    }
}

private fun deriveGenderFromId(idNumber: String): Gender? {
    val clean = idNumber.trim()
    val genderChar = when {
        clean.length >= 17 -> clean[16]
        clean.length == 15 -> clean[14]
        else -> return null
    }
    val digit = genderChar.digitToIntOrNull() ?: return null
    return if (digit % 2 == 0) Gender.Female else Gender.Male
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
