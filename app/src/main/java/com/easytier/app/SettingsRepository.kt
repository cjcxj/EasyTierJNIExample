package com.easytier.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.wire.WireJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// 通过扩展属性在 Context 中创建 DataStore 实例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    // 定义用于存储配置 JSON 字符串的 Key
    private val configKey = stringPreferencesKey("user_config")

    // 初始化 Moshi，使用 WireJsonAdapterFactory 来处理由 Wire 生成的数据类
    private val moshi = Moshi.Builder()
        .add(WireJsonAdapterFactory())
        .add(KotlinJsonAdapterFactory())
        .build()

    // 获取 ConfigData 的 JSON 适配器
    private val configAdapter = moshi.adapter(ConfigData::class.java)

    /**
     * 从 DataStore 加载配置。
     * 如果没有保存的配置，则返回默认的 ConfigData 实例。
     */
    suspend fun loadConfig(): ConfigData {
        val preferences = context.dataStore.data.first()
        val configJson = preferences[configKey]
        return if (configJson != null) {
            try {
                configAdapter.fromJson(configJson) ?: ConfigData()
            } catch (e: Exception) {
                // 解析失败时返回默认值
                ConfigData()
            }
        } else {
            // 没有找到保存的值，返回默认值
            ConfigData()
        }
    }

    /**
     * 将配置保存到 DataStore。
     * @param configData 要保存的配置对象。
     */
    suspend fun saveConfig(configData: ConfigData) {
        val configJson = configAdapter.toJson(configData)
        context.dataStore.edit { settings ->
            settings[configKey] = configJson
        }
    }
}