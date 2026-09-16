@file:Suppress("unused")

package llm.miband.littlewhite.hook

import android.content.Context
import llm.miband.littlewhite.log.LogCollector
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 本机小爱回答引擎门面（voiceassist 主进程内），对上层 [OpenAiServer] 提供统一问答入口。
 *
 * 两个引擎：
 * - `miclaw`：超级小爱 Agent（osbot）。通过宿主 ExternalAgentClient openSession + submit 驱动，
 *   能真正理解并执行手机操作（打开 App、设闹钟、发消息等），是"操控手机"的主力；
 * - `fast`  ：小爱快速问答（云端）。走 [FastXiaoaiEngine] 的 nlp 注入 + 流式捕获，纯聊天。
 *
 * 计费标识复用小米为穿戴设备预设的官方组合（见宿主 LyraAgentService）：
 * agentId="com.xiaomi.lyrabridge-watch"（手表语音助手）+ bizId="miwear" + featureId="miclaw"，
 * 否则 osbot 调 LLM 网关会返回 400 code=20003 "Invalid bizId or featureId"。
 */
object XiaoaiEngine {

    private const val TAG = "XiaoaiEngine"

    private const val CLIENT_CLASS = "com.aios.apptoolsdk.ExternalAgentClient"
    private const val APPMETA_CLASS = "com.aios.apptoolsdk.AppMeta"

    /** 官方穿戴 agent：targetPackage + tag => agentId="com.xiaomi.lyrabridge-watch" */
    private const val AGENT_TARGET_PACKAGE = "com.xiaomi.lyrabridge"
    private const val AGENT_TAG = "watch"

    /** 小米为穿戴接入 osbot 预设的合法计费标识 */
    private const val BIZ_ID = "miwear"
    private const val FEATURE_ID = "miclaw"

    private const val APP_NAME = "环上LLM"

    /** osbot 连接握手等待上限 */
    private const val CONNECT_TIMEOUT_MS = 5_000L

    @Volatile
    private var started = false

    private var clientClass: Class<*>? = null
    private var appMetaClass: Class<*>? = null

    @Volatile
    private var client: Any? = null

    /**
     * 初始化并连接宿主的 ExternalAgentClient（同 UID，可过 CallerVerifier）。
     * 全程异常隔离：连接失败只是让 miclaw 不可用，不影响 fast 引擎与 HTTP 服务。
     */
    fun start(classLoader: ClassLoader, context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            try {
                clientClass = classLoader.loadClass(CLIENT_CLASS)
                appMetaClass = classLoader.loadClass(APPMETA_CLASS)
                connectClient(classLoader, context)
                LogCollector.i(TAG, "引擎就绪，osbot 客户端连接=${client != null}")
            } catch (t: Throwable) {
                LogCollector.e(TAG, "osbot 初始化失败（miclaw 引擎将不可用）", t)
            }
        }
    }

    /** osbot 通道是否已连通（miclaw 可用性标志） */
    fun isOsbotConnected(): Boolean = client != null

    /**
     * 统一问答入口。
     * @param engine    "miclaw"（Agent 操控手机）/ "fast"（快速问答）
     * @param maxLen    返回文本长度上限，<=0 表示不截断
     * @param timeoutMs 单次生成等待上限
     * @return 回答文本；失败或超时返回 null（由上层转成 OpenAI 错误响应）
     */
    fun ask(engine: String, query: String, maxLen: Int, timeoutMs: Long): String? {
        if (query.isBlank()) return null
        val raw = try {
            when (engine) {
                "fast" -> FastXiaoaiEngine.askLong(query, timeoutMs = timeoutMs)
                else -> askOsbot(query, timeoutMs)
            }
        } catch (t: Throwable) {
            LogCollector.e(TAG, "引擎 $engine 调用异常", t)
            null
        } ?: return null
        val text = raw.trim()
        if (text.isEmpty()) return null
        return if (maxLen > 0 && text.length > maxLen) {
            text.substring(0, maxLen).trimEnd() + "…"
        } else {
            text
        }
    }

    /** 反射创建并连接 ExternalAgentClient，等待 onConnected */
    private fun connectClient(classLoader: ClassLoader, context: Context) {
        val cc = clientClass ?: return
        val instance = cc.getMethod("create", Context::class.java).invoke(null, context)
        val listenerIface = cc.declaredClasses.first { it.simpleName == "ConnectionListener" }
        val connected = CountDownLatch(1)
        val listener = Proxy.newProxyInstance(
            classLoader, arrayOf(listenerIface),
            InvocationHandler { _, method, _ ->
                if (method.name == "onConnected") connected.countDown()
                null
            },
        )
        cc.getMethod("setConnectionListener", listenerIface).invoke(instance, listener)
        cc.getMethod("connect").invoke(instance)
        val ok = connected.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        client = if (ok) instance else null
        if (!ok) LogCollector.w(TAG, "ExternalAgentClient 连接超时，miclaw 通道不可用")
    }

    /** 反射走 ExternalAgentClient 的 openSession + submit，阻塞取 onComplete 文本 */
    private fun askOsbot(query: String, timeoutMs: Long): String? {
        val cc = clientClass ?: return null
        val c = client ?: run {
            LogCollector.w(TAG, "osbot 未连接，miclaw 请求被丢弃")
            return null
        }
        val metaClass = appMetaClass ?: return null
        val wait = timeoutMs.coerceIn(5_000L, 180_000L)
        try {
            val builderClass = metaClass.declaredClasses.first { it.simpleName == "Builder" }
            val builder = builderClass.getDeclaredConstructor().newInstance()
            builderClass.getMethod("appName", String::class.java).invoke(builder, APP_NAME)
            builderClass.getMethod("targetPackage", String::class.java).invoke(builder, AGENT_TARGET_PACKAGE)
            builderClass.getMethod("tag", String::class.java).invoke(builder, AGENT_TAG)
            builderClass.getMethod("bizId", String::class.java).invoke(builder, BIZ_ID)
            builderClass.getMethod("featureId", String::class.java).invoke(builder, FEATURE_ID)
            val meta = builderClass.getMethod("build").invoke(builder)

            val sessionId = cc.getMethod(
                "openSession", metaClass, Boolean::class.javaPrimitiveType,
            ).invoke(c, meta, false) as? String
            if (sessionId.isNullOrEmpty() || sessionId.startsWith("error:")) {
                LogCollector.w(TAG, "openSession 失败: $sessionId")
                return null
            }

            val callbackIface = cc.declaredClasses.first { it.simpleName == "Callback" }
            val latch = CountDownLatch(1)
            val answerRef = AtomicReference<String?>(null)
            val cb = Proxy.newProxyInstance(
                cc.classLoader, arrayOf(callbackIface),
                InvocationHandler { _, method, args ->
                    when (method.name) {
                        "onComplete" -> {
                            answerRef.set(args?.getOrNull(1) as? String)
                            latch.countDown()
                        }

                        "onError" -> {
                            answerRef.set(null)
                            latch.countDown()
                        }
                    }
                    null
                },
            )
            cc.getMethod("submit", String::class.java, String::class.java, callbackIface)
                .invoke(c, sessionId, query, cb)
            val done = latch.await(wait, TimeUnit.MILLISECONDS)
            try {
                cc.getMethod("closeSession", String::class.java).invoke(c, sessionId)
            } catch (_: Throwable) {
            }
            if (!done) LogCollector.w(TAG, "osbot 生成超时（${wait}ms）")
            return answerRef.get()?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            LogCollector.e(TAG, "askOsbot 异常", t)
            return null
        }
    }
}
