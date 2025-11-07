package com.corn.manageapp.utils

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets

object VCardSigner {
    /**
     * NOTE 格式:
     * NOTE:UID=<UIDHEX>;SIG=<base64(sig)>;ALG=ED25519;VER=1
     *
     * 签名内容：直接对 UID 的原始 bytes 签名（可拓展为对 UID + payload）
     */

    fun buildSignedVCard(
        context: Context,
        fullName: String,
        tel: String,
        email: String,
        uidHex: String
    ): String? {
        // 读取私钥（解密得到 privateBase64）
        val privateBase64 = KeyStoreHelper.loadEncryptedPrivate(context) ?: return null
        val uidBytes = hexToByteArray(uidHex)
        val sig = Ed25519Utils.signBase64Private(privateBase64, uidBytes)
        val sigB64 = Base64.encodeToString(sig, Base64.NO_WRAP)
        val note = "UID=$uidHex;SIG=$sigB64;ALG=ED25519;VER=1"

        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$fullName")
            appendLine("ORG:COMCORN")
            appendLine("EMAIL:$email")
            appendLine("TEL:$tel")
            appendLine("NOTE:$note")
            appendLine("END:VCARD")
        }
    }

    private fun hexToByteArray(s: String): ByteArray {
        val str = s.replace("[^0-9A-Fa-f]".toRegex(), "")
        val len = str.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            out[i / 2] = ((Character.digit(str[i], 16) shl 4) + Character.digit(str[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }
}
