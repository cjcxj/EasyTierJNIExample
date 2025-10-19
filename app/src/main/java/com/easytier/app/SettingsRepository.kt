package com.easytier.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.squareup.moshi.Types

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val allConfigsKey = stringPreferencesKey("all_user_configs")
    private val activeConfigIdKey = stringPreferencesKey("active_config_id")

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Create an adapter for a List of ConfigData
    private val configListAdapter = moshi.adapter<List<ConfigData>>(
        Types.newParameterizedType(List::class.java, ConfigData::class.java)
    )

    suspend fun getAllConfigs(): List<ConfigData> {
        val jsonString = context.dataStore.data.map { it[allConfigsKey] }.first()
        return if (jsonString != null) {
            configListAdapter.fromJson(jsonString) ?: listOf(ConfigData())
        } else {
            listOf(ConfigData()) // If nothing is saved, return a default one
        }
    }

    suspend fun saveAllConfigs(configs: List<ConfigData>) {
        val jsonString = configListAdapter.toJson(configs)
        context.dataStore.edit { settings ->
            settings[allConfigsKey] = jsonString
        }
    }

    suspend fun getActiveConfigId(): String? {
        return context.dataStore.data.map { it[activeConfigIdKey] }.first()
    }

    suspend fun setActiveConfigId(id: String) {
        context.dataStore.edit {
            it[activeConfigIdKey] = id
        }
    }
}