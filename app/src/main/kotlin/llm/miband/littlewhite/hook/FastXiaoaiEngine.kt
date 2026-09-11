package llm.miband.littlewhite.hook

import llm.miband.littlewhite.config.ConfigStore
import llm.miband.littlewhite.log.LogCollector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 手机端小爱「快速模式」回答引擎（voiceassist 主进程内，A 方案真注入 + 流式捕获）。
 *
 * 注入：把系统提示词拼进 query 前缀，反射 v51.m0.sendNlpRequest(...setIsAddQueryCard(false))。
 * 捕获（双路径，取先拿到内容者）：
 *   - n31.o0.H0(Instruction)：AIVS 入站分发（UI 之前），后台也能收到云端回流 → onToast
 *   - ic1.a.sendStreamData：RN bridge 渲染层（前台才有完整分片）→ onStreamData
 *   两者都汇入 feedChunk 按 dialog_id 聚合 markdown_text，遇 <FINAL> 或够长完成。
 * 结果做 HTML/markdown 清洗 + 截断，保证手环小屏能显示。
 */
object FastXiaoaiEngine {

    private const val TAG = "FastXiaoaiEngine"

    const val INJECTION_READY = true

    private const val WAIT_MS = 15_000L
    private const val MAX_ANSWER_LEN = 100

    /** 静默收口窗口：已聚合到内容后，若超过该时长无新分片，即按现有内容完成（短答案无句末标点/无 <FINAL> 时兜底） */
    private const val IDLE_FINISH_MS = 1_500L

    private var classLoader: ClassLoader? = null
    private var config: ConfigStore? = null

    private class Waiter {
        val latch = CountDownLatch(1)
        val dialog = AtomicReference<String?>(null)
        val sb = StringBuilder()
        val result = AtomicReference<String?>(null)
        @Volatile var inputQuery: String = ""
        @Volatile var lastChunkAt: Long = 0L
    }

    private val waiter = AtomicReference<Waiter?>(null)

    fun init(cl: ClassLoader, cfg: ConfigStore) {
        classLoader = cl
        config = cfg
    }

    /** 共享聚合：按注入后首个 dialog_id 累积分片，<FINAL> 或够长即完成；跳过回显首片 */
    private fun feedChunk(dialogId: String, text: String?) {
        val w = waiter.get() ?: return
        if (dialogId.isEmpty() || text == null) return
        val cur = w.dialog.get()
        if (cur == null) w.dialog.set(dialogId) else if (cur != dialogId) return
        // 手机端会把注入的 query 作为首个分片回显；仅当首片"恰好等于"原始问题才判为回显丢弃。
        // 不能用 contains/startsWith：答案以问题开头时（如问"你好"答"你好呀…"）会误删首个有效分片。
        if (w.sb.isEmpty() && w.inputQuery.isNotEmpty() && text.trim() == w.inputQuery) {
            LogCollector.i(TAG, "跳过回显分片 len=${text.length}")
            return
        }
        LogCollector.i(TAG, "chunk dlg=${dialogId.take(8)} len=${text.length} t=${text.take(40)}")
        when {
            text == "<FINAL>" -> finish(w)
            text.isNotEmpty() -> {
                w.sb.append(text)
                w.lastChunkAt = System.currentTimeMillis()
                val s = w.sb
                // 够长、或已累积成句且以句末标点收尾，即完成（后台常无独立 <FINAL> 分片）
                if (s.length >= MAX_ANSWER_LEN ||
                    (s.length >= 24 && s.last() in "。！？!?")
                ) {
                    finish(w)
                }
            }
        }
    }

    private fun finish(w: Waiter) {
        if (w.result.get() != null) return
        w.result.set(cleanAndTruncate(w.sb.toString()))
        w.latch.countDown()
    }

