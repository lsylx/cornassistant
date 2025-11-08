package com.corn.manageapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.peopleStore by preferencesDataStore("people_store")

private val PEOPLE_KEY = stringPreferencesKey("people_json")

class PeopleRepository(private val context: Context) {

    private val gson = Gson()
    private val listType = object : TypeToken<List<Person>>() {}.type

    val peopleFlow: Flow<List<Person>> = context.peopleStore.data.map { pref ->
        pref[PEOPLE_KEY]?.let { decode(it) } ?: emptyList()
    }

    suspend fun upsert(person: Person) {
        context.peopleStore.edit { pref ->
            val current = pref[PEOPLE_KEY]?.let { decode(it) }?.toMutableList() ?: mutableListOf()
            val index = current.indexOfFirst { it.id == person.id }
            if (index >= 0) {
                current[index] = person
            } else {
                current.add(person)
            }
            pref[PEOPLE_KEY] = gson.toJson(current)
        }
    }

    suspend fun delete(personId: String) {
        context.peopleStore.edit { pref ->
            val current = pref[PEOPLE_KEY]?.let { decode(it) }?.toMutableList() ?: mutableListOf()
            val removed = current.removeAll { it.id == personId }
            if (removed) {
                pref[PEOPLE_KEY] = gson.toJson(current)
            }
        }
    }

    private fun decode(json: String): List<Person> {
        return runCatching { gson.fromJson<List<Person>>(json, listType) }.getOrElse { emptyList() }
    }
}
