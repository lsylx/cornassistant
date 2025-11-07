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
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
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

import java.nio.charset.Charset
import java.util.Locale

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Filled.Home),
    PEOPLE("人员管理", Icons.Filled.Person),
    INVENTORY("机房管理", Icons.Filled.ShoppingCart),
    SETTINGS("设置", Icons.Filled.Settings),
    INVENTORY_QUERY("查询服务器", Icons.Filled.ShoppingCart),
    SETTINGS_DCIM("DCIM配置", Icons.Filled.Settings),
    DETAIL("设备详情", Icons.Filled.ShoppingCart)
}

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    /** ✅ 等待写卡的数据：name | phone | emailPrefix */
    private var pendingWriteTriple: Triple<String, String, String>? = null

    /** ✅ 读取完成后回调 UI（携带 uid 与 vcard） */
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
                                    onWriteRequest = { name, phone, emailPrefix ->
                                        // 由 UI 触发，开始等待用户贴卡
                                        pendingWriteTriple = Triple(name, phone, emailPrefix)
                                    },
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
                                    onOpenDcim = { current = AppDestinations.SETTINGS_DCIM }
                                )

                            AppDestinations.SETTINGS_DCIM ->
                                DcimSettingsScreen(
                                    repo = dcimRepo,
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
        pendingWriteTriple?.let { triple ->
            tag?.let { t ->
                val (name, phone, emailPrefix) = triple
                val vcard = VCardSigner.buildSignedVCard(
                    context = this,
                    fullName = name,
                    tel = phone,
                    email = "$emailPrefix@comcorn.cn",
                    uidHex = uidHex
                )
                if (vcard != null) {
                    writeNfcTag(t, vcard)
                }
                pendingWriteTriple = null
            }
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
                        sb.append(rec.payload.toString(Charset.forName("UTF-8")))
                    }
                    onTagReadCallback?.invoke(NfcReadResult(uidHex = uidHex, vcard = sb.toString()))
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun writeNfcTag(tag: Tag, data: String) {
        try {
            val ndef = Ndef.get(tag)
            val msg = NdefMessage(
                arrayOf(NdefRecord.createTextRecord(Locale.CHINA.language, data))
            )
            if (ndef != null) {
                ndef.connect()
                if (ndef.isWritable) ndef.writeNdefMessage(msg)
                ndef.close()
            } else {
                val formatable = NdefFormatable.get(tag)
                formatable?.connect()
                formatable?.format(msg)
                formatable?.close()
            }
        } catch (_: Exception) {
        }
    }
}

/** ✅ 读取回调数据模型：UID + vCard 原文 */
data class NfcReadResult(
    val uidHex: String,
    val vcard: String
)

@Composable
fun InventoryScreen(
    modifier: Modifier = Modifier,
    onOpenQueryServer: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
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
    onOpenDcim: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("设置", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        Button(onOpenDcim, Modifier.fillMaxWidth()) { Text("DCIM 配置") }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Welcome to $name!", style = MaterialTheme.typography.titleLarge)
    }
}
