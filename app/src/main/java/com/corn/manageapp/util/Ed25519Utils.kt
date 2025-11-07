package com.corn.manageapp.utils

import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

object Ed25519Utils {

    /**
     * 生成一对 Ed25519 key（返回 Pair(publicBase64, privateBase64)）
     * NOTE: privateBase64 需要安全保存（此处我们会用 Keystore 加密存储）
     */
    fun generateKeyPairBase64(): Pair<String, String> {
        val random = SecureRandom()
        val priv = Ed25519PrivateKeyParameters(random)
        val pub = priv.generatePublicKey()
        val pubB = pub.encoded
        val privB = priv.encoded
        val pub64 = Base64.encodeToString(pubB, Base64.NO_WRAP)
        val priv64 = Base64.encodeToString(privB, Base64.NO_WRAP)
        return Pair(pub64, priv64)
    }

    fun signBase64Private(privateKeyBase64: String, message: ByteArray): ByteArray {
        val privBytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP)
        val priv = Ed25519PrivateKeyParameters(privBytes, 0)
        val signer = Ed25519Signer()
        signer.init(true, priv)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun verifyBase64Public(publicKeyBase64: String, message: ByteArray, signature: ByteArray): Boolean {
        val pubBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        val pub = Ed25519PublicKeyParameters(pubBytes, 0)
        val signer = Ed25519Signer()
        signer.init(false, pub)
        signer.update(message, 0, message.size)
        return signer.verifySignature(signature)
    }
}