    /**
     * 静默收口看门狗：部分短答案（如"1+1等于2"）无句末标点、也不会收到 <FINAL>，
     * 旧逻辑只能干等到 WAIT_MS 超时。这里在已聚合到内容后，若 IDLE_FINISH_MS 内无新分片，
     * 即认为流已结束并按现有内容完成。仅在 sb 非空时才收口，避免把纯回显当成答案。
     */
    private fun startIdleWatchdog(w: Waiter) {
        val deadline = System.currentTimeMillis() + WAIT_MS + 1_000L
        Thread({
            while (true) {
                try {
                    Thread.sleep(200)
                } catch (_: Throwable) {
                    return@Thread
                }
                if (w.latch.count == 0L) return@Thread
                if (System.currentTimeMillis() > deadline) return@Thread
                if (w.sb.isEmpty()) continue
                val last = w.lastChunkAt
                if (last > 0 && System.currentTimeMillis() - last >= IDLE_FINISH_MS) {
                    LogCollector.i(TAG, "静默 ${IDLE_FINISH_MS}ms 无新分片，按已聚合内容收口 len=${w.sb.length}")
                    finish(w)
                    return@Thread
                }
            }
        }, "FastXiaoaiIdle").apply { isDaemon = true }.start()
    }

    /** H0 入站路径：fullName 以 Template 开头时投递（ToastStream 分片 / Toast 单条） */
    fun onToast(fullName: String?, dialogId: String?, text: String?) {
        if (fullName == null || !fullName.startsWith("Template")) return
        feedChunk(dialogId.orEmpty(), text)
    }

    /** sendStreamData 渲染层路径：解析 ToastStream JSON 分片 */
    fun onStreamData(json: String?) {
        if (json.isNullOrBlank() || !json.contains("ToastStream")) return
        try {
            val root = org.json.JSONObject(json)
            val header = root.optJSONObject("header") ?: return
            val dialogId = header.optString("dialog_id", "")
            val md = root.optJSONObject("payload")?.optString("markdown_text", "")
            feedChunk(dialogId, md)
        } catch (_: Throwable) {
        }
    }

    /** 去 HTML 标签与 markdown 符号、折叠空白、截断到手环可显示长度 */
    private fun cleanAndTruncate(raw: String): String? {
        var s = raw.replace(Regex("<[^>]*>"), " ")
        s = s.replace(Regex("<[^>]*$"), "")
        s = s.replace(Regex("[#*`>|_]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        if (s.length > MAX_ANSWER_LEN) {
            s = s.substring(0, MAX_ANSWER_LEN).trimEnd() + "…"
        }
        return s.takeIf { it.isNotBlank() }
    }

    fun ask(query: String): String? {
        val cl = classLoader ?: return null
        if (query.isBlank()) return null
        val w = Waiter()
        w.inputQuery = query
        waiter.set(w)
        startIdleWatchdog(w)
        try {
            if (!inject(cl, query)) {
                waiter.set(null)
                return null
            }
            val ok = w.latch.await(WAIT_MS, TimeUnit.MILLISECONDS)
            val ans = if (ok) w.result.get() else null
            if (ans == null) LogCollector.w(TAG, "fast 注入后未聚合到流式回答")
            return ans
        } catch (t: Throwable) {
            LogCollector.e(TAG, "ask 异常", t)
            return null
        } finally {
            waiter.compareAndSet(w, null)
        }
    }

    /** 把系统提示词并入 query 前缀（小爱无 system_prompt 字段），再反射 sendNlpRequest 注入 */
    private fun inject(cl: ClassLoader, query: String): Boolean = try {
        val sys = config?.getSystemPrompt()?.trim().orEmpty()
        val prefixed = if (sys.isEmpty()) query else "$sys\n用户问题：$query"
        val m0 = cl.loadClass("v51.m0")
        val builderCls = cl.loadClass("v51.m0\$e")
        val dCls = cl.loadClass("v51.m0\$d")
        val builder = builderCls.getDeclaredConstructor().newInstance()
        builderCls.getMethod("setQuery", String::class.java).invoke(builder, prefixed)
        try {
            builderCls.getMethod("setIsAddQueryCard", Boolean::class.javaPrimitiveType)
                .invoke(builder, false)
        } catch (_: Throwable) {
        }
        val params = builderCls.getMethod("build").invoke(builder)
        m0.getMethod("sendNlpRequest", dCls).invoke(null, params)
        LogCollector.i(TAG, "fast 注入 sendNlpRequest 成功 query=${query.take(30)}")
        true
    } catch (t: Throwable) {
        LogCollector.e(TAG, "fast 注入失败(v51.m0 类名可能随版本变)", t)
        false
    }
}
