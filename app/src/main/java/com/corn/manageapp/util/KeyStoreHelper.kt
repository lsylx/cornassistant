package com.corn.manageapp.utils

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeyStoreHelper {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val AES_KEY_ALIAS = "Ed25519_KEK" // 用于加密私钥的对称密钥别名
    private const val PREFS = "ed_keys_prefs"
    private const val PREF_PRIVATE_ENC = "private_enc" // 存 Base64(iv + ct)
    private const val PREF_PUBLIC = "public_b64"

    // GCM params
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private fun ensureAesKey() {
        val ks = KeyStore.getInstance(ANDROID_KEY_STORE)
        ks.load(null)
        if (ks.containsAlias(AES_KEY_ALIAS)) return

        val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEY_STORE)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            AES_KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    private fun getAesKey(): SecretKey {
        ensureAesKey()
        val ks = KeyStore.getInstance(ANDROID_KEY_STORE)
        ks.load(null)
        val entry = ks.getEntry(AES_KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    fun storeEncryptedPrivate(context: Context, privateBase64: String) {
        val key = getAesKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv // 12 bytes GCM iv
        val ct = cipher.doFinal(privateBase64.toByteArray(StandardCharsets.UTF_8))
        // 保存 iv + ct 的 Base64
        val combined = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ct, 0, combined, iv.size, ct.size)
        val b64 = Base64.encodeToString(combined, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_PRIVATE_ENC, b64)
            .apply()
    }

    fun loadEncryptedPrivate(context: Context): String? {
        val b64 = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_PRIVATE_ENC, null) ?: return null
        val combined = Base64.decode(b64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12) // GCM IV 12 bytes
        val ct = combined.copyOfRange(12, combined.size)
        val key = getAesKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val plain = cipher.doFinal(ct)
        return String(plain, StandardCharsets.UTF_8) // this is privateBase64
    }

    fun storePublic(context: Context, publicBase64: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_PUBLIC, publicBase64).apply()
    }

    fun loadPublic(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_PUBLIC, null)
    }

    fun hasKeyPair(context: Context): Boolean {
        val priv = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_PRIVATE_ENC, null)
        val pub = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_PUBLIC, null)
        return !priv.isNullOrEmpty() && !pub.isNullOrEmpty()
    }

    fun clearKeys(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
