package com.corn.manageapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Stable
class PeopleManagementState {
    var name by mutableStateOf("")
    var phone by mutableStateOf("")
    var emailPrefix by mutableStateOf("")
    var enableCounter by mutableStateOf(true)

    var lastUid by mutableStateOf("")
    var lastDoorNum by mutableStateOf("")
    var verifyOk by mutableStateOf<Boolean?>(null)
    var hasNoteSignature by mutableStateOf<Boolean?>(null)
    var parsedLines by mutableStateOf<List<String>>(emptyList())
    var lastCounter by mutableStateOf<Int?>(null)
    var writeStatus by mutableStateOf<NfcWriteResult?>(null)
    var lastVcard by mutableStateOf("")
    var upgradeStatus by mutableStateOf<NfcWriteResult?>(null)

    val people = mutableStateListOf<Person>()
    var isAddingPerson by mutableStateOf(false)
    var detailPerson by mutableStateOf<Person?>(null)
}

@Composable
fun rememberPeopleManagementState(): PeopleManagementState {
    return remember { PeopleManagementState() }
}

@Composable
fun PeopleManagementMenu(
    modifier: Modifier = Modifier,
    state: PeopleManagementState,
    onOpenWrite: () -> Unit,
    onOpenRead: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenAccess: () -> Unit,
    onWriteStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onTagRead: ((NfcReadResult) -> Unit) -> Unit,
    onUpgradeStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onNavigateToRead: () -> Unit
) {
    PeopleManagementBinding(state, onWriteStatus, onTagRead, onUpgradeStatus, onNavigateToRead)

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onOpenWrite, modifier = Modifier.fillMaxWidth()) {
            Text("写入门禁卡")
        }
        Button(onClick = onOpenRead, modifier = Modifier.fillMaxWidth()) {
            Text("读取门禁卡")
        }
        Button(onClick = onOpenPeople, modifier = Modifier.fillMaxWidth()) {
            Text("人员信息")
        }
        Button(onClick = onOpenAccess, modifier = Modifier.fillMaxWidth()) {
            Text("门禁管理")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleWriteScreen(
    modifier: Modifier = Modifier,
    state: PeopleManagementState,
    onBack: () -> Unit,
    onWriteRequest: (String, String, String, Boolean) -> Unit,
    onWriteStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onTagRead: ((NfcReadResult) -> Unit) -> Unit,
    onUpgradeStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onNavigateToRead: () -> Unit
) {
    PeopleManagementBinding(state, onWriteStatus, onTagRead, onUpgradeStatus, onNavigateToRead)

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onBack) { Text("← 返回") }

        OutlinedTextField(
            value = state.name,
            onValueChange = { state.name = it },
            label = { Text("姓名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.phone,
            onValueChange = { state.phone = it },
            label = { Text("手机号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.emailPrefix,
            onValueChange = { state.emailPrefix = it },
            label = { Text("邮箱前半部分（如：john）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = state.enableCounter,
                onCheckedChange = { state.enableCounter = it }
            )
            Spacer(Modifier.width(4.dp))
            Text("写卡时开启 NFC 计数器", style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = {
                val n = state.name.trim()
                val p = state.phone.trim()
                val e = state.emailPrefix.trim()
                if (n.isEmpty() || p.isEmpty() || e.isEmpty()) return@Button
                onWriteRequest(n, p, e, state.enableCounter)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.writeStatus !is NfcWriteResult.Waiting
        ) {
            Text(if (state.writeStatus is NfcWriteResult.Waiting) "请贴卡" else "写入 NFC")
        }

        val preview = buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:${state.name.ifBlank { "张三" }}")
            appendLine("ORG:COMCORN")
            appendLine("EMAIL:${(state.emailPrefix.ifBlank { "john" })}@comcorn.cn")
            appendLine("TEL:${state.phone.ifBlank { "13800000000" }}")
            appendLine("NOTE:(刷卡写入时生成 UID 签名)")
            appendLine("END:VCARD")
        }

        Text("预览", style = MaterialTheme.typography.titleSmall)
        Surface(tonalElevation = 1.dp) {
            Text(preview, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp))
        }

        when (val status = state.writeStatus) {
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
                        status.tagType?.let {
                            append(" 标签：")
                            append(it)
                        }
                        if (status.counterEnabled) {
                            append("（计数器已启用）")
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

@Composable
fun PeopleReadScreen(
    modifier: Modifier = Modifier,
    state: PeopleManagementState,
    onBack: () -> Unit,
    onUpgradeRequest: (String, String) -> Unit,
    onWriteStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onTagRead: ((NfcReadResult) -> Unit) -> Unit,
    onUpgradeStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onNavigateToRead: () -> Unit
) {
    PeopleManagementBinding(state, onWriteStatus, onTagRead, onUpgradeStatus, onNavigateToRead)

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onBack) { Text("← 返回") }

        InfoLine("卡片 UID", state.lastUid.takeIf { it.isNotEmpty() })
        InfoLine("门禁卡号（10位）", state.lastDoorNum.takeIf { it.isNotEmpty() })
        CounterInfo(state.lastCounter)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("验证结果：")
            Spacer(Modifier.width(6.dp))
            when (state.verifyOk) {
                true -> {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "真", tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("真", color = MaterialTheme.colorScheme.primary)
                }
                false -> {
                    Icon(Icons.Filled.Close, contentDescription = "假", tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    val msg = if (state.hasNoteSignature == false) "假（缺少 NOTE 签名）" else "假"
                    Text(msg, color = MaterialTheme.colorScheme.error)
                }
                null -> {
                    Text("（请刷卡）")
                }
            }
        }

        if (state.parsedLines.isNotEmpty()) {
            Divider()
            state.parsedLines.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text("请贴卡读取信息", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        val canUpgrade = state.lastUid.isNotEmpty() && state.lastVcard.isNotEmpty()
        Button(
            onClick = { if (canUpgrade) onUpgradeRequest(state.lastUid, state.lastVcard) },
            modifier = Modifier.fillMaxWidth(),
            enabled = canUpgrade && state.upgradeStatus !is NfcWriteResult.Waiting
        ) {
            Text("一键升级到新版本门卡")
        }

        when (val status = state.upgradeStatus) {
            NfcWriteResult.Waiting -> {
                Text(
                    "请再次贴卡完成升级…",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            is NfcWriteResult.Success -> {
                Text(
                    buildString {
                        append("✅ 升级完成 (UID: ")
                        append(status.uidHex)
                        append(")")
                        if (state.lastDoorNum.isNotEmpty()) {
                            append(" 门禁号：")
                            append(state.lastDoorNum)
                        }
                    },
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            is NfcWriteResult.Failure -> {
                Text(
                    status.reason,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            null -> {}
        }
    }
}

@Composable
fun PeopleListScreen(
    modifier: Modifier = Modifier,
    state: PeopleManagementState,
    onBack: () -> Unit,
    onOpenWrite: () -> Unit,
    onWriteStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onTagRead: ((NfcReadResult) -> Unit) -> Unit,
    onUpgradeStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onNavigateToRead: () -> Unit
) {
    PeopleManagementBinding(state, onWriteStatus, onTagRead, onUpgradeStatus, onNavigateToRead)

    val scrollState = rememberScrollState()
    if (state.isAddingPerson) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            TextButton(onClick = { state.isAddingPerson = false }) { Text("← 返回") }
            Spacer(Modifier.height(12.dp))
            AddPersonPage(
                onCancel = { state.isAddingPerson = false },
                onSave = { person ->
                    state.people.add(person)
                    state.isAddingPerson = false
                }
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.height(12.dp))

            if (state.people.isEmpty()) {
                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("暂无人员，请点击下方按钮添加", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.people.forEach { person ->
                        PersonCard(person = person, onClick = { state.detailPerson = person })
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { state.isAddingPerson = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("添加人员")
            }
        }
    }

    state.detailPerson?.let { person ->
        PersonDetailDialog(
            person = person,
            onDismiss = { state.detailPerson = null },
            onFillWrite = {
                state.name = person.name
                state.phone = person.phone
                state.emailPrefix = person.email.substringBefore('@')
                state.detailPerson = null
                onOpenWrite()
            }
        )
    }
}

@Composable
fun PeopleAccessScreen(
    modifier: Modifier = Modifier,
    state: PeopleManagementState,
    onBack: () -> Unit,
    onWriteStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onTagRead: ((NfcReadResult) -> Unit) -> Unit,
    onUpgradeStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onNavigateToRead: () -> Unit
) {
    PeopleManagementBinding(state, onWriteStatus, onTagRead, onUpgradeStatus, onNavigateToRead)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(onClick = onBack) { Text("← 返回") }
        Text("门禁管理（可扩展）", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PeopleManagementBinding(
    state: PeopleManagementState,
    onWriteStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onTagRead: ((NfcReadResult) -> Unit) -> Unit,
    onUpgradeStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onNavigateToRead: () -> Unit
) {
    val ctx = LocalContext.current
    LaunchedEffect(onWriteStatus, onTagRead, onUpgradeStatus) {
        onWriteStatus { status ->
            state.writeStatus = status
            if (status is NfcWriteResult.Success) {
                Toast.makeText(ctx, "写卡成功", Toast.LENGTH_SHORT).show()
            }
        }
        onUpgradeStatus { status ->
            state.upgradeStatus = status
            if (status is NfcWriteResult.Success) {
                Toast.makeText(ctx, "升级完成", Toast.LENGTH_SHORT).show()
            }
        }
        onTagRead { res ->
            state.lastUid = res.uidHex
            state.lastDoorNum = calcDoorNum10(res.uidHex)
            val noteLine = res.vcard.lines().firstOrNull { it.startsWith("NOTE:", ignoreCase = true) }
                ?.substringAfter("NOTE:", "")
                ?.trim()
                ?: ""
            val noteExists = noteLine.isNotEmpty()
            state.hasNoteSignature = noteExists
            state.verifyOk = if (noteExists) {
                VCardVerifier.verifyNoteWithStoredPublic(ctx, res.uidHex, noteLine)
            } else false
            state.parsedLines = parseVCard(res.vcard)
            state.lastCounter = res.nfcCounter
            state.lastVcard = res.vcard
            state.upgradeStatus = null
            onNavigateToRead()
        }
    }
}

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
private fun CounterInfo(counter: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(onClick = {}, label = { Text("NFC 计数器") })
        Spacer(Modifier.width(8.dp))
        if (counter != null) {
            Text(counter.toString(), style = MaterialTheme.typography.titleMedium)
        } else {
            Text(
                "未启用或不支持",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

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

private fun calcDoorNum10(uidHex: String): String {
    val clean = uidHex.replace("[^0-9A-Fa-f]".toRegex(), "")
    if (clean.length < 8) return ""
    val first4 = clean.substring(0, 8)
    val value = first4.toULong(16).toLong()
    return value.toString().padStart(10, '0')
}

@Composable
private fun PersonCard(person: Person, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("${person.name} (${person.gender})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("身份证：${person.idNumber}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(2.dp))
            Text("出生日期：${person.birthDate}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(2.dp))
            Text("手机号：${person.phone}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(2.dp))
            Text("邮箱：${person.email}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AddPersonPage(
    onCancel: () -> Unit,
    onSave: (Person) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("男") }
    var idNumber by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }

    val birthDate = remember(idNumber) { extractBirthDate(idNumber) }
    val isValid = name.isNotBlank() && phone.isNotBlank() && email.isNotBlank() && birthDate.isNotEmpty()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("添加人员", style = MaterialTheme.typography.titleMedium)
        Text("请填写人员信息，保存后将出现在人员列表中。", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("姓名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("性别：")
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = gender == "男",
                onClick = { gender = "男" },
                label = { Text("男") },
                leadingIcon = if (gender == "男") {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                } else null
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = gender == "女",
                onClick = { gender = "女" },
                label = { Text("女") },
                leadingIcon = if (gender == "女") {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                } else null
            )
        }

        OutlinedTextField(
            value = idNumber,
            onValueChange = { idNumber = it },
            label = { Text("身份证号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (idNumber.isNotBlank()) {
            Text(
                if (birthDate.isNotEmpty()) "出生日期：$birthDate" else "身份证号格式不正确，无法解析出生日期",
                style = MaterialTheme.typography.bodySmall,
                color = if (birthDate.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
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

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = {
                if (isValid) {
                    onSave(
                        Person(
                            name = name.trim(),
                            gender = gender,
                            idNumber = idNumber.trim(),
                            birthDate = birthDate,
                            phone = phone.trim(),
                            email = email.trim()
                        )
                    )
                }
            }, enabled = isValid) {
                Text("保存")
            }
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun PersonDetailDialog(person: Person, onDismiss: () -> Unit, onFillWrite: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onFillWrite) {
                Text("填入写卡页面")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        title = { Text("人员详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("姓名", person.name)
                DetailLine("性别", person.gender)
                DetailLine("身份证号", person.idNumber)
                DetailLine("出生日期", person.birthDate)
                DetailLine("手机号", person.phone)
                DetailLine("邮箱", person.email)
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private data class Person(
    val name: String,
    val gender: String,
    val idNumber: String,
    val birthDate: String,
    val phone: String,
    val email: String
)

private fun extractBirthDate(idNumber: String): String {
    val clean = idNumber.trim()
    if (clean.length == 18) {
        val dateStr = clean.substring(6, 14)
        return parseDate(dateStr)
    }
    if (clean.length == 15) {
        val dateStr = "19${clean.substring(6, 12)}"
        return parseDate(dateStr)
    }
    return ""
}

private fun parseDate(raw: String): String {
    if (!raw.matches(Regex("\\d{8}"))) return ""
    val parser = SimpleDateFormat("yyyyMMdd", Locale.CHINA).apply { isLenient = false }
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    return try {
        val date = parser.parse(raw)
        if (date != null) formatter.format(date) else ""
    } catch (_: ParseException) {
        ""
    }
}
