package com.zhisiluo.superxiaoai.config

import android.content.SharedPreferences
import android.util.Base64
import io.github.libxposed.api.XposedModule
import io.github.libxposed.service.XposedService

/**
 * 超级小爱 配置存储 —— 封装 Remote Preferences 的读写入口。
 *
 * 同一份数据通过 [fromModule]（Hook 进程只读）和 [fromService]（模块 App 进程可写）
 * 获取对应的 [ConfigStore] 实例，两者底层指向同一个 Remote Preferences group，
 * 因此 Hook 端能实时读到 App 端写入的配置变更。
 */
class ConfigStore private constructor(private val prefs: SharedPreferences) {

    companion object {
        /** Remote Preferences 的 group 名称，两端必须一致 */
        private const val PREFS_GROUP = "config"

        /**
         * 从 Hook 进程端创建只读 [ConfigStore]。
         * XposedModule.getRemotePreferences() 返回的 SharedPreferences 在 Hook 进程中为只读，
         * setter 调用 prefs.edit().apply() 会被框架安全忽略，不会抛出异常。
         */
        @JvmStatic
        fun fromModule(module: XposedModule): ConfigStore =
            ConfigStore(module.getRemotePreferences(PREFS_GROUP))

        /**
         * 从模块 App 进程端创建可写 [ConfigStore]。
         * XposedService.getRemotePreferences() 返回的 SharedPreferences 支持读写。
         */
        @JvmStatic
        fun fromService(service: XposedService): ConfigStore =
            ConfigStore(service.getRemotePreferences(PREFS_GROUP))
    }

    // ==================== 读取器 ====================

    /** 模块启用开关 */
    fun isEnabled(): Boolean =
        prefs.getBoolean(ConfigKeys.KEY_ENABLED, ConfigKeys.DEFAULT_ENABLED)

    /** 监听端口：非法值（越界）回退默认端口，避免绑定失败 */
    fun getOpenAiPort(): Int {
        val v = prefs.getInt(ConfigKeys.KEY_OPENAI_PORT, ConfigKeys.DEFAULT_OPENAI_PORT)
        return if (v in 1024..65535) v else ConfigKeys.DEFAULT_OPENAI_PORT
    }

    /** 是否允许局域网访问：false 仅绑定 127.0.0.1 */
    fun isOpenAiLan(): Boolean =
        prefs.getBoolean(ConfigKeys.KEY_OPENAI_LAN, ConfigKeys.DEFAULT_OPENAI_LAN)

    /** 访问令牌：读取时自动解密，解密失败返回空串（等同不校验） */
    fun getOpenAiToken(): String =
        ApiKeyCipher.decrypt(prefs.getString(ConfigKeys.KEY_OPENAI_TOKEN, "") ?: "")

    /** 默认回答引擎："miclaw" / "fast" */
    fun getXiaoaiEngine(): String =
        prefs.getString(ConfigKeys.KEY_XIAOAI_ENGINE, ConfigKeys.DEFAULT_XIAOAI_ENGINE)
            ?: ConfigKeys.DEFAULT_XIAOAI_ENGINE

    /** 回答最大长度：0 = 不截断 */
    fun getOpenAiMaxAnswerLen(): Int =
        prefs.getInt(
            ConfigKeys.KEY_OPENAI_MAX_ANSWER_LEN,
            ConfigKeys.DEFAULT_OPENAI_MAX_ANSWER_LEN,
        ).coerceAtLeast(0)

    /** fast 引擎等待上限（毫秒） */
    fun getFastWaitMs(): Long =
        prefs.getLong(ConfigKeys.KEY_FAST_WAIT_MS, ConfigKeys.DEFAULT_FAST_WAIT_MS)
            .coerceIn(3_000L, 120_000L)

    /** miclaw 引擎等待上限（毫秒） */
    fun getMiclawWaitMs(): Long =
        prefs.getLong(ConfigKeys.KEY_MICLAW_WAIT_MS, ConfigKeys.DEFAULT_MICLAW_WAIT_MS)
            .coerceIn(3_000L, 180_000L)

    /** 是否透传请求体中的 system 提示词 */
    fun isOpenAiForwardSystem(): Boolean =
        prefs.getBoolean(
            ConfigKeys.KEY_OPENAI_FORWARD_SYSTEM,
            ConfigKeys.DEFAULT_OPENAI_FORWARD_SYSTEM,
        )

    /** 是否把历史消息拼进提问 */
    fun isOpenAiForwardHistory(): Boolean =
        prefs.getBoolean(
            ConfigKeys.KEY_OPENAI_FORWARD_HISTORY,
            ConfigKeys.DEFAULT_OPENAI_FORWARD_HISTORY,
        )

