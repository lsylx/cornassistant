package com.corn.manageapp.utils

import android.content.Context
import android.util.Base64

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
        val note = buildSignedNote(context, uidHex) ?: return null

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

    fun buildSignedNote(context: Context, uidHex: String): String? {
        val privateBase64 = KeyStoreHelper.loadEncryptedPrivate(context) ?: return null
        val uidBytes = hexToByteArray(uidHex)
        val sig = Ed25519Utils.signBase64Private(privateBase64, uidBytes)
        val sigB64 = Base64.encodeToString(sig, Base64.NO_WRAP)
        return "UID=$uidHex;SIG=$sigB64;ALG=ED25519;VER=1"
    }

    fun injectSignedNote(context: Context, originalVcard: String, uidHex: String): String? {
        val note = buildSignedNote(context, uidHex) ?: return null
        val newline = if (originalVcard.contains("\r\n")) "\r\n" else "\n"
        val normalized = originalVcard.replace("\r\n", "\n")
        val lines = normalized.split('\n')
        if (lines.isEmpty()) return "NOTE:$note"

        val rebuilt = mutableListOf<String>()
        var noteInserted = false
        var endInserted = false

        for (line in lines) {
            when {
                line.isEmpty() && rebuilt.isEmpty() -> {
                    // Preserve leading empties if any
                    rebuilt.add(line)
                }
                line.equals("END:VCARD", ignoreCase = true) -> {
                    if (!noteInserted) {
                        rebuilt.add("NOTE:$note")
                        noteInserted = true
                    }
                    rebuilt.add(line)
                    endInserted = true
                }
                line.startsWith("NOTE:", ignoreCase = true) -> {
                    if (!noteInserted) {
                        rebuilt.add("NOTE:$note")
                        noteInserted = true
                    }
                    // Skip original NOTE lines
                }
                else -> rebuilt.add(line)
            }
        }

        if (!noteInserted) {
            if (endInserted) {
                val endIndex = rebuilt.indexOfLast { it.equals("END:VCARD", ignoreCase = true) }
                if (endIndex >= 0) {
                    rebuilt.add(endIndex, "NOTE:$note")
                    noteInserted = true
                }
            }
        }

        if (!noteInserted) {
            rebuilt.add("NOTE:$note")
        }

        val result = rebuilt.joinToString(newline)
        val needsTrailingNewline = originalVcard.endsWith("\n") || originalVcard.endsWith("\r\n")
        return if (needsTrailingNewline) result + newline else result
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
