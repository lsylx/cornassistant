package com.corn.manageapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.corn.manageapp.utils.Ed25519Utils
import com.corn.manageapp.utils.KeyStoreHelper
import kotlinx.coroutines.launch

@Composable
fun PublicKeyManagementScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var publicInput by rememberSaveable { mutableStateOf("") }
    var privateInput by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var hasPair by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        publicInput = KeyStoreHelper.loadPublic(ctx).orEmpty()
        hasPair = KeyStoreHelper.hasKeyPair(ctx)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(8.dp))
            Text("公私钥管理", style = MaterialTheme.typography.titleLarge)
        }

        Text(
            if (hasPair) "✅ 已配置密钥对" else "⚠️ 尚未配置密钥对",
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasPair) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )

        Button(
            onClick = {
                scope.launch {
                    val (pub, priv) = Ed25519Utils.generateKeyPairBase64()
                    KeyStoreHelper.storeEncryptedPrivate(ctx, priv)
                    KeyStoreHelper.storePublic(ctx, pub)
                    publicInput = pub
                    privateInput = priv
                    hasPair = true
                    message = "✅ 已生成并保存新的密钥对，请立即备份私钥"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("生成新的 Ed25519 密钥对")
        }

        Text("私钥 Base64（仅用于导入/备份）", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = privateInput,
            onValueChange = { privateInput = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 4,
            placeholder = { Text("请输入或粘贴私钥 Base64") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )

        Text("公钥 Base64", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = publicInput,
            onValueChange = { publicInput = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 3,
            placeholder = { Text("请输入或粘贴公钥 Base64") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )

        Button(
            onClick = {
                val pub = publicInput.trim()
                val priv = privateInput.trim()
                if (pub.isEmpty() || priv.isEmpty()) {
                    message = "❌ 请同时填写公钥和私钥"
                    return@Button
                }
                scope.launch {
                    try {
                        KeyStoreHelper.storeEncryptedPrivate(ctx, priv)
                        KeyStoreHelper.storePublic(ctx, pub)
                        hasPair = true
                        message = "✅ 已保存导入的密钥对"
                    } catch (e: Exception) {
                        message = e.message?.let { "❌ 保存失败：$it" } ?: "❌ 保存失败"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存导入的密钥对")
        }

        Button(
            onClick = {
                scope.launch {
                    KeyStoreHelper.clearKeys(ctx)
                    hasPair = false
                    message = "⚠️ 已清除已保存的密钥"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("清除本地密钥")
        }

        if (message.isNotEmpty()) {
            val color = when {
                message.startsWith("❌") -> MaterialTheme.colorScheme.error
                message.startsWith("⚠️") -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
            Text(message, color = color)
        }
    }
}