    /** 系统提示词：若用户未设置（空字符串），返回默认值 */
    fun getSystemPrompt(): String {
        val v = prefs.getString(ConfigKeys.KEY_SYSTEM_PROMPT, "") ?: ""
        return if (v.isBlank()) ConfigKeys.DEFAULT_SYSTEM_PROMPT else v
    }

    // ==================== 主题设置 ====================

    /** 主题模式（与 Miuix ColorSchemeMode 枚举名对应，String 存储） */
    fun getThemeMode(): String =
        prefs.getString(ConfigKeys.KEY_THEME_MODE, ConfigKeys.DEFAULT_THEME_MODE)
            ?: ConfigKeys.DEFAULT_THEME_MODE

    fun setThemeMode(mode: String) {
        prefs.edit().putString(ConfigKeys.KEY_THEME_MODE, mode).apply()
    }

    /** Monet 种子色（ARGB Int，0 = 跟随系统壁纸） */
    fun getKeyColor(): Long =
        prefs.getLong(ConfigKeys.KEY_KEY_COLOR, ConfigKeys.DEFAULT_KEY_COLOR)

    fun setKeyColor(color: Long) {
        prefs.edit().putLong(ConfigKeys.KEY_KEY_COLOR, color).apply()
    }

    /** 调色板风格（TonalSpot / Neutral / Vibrant / Expressive 等） */
    fun getPaletteStyle(): String =
        prefs.getString(ConfigKeys.KEY_PALETTE_STYLE, ConfigKeys.DEFAULT_PALETTE_STYLE)
            ?: ConfigKeys.DEFAULT_PALETTE_STYLE

    fun setPaletteStyle(style: String) {
        prefs.edit().putString(ConfigKeys.KEY_PALETTE_STYLE, style).apply()
    }

    /** 动态取色规范（Spec2021 / Spec2025） */
    fun getColorSpec(): String =
        prefs.getString(ConfigKeys.KEY_COLOR_SPEC, ConfigKeys.DEFAULT_COLOR_SPEC)
            ?: ConfigKeys.DEFAULT_COLOR_SPEC

    fun setColorSpec(spec: String) {
        prefs.edit().putString(ConfigKeys.KEY_COLOR_SPEC, spec).apply()
    }

    // ==================== 视觉效果（移植 KSU ColorPalette） ====================

    /** Monet 动态取色总开关（KSU 风格：关闭时 themeMode 回退非 Monet 系列） */
    fun isMiuixMonet(): Boolean =
        prefs.getBoolean(ConfigKeys.KEY_MIUIX_MONET, ConfigKeys.DEFAULT_MIUIX_MONET)

    fun setMiuixMonet(v: Boolean) {
        prefs.edit().putBoolean(ConfigKeys.KEY_MIUIX_MONET, v).apply()
    }

    fun isEnableBlur(): Boolean =
        prefs.getBoolean(ConfigKeys.KEY_ENABLE_BLUR, ConfigKeys.DEFAULT_ENABLE_BLUR)

    fun setEnableBlur(v: Boolean) {
        prefs.edit().putBoolean(ConfigKeys.KEY_ENABLE_BLUR, v).apply()
    }

    fun isFloatingBottomBar(): Boolean =
        prefs.getBoolean(ConfigKeys.KEY_FLOATING_BOTTOM_BAR, ConfigKeys.DEFAULT_FLOATING_BOTTOM_BAR)

    fun setFloatingBottomBar(v: Boolean) {
        prefs.edit().putBoolean(ConfigKeys.KEY_FLOATING_BOTTOM_BAR, v).apply()
    }

    fun isFloatingBottomBarBlur(): Boolean =
        prefs.getBoolean(ConfigKeys.KEY_FLOATING_BOTTOM_BAR_BLUR, ConfigKeys.DEFAULT_FLOATING_BOTTOM_BAR_BLUR)

    fun setFloatingBottomBarBlur(v: Boolean) {
        prefs.edit().putBoolean(ConfigKeys.KEY_FLOATING_BOTTOM_BAR_BLUR, v).apply()
    }

    fun isEnableNavigationBadge(): Boolean =
        prefs.getBoolean(ConfigKeys.KEY_ENABLE_NAVIGATION_BADGE, ConfigKeys.DEFAULT_ENABLE_NAVIGATION_BADGE)

    fun setEnableNavigationBadge(v: Boolean) {
        prefs.edit().putBoolean(ConfigKeys.KEY_ENABLE_NAVIGATION_BADGE, v).apply()
    }

    fun isEnablePredictiveBack(): Boolean =
        prefs.getBoolean(ConfigKeys.KEY_ENABLE_PREDICTIVE_BACK, ConfigKeys.DEFAULT_ENABLE_PREDICTIVE_BACK)

