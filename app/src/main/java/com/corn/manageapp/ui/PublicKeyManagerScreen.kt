package com.corn.manageapp.ui

import android.content.Context
import android.security.keystore.KeyProperties
import android.security.keystore.KeyGenParameterSpec
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.security.KeyPairGenerator
import java.security.KeyStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicKeyManagementScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    var pubKey by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("公钥管理") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← 返回") }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = modifier
                .padding(inner)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Button(
                onClick = {
                    val res = generateKeyPair()
                    message = if (res) "✅ 已生成密钥对" else "❌ 生成失败"
                    pubKey = loadPublicKeyBase64()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("生成密钥对（只需一次）")
            }

            Spacer(Modifier.height(20.dp))

            Text("公钥 Base64:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = pubKey.ifEmpty { "（暂无）" },
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(20.dp))

            if (message.isNotEmpty()) {
                Text(message, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private const val KEY_ALIAS = "ComCornNFCKey"

fun generateKeyPair(): Boolean {
    return try {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()

        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
        true
    } catch (e: Exception) {
        false
    }
}
fun loadPublicKeyBase64(): String {
    return try {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val pub = ks.getCertificate(KEY_ALIAS).publicKey
        android.util.Base64.encodeToString(pub.encoded, android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    }
}

