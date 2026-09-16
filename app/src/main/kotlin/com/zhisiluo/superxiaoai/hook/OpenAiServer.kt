@file:Suppress("unused")

package com.zhisiluo.superxiaoai.hook

import com.zhisiluo.superxiaoai.config.ConfigStore
import com.zhisiluo.superxiaoai.log.LogCollector
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 标准 OpenAI 兼容接口服务端（运行在 voiceassist 主进程内）。
 *
 * 暴露给局域网内的其它 AI（Home Assistant / Open WebUI / 自建 Agent 等）调用，
 * 由本机超级小爱真实执行，因此"调用接口 = 操控手机"。
 *
 * 端点：
 * - `POST /v1/chat/completions` 对话补全（支持 stream 伪流式 / SSE）
 * - `GET  /v1/models`           可用模型列表
 * - `GET  /status`              服务运行状态与调用统计（供模块 App 状态页读取）
 * - `GET  /`                    纯文本引导页
 *
 * 鉴权：`Authorization: Bearer <token>` 或 `x-api-key: <token>`；token 为空则不校验。
 * 协议手写实现（零依赖），仅支持 Content-Length 定长的请求体，够覆盖所有主流 SDK。
 */
object OpenAiServer {

    private const val TAG = "OpenAiServer"

    /** 最大并发在途请求数，超出直接 429，避免把宿主进程拖垮 */
    private const val MAX_CONCURRENT = 4
    /** 单连接读写超时（必须大于引擎最长等待时间） */
    private const val SOCKET_TIMEOUT_MS = 120_000
    /** 请求头上限，防畸形请求撑爆内存 */
    private const val MAX_HEADER_BYTES = 64 * 1024
    /** 请求体上限 */
    private const val MAX_BODY_BYTES = 8 * 1024 * 1024
    /** 内存中保留的最近调用记录条数 */
    private const val RECENT_CAPACITY = 20

    /** 模型 id：miclaw / 超级小爱 Agent（可真正操控手机） */
    const val MODEL_MICLAW = "xiaoai"
    /** 模型 id：fast / 小爱快速问答（纯聊天，不操控设备） */
    const val MODEL_FAST = "xiaoai-fast"

    @Volatile
    private var started = false

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var config: ConfigStore? = null

    @Volatile
    private var boundPort = 0

    @Volatile
    private var boundLan = false

    private val inflight = AtomicInteger(0)

    private val statTotal = AtomicInteger(0)
    private val statOk = AtomicInteger(0)
    private val statFail = AtomicInteger(0)
    private val statLatencySum = AtomicLong(0)

    private val recent = ArrayDeque<CallRecord>()
    private val recentLock = Any()

    /** 单条调用记录（仅存内存，重启超级小爱即清空） */
    data class CallRecord(
        val at: Long,
        val model: String,
        val engine: String,
        val query: String,
        val elapsedMs: Long,
        val ok: Boolean,
        val answerLen: Int,
    )

    // ==================== 生命周期 ====================

    /**
     * 启动 HTTP 服务端。幂等；端口被占用等失败只记日志，不影响小爱本体。
     * 配置（端口 / 是否允许局域网）在启动时读取一次，改动后需重启超级小爱生效。
     */
    fun start(cfg: ConfigStore) {
        if (started) return
        synchronized(this) {
            if (started) return
            config = cfg
            val port = cfg.getOpenAiPort()
            val lan = cfg.isOpenAiLan()
            try {
                val bind = InetAddress.getByName(if (lan) "0.0.0.0" else "127.0.0.1")
                val server = ServerSocket(port, 32, bind)
                serverSocket = server
                boundPort = port
                boundLan = lan
                started = true
                Thread({ acceptLoop(server) }, "OpenAiAccept").apply { isDaemon = true }.start()
                LogCollector.i(TAG, "OpenAI 兼容接口已启动 -> http://${if (lan) "0.0.0.0" else "127.0.0.1"}:$port/v1")
            } catch (t: Throwable) {
                LogCollector.e(TAG, "接口启动失败（端口 $port 可能被占用）: ${t.message}", t)
            }
        }
    }

    /** 服务是否已启动 */
    fun isRunning(): Boolean = started && serverSocket?.isClosed == false

    /** 当前监听端口；未启动返回 0 */
    fun port(): Int = if (isRunning()) boundPort else 0

    /** 是否允许局域网访问 */
    fun isLan(): Boolean = boundLan

