package com.easytier.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.squareup.moshi.Types
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val allConfigsKey = stringPreferencesKey("all_user_configs")
    private val activeConfigIdKey = stringPreferencesKey("active_config_id")
    private val machineIdKey = stringPreferencesKey("machine_id")
    private val configServerUrlKey = stringPreferencesKey("config_server_url")
    private val configServerHostnameKey = stringPreferencesKey("config_server_hostname")
    private val configServerSecureModeKey = booleanPreferencesKey("config_server_secure_mode")
    private val autoConnectConfigServerKey = booleanPreferencesKey("auto_connect_config_server")

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

    /** 获取机器 ID，若不存在则生成 UUID 并持久化后返回 */
    suspend fun getMachineId(): String {
        val existing = context.dataStore.data.map { it[machineIdKey] }.first()
        if (!existing.isNullOrBlank()) return existing
        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { it[machineIdKey] = newId }
        return newId
    }

    suspend fun getConfigServerUrl(): String? {
        return context.dataStore.data.map { it[configServerUrlKey] }.first()
    }

    suspend fun setConfigServerUrl(url: String) {
        context.dataStore.edit { it[configServerUrlKey] = url }
    }

    suspend fun getConfigServerHostname(): String? {
        return context.dataStore.data.map { it[configServerHostnameKey] }.first()
    }

    suspend fun setConfigServerHostname(hostname: String) {
        context.dataStore.edit { it[configServerHostnameKey] = hostname }
    }

    suspend fun getConfigServerSecureMode(): Boolean {
        return context.dataStore.data.map { it[configServerSecureModeKey] }.first() ?: false
    }

    suspend fun setConfigServerSecureMode(secureMode: Boolean) {
        context.dataStore.edit { it[configServerSecureModeKey] = secureMode }
    }

    suspend fun getAutoConnectConfigServer(): Boolean {
        return context.dataStore.data.map { it[autoConnectConfigServerKey] }.first() ?: false
    }

    suspend fun setAutoConnectConfigServer(autoConnect: Boolean) {
        context.dataStore.edit { it[autoConnectConfigServerKey] = autoConnect }
    }
}