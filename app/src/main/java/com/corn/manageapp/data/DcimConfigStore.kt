package com.corn.manageapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.dcimStore by preferencesDataStore("dcim_config")

object DcimKeys {
    val HOST = stringPreferencesKey("host")
    val USERNAME = stringPreferencesKey("username")
    val PASSWORD = stringPreferencesKey("password")
}

data class DcimConfig(
    val host: String,
    val username: String,
    val password: String
)

class DcimConfigRepository(private val context: Context) {

    val configFlow = context.dcimStore.data.map { pref ->
        DcimConfig(
            host = pref[DcimKeys.HOST] ?: "",
            username = pref[DcimKeys.USERNAME] ?: "",
            password = pref[DcimKeys.PASSWORD] ?: ""
        )
    }

    suspend fun saveConfig(host: String, username: String, password: String) {
        context.dcimStore.edit { pref ->
            pref[DcimKeys.HOST] = host
            pref[DcimKeys.USERNAME] = username
            pref[DcimKeys.PASSWORD] = password
        }
    }
}