    /** 停止服务（配置变更后由宿主进程重建时调用） */
    fun stop() {
        val s = serverSocket ?: return
        try {
            s.close()
        } catch (_: Throwable) {
        }
        serverSocket = null
        started = false
        LogCollector.i(TAG, "接口已停止")
    }

    // ==================== 接入循环 ====================

    private fun acceptLoop(server: ServerSocket) {
        while (true) {
            val socket = try {
                server.accept()
            } catch (t: Throwable) {
                // 主动 close 导致的异常 = 正常退出；其它情况继续接受连接
                if (serverSocket !== server || server.isClosed) return
                LogCollector.w(TAG, "accept 异常: ${t.message}")
                continue
            }
            Thread({ handleConnection(socket) }, "OpenAiConn").apply { isDaemon = true }.start()
        }
    }

    // ==================== 单连接处理 ====================

    private fun handleConnection(socket: Socket) {
        try {
            socket.use {
                it.soTimeout = SOCKET_TIMEOUT_MS
                val input = it.getInputStream()
                val output = it.getOutputStream()

                val head = readHead(input)
                if (head == null) {
                    respondError(output, 400, "malformed http header")
                    return
                }
                val lines = head.split("\r\n")
                val parts = lines.firstOrNull().orEmpty().split(" ")
                if (parts.size < 2) {
                    respondError(output, 400, "malformed request line")
                    return
                }
                val method = parts[0].uppercase()
                val path = parts[1].substringBefore('?')
                val headers = parseHeaders(lines)

                // 浏览器跨域预检
                if (method == "OPTIONS") {
                    respond(output, 204, "text/plain", ByteArray(0))
                    return
                }

                val cfg = config
                if (cfg == null) {
                    respondError(output, 503, "server not configured")
                    return
                }
                if (!authorized(headers, cfg)) {
                    respondError(output, 401, "invalid api key")
                    return
                }

                when {
                    method == "GET" && (path == "/v1/models" || path == "/models") ->
                        handleModels(output)

                    method == "GET" && (path == "/status" || path == "/v1/status") ->
                        handleStatus(output)

                    method == "GET" && (path == "/" || path == "/health") ->
                        respond(output, 200, "text/plain; charset=utf-8", hintPage().toByteArray())

                    method == "POST" && (path == "/v1/chat/completions" || path == "/chat/completions") ->
                        handleChat(output, input, headers)

                    method == "POST" && (path == "/stats/clear" || path == "/v1/stats/clear") -> {
                        clearStats()
                        respondJson(
                            output,
                            200,
                            JSONObject().put("ok", true).put("message", "stats cleared"),
                        )
                    }

                    else -> respondError(output, 404, "unknown endpoint: $path")
                }
            }
        } catch (t: Throwable) {
            LogCollector.w(TAG, "连接处理异常: ${t.message}")
        }
    }

    // ==================== 端点实现 ====================

    private fun handleModels(output: OutputStream) {
        val data = JSONArray()
        data.put(modelEntry(MODEL_MICLAW))
        data.put(modelEntry(MODEL_FAST))
        val body = JSONObject()
            .put("object", "list")
            .put("data", data)
        respondJson(output, 200, body)
    }

    private fun modelEntry(id: String): JSONObject = JSONObject()
        .put("id", id)
        .put("object", "model")
        .put("created", 1735689600)
        .put("owned_by", "xiaomi-xiaoai")

    private fun handleStatus(output: OutputStream) {
        val total = statTotal.get()
        val ok = statOk.get()
        val fail = statFail.get()
        val avg = if (total > 0) statLatencySum.get() / total else 0L

        val stats = JSONObject()
            .put("total", total)
            .put("ok", ok)
            .put("fail", fail)
            .put("avg_ms", avg)
            .put("inflight", inflight.get())

        val list = JSONArray()
        synchronized(recentLock) {
            for (r in recent) {
                list.put(
                    JSONObject()
                        .put("at", r.at)
                        .put("model", r.model)
                        .put("engine", r.engine)
                        .put("query", r.query)
                        .put("ms", r.elapsedMs)
                        .put("ok", r.ok)
                        .put("len", r.answerLen),
                )
            }
        }

        val body = JSONObject()
            .put("running", isRunning())
            .put("port", port())
            .put("lan", boundLan)
            .put("engine_default", config?.getXiaoaiEngine() ?: "miclaw")
            .put("osbot_connected", XiaoaiEngine.isOsbotConnected())
            .put("fast_ready", FastXiaoaiEngine.INJECTION_READY)
            .put("stats", stats)
            .put("recent", list)
        respondJson(output, 200, body)
    }

