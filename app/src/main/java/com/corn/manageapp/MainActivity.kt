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
import android.nfc.tech.NfcA
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

    /** ✅ 等待写卡的数据：name | phone | emailPrefix | enableCounter */
    private var pendingWriteRequest: PendingWriteRequest? = null
    private var pendingUpgradeRequest: PendingUpgradeRequest? = null

    /** ✅ 写卡 / 读卡回调（通知 UI 状态） */
    private var onTagWriteCallback: ((NfcWriteResult) -> Unit)? = null
    private var onTagReadCallback: ((NfcReadResult) -> Unit)? = null
    private var onUpgradeStatusCallback: ((NfcWriteResult) -> Unit)? = null

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
                val peopleRepo = remember { PeopleRepository(this@MainActivity) }
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
                                    peopleRepository = peopleRepo,
                                    onWriteRequest = { name, phone, emailPrefix, enableCounter ->
                                        pendingUpgradeRequest = null
                                        pendingWriteRequest = PendingWriteRequest(name, phone, emailPrefix, enableCounter)
                                        onTagWriteCallback?.invoke(NfcWriteResult.Waiting)
                                    },
                                    onWriteStatus = { cb -> onTagWriteCallback = cb },
                                    onTagRead = { cb -> onTagReadCallback = cb },
                                    onUpgradeRequest = { uidHex, originalVcard ->
                                        pendingWriteRequest = null
                                        pendingUpgradeRequest = PendingUpgradeRequest(uidHex, originalVcard)
                                        onUpgradeStatusCallback?.invoke(NfcWriteResult.Waiting)
                                    },
                                    onUpgradeStatus = { cb -> onUpgradeStatusCallback = cb }
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
        pendingWriteRequest?.let { request ->
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

            pendingWriteRequest = null
            onTagWriteCallback?.invoke(result)
            return
        }

        pendingUpgradeRequest?.let { request ->
            val tagObj = tag
            val upgraded = VCardSigner.injectSignedNote(
                context = this,
                originalVcard = request.originalVcard,
                uidHex = request.uidHex
            )
            val result = when {
                tagObj == null -> NfcWriteResult.Failure("❌ 升级失败：未识别到 NFC 卡片")
                upgraded == null -> NfcWriteResult.Failure("❌ 升级失败：请先在密钥管理中配置公私钥")
                else -> writeNfcTag(tagObj, upgraded, request.uidHex, enableCounter = false)
            }
            pendingUpgradeRequest = null
            onUpgradeStatusCallback?.invoke(result)
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
                    val counter = readNtagCounter(tag)
                    onTagReadCallback?.invoke(
                        NfcReadResult(
                            uidHex = uidHex,
                            vcard = sb.toString(),
                            nfcCounter = counter
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
            val counterResult = if (enableCounter) ensureNtagCounterEnabled(tag) else null
            if (ndef != null) {
                ndef.connect()
                return if (ndef.isWritable) {
                    ndef.writeNdefMessage(msg)
                    ndef.close()
                    NfcWriteResult.Success(uidHex, counterResult?.tagType, counterResult?.enabled == true)
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
                    NfcWriteResult.Success(uidHex, counterResult?.tagType, counterResult?.enabled == true)
                } else {
                    NfcWriteResult.Failure("❌ 写入失败：不支持 NDEF")
                }
            }
        } catch (e: Exception) {
            NfcWriteResult.Failure("❌ 写入失败：${e.message ?: "未知错误"}")
        }
    }

    private fun ensureNtagCounterEnabled(tag: Tag): CounterEnableResult? {
        val nfcA = android.nfc.tech.NfcA.get(tag)
        val version = try {
            nfcA?.connect()
            val resp = nfcA?.transceive(byteArrayOf(0x60.toByte()))
            resp
        } catch (_: Exception) {
            null
        } finally {
            try {
                nfcA?.close()
            } catch (_: Exception) {
            }
        }

        val storageSize = version?.getOrNull(6)?.toInt() ?: return null
        val (tagType, configPage) = when (storageSize) {
            0x0F -> "NTAG213" to 0x29
            0x11 -> "NTAG215" to 0x83
            0x13 -> "NTAG216" to 0xE3
            else -> return CounterEnableResult("未知标签", false)
        }

        val ultralight = android.nfc.tech.MifareUltralight.get(tag) ?: return CounterEnableResult(tagType, false)
        return try {
            ultralight.connect()
            val raw = ultralight.readPages(configPage)
            if (raw.size < 8) {
                return CounterEnableResult(tagType, false)
            }

            val accessConfig = raw.copyOfRange(4, 8)

            // ACCESS 字节位于配置页 + 1 的 Byte0；Bit4 控制 NFC 计数器使能
            val accessByte = accessConfig[0].toInt() and 0xFF
            val alreadyEnabled = (accessByte and 0x10) != 0
            if (!alreadyEnabled) {
                accessConfig[0] = (accessByte or 0x10).toByte()
                ultralight.writePage(configPage + 1, accessConfig)
            }

            val finalAccess = if (alreadyEnabled) accessByte else (accessByte or 0x10)
            CounterEnableResult(tagType, (finalAccess and 0x10) != 0)
        } catch (_: Exception) {
            CounterEnableResult(tagType, false)
        } finally {
            try {
                ultralight.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun readNtagCounter(tag: Tag?): Int? {
        tag ?: return null
        val nfcA = android.nfc.tech.NfcA.get(tag) ?: return null
        val commands = listOf(
            byteArrayOf(0x39.toByte(), 0x02),
            byteArrayOf(0x39.toByte(), 0x00)
        )
        return try {
            nfcA.connect()
            for (command in commands) {
                val response = try {
                    nfcA.transceive(command)
                } catch (_: Exception) {
                    null
                }
                if (response != null && response.size >= 3) {
                    return (response[0].toInt() and 0xFF shl 16) or
                        (response[1].toInt() and 0xFF shl 8) or
                        (response[2].toInt() and 0xFF)
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            try {
                nfcA.close()
            } catch (_: Exception) {
            }
        }
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
    val nfcCounter: Int?
)

sealed class NfcWriteResult {
    data object Waiting : NfcWriteResult()
    data class Success(val uidHex: String, val tagType: String? = null, val counterEnabled: Boolean = false) : NfcWriteResult()
    data class Failure(val reason: String) : NfcWriteResult()
}

private data class PendingWriteRequest(
    val name: String,
    val phone: String,
    val emailPrefix: String,
    val enableCounter: Boolean
)

private data class PendingUpgradeRequest(
    val uidHex: String,
    val originalVcard: String
)

private data class CounterEnableResult(
    val tagType: String,
    val enabled: Boolean
)

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
