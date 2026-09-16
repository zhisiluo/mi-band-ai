@file:Suppress("unused")

package com.zhisiluo.superxiaoai.config

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 超级小爱 —— 配置预设管理（保存 / 删除 / 应用）
 *
 * 模块整体转型为「超级小爱转 OpenAI 兼容接口」后，可预设的配置只剩服务端一组：
 * 监听端口、局域网开关、访问令牌、默认引擎、回答长度上限、各引擎等待上限、
 * system / history 透传开关与内置系统提示词。
 *
 * 预设只保存在模块 App 本地（SharedPreferences "llm_presets"），
 * Hook 进程不读取预设，只读取 [ConfigStore] 中实际生效的配置 ——
 * 因此预设的保存/应用完全发生在模块 App 进程（设置页）。
 */
object PresetManager {

    private const val PREFS_NAME = "llm_presets"
    private const val KEY_PREFIX = "preset_"

    /** 服务端配置分组（唯一分组） */
    const val CATEGORY_SERVER = "server"

    private val json = Json { ignoreUnknownKeys = true }

    private var prefs: SharedPreferences? = null

    /** 初始化：必须传入模块 App 的 Context（SettingsActivity 内调用一次） */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 单个预设：名称 + 分组 + 配置键值映射（值统一为字符串，应用时按类型解析） */
    @Serializable
    data class ConfigPreset(
        val name: String,
        val category: String,
        val values: Map<String, String>,
    )

    // ==================== 增删查 ====================

    private fun keyOf(category: String, name: String): String = "$KEY_PREFIX$category:$name"

    /**
     * 保存预设。若同名已存在则覆盖（返回 true）；名称为空或未初始化返回 false。
     */
    fun savePreset(category: String, name: String, values: Map<String, String>): Boolean {
        val p = prefs ?: return false
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val preset = ConfigPreset(trimmed, category, values)
        p.edit()
            .putString(keyOf(category, trimmed), json.encodeToString(ConfigPreset.serializer(), preset))
            .apply()
        return true
    }

    /** 删除指定预设。 */
    fun deletePreset(category: String, name: String) {
        val p = prefs ?: return
        p.edit().remove(keyOf(category, name.trim())).apply()
    }

    /** 列出某分组下全部预设名称（按名称排序）。 */
    fun listPresets(category: String): List<String> {
        val p = prefs ?: return emptyList()
        val prefix = "$KEY_PREFIX$category:"
        return p.all.keys
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .sorted()
    }

    /** 加载某预设的配置键值映射；不存在返回 null。 */
    fun loadPreset(category: String, name: String): Map<String, String>? {
        val p = prefs ?: return null
        val raw = p.getString(keyOf(category, name.trim()), null) ?: return null
        return try {
            json.decodeFromString(ConfigPreset.serializer(), raw).values
        } catch (_: Throwable) {
            null
        }
    }

    /** 判断某名称预设是否已存在。 */
    fun presetExists(category: String, name: String): Boolean {
        val p = prefs ?: return false
        return p.contains(keyOf(category, name.trim()))
    }

    // ==================== 导出当前配置 ====================

    /**
     * 导出当前服务端配置为「键 -> 字符串值」映射（供保存预设）。
     * 各键值取自 [ConfigStore] 的类型化 getter，统一字符串化。
     */
    fun exportValues(config: ConfigStore, category: String): Map<String, String> = when (category) {
        CATEGORY_SERVER -> linkedMapOf(
            ConfigKeys.KEY_OPENAI_PORT to config.getOpenAiPort().toString(),
            ConfigKeys.KEY_OPENAI_LAN to config.isOpenAiLan().toString(),
            ConfigKeys.KEY_OPENAI_TOKEN to config.getOpenAiToken(),
            ConfigKeys.KEY_XIAOAI_ENGINE to config.getXiaoaiEngine(),
            ConfigKeys.KEY_OPENAI_MAX_ANSWER_LEN to config.getOpenAiMaxAnswerLen().toString(),
            ConfigKeys.KEY_FAST_WAIT_MS to config.getFastWaitMs().toString(),
            ConfigKeys.KEY_MICLAW_WAIT_MS to config.getMiclawWaitMs().toString(),
            ConfigKeys.KEY_OPENAI_FORWARD_SYSTEM to config.isOpenAiForwardSystem().toString(),
            ConfigKeys.KEY_OPENAI_FORWARD_HISTORY to config.isOpenAiForwardHistory().toString(),
            ConfigKeys.KEY_SYSTEM_PROMPT to config.getSystemPrompt(),
        )
        else -> emptyMap()
    }

    // ==================== 应用预设到 ConfigStore ====================

    /**
     * 把一个预设的值映射应用回 [ConfigStore]。
     * 值统一为字符串，这里按各配置键的语义解析并调用对应 setter；
     * 解析失败的安全降级为不写该键（保留当前值）。
     */
    fun applyPreset(config: ConfigStore, values: Map<String, String>) {
        values.forEach { (key, value) ->
            when (key) {
                ConfigKeys.KEY_OPENAI_PORT ->
                    value.toIntOrNull()?.let { config.setOpenAiPort(it) }
                ConfigKeys.KEY_OPENAI_LAN -> config.setOpenAiLan(value == "true")
                ConfigKeys.KEY_OPENAI_TOKEN -> config.setOpenAiToken(value)
                ConfigKeys.KEY_XIAOAI_ENGINE -> config.setXiaoaiEngine(value)
                ConfigKeys.KEY_OPENAI_MAX_ANSWER_LEN ->
                    value.toIntOrNull()?.let { config.setOpenAiMaxAnswerLen(it) }
                ConfigKeys.KEY_FAST_WAIT_MS ->
                    value.toLongOrNull()?.let { config.setFastWaitMs(it) }
                ConfigKeys.KEY_MICLAW_WAIT_MS ->
                    value.toLongOrNull()?.let { config.setMiclawWaitMs(it) }
                ConfigKeys.KEY_OPENAI_FORWARD_SYSTEM -> config.setOpenAiForwardSystem(value == "true")
                ConfigKeys.KEY_OPENAI_FORWARD_HISTORY -> config.setOpenAiForwardHistory(value == "true")
                ConfigKeys.KEY_SYSTEM_PROMPT -> config.setSystemPrompt(value)
                // 其他键（如 enabled）不参与预设
            }
        }
    }
}