    private fun handleChat(output: OutputStream, input: InputStream, headers: Map<String, String>) {
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len <= 0) {
            respondError(output, 400, "empty request body")
            return
        }
        if (len > MAX_BODY_BYTES) {
            respondError(output, 413, "request body too large")
            return
        }
        val raw = readFully(input, len)
        if (raw == null) {
            respondError(output, 400, "incomplete request body")
            return
        }
        val root = try {
            JSONObject(String(raw, Charsets.UTF_8))
        } catch (t: Throwable) {
            respondError(output, 400, "invalid json body")
            return
        }

        val model = root.optString("model", "").ifBlank { MODEL_MICLAW }
        val stream = root.optBoolean("stream", false)
        val query = buildQuery(root)
        if (query == null) {
            respondError(output, 400, "no user message found in messages")
            return
        }

        // 并发闸门
        if (inflight.incrementAndGet() > MAX_CONCURRENT) {
            inflight.decrementAndGet()
            respondError(output, 429, "too many concurrent requests")
            return
        }

        val cfg = config
        val engine = resolveEngine(model, cfg)
        val maxLen = cfg?.getOpenAiMaxAnswerLen() ?: 0
        val timeoutMs = when (engine) {
            "fast" -> cfg?.getFastWaitMs() ?: 30_000L
            else -> cfg?.getMiclawWaitMs() ?: 45_000L
        }

        val id = "chatcmpl-" + java.util.UUID.randomUUID().toString().replace("-", "").take(24)
        val created = System.currentTimeMillis() / 1000
        val startedAt = System.currentTimeMillis()
        var answer: String? = null
        var failure: String? = null
        try {
            answer = XiaoaiEngine.ask(engine, query, maxLen, timeoutMs)
            if (answer == null) failure = "$engine 引擎未返回内容（小爱未连接或超时）"
        } catch (t: Throwable) {
            failure = "engine error: ${t.message}"
            LogCollector.e(TAG, "引擎调用异常 model=$model", t)
        } finally {
            inflight.decrementAndGet()
        }

        val elapsed = System.currentTimeMillis() - startedAt
        val text = answer
        if (text == null) {
            record(model, engine, query, elapsed, false, 0)
            respondError(output, 502, failure ?: "engine unavailable")
            return
        }
        record(model, engine, query, elapsed, true, text.length)
        LogCollector.i(TAG, "chat ok model=$model engine=$engine ${elapsed}ms len=${text.length}")