    fun setEnablePredictiveBack(v: Boolean) {
        prefs.edit().putBoolean(ConfigKeys.KEY_ENABLE_PREDICTIVE_BACK, v).apply()
    }

    fun getPageScale(): Float =
        prefs.getFloat(ConfigKeys.KEY_PAGE_SCALE, ConfigKeys.DEFAULT_PAGE_SCALE)

    fun setPageScale(v: Float) {
        prefs.edit().putFloat(ConfigKeys.KEY_PAGE_SCALE, v).apply()
    }

    // ==================== 写入器 ====================

    fun setEnabled(v: Boolean) {
        prefs.edit().putBoolean(ConfigKeys.KEY_ENABLED, v).apply()
    }

    /** 写入监听端口（越界值直接忽略，防止界面输入非法数据落库） */
    fun setOpenAiPort(v: Int) {
        if (v in 1024..65535) {
            prefs.edit().putInt(ConfigKeys.KEY_OPENAI_PORT, v).apply()
        }
    }

    fun setOpenAiLan(v: Boolean) {
        prefs.edit().putBoolean(ConfigKeys.KEY_OPENAI_LAN, v).apply()
    }

    /** 访问令牌：写入时自动加密；空串表示关闭校验 */
    fun setOpenAiToken(v: String) {
        prefs.edit().putString(ConfigKeys.KEY_OPENAI_TOKEN, ApiKeyCipher.encrypt(v)).apply()
    }

    /** 默认回答引擎："miclaw" / "fast" */
    fun setXiaoaiEngine(v: String) {
        prefs.edit().putString(ConfigKeys.KEY_XIAOAI_ENGINE, v).apply()
    }

    /** 回答最大长度：0 = 不截断 */
    fun setOpenAiMaxAnswerLen(v: Int) {
        prefs.edit().putInt(ConfigKeys.KEY_OPENAI_MAX_ANSWER_LEN, v.coerceAtLeast(0)).apply()
    }

    /** fast 引擎等待上限（毫秒） */
    fun setFastWaitMs(v: Long) {
        prefs.edit().putLong(ConfigKeys.KEY_FAST_WAIT_MS, v.coerceIn(3_000L, 120_000L)).apply()
    }

    /** miclaw 引擎等待上限（毫秒） */
    fun setMiclawWaitMs(v: Long) {
        prefs.edit().putLong(ConfigKeys.KEY_MICLAW_WAIT_MS, v.coerceIn(3_000L, 180_000L)).apply()
    }

    fun setOpenAiForwardSystem(v: Boolean) {
        prefs.edit().putBoolean(ConfigKeys.KEY_OPENAI_FORWARD_SYSTEM, v).apply()
    }

    fun setOpenAiForwardHistory(v: Boolean) {
        prefs.edit().putBoolean(ConfigKeys.KEY_OPENAI_FORWARD_HISTORY, v).apply()
    }

    fun setSystemPrompt(v: String) {
        prefs.edit().putString(ConfigKeys.KEY_SYSTEM_PROMPT, v).apply()
    }

    // ==================== 变更监听 ====================

    /**
     * 注册配置变更监听器。
     * 直接委托给 [SharedPreferences.registerOnSharedPreferenceChangeListener]。
     * Hook 进程可通过此监听器实时感知 App 端写入的配置变化。
     */
    fun registerOnChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(l)
    }
}

// ====================================================================
// 访问令牌简单对称加密（XOR + Base64）
// 仅用于防误读（如日志、明文文件），非高安全方案。
// 加密密钥写死在代码内是已知的权衡 —— 避免引入额外加密依赖。
// 解密失败时静默返回空串，不抛出异常。
// ====================================================================
private object ApiKeyCipher {

    // 固定混淆密钥（长度 16 字节，与 Base64 输出无直接关联）
    private const val SALT = "R1ng0nLLM!2026*"

    /**
     * 加密：XOR 异或 + Base64 编码。
     * 输入空串时返回空串。
     */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val data = plain.toByteArray(Charsets.UTF_8)
        val salt = SALT.toByteArray(Charsets.UTF_8)
        for (i in data.indices) {
            data[i] = (data[i].toInt() xor salt[i % salt.size].toInt()).toByte()
        }
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    /**
     * 解密：Base64 解码 + XOR 异或。
     * 输入空串或解密失败均返回空串。
     */
    fun decrypt(cipher: String): String {
        if (cipher.isEmpty()) return ""
        return try {
            val data = Base64.decode(cipher, Base64.NO_WRAP)
            val salt = SALT.toByteArray(Charsets.UTF_8)
            for (i in data.indices) {
                data[i] = (data[i].toInt() xor salt[i % salt.size].toInt()).toByte()
            }
            String(data, Charsets.UTF_8)
        } catch (_: Exception) {
            // 数据损坏或密钥不匹配时静默降级
            ""
        }
    }
}
