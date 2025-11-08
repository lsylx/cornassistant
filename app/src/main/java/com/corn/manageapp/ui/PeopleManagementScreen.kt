package com.corn.manageapp.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.corn.manageapp.NfcReadResult
import com.corn.manageapp.NfcWriteResult
import com.corn.manageapp.data.PeopleRepository
import com.corn.manageapp.data.Person
import com.corn.manageapp.util.AvatarStorage
import com.corn.manageapp.utils.VCardSigner
import com.corn.manageapp.utils.VCardVerifier
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 人员管理（离线签名版）
 * - 写入：仅收集 name/phone/emailPrefix，实际写卡在 MainActivity 中完成（使用私钥签名 UID）
 * - 读取：收到 UID + vCard，解析 NOTE 验真伪、显示 UID 和 10位门禁卡号
 */
private enum class PeoplePage { MENU, WRITE, READ, PEOPLE, ACCESS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleManagementScreen(
    modifier: Modifier = Modifier,
    peopleRepository: PeopleRepository,
    onWriteRequest: (String, String, String, Boolean) -> Unit,
    onWriteStatus: ((NfcWriteResult) -> Unit) -> Unit,
    onTagRead: ((NfcReadResult) -> Unit) -> Unit,
    onUpgradeRequest: (String, String) -> Unit,
    onUpgradeStatus: ((NfcWriteResult) -> Unit) -> Unit
) {
    val ctx = LocalContext.current

    var currentPage by rememberSaveable { mutableStateOf(PeoplePage.MENU) }

    // 写入表单
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var emailPrefix by rememberSaveable { mutableStateOf("") }
    var enableCounter by rememberSaveable { mutableStateOf(true) }

    // 读取结果
    var lastUid by remember { mutableStateOf("") }
    var lastDoorNum by remember { mutableStateOf("") }
    var verifyOk by remember { mutableStateOf<Boolean?>(null) }
    var hasNoteSignature by remember { mutableStateOf<Boolean?>(null) }
    var parsedLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastCounter by remember { mutableStateOf<Int?>(null) }
    var writeStatus by remember { mutableStateOf<NfcWriteResult?>(null) }
    var lastVcard by remember { mutableStateOf("") }
    var upgradeStatus by remember { mutableStateOf<NfcWriteResult?>(null) }
    var localUpgradeError by remember { mutableStateOf<String?>(null) }

    // 人员管理
    val people by peopleRepository.peopleFlow.collectAsState(initial = emptyList())
    var isAddingPerson by remember { mutableStateOf(false) }
    var detailPerson by remember { mutableStateOf<Person?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var matchedPerson by remember { mutableStateOf<Person?>(null) }
    var hasReadCard by remember { mutableStateOf(false) }
    val currentPeople by rememberUpdatedState(people)

    // 注册 NFC 读取回调（组件进入时）
    LaunchedEffect(Unit) {
        onWriteStatus { status ->
            writeStatus = status
        }
        onUpgradeStatus { status ->
            upgradeStatus = status
        }
        onTagRead { res ->
            hasReadCard = true
            lastUid = res.uidHex
            lastDoorNum = calcDoorNum10(res.uidHex)
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
            lastCounter = res.nfcCounter
            lastVcard = res.vcard
            upgradeStatus = null
            localUpgradeError = null
            matchedPerson = findMatchingPerson(res.vcard, currentPeople)
            currentPage = PeoplePage.READ
        }
    }

    LaunchedEffect(writeStatus) {
        when (val status = writeStatus) {
            NfcWriteResult.Waiting -> currentPage = PeoplePage.WRITE
            is NfcWriteResult.Success -> {
                currentPage = PeoplePage.WRITE
                Toast.makeText(ctx, "写入完成", Toast.LENGTH_SHORT).show()
            }
            is NfcWriteResult.Failure -> currentPage = PeoplePage.WRITE
            null -> {}
        }
    }

    LaunchedEffect(upgradeStatus) {
        when (val status = upgradeStatus) {
            NfcWriteResult.Waiting -> currentPage = PeoplePage.READ
            is NfcWriteResult.Success -> {
                currentPage = PeoplePage.READ
                Toast.makeText(ctx, "升级完成", Toast.LENGTH_SHORT).show()
            }
            is NfcWriteResult.Failure -> currentPage = PeoplePage.READ
            null -> {}
        }
    }
    LaunchedEffect(people) {
        detailPerson?.let { current ->
            detailPerson = people.firstOrNull { it.id == current.id }
        }
        matchedPerson?.let { current ->
            matchedPerson = people.firstOrNull { it.id == current.id }
        }
        if (matchedPerson == null && lastVcard.isNotEmpty()) {
            matchedPerson = findMatchingPerson(lastVcard, people)
        }
    }

    val menuScrollState = rememberScrollState()
    val writeScrollState = rememberScrollState()
    val readScrollState = rememberScrollState()
    val peopleScrollState = rememberScrollState()
    val accessScrollState = rememberScrollState()

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        when (currentPage) {
            PeoplePage.MENU -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(menuScrollState)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { currentPage = PeoplePage.WRITE }, modifier = Modifier.fillMaxWidth()) {
                        Text("写入")
                    }
                    Button(onClick = { currentPage = PeoplePage.READ }, modifier = Modifier.fillMaxWidth()) {
                        Text("读取")
                    }
                    Button(
                        onClick = {
                            currentPage = PeoplePage.PEOPLE
                            detailPerson = null
                            isAddingPerson = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("人员")
                    }
                    Button(onClick = { currentPage = PeoplePage.ACCESS }, modifier = Modifier.fillMaxWidth()) {
                        Text("门禁")
                    }

                    if (writeStatus is NfcWriteResult.Success) {
                        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("最近写卡成功", style = MaterialTheme.typography.titleSmall)
                                Text("UID：${(writeStatus as NfcWriteResult.Success).uidHex}", style = MaterialTheme.typography.bodySmall)
                                val door = calcDoorNum10((writeStatus as NfcWriteResult.Success).uidHex)
                                if (door.isNotEmpty()) {
                                    Text("门禁号：$door", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    if (lastUid.isNotEmpty() || lastDoorNum.isNotEmpty()) {
                        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                if (lastUid.isNotEmpty()) {
                                    Text("最近读取 UID：$lastUid", style = MaterialTheme.typography.bodySmall)
                                }
                                if (lastDoorNum.isNotEmpty()) {
                                    Text("门禁号：$lastDoorNum", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            PeoplePage.WRITE -> {
                SubPageContainer(scrollState = writeScrollState, onBack = {
                    currentPage = PeoplePage.MENU
                }) {
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
                        Spacer(Modifier.width(4.dp))
                        Text("写卡时开启 NFC 计数器", style = MaterialTheme.typography.bodyMedium)
                    }

                    Button(
                        onClick = {
                            val n = name.trim()
                            val p = phone.trim()
                            val e = emailPrefix.trim()
                            if (n.isEmpty() || p.isEmpty() || e.isEmpty()) return@Button
                            onWriteRequest(n, p, e, enableCounter)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = writeStatus !is NfcWriteResult.Waiting
                    ) {
                        Text(if (writeStatus is NfcWriteResult.Waiting) "请贴卡" else "写入 NFC")
                    }

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

            PeoplePage.READ -> {
                SubPageContainer(scrollState = readScrollState, onBack = {
                    currentPage = PeoplePage.MENU
                }) {
                    Text("读取 vCard 并离线验证", style = MaterialTheme.typography.titleMedium)

                    InfoLine("卡片 UID", lastUid.takeIf { it.isNotEmpty() })
                    InfoLine("门禁卡号（10位）", lastDoorNum.takeIf { it.isNotEmpty() })
                    CounterInfo(lastCounter, hasReadCard)

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

                    if (hasReadCard) {
                        MatchedPersonSection(
                            matchedPerson = matchedPerson,
                            onOpenDetail = { person ->
                                isAddingPerson = false
                                detailPerson = person
                                currentPage = PeoplePage.PEOPLE
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

                    Spacer(Modifier.height(16.dp))

                    val canUpgrade = lastUid.isNotEmpty() && lastVcard.isNotEmpty()
                    Button(
                        onClick = {
                            if (canUpgrade) {
                                val upgraded = VCardSigner.injectSignedNote(ctx, lastVcard, lastUid)
                                if (upgraded == null) {
                                    localUpgradeError = "❌ 无法生成签名，请先配置密钥"
                                } else {
                                    localUpgradeError = null
                                    onUpgradeRequest(lastUid, upgraded)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canUpgrade && upgradeStatus !is NfcWriteResult.Waiting
                    ) {
                        Text("一键升级到新版本门卡")
                    }

                    localUpgradeError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    when (val status = upgradeStatus) {
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
                                    if (lastDoorNum.isNotEmpty()) {
                                        append(" 门禁号：")
                                        append(lastDoorNum)
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

            PeoplePage.PEOPLE -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(peopleScrollState)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = {
                        currentPage = PeoplePage.MENU
                        detailPerson = null
                        isAddingPerson = false
                    }) {
                        Text("← 返回")
                    }

                    when {
                        detailPerson != null -> {
                            PersonDetailPage(
                                modifier = Modifier.fillMaxWidth(),
                                person = detailPerson!!,
                                onBack = { detailPerson = null },
                                onFillWrite = {
                                    detailPerson?.let { person ->
                                        name = person.name
                                        phone = person.phone
                                        emailPrefix = person.email.substringBefore('@')
                                    }
                                    currentPage = PeoplePage.WRITE
                                    detailPerson = null
                                },
                                onDelete = { person ->
                                    coroutineScope.launch {
                                        AvatarStorage.deleteAvatar(person.avatarPath)
                                        peopleRepository.delete(person.id)
                                    }
                                    if (matchedPerson?.id == person.id) {
                                        matchedPerson = null
                                    }
                                    detailPerson = null
                                }
                            )
                        }

                        isAddingPerson -> {
                            AddPersonPage(
                                modifier = Modifier.fillMaxWidth(),
                                onCancel = { isAddingPerson = false },
                                onSave = { person ->
                                    coroutineScope.launch {
                                        peopleRepository.upsert(person)
                                    }
                                    isAddingPerson = false
                                }
                            )
                        }

                        else -> {
                            PersonListPage(
                                modifier = Modifier.fillMaxWidth(),
                                people = people,
                                onAdd = { isAddingPerson = true },
                                onSelect = { person -> detailPerson = person }
                            )
                        }
                    }
                }
            }

            PeoplePage.ACCESS -> {
                SubPageContainer(scrollState = accessScrollState, onBack = {
                    currentPage = PeoplePage.MENU
                }) {
                    Text("门禁管理（可扩展）", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

}

@Composable
private fun SubPageContainer(
    scrollState: ScrollState,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← 返回")
        }
        content()
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
private fun CounterInfo(counter: Int?, hasReadCard: Boolean) {
    if (!hasReadCard) return
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

/* ----------------- 人员管理 ----------------- */

@Composable
private fun PersonAvatarView(
    avatarPath: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, avatarPath) {
        value = AvatarStorage.loadBitmap(context, avatarPath)?.asImageBitmap()
    }
    Surface(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        tonalElevation = 2.dp,
        color = if (imageBitmap == null) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = CircleShape
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.takeIf { it.isNotBlank() }?.take(2) ?: "头像",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PersonCard(person: Person, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonAvatarView(
                avatarPath = person.avatarPath,
                name = person.name,
                size = 56.dp
            )
            Spacer(Modifier.width(16.dp))
            Column {
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
}

@Composable
private fun AddPersonPage(
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onSave: (Person) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("男") }
    var idNumber by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var avatarPath by rememberSaveable { mutableStateOf<String?>(null) }

    val birthDate = remember(idNumber) { extractBirthDate(idNumber) }
    val isValid = name.isNotBlank() && phone.isNotBlank() && email.isNotBlank() && birthDate.isNotEmpty()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("添加人员", style = MaterialTheme.typography.titleMedium)
        Text("请填写人员信息，保存后将出现在人员列表中。", style = MaterialTheme.typography.bodySmall)

        AvatarPicker(
            avatarPath = avatarPath,
            onAvatarChanged = { avatarPath = it },
            displayName = name
        )

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
                            email = email.trim(),
                            avatarPath = avatarPath
                        )
                    )
                }
            }, enabled = isValid) {
                Text("保存")
            }
            TextButton(onClick = {
                scope.launch {
                    AvatarStorage.deleteAvatar(avatarPath)
                    avatarPath = null
                    onCancel()
                }
            }) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun PersonListPage(
    modifier: Modifier = Modifier,
    people: List<Person>,
    onAdd: () -> Unit,
    onSelect: (Person) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("人员列表", style = MaterialTheme.typography.titleMedium)
        if (people.isEmpty()) {
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
                people.forEach { person ->
                    PersonCard(person = person, onClick = { onSelect(person) })
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("添加人员")
        }
    }
}

@Composable
private fun PersonDetailPage(
    modifier: Modifier = Modifier,
    person: Person,
    onBack: () -> Unit,
    onFillWrite: () -> Unit,
    onDelete: (Person) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("人员详情", style = MaterialTheme.typography.titleMedium)
        PersonAvatarView(
            avatarPath = person.avatarPath,
            name = person.name,
            size = 96.dp
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailLine("姓名", person.name)
            DetailLine("性别", person.gender)
            DetailLine("身份证号", person.idNumber)
            DetailLine("出生日期", person.birthDate)
            DetailLine("手机号", person.phone)
            DetailLine("邮箱", person.email)
        }

        Button(onClick = onFillWrite, modifier = Modifier.fillMaxWidth()) {
            Text("填入写卡页面")
        }

        OutlinedButton(
            onClick = { onDelete(person) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("删除人员")
        }

        TextButton(onClick = onBack) {
            Text("返回列表")
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AvatarPicker(
    avatarPath: String?,
    onAvatarChanged: (String?) -> Unit,
    displayName: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                val path = AvatarStorage.saveBitmap(context, bitmap)
                if (path != null) {
                    avatarPath?.let { AvatarStorage.deleteAvatar(it) }
                    onAvatarChanged(path)
                }
            }
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = AvatarStorage.copyFromUri(context, uri)
                if (path != null) {
                    avatarPath?.let { AvatarStorage.deleteAvatar(it) }
                    onAvatarChanged(path)
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PersonAvatarView(
            avatarPath = avatarPath,
            name = displayName,
            size = 88.dp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { takePictureLauncher.launch(null) }) {
                Text("拍照上传")
            }
            OutlinedButton(onClick = { pickImageLauncher.launch("image/*") }) {
                Text("本地选择")
            }
        }
        if (avatarPath != null) {
            TextButton(onClick = {
                scope.launch {
                    AvatarStorage.deleteAvatar(avatarPath)
                    onAvatarChanged(null)
                }
            }) {
                Text("移除头像")
            }
        }
    }
}

@Composable
private fun MatchedPersonSection(
    matchedPerson: Person?,
    onOpenDetail: ((Person) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Divider()
        Text("人员数据库匹配", style = MaterialTheme.typography.titleSmall)
        if (matchedPerson != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PersonAvatarView(
                    avatarPath = matchedPerson.avatarPath,
                    name = matchedPerson.name,
                    size = 64.dp
                )
                Column(Modifier.weight(1f)) {
                    Text(matchedPerson.name, style = MaterialTheme.typography.titleMedium)
                    Text(matchedPerson.phone, style = MaterialTheme.typography.bodySmall)
                    Text(matchedPerson.email, style = MaterialTheme.typography.bodySmall)
                }
                if (onOpenDetail != null) {
                    TextButton(onClick = { onOpenDetail(matchedPerson) }) {
                        Text("查看")
                    }
                }
            }
        } else {
            Text(
                "未在人员数据库中找到匹配信息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

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

private fun findMatchingPerson(vcard: String, people: List<Person>): Person? {
    if (vcard.isBlank() || people.isEmpty()) return null
    val fields = parseVCardFields(vcard)
    val email = fields["EMAIL"]?.lowercase(Locale.getDefault())
    val phone = fields["TEL"]?.normalizedPhone()
    val name = fields["FN"]?.trim()
    return people.firstOrNull { person ->
        val matchEmail = email != null && person.email.lowercase(Locale.getDefault()) == email
        val matchPhone = phone != null && person.phone.normalizedPhone() == phone
        val matchName = name != null && person.name.equals(name, ignoreCase = true)
        matchEmail || matchPhone || matchName
    }
}

private fun parseVCardFields(vcard: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val lines = vcard.replace("\r\n", "\n").split("\n")
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        val parts = trimmed.split(':', limit = 2)
        if (parts.size == 2) {
            val key = parts[0].substringBefore(';').uppercase(Locale.getDefault())
            val value = parts[1].trim()
            map[key] = value
        }
    }
    return map
}

private fun String.normalizedPhone(): String {
    return filter { it.isDigit() }
}