        if (stream) {
            respondStream(output, id, created, model, text, query, text)
        } else {
            respondCompletion(output, id, created, model, text, query)
        }
    }

    // ==================== 响应组装 ====================

    private fun respondCompletion(
        output: OutputStream,
        id: String,
        created: Long,
        model: String,
        text: String,
        query: String,
    ) {
        val message = JSONObject()
            .put("role", "assistant")
            .put("content", text)

        val choice = JSONObject()
            .put("index", 0)
            .put("message", message)
            .put("finish_reason", "stop")

        val body = JSONObject()
            .put("id", id)
            .put("object", "chat.completion")
            .put("created", created)
            .put("model", model)
            .put("choices", JSONArray().put(choice))
            .put("usage", usage(query, text))
        respondJson(output, 200, body)
    }

    /**
     * SSE 伪流式：小爱一次返回完整文本，这里按 OpenAI 分片协议补发
     * role 帧 / content 帧 / 结束帧 / [DONE]，保证所有流式客户端能正常解析。
     */
    private fun respondStream(
        output: OutputStream,
        id: String,
        created: Long,
        model: String,
        text: String,
        query: String,
        answer: String,
    ) {
        val head = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream; charset=utf-8\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: close\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Headers: Authorization, Content-Type, x-api-key\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("\r\n")
        }
        output.write(head.toByteArray(Charsets.ISO_8859_1))

        fun send(delta: JSONObject, finish: String?) {
            val choice = JSONObject()
                .put("index", 0)
                .put("delta", delta)
                .put("finish_reason", finish ?: JSONObject.NULL)
            val chunk = JSONObject()
                .put("id", id)
                .put("object", "chat.completion.chunk")
                .put("created", created)
                .put("model", model)
                .put("choices", JSONArray().put(choice))
            output.write("data: $chunk\n\n".toByteArray(Charsets.UTF_8))
            output.flush()
        }

        send(JSONObject().put("role", "assistant").put("content", ""), null)
        send(JSONObject().put("content", text), null)
        send(JSONObject(), "stop")

        val tail = JSONObject()
            .put("id", id)
            .put("object", "chat.completion.chunk")
            .put("created", created)
            .put("model", model)
            .put("choices", JSONArray())
            .put("usage", usage(query, answer))
        output.write("data: $tail\n\n".toByteArray(Charsets.UTF_8))
        output.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
        output.flush()
    }

    /** token 用量为估算值（本地引擎无真实 tokenizer），仅用于满足客户端字段要求 */
    private fun usage(prompt: String, completion: String): JSONObject {
        val p = estimateTokens(prompt)
        val c = estimateTokens(completion)
        return JSONObject()
            .put("prompt_tokens", p)
            .put("completion_tokens", c)
            .put("total_tokens", p + c)
    }

    private fun estimateTokens(s: String): Int = if (s.isEmpty()) 0 else (s.length / 2).coerceAtLeast(1)

    // ==================== 请求解析 ====================

    /**
     * 把 OpenAI messages 数组折算成本地引擎能吃的一段查询文本。
     * - 最后一条 user 消息 = 本次要执行的指令（miclaw 是 Agent，单轮指令效果最好）；
     * - system / 历史轮次默认不拼接，可在设置里开启（对 Agent 类引擎开启可能干扰其决策）。
     */
    private fun buildQuery(root: JSONObject): String? {
        val messages = root.optJSONArray("messages") ?: return null
        val cfg = config
        val forwardSystem = cfg?.isOpenAiForwardSystem() == true
        val forwardHistory = cfg?.isOpenAiForwardHistory() == true

        val sys = StringBuilder()
        val turns = ArrayList<Pair<String, String>>()

        for (i in 0 until messages.length()) {
            val m = messages.optJSONObject(i) ?: continue
            val role = m.optString("role", "user").lowercase()
            val text = extractText(m.opt("content"))?.trim().orEmpty()
            if (text.isEmpty()) continue
            if (role == "system" || role == "developer") {
                if (forwardSystem) sys.append(text).append('\n')
            } else {
                turns.add(role to text)
            }
        }

        val lastUser = turns.indexOfLast { it.first == "user" }
        if (lastUser < 0) return null
        val question = turns[lastUser].second

        val prefix = StringBuilder()
        if (sys.isNotEmpty()) prefix.append(sys.toString().trim()).append("\n\n")
        if (forwardHistory) {
            for (i in 0 until lastUser) {
                val (role, text) = turns[i]
                prefix.append(if (role == "user") "用户：" else "助手：").append(text).append('\n')
            }
            if (prefix.isNotEmpty()) prefix.append('\n')
        }

        return (prefix.toString() + question).takeIf { it.isNotBlank() }
    }

    /** content 兼容三种形态：纯字符串 / 多模态分片数组 / 其它对象 */
    private fun extractText(content: Any?): String? = when (content) {
        null, JSONObject.NULL -> null
        is String -> content
        is JSONArray -> {
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                when (val part = content.opt(i)) {
                    is String -> sb.append(part)
                    is JSONObject -> {
                        val t = part.optString("text", "")
                        if (t.isNotEmpty()) sb.append(t)
                    }

                    else -> {
                    }
                }
            }
            sb.toString()
        }

        else -> content.toString()
    }

    /** model 名 -> 本地引擎；未识别时回退设置里的默认引擎 */
    private fun resolveEngine(model: String, cfg: ConfigStore?): String {
        val m = model.trim().lowercase()
        if (m.isEmpty()) return cfg?.getXiaoaiEngine() ?: "miclaw"
        return when {
            m.contains("fast") || m.contains("cloud") || m.contains("quick") -> "fast"
            m.contains("miclaw") || m.contains("agent") ||
                m.contains("xiaoai") || m.contains("xiaomi") || m.contains("super") -> "miclaw"

            else -> cfg?.getXiaoaiEngine() ?: "miclaw"
        }
    }

    /** 校验 Bearer token / x-api-key；未设置 token 时放行 */
    private fun authorized(headers: Map<String, String>, cfg: ConfigStore): Boolean {
        val token = cfg.getOpenAiToken().trim()
        if (token.isEmpty()) return true
        val auth = headers["authorization"]?.trim().orEmpty()
        val provided = if (auth.startsWith("Bearer ", ignoreCase = true)) {
            auth.substring(7).trim()
        } else {
            auth
        }.ifEmpty {
            headers["x-api-key"]?.trim().orEmpty()
        }
        return provided.isNotEmpty() && provided == token
    }

    private fun parseHeaders(lines: List<String>): Map<String, String> {
        val map = HashMap<String, String>()
        for (i in 1 until lines.size) {
            val idx = lines[i].indexOf(':')
            if (idx > 0) {
                map[lines[i].substring(0, idx).trim().lowercase()] = lines[i].substring(idx + 1).trim()
            }
        }
        return map
    }

    // ==================== HTTP 底层 ====================

    /**
     * 逐字节读到 \r\n\r\n 为止（不消费 body），返回头部文本。
     * 用 ISO-8859-1 解码保证字节一对一，避免 UTF-8 解码器在多字节被截断时吞字节。
     */
    private fun readHead(input: InputStream): String? {
        val buf = ByteArrayOutputStream(1024)
        val needle = byteArrayOf(13, 10, 13, 10)
        var matched = 0
        while (true) {
            val b = input.read()
            if (b == -1) return null
            buf.write(b)
            val v = b.toByte()
            matched = when {
                v == needle[matched] -> matched + 1
                v == needle[0] -> 1
                else -> 0
            }
            if (matched == needle.size) return buf.toString("ISO-8859-1")
            if (buf.size() > MAX_HEADER_BYTES) return null
        }
    }

    /** 按 Content-Length 精确读满（不用 readNBytes，兼容低版本 Android） */
    private fun readFully(input: InputStream, len: Int): ByteArray? {
        val out = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = try {
                input.read(out, off, len - off)
            } catch (t: Throwable) {
                return null
            }
            if (n <= 0) return null
            off += n
        }
        return out
    }

    private fun respondJson(output: OutputStream, code: Int, body: JSONObject) {
        respond(output, code, "application/json; charset=utf-8", body.toString().toByteArray(Charsets.UTF_8))
    }

    private fun respondError(output: OutputStream, code: Int, message: String) {
        val body = JSONObject()
            .put(
                "error",
                JSONObject()
                    .put("message", message)
                    .put("type", if (code >= 500) "server_error" else "invalid_request_error")
                    .put("code", code),
            )
        respondJson(output, code, body)
    }

    private fun respond(output: OutputStream, code: Int, contentType: String, body: ByteArray) {
        val head = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason(code)).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Connection: close\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Headers: Authorization, Content-Type, x-api-key\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("\r\n")
        }
        output.write(head.toByteArray(Charsets.ISO_8859_1))
        if (body.isNotEmpty()) output.write(body)
        output.flush()
    }

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"
        204 -> "No Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        413 -> "Payload Too Large"
        429 -> "Too Many Requests"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> "OK"
    }

    private fun hintPage(): String = buildString {
        append("超级小爱 OpenAI 兼容接口\n\n")
        append("POST /v1/chat/completions\n")
        append("GET  /v1/models\n")
        append("GET  /status\n")
        append("POST /stats/clear\n\n")
        append("model：").append(MODEL_MICLAW).append("（Agent，可操控手机）/ ")
        append(MODEL_FAST).append("（快速问答）\n")
    }

    // ==================== 统计 ====================

    private fun record(model: String, engine: String, query: String, elapsedMs: Long, ok: Boolean, answerLen: Int) {
        statTotal.incrementAndGet()
        if (ok) statOk.incrementAndGet() else statFail.incrementAndGet()
        statLatencySum.addAndGet(elapsedMs)
        val item = CallRecord(
            at = System.currentTimeMillis(),
            model = model,
            engine = engine,
            query = query.replace(Regex("\\s+"), " ").take(60),
            elapsedMs = elapsedMs,
            ok = ok,
            answerLen = answerLen,
        )
        synchronized(recentLock) {
            recent.addFirst(item)
            while (recent.size > RECENT_CAPACITY) recent.removeLast()
        }
    }

    /** 清空统计与调用记录 */
    fun clearStats() {
        statTotal.set(0)
        statOk.set(0)
        statFail.set(0)
        statLatencySum.set(0)
        synchronized(recentLock) { recent.clear() }
    }
}
