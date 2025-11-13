package com.corn.manageapp.utils

import android.content.Context
import android.util.Base64

data class SignedVCardPayload(
    val vcard: String,
    val note: String
)

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
    ): SignedVCardPayload? {
        val note = buildSignedNote(context, uidHex) ?: return null

        val vcard = buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$fullName")
            appendLine("ORG:COMCORN")
            appendLine("EMAIL:$email")
            appendLine("TEL:$tel")
            appendLine("END:VCARD")
        }
        return SignedVCardPayload(vcard, note)
    }

    fun buildSignedNote(context: Context, uidHex: String): String? {
        val privateBase64 = KeyStoreHelper.loadEncryptedPrivate(context) ?: return null
        val uidBytes = hexToByteArray(uidHex)
        val sig = Ed25519Utils.signBase64Private(privateBase64, uidBytes)
        val sigB64 = Base64.encodeToString(sig, Base64.NO_WRAP)
        return "UID=$uidHex;SIG=$sigB64;ALG=ED25519;VER=1"
    }

    fun injectSignedNote(context: Context, originalVcard: String, uidHex: String): SignedVCardPayload? {
        val note = buildSignedNote(context, uidHex) ?: return null
        val cleaned = stripNoteLines(originalVcard)
        val finalVcard = if (cleaned.isBlank()) originalVcard else cleaned
        return SignedVCardPayload(finalVcard, note)
    }

    private fun stripNoteLines(original: String): String {
        if (original.isBlank()) return original
        val newline = if (original.contains("\r\n")) "\r\n" else "\n"
        val normalized = original.replace("\r\n", "\n")
        val filtered = normalized.split('\n').filterNot { it.startsWith("NOTE:", ignoreCase = true) }
        val rebuilt = filtered.joinToString("\n")
        val needsTrailingNewline = original.endsWith("\n") || original.endsWith("\r\n")
        return if (needsTrailingNewline) rebuilt + newline else rebuilt
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
