package com.zhisiluo.superxiaoai.config

/**
 * 超级小爱 全部配置项的键名与默认值定义（单一来源）。
 *
 * 键名与默认值同时被 Hook 进程（XposedModule 只读侧）和
 * 模块 App 进程（XposedService 读写侧）引用，两处必须保持一致。
 *
 * 模块定位：把本机超级小爱（宿主 com.miui.voiceassist）的回答能力，
 * 封装为标准的 OpenAI 兼容 HTTP 接口供局域网 AI 调用。
 * 因此配置项以「HTTP 服务端 + 回答引擎」为主，不再包含远程 LLM API 参数。
 */
object ConfigKeys {

    // ---------- 模块总开关 ----------
    const val KEY_ENABLED = "enabled"

    // ---------- OpenAI 兼容服务端 ----------
    /** 监听端口 */
    const val KEY_OPENAI_PORT = "openai_port"

    /** 是否允许局域网访问：false 仅绑定 127.0.0.1，true 绑定 0.0.0.0 */
    const val KEY_OPENAI_LAN = "openai_lan"

    /** 访问令牌：空 = 不校验；非空时要求 Authorization: Bearer <token> */
    const val KEY_OPENAI_TOKEN = "openai_token"

    /** 默认回答引擎："miclaw"（osbot 大模型）/ "fast"（手机端传统云端小爱） */
    const val KEY_XIAOAI_ENGINE = "xiaoai_engine"

    /** 回答最大长度：0 = 不截断，原样返回引擎产出的完整回答 */
    const val KEY_OPENAI_MAX_ANSWER_LEN = "openai_max_answer_len"

    /** fast 引擎等待上限（毫秒）：传统云端小爱回流较快 */
    const val KEY_FAST_WAIT_MS = "fast_wait_ms"

    /** miclaw 引擎等待上限（毫秒）：osbot 大模型生成更慢，需给足时间 */
    const val KEY_MICLAW_WAIT_MS = "miclaw_wait_ms"

    /** 是否把请求体中的 system 提示词透传给小爱（拼在提问前） */
    const val KEY_OPENAI_FORWARD_SYSTEM = "openai_forward_system"

    /** 是否把历史消息拼进提问（小爱侧无会话句柄，只能扁平化拼接） */
    const val KEY_OPENAI_FORWARD_HISTORY = "openai_forward_history"

    // ---------- 系统提示词 ----------
    /** 模块内置的系统提示词：拼在每次提问前，约束小爱的回答风格 */
    const val KEY_SYSTEM_PROMPT = "system_prompt"

    // ---------- 主题与视觉（移植 KernelSU ColorPalette） ----------
    /** 主题模式：与 Miuix ColorSchemeMode 枚举名对应，String 存储避免依赖 ui 包 */
    const val KEY_THEME_MODE = "theme_mode"

    /** Monet 动态取色种子色（ARGB Int，0 = 跟随系统壁纸） */
    const val KEY_KEY_COLOR = "key_color"

    /** 调色板风格（对应 Miuix ThemePaletteStyle 枚举名） */
    const val KEY_PALETTE_STYLE = "palette_style"

    /** 动态取色规范（Spec2021 / Spec2025，对应 Miuix ThemeColorSpec） */
    const val KEY_COLOR_SPEC = "color_spec"

    /** Monet 动态取色总开关（KSU 风格：TabRow 选 System/Light/Dark，Monet 开关偏移 +3） */
    const val KEY_MIUIX_MONET = "miuix_monet"

    const val KEY_ENABLE_BLUR = "enable_blur"
    const val KEY_FLOATING_BOTTOM_BAR = "enable_floating_bottom_bar"
    const val KEY_FLOATING_BOTTOM_BAR_BLUR = "enable_floating_bottom_bar_blur"
    const val KEY_ENABLE_NAVIGATION_BADGE = "enable_navigation_badge"
    const val KEY_ENABLE_PREDICTIVE_BACK = "enable_predictive_back"
    const val KEY_PAGE_SCALE = "page_scale"

    // ---------- 默认值 ----------
    const val DEFAULT_ENABLED = true

    /** 默认监听端口 */
    const val DEFAULT_OPENAI_PORT = 6267

    /** 默认仅本机可访问，用户显式开启后才监听局域网 */
    const val DEFAULT_OPENAI_LAN = false

    /** 默认不校验令牌（家庭内网自用，降低接入门槛） */
    const val DEFAULT_OPENAI_TOKEN = ""

    /** 默认引擎：miclaw（osbot 大模型，回答质量更高） */
    const val DEFAULT_XIAOAI_ENGINE = "miclaw"

    /** 默认不截断，返回完整回答 */
    const val DEFAULT_OPENAI_MAX_ANSWER_LEN = 0

    /** fast 引擎默认等待上限：30 秒 */
    const val DEFAULT_FAST_WAIT_MS = 30_000L

    /** miclaw 引擎默认等待上限：45 秒 */
    const val DEFAULT_MICLAW_WAIT_MS = 45_000L

    /** 默认不透传 system：小爱自带助手人格，透传易与内置提示词冲突 */
    const val DEFAULT_OPENAI_FORWARD_SYSTEM = false

    /** 默认透传历史：多轮对话是 OpenAI 客户端的常见预期 */
    const val DEFAULT_OPENAI_FORWARD_HISTORY = false

    /** 内置系统提示词：约束小爱按助手口吻作答 */
    const val DEFAULT_SYSTEM_PROMPT =
        "你是一个语音助手，正在通过接口回答用户的问题。\n" +
        "回答要简洁准确，直接给出结论，不要寒暄，不要询问是否需要更多帮助。"

    /** 默认主题模式：system（跟随系统深浅色，无动态取色） */
    const val DEFAULT_THEME_MODE = "system"

    /** 默认种子色：0 表示跟随系统壁纸 */
    const val DEFAULT_KEY_COLOR = 0L

    /** 默认调色板风格 */
    const val DEFAULT_PALETTE_STYLE = "TonalSpot"

    /** 默认动态取色规范 */
    const val DEFAULT_COLOR_SPEC = "Spec2021"

    /** Monet 动态取色总开关默认关闭 */
    const val DEFAULT_MIUIX_MONET = false

    // ---------- 视觉效果默认值（与 KernelSU 持久化默认一致） ----------
    const val DEFAULT_ENABLE_BLUR = false
    const val DEFAULT_FLOATING_BOTTOM_BAR = false
    const val DEFAULT_FLOATING_BOTTOM_BAR_BLUR = false
    const val DEFAULT_ENABLE_NAVIGATION_BADGE = true
    const val DEFAULT_ENABLE_PREDICTIVE_BACK = false
    const val DEFAULT_PAGE_SCALE = 1.0f
}