package com.corn.manageapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

import com.corn.manageapp.ui.theme.MyApplicationTheme
import com.corn.manageapp.ui.*
import com.corn.manageapp.data.*
import com.corn.manageapp.network.HardwareItem
import com.corn.manageapp.utils.VCardSigner

import java.util.Locale

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Filled.Home),
    PEOPLE("人员管理", Icons.Filled.Person),
    INVENTORY("机房管理", Icons.Filled.ShoppingCart),
    SETTINGS("设置", Icons.Filled.Settings),
    INVENTORY_QUERY("查询服务器", Icons.Filled.ShoppingCart),
    SETTINGS_DCIM("DCIM配置", Icons.Filled.Settings),
    SETTINGS_KEYS("密钥管理", Icons.Filled.Settings),
    DETAIL("设备详情", Icons.Filled.ShoppingCart)
}

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    /** ✅ 等待写卡的数据 */
    private var pendingWrite: PendingWrite? = null

    /** ✅ 写卡 / 读卡回调（通知 UI 状态） */
    private var onTagWriteCallback: ((NfcWriteResult) -> Unit)? = null
    private var onTagReadCallback: ((NfcReadResult) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        val dcimRepo = DcimConfigRepository(this)
        setContent {
            MyApplicationTheme {
                var current by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
                var detailItem by remember { mutableStateOf<HardwareItem?>(null) }

                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        listOf(
                            AppDestinations.HOME,
                            AppDestinations.PEOPLE,
                            AppDestinations.INVENTORY,
                            AppDestinations.SETTINGS
                        ).forEach { item ->
                            item(
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label) },
                                selected = current == item,
                                onClick = { current = item }
                            )
                        }
                    }
                ) {
                    Scaffold { inner ->
                        when (current) {
                            AppDestinations.HOME ->
                                Greeting("COMCORN Cloud", Modifier.padding(inner))

                            /** ✅ 人员管理：新版签名/验证接口 */
                            AppDestinations.PEOPLE ->
                                PeopleManagementScreen(
                                    modifier = Modifier.padding(inner),
                                    onWriteRequest = { name, phone, emailPrefix, enableCounter ->
                                        // 由 UI 触发，开始等待用户贴卡
                                        pendingWrite = PendingWrite(name, phone, emailPrefix, enableCounter)
                                        onTagWriteCallback?.invoke(
                                            NfcWriteResult.Waiting
                                        )
                                    },
                                    onWriteStatus = { cb -> onTagWriteCallback = cb },
                                    onTagRead = { cb -> onTagReadCallback = cb }
                                )

                            AppDestinations.INVENTORY ->
                                InventoryScreen(
                                    modifier = Modifier.padding(inner),
                                    onOpenQueryServer = { current = AppDestinations.INVENTORY_QUERY }
                                )

                            AppDestinations.INVENTORY_QUERY ->
                                QueryServerScreen(
                                    repo = dcimRepo,
                                    onBack = { current = AppDestinations.INVENTORY },
                                    onOpenDetail = { item ->
                                        detailItem = item
                                        current = AppDestinations.DETAIL
                                    }
                                )

                            AppDestinations.SETTINGS ->
                                SettingsScreen(
                                    modifier = Modifier.padding(inner),
                                    onOpenDcim = { current = AppDestinations.SETTINGS_DCIM },
                                    onOpenKeyManager = { current = AppDestinations.SETTINGS_KEYS }
                                )

                            AppDestinations.SETTINGS_DCIM ->
                                DcimSettingsScreen(
                                    repo = dcimRepo,
                                    onBack = { current = AppDestinations.SETTINGS }
                                )

                            AppDestinations.SETTINGS_KEYS ->
                                PublicKeyManagementScreen(
                                    modifier = Modifier.padding(inner),
                                    onBack = { current = AppDestinations.SETTINGS }
                                )

                            AppDestinations.DETAIL ->
                                DeviceDetailScreen(
                                    item = detailItem ?: HardwareItem(),
                                    onBack = { current = AppDestinations.INVENTORY_QUERY }
                                )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)

        // ✅ 统一取 UID（HEX 大写）
        val uidHex = tag?.id?.joinToString("") { "%02X".format(it) } ?: ""

        // ✅ 写卡：如果有待写入信息，则对 UID 做 Ed25519 签名并生成 vCard 写入
        pendingWrite?.let { request ->
            val (name, phone, emailPrefix, enableCounter) = request
            val tagObj = tag
            val vcard = VCardSigner.buildSignedVCard(
                context = this,
                fullName = name,
                tel = phone,
                email = "$emailPrefix@comcorn.cn",
                uidHex = uidHex
            )

            val result = when {
                tagObj == null -> NfcWriteResult.Failure("❌ 写入失败：未识别到 NFC 卡片")
                vcard == null -> NfcWriteResult.Failure("❌ 写入失败：请先在密钥管理中配置公私钥")
                else -> writeNfcTag(tagObj, vcard, uidHex, enableCounter)
            }

            pendingWrite = null
            onTagWriteCallback?.invoke(result)
            return
        }

        // ✅ 读卡：读取全部 NDEF 文本，回传给 UI（携带 UID）
        tag?.let { t ->
            val ndef = Ndef.get(t)
            if (ndef != null) {
                try {
                    ndef.connect()
                    val msg = ndef.ndefMessage
                    ndef.close()
                    val sb = StringBuilder()
                    msg?.records?.forEach { rec ->
                        sb.append(rec.toDecodedText())
                    }
                    val (counter, tagType) = readNtagCounter(t)
                    onTagReadCallback?.invoke(
                        NfcReadResult(
                            uidHex = uidHex,
                            vcard = sb.toString(),
                            counter = counter,
                            tagType = tagType
                        )
                    )
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun writeNfcTag(tag: Tag, data: String, uidHex: String, enableCounter: Boolean): NfcWriteResult {
        return try {
            val ndef = Ndef.get(tag)
            val msg = NdefMessage(
                arrayOf(NdefRecord.createTextRecord(Locale.CHINA.language, data))
            )
            if (ndef != null) {
                ndef.connect()
                return if (ndef.isWritable) {
                    ndef.writeNdefMessage(msg)
                    ndef.close()
                    val (counterConfigured, tagType) = configureNtagCounter(tag, enableCounter)
                    NfcWriteResult.Success(uidHex, counterConfigured, tagType)
                } else {
                    ndef.close()
                    NfcWriteResult.Failure("❌ 写入失败：卡片为只读")
                }
            } else {
                val formatable = NdefFormatable.get(tag)
                formatable?.connect()
                return if (formatable != null) {
                    formatable.format(msg)
                    formatable.close()
                    val (counterConfigured, tagType) = configureNtagCounter(tag, enableCounter)
                    NfcWriteResult.Success(uidHex, counterConfigured, tagType)
                } else {
                    NfcWriteResult.Failure("❌ 写入失败：不支持 NDEF")
                }
            }
        } catch (e: Exception) {
            NfcWriteResult.Failure("❌ 写入失败：${e.message ?: "未知错误"}")
        }
    }

    private fun configureNtagCounter(tag: Tag, enable: Boolean): Pair<Boolean?, String?> {
        val mifare = MifareUltralight.get(tag) ?: return (if (enable) false else null) to null
        var configured: Boolean? = if (enable) false else null
        var label: String? = null
        try {
            mifare.connect()
            label = mifare.type.toNtagLabel()
            if (!enable) {
                configured = null
            } else {
                val configPage = when (mifare.type) {
                    MifareUltralight.TYPE_NTAG213 -> 0x29
                    MifareUltralight.TYPE_NTAG215 -> 0x2A
                    MifareUltralight.TYPE_NTAG216 -> 0x2A
                    else -> null
                }
                if (configPage != null) {
                    val raw = mifare.readPages(configPage)
                    if (raw.size >= 4) {
                        val page = raw.copyOfRange(0, 4)
                        val current = page[0].toInt() and 0xFF
                        val desired = current or 0x04 // 启用计数器位
                        if (desired != current) {
                            page[0] = desired.toByte()
                            mifare.writePage(configPage, page)
                        }
                        configured = true
                    }
                }
            }
        } catch (_: Exception) {
            if (enable) configured = false
        } finally {
            try {
                mifare.close()
            } catch (_: Exception) {
            }
        }
        return configured to label
    }

    private fun readNtagCounter(tag: Tag): Pair<Int?, String?> {
        val mifare = MifareUltralight.get(tag) ?: return null to null
        var counter: Int? = null
        var label: String? = null
        try {
            mifare.connect()
            val type = mifare.type
            label = type.toNtagLabel()
            if (type == MifareUltralight.TYPE_NTAG213 || type == MifareUltralight.TYPE_NTAG215 || type == MifareUltralight.TYPE_NTAG216) {
                val response = mifare.transceive(byteArrayOf(0x39.toByte(), 0x02))
                if (response.size >= 3) {
                    counter = ((response[0].toInt() and 0xFF) shl 16) or
                        ((response[1].toInt() and 0xFF) shl 8) or
                        (response[2].toInt() and 0xFF)
                }
            }
        } catch (_: Exception) {
            counter = null
        } finally {
            try {
                mifare.close()
            } catch (_: Exception) {
            }
        }
        return counter to label
    }
}

private fun NdefRecord.toDecodedText(): String {
    return try {
        if (tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_TEXT)) {
            val payload = payload
            if (payload.isEmpty()) return ""
            val status = payload[0].toInt()
            val isUtf16 = (status and 0x80) != 0
            val langLength = status and 0x3F
            val textEncoding = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
            val text = payload.copyOfRange(1 + langLength, payload.size)
            String(text, textEncoding)
        } else {
            String(payload, Charsets.UTF_8)
        }
    } catch (_: Exception) {
        ""
    }
}

/** ✅ 读取回调数据模型：UID + vCard 原文 */
data class NfcReadResult(
    val uidHex: String,
    val vcard: String,
    val counter: Int? = null,
    val tagType: String? = null
)

sealed class NfcWriteResult {
    data object Waiting : NfcWriteResult()
    data class Success(val uidHex: String, val counterConfigured: Boolean?, val tagType: String?) : NfcWriteResult()
    data class Failure(val reason: String) : NfcWriteResult()
}

private data class PendingWrite(
    val name: String,
    val phone: String,
    val emailPrefix: String,
    val enableCounter: Boolean
)

private fun Int.toNtagLabel(): String? = when (this) {
    MifareUltralight.TYPE_NTAG213 -> "NTAG213"
    MifareUltralight.TYPE_NTAG215 -> "NTAG215"
    MifareUltralight.TYPE_NTAG216 -> "NTAG216"
    MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight"
    MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
    else -> null
}

@Composable
fun InventoryScreen(
    modifier: Modifier = Modifier,
    onOpenQueryServer: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("机房管理", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenQueryServer, modifier = Modifier.fillMaxWidth()) {
            Text("查询服务器")
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenDcim: () -> Unit,
    onOpenKeyManager: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        Button(onOpenDcim, Modifier.fillMaxWidth()) { Text("DCIM 配置") }
        Spacer(Modifier.height(12.dp))
        Button(onOpenKeyManager, Modifier.fillMaxWidth()) { Text("公私钥管理") }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    Box(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        contentAlignment = Alignment.Center
    ) {
        Text("Welcome to $name!", style = MaterialTheme.typography.titleLarge)
    }
}
