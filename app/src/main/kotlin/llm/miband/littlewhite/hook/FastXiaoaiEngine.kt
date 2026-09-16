package llm.miband.littlewhite.hook

import llm.miband.littlewhite.config.ConfigStore
import llm.miband.littlewhite.log.LogCollector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

    /** 静默收口窗口：已聚合到内容后，若超过该时长无新分片，即按现有内容完成 */
    private const val IDLE_FINISH_MS = 1_500L

    /**
     * 回显判定阈值：累积分片与注入文本至少重合这么多字符，才认定为「小爱回显回放」。
     * 取 12 是为了避免把恰好以「你/你是」开头的真实答案误判成回显而丢掉首字。
     */
    private const val ECHO_MIN_MATCH = 12

    /** 未认领的 waiter 只在该时长内接受分片接管，避免上一条请求的迟到分片污染新请求 */
    private const val CLAIM_WINDOW_MS = 10_000L

    /** API 模式默认等待上限（长回答云端回流更久） */
    private const val API_WAIT_MS = 30_000L

    /** API 模式静默收口窗口：长回答分片间隔明显大于短答，取更长避免被拦腰截断 */
    private const val API_IDLE_FINISH_MS = 2_500L

    private var classLoader: ClassLoader? = null
    private var config: ConfigStore? = null

    /**
     * @param maxLen       返回长度上限，<=0 表示不截断（API 模式）
     * @param keepMarkdown true 保留 markdown 原文结构（API 模式）；false 压成手环可读的纯文本
     * @param idleFinishMs 聚合到内容后，多久无新分片即认为流已结束
     */
    private class Waiter(
        val id: Int,
        val maxLen: Int,
        val keepMarkdown: Boolean,
        val idleFinishMs: Long,
    ) {
        val latch = CountDownLatch(1)
        val dialog = AtomicReference<String?>(null)
        val sb = StringBuilder()
        /** 回显回放缓冲：累积到与注入文本分叉为止，分叉前的部分一律丢弃 */
        val echoBuf = StringBuilder()
        val result = AtomicReference<String?>(null)
        @Volatile var inputQuery: String = ""
        @Volatile var injectedQuery: String = ""
        @Volatile var echoDone: Boolean = false
        @Volatile var lastChunkAt: Long = 0L
        val createdAt: Long = System.currentTimeMillis()
    }

    /**
     * 并发等待槽。桥服务端是多线程（每个连接一个线程），同一时刻可能有多条请求在途。
     * 旧实现用单个 AtomicReference，新请求直接顶掉旧请求，导致旧请求一个分片都收不到、干等超时。
     * 现改为：按 dialog_id 路由，未认领的空槽由最早创建的 waiter 顺序接管。
     */
    private val pending = ConcurrentHashMap<Int, Waiter>()
    private val seq = AtomicInteger(0)

    fun init(cl: ClassLoader, cfg: ConfigStore) {
        classLoader = cl
        config = cfg
    }

    /**
     * 归属分片到具体 waiter：
     *  1) 已认领该 dialog_id 的 waiter 直接命中（同一请求的后续分片）；
     *  2) 分片内容恰好是某未认领 waiter 注入文本的前缀 → 这是小爱回显，按内容精确认领，
     *     并发下不会张冠李戴；
     *  3) 兜底：最早创建且未认领、且仍在认领窗口内的 waiter（无回显场景）。
     */
    private fun route(dialogId: String, text: String): Waiter? {
        val list = pending.values.sortedBy { it.id }
        list.firstOrNull { it.dialog.get() == dialogId }?.let { return it }
        list.firstOrNull { it.dialog.get() == null && it.injectedQuery.startsWith(text) }?.let { w ->
            if (w.dialog.compareAndSet(null, dialogId)) return w
        }
        val free = list.firstOrNull {
            it.dialog.get() == null && System.currentTimeMillis() - it.createdAt <= CLAIM_WINDOW_MS
        } ?: return null
        return if (free.dialog.compareAndSet(null, dialogId)) free else null
    }

    /** 共享聚合：按 dialog_id 归入对应 waiter，剥离回显后 <FINAL> 或够长即完成 */
    private fun feedChunk(dialogId: String, text: String?) {
        if (dialogId.isEmpty() || text.isNullOrEmpty()) return
        val w = route(dialogId, text) ?: return
        if (w.echoDone || w.injectedQuery.isEmpty()) {
            appendAnswer(w, dialogId, text)
            return
        }
        // 小爱会把注入的整段 query（系统提示词 + 「用户问题：xxx」）原样回显成若干分片。
        // 累积比对注入文本：仍是其前缀 → 纯回显，丢弃；分叉 → 剥掉前缀，尾巴才是真实答案。
        w.echoBuf.append(text)
        val cpl = commonPrefixLen(w.echoBuf, w.injectedQuery)
        if (cpl >= w.echoBuf.length) {
            if (w.echoBuf.length >= w.injectedQuery.length) w.echoDone = true
            LogCollector.i(TAG, "#${w.id} 跳过回显分片 len=${text.length}")
            return
        }
        w.echoDone = true
        if (cpl >= ECHO_MIN_MATCH) {
            val tail = w.echoBuf.substring(cpl)
            w.echoBuf.setLength(0)
            LogCollector.i(TAG, "#${w.id} 剥离回显前缀 ${cpl} 字，剩余答案 len=${tail.length}")
            if (tail.isNotEmpty()) appendAnswer(w, dialogId, tail)
        } else {
            // 与注入文本几乎不重合：本条本就没有回显，整段都是答案
            val buf = w.echoBuf.toString()
            w.echoBuf.setLength(0)
            appendAnswer(w, dialogId, buf)
        }
    }

    private fun appendAnswer(w: Waiter, dialogId: String, text: String) {
        if (text.isEmpty()) return
        if (text == "<FINAL>") {
            finish(w)
            return
        }
        LogCollector.i(TAG, "#${w.id} chunk dlg=${dialogId.take(8)} len=${text.length} t=${text.take(40)}")
        w.sb.append(text)
        w.lastChunkAt = System.currentTimeMillis()
        val s = w.sb
        // 够长即完成；手环短答模式下另允许"已累积成句且以句末标点收尾"提前收口
        // （后台常无独立 <FINAL> 分片）。API 模式保留完整回答，只走 <FINAL> 与静默收口。
        val longEnough = w.maxLen > 0 && s.length >= w.maxLen
        val sentenceDone = !w.keepMarkdown && s.length >= 24 && s.last() in "。！？!?"
        if (longEnough || sentenceDone) {
            finish(w)
        }
    }

    private fun commonPrefixLen(a: CharSequence, b: String): Int {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return i
    }

    private fun finish(w: Waiter) {
        if (w.result.get() != null) return
        pending.remove(w.id)
        w.result.set(clean(w.sb.toString(), w.keepMarkdown, w.maxLen))
        w.latch.countDown()
    }

    /**
     * 静默收口看门狗：部分短答案（如"1+1等于2"）无句末标点、也不会收到 <FINAL>，
     * 旧逻辑只能干等到超时。这里在已聚合到内容后，若 idleFinishMs 内无新分片，
     * 即认为流已结束并按现有内容完成。仅在 sb 非空时才收口，避免把纯回显当成答案。
     * 到达等待上限时同样按已聚合内容收口，确保长回答不会因缺少 <FINAL> 被整条丢弃。
     */
    private fun startIdleWatchdog(w: Waiter, waitMs: Long) {
        val deadline = System.currentTimeMillis() + waitMs + 1_000L
        Thread({
            while (true) {
                try {
                    Thread.sleep(200)
                } catch (_: Throwable) {
                    return@Thread
                }
                if (w.latch.count == 0L) return@Thread
                if (System.currentTimeMillis() > deadline) {
                    if (w.sb.isNotEmpty()) {
                        LogCollector.i(TAG, "#${w.id} 到达等待上限，按已聚合内容收口 len=${w.sb.length}")
                        finish(w)
                    }
                    return@Thread
                }
                if (w.sb.isEmpty()) continue
                val last = w.lastChunkAt
                if (last > 0 && System.currentTimeMillis() - last >= w.idleFinishMs) {
                    LogCollector.i(TAG, "#${w.id} 静默 ${w.idleFinishMs}ms 无新分片，按已聚合内容收口 len=${w.sb.length}")
                    finish(w)
                    return@Thread
                }
            }
        }, "FastXiaoaiIdle").apply { isDaemon = true }.start()
    }
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

    /**
     * 结果清洗。
     * - 手环短答模式（keepMarkdown=false）：去 HTML 标签与 markdown 符号、折叠空白、按 maxLen 截断；
     * - API 模式（keepMarkdown=true）：只去 HTML 标签与首尾空白，保留 markdown 原文与换行。
     */
    private fun clean(raw: String, keepMarkdown: Boolean, maxLen: Int): String? {
        var s = if (keepMarkdown) {
            raw.replace(Regex("<[^>]*>"), "")
        } else {
            raw.replace(Regex("<[^>]*>"), " ")
        }
        s = s.replace(Regex("<[^>]*$"), "")
        if (keepMarkdown) {
            s = s.trim()
        } else {
            s = s.replace(Regex("[#*`>|_]"), " ")
            s = s.replace(Regex("\\s+"), " ").trim()
        }
        if (maxLen > 0 && s.length > maxLen) {
            s = s.substring(0, maxLen).trimEnd() + "…"
        }
        return s.takeIf { it.isNotBlank() }
    }

    /** 手环短答语义：截断到可显示长度、压成纯文本 */
    fun ask(query: String): String? =
        askInternal(query, MAX_ANSWER_LEN, false, WAIT_MS, IDLE_FINISH_MS)

    /**
     * API 模式：面向 OpenAI 接口调用方。
     * 默认不截断、保留 markdown 原文，等待与静默窗口也更宽松——
     * 长回答的分片间隔可能超过短答模式的静默阈值，用短答参数会把回答拦腰截断。
     */
    fun askLong(
        query: String,
        maxLen: Int = 0,
        keepMarkdown: Boolean = true,
        timeoutMs: Long = API_WAIT_MS,
        idleMs: Long = API_IDLE_FINISH_MS,
    ): String? = askInternal(query, maxLen, keepMarkdown, timeoutMs, idleMs)

    private fun askInternal(
        query: String,
        maxLen: Int,
        keepMarkdown: Boolean,
        waitMs: Long,
        idleMs: Long,
    ): String? {
        val cl = classLoader ?: return null
        if (query.isBlank()) return null
        val w = Waiter(seq.incrementAndGet(), maxLen, keepMarkdown, idleMs)
        w.inputQuery = query
        w.injectedQuery = buildInjectedQuery(query)
        pending[w.id] = w
        startIdleWatchdog(w, waitMs)
        try {
            if (!inject(cl, w.injectedQuery)) {
                return null
            }
            if (!w.latch.await(waitMs, TimeUnit.MILLISECONDS) && w.sb.isNotEmpty()) {
                // 已拿到部分回答但流迟迟不收尾：按现有内容收口，好过整条丢弃
                LogCollector.i(TAG, "#${w.id} 等待 ${waitMs}ms 超时，按已聚合内容收口 len=${w.sb.length}")
                finish(w)
            }
            val ans = w.result.get()
            if (ans == null) LogCollector.w(TAG, "#${w.id} fast 注入后未聚合到流式回答 query=${query.take(20)}")
            return ans
        } catch (t: Throwable) {
            LogCollector.e(TAG, "#${w.id} ask 异常", t)
            return null
        } finally {
            pending.remove(w.id)
        }
    }

    /** 把系统提示词并入 query 前缀（小爱无 system_prompt 字段）；该整段会被回显，由 feedChunk 剥离 */
    private fun buildInjectedQuery(query: String): String {
        val sys = config?.getSystemPrompt()?.trim().orEmpty()
        return if (sys.isEmpty()) query else "$sys\n用户问题：$query"
    }

    /** 反射 sendNlpRequest 注入（入参已是拼好提示词的完整文本） */
    private fun inject(cl: ClassLoader, prefixed: String): Boolean = try {
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
        LogCollector.i(TAG, "fast 注入 sendNlpRequest 成功 query=${prefixed.take(24)}…")
        true
    } catch (t: Throwable) {
        LogCollector.e(TAG, "fast 注入失败(v51.m0 类名可能随版本变)", t)
        false
    }
}