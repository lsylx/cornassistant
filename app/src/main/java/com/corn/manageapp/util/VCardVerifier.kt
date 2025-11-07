package com.corn.manageapp.utils

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.util.Locale

object VCardVerifier {

    /**
     * 解析 NOTE 字段: 简单的 key=value;... 解析
     */
    private fun parseNote(note: String): Map<String, String> {
        return note.split(";").mapNotNull { part ->
            val kv = part.split("=", limit = 2)
            if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
        }.toMap()
    }

    fun verifyNoteWithStoredPublic(context: Context, uidHex: String, note: String): Boolean {
        val parsed = parseNote(note)
        val sigB64 = parsed["SIG"] ?: return false
        val uidFromNote = parsed["UID"] ?: return false
        val alg = parsed["ALG"] ?: "ED25519"
        if (!uidHex.equals(uidFromNote, ignoreCase = true)) return false

        val publicBase64 = KeyStoreHelper.loadPublic(context) ?: return false
        val signature = Base64.decode(sigB64, Base64.NO_WRAP)
        val uidBytes = hexToByteArray(uidHex)
        return Ed25519Utils.verifyBase64Public(publicBase64, uidBytes, signature)
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
