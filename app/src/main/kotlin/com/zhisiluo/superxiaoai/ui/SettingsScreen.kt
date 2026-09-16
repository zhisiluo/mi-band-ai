@file:OptIn(ExperimentalScrollBarApi::class)

package com.zhisiluo.superxiaoai.ui

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.XposedService
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.zhisiluo.superxiaoai.LsposedBinding
import com.zhisiluo.superxiaoai.config.ConfigKeys
import com.zhisiluo.superxiaoai.config.ConfigStore
import com.zhisiluo.superxiaoai.config.PresetManager
import com.zhisiluo.superxiaoai.hook.Bridge
import androidx.compose.runtime.LaunchedEffect
import com.zhisiluo.superxiaoai.log.LogCollector
import com.zhisiluo.superxiaoai.ui.VisualPrefs
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import com.zhisiluo.superxiaoai.ui.BlurredBar
import com.zhisiluo.superxiaoai.ui.LocalEnableBlur
import com.zhisiluo.superxiaoai.ui.LocalEnableFloatingBar
import com.zhisiluo.superxiaoai.ui.LocalEnableFloatingBarBlur
import com.zhisiluo.superxiaoai.ui.LocalEnableNavigationBadge
import com.zhisiluo.superxiaoai.ui.LocalPageScale
import com.zhisiluo.superxiaoai.ui.rememberBlurBackdrop
import com.zhisiluo.superxiaoai.ui.component.FloatingBottomBar
import com.zhisiluo.superxiaoai.ui.component.FloatingBottomBarItem

/**
 * 超级小爱 —— 完整设置页（参考 KernelSU Manager 设计风格）。
 *
 * 4 个 Tab 通过 HorizontalPager 左右滑动切换：
 * 0=状态 1=配置 2=统计 3=关于
 *
 * @param binding LSPosed Service 绑定信息；为 null 表示未检测到框架，显示提示
 * @param pagerState Pager 状态（由外层提升注入：主题切换动画会重建本页面组合，
 *                   状态提升到 AnimatedContent 之外以保持 Tab 位置存活）
 * @param showThemePage 是否显示主题设置二级页（同样提升注入，避免切换主题后被踢回主页）
 * @param onOpenThemePage 打开主题设置页
 * @param onCloseThemePage 关闭主题设置页
 * @param onThemeModeChange 主题模式变更回调（持久化 + 驱动 AppTheme 重建）
 * @param onKeyColorChange Monet 种子色变更回调
 * @param onPaletteStyleChange 调色板风格变更回调
 * @param onColorSpecChange 动态取色规范变更回调
 */
@Composable
fun SettingsScreen(
    binding: LsposedBinding?,
    pagerState: PagerState,
    showThemePage: Boolean,
    onOpenThemePage: () -> Unit,
    onCloseThemePage: () -> Unit,
    onThemeModeChange: (String) -> Unit = {},
    onKeyColorChange: (Long) -> Unit = {},
    onPaletteStyleChange: (ThemePaletteStyle) -> Unit = {},
    onColorSpecChange: (ThemeColorSpec) -> Unit = {},
    onMiuixMonetChange: (Boolean) -> Unit = {},
    onVisualPrefsChange: (VisualPrefs) -> Unit = {},
    onEnablePredictiveBackChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    PresetManager.init(context)
    val scope = rememberCoroutineScope()

    val config = binding?.config

    data class TabInfo(val label: String, val icon: ImageVector)
    val tabs = listOf(
        TabInfo("状态", MiuixIcons.Home),
        TabInfo("配置", MiuixIcons.Settings),
        TabInfo("统计", MiuixIcons.Info),
        TabInfo("关于", MiuixIcons.Edit),
    )

    // 主题设置页时拦截系统返回：回到主设置页而非直接退出
    BackHandler(enabled = showThemePage && config != null) {
        onCloseThemePage()
    }

    // 主题设置页需要 config 才能操作
    if (showThemePage && config != null) {
        ThemeSettingsScreen(
            config = config,
            onBack = onCloseThemePage,
            onThemeModeChange = onThemeModeChange,
            onKeyColorChange = onKeyColorChange,
            onPaletteStyleChange = onPaletteStyleChange,
            onColorSpecChange = onColorSpecChange,
            onMiuixMonetChange = onMiuixMonetChange,
            onVisualPrefsChange = onVisualPrefsChange,
            onEnablePredictiveBackChange = onEnablePredictiveBackChange,
        )
        return
    }

    // 视觉效果（从 CompositionLocal 读取，由 SettingsActivity 提供）
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBar = LocalEnableFloatingBar.current
    val enableFloatingBarBlur = LocalEnableFloatingBarBlur.current
    val enableNavBadge = LocalEnableNavigationBadge.current
    val pageScale = LocalPageScale.current
    // 顶部栏模糊 backdrop（参考 KernelSU：blur 开启且支持时非空）
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = blurBackdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    // 悬浮底栏玻璃效果 backdrop（参考 KernelSU：始终创建，pager 内容挂在上面）
    val floatingSurface = MiuixTheme.colorScheme.surface
    val floatingBackdrop = rememberLayerBackdrop {
        drawRect(floatingSurface)
        drawContent()
    }

    Scaffold(
        topBar = {
            if (blurActive) {
                BlurredBar(blurBackdrop) {
                    SmallTopAppBar(title = "超级小爱", color = barColor)
                }
            } else {
                SmallTopAppBar(title = "超级小爱")
            }
        },
        bottomBar = {
            if (enableFloatingBar) {
                // 悬浮胶囊底部导航栏（居中显示，参考 KernelSU FloatingBottomBar）
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    FloatingBottomBar(
                        modifier = Modifier.padding(bottom = 12.dp),
                        selectedIndex = { pagerState.currentPage },
                        onSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                        backdrop = floatingBackdrop,
                        tabsCount = tabs.size,
                        isBlurEnabled = enableFloatingBarBlur,
                    ) {
                        tabs.forEachIndexed { i, tab ->
                            FloatingBottomBarItem(
                                onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                                modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                            ) {
                                Icon(imageVector = tab.icon, contentDescription = tab.label)
                                Text(
                                    text = tab.label,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                )
                            }
                        }
                    }
                }
            } else {
                 NavigationBar {
                     tabs.forEachIndexed { i, tab ->
                         NavigationBarItem(
                             selected = pagerState.currentPage == i,
                             onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                             icon = tab.icon,
                             label = tab.label,
                             badge = {
                                 if (enableNavBadge && i == 0) {
                                     // 状态 Tab 显示连接状态角标：已连接用绿色，未连接用 error
                                     Badge(
                                         containerColor = if (binding != null) {
                                             Color(0xFF4CAF50)
                                         } else {
                                             MiuixTheme.colorScheme.error
                                         },
                                         modifier = Modifier.size(8.dp),
                                     )
                                 }
                             },
                         )
                     }
                 }
             }
        },
    ) { innerPadding ->
        val contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 12.dp,
            bottom = innerPadding.calculateBottomPadding() + 12.dp,
        )

        // 页面缩放：包裹 Pager 内容
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // 顶部栏模糊：把内容绘制到 blur backdrop 上，顶栏才能模糊到滚动内容
                    if (blurActive) Modifier.layerBackdrop(blurBackdrop!!) else Modifier
                )
                .graphicsLayer {
                    scaleX = pageScale
                    scaleY = pageScale
                },
        ) {
            // 悬浮底栏玻璃效果：把 Pager 内容绘制到浮动底栏 backdrop 上
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.then(
                    if (enableFloatingBar && enableFloatingBarBlur) {
                        Modifier.layerBackdrop(floatingBackdrop)
                    } else {
                        Modifier
                    }
                ),
            ) { page ->
                when (page) {
                    0 -> StatusTabContent(binding = binding, context = context, contentPadding = contentPadding)
                    1 -> ConfigTabContent(config = config, context = context, scope = scope, contentPadding = contentPadding)
                    2 -> StatsTabContent(config = config, context = context, scope = scope, contentPadding = contentPadding)
                    3 -> AboutTabContent(config = config, context = context, scope = scope, contentPadding = contentPadding, onOpenThemePage = onOpenThemePage)
                }
            }
        }
    }
}

// ====================================================================
// Tab 0：状态 —— LSPosed 连接状态总览（参考 KernelSU StatusCard 设计）
// ====================================================================

/**
 * 状态页 —— 展示 LSPosed 框架连接状态、模块作用域、目标进程 Hook 状态。
 * 参考 KernelSU HomePager 的 StatusCard + InfoCard 设计风格。
 */
@Composable
private fun StatusTabContent(
    binding: LsposedBinding?,
    context: Context,
    contentPadding: PaddingValues,
) {
    val service = binding?.service
    val listState = rememberLazyListState()
    // 协程作用域（与上方读取 scope 列表的局部变量区分命名）
    val uiScope = rememberCoroutineScope()
    // 手机端小爱(osbot)桥服务端运行状态
    var xiaoaiStatus by remember { mutableStateOf<XiaoaiStatus?>(null) }
    var xiaoaiChecked by remember { mutableStateOf(false) }
    var showRestartVA by remember { mutableStateOf(false) }
    var restartingVA by remember { mutableStateOf(false) }
    // 查询 voiceassist 侧桥状态：连不上即代表未运行 / 进程被系统回收
    fun refreshXiaoai() {
        xiaoaiChecked = false
        uiScope.launch(Dispatchers.IO) {
            val raw = Bridge.requestStatus()
            withContext(Dispatchers.Main) {
                xiaoaiStatus = parseXiaoaiStatus(raw)
                xiaoaiChecked = true
            }
        }
    }
    LaunchedEffect(Unit) { refreshXiaoai() }

    // 异常容错读取框架信息（未绑定时为默认占位）
    val frameworkName = remember { runCatching { service?.frameworkName }.getOrNull() ?: "LSPosed" }
    val frameworkVersion = remember { runCatching { service?.frameworkVersion }.getOrNull() ?: "?" }
    val frameworkVersionCode = remember { runCatching { service?.frameworkVersionCode }.getOrNull() ?: 0L }
    val scope = remember { runCatching { service?.scope }.getOrNull() ?: emptyList() }
    val targets = remember { runCatching { service?.runningTargets }.getOrNull() ?: emptyList() }
    val targetInScope = scope.any { it.equals(VOICE_ASSIST_PACKAGE, ignoreCase = true) }
    val vaTarget = targets.firstOrNull { it.processName.contains(VOICE_ASSIST_PACKAGE) }

    // 是否已激活（Service 绑定成功）
    val activated = binding != null

    // 大卡片配色（恢复原经典配色，与动态取色无关）：未激活浅红，激活浅绿
    val cardBg = if (!activated) Color(0xFFF8D7DA) else Color(0xFFDFFAE4)
    val cardFg = if (!activated) Color(0xFF8B1A1A) else Color(0xFF1A3825)
    val tagColor = MiuixTheme.colorScheme.secondaryContainer
    val tagTextColor = MiuixTheme.colorScheme.onSecondaryContainer

    Box {
        LazyColumn(state = listState, contentPadding = contentPadding) {
            // ---------- 主状态卡片（参考 KernelSU StatusCard） ----------
            item(key = "statusCard") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        colors = CardDefaults.defaultColors(color = cardBg),
                        onClick = {
                            // 点击卡片尝试打开 LSPosed 管理器
                            runCatching {
                                val intent = context.packageManager.getLaunchIntentForPackage("org.lsposed.manager")
                                if (intent != null) context.startActivity(intent)
                            }
                        },
                        showIndication = true,
                        pressFeedbackType = PressFeedbackType.Tilt,
                    ) {
                        Box {
                            // 右下角大图标（参考 KernelSU：110dp 对勾/叉号，右下偏移）
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset(27.dp, 31.dp),
                                contentAlignment = Alignment.BottomEnd,
                            ) {
                                Icon(
                                    modifier = Modifier.size(110.dp),
                                    imageVector = if (activated) MiuixIcons.Ok else MiuixIcons.Close,
                                    tint = if (activated) {
                                        Color(0xFF36D167)
                                    } else {
                                        MiuixTheme.colorScheme.error.copy(alpha = 0.8f)
                                    },
                                    contentDescription = null,
                                )
                            }
                            // 左下角工作模式标签（参考 KernelSU workingMode）
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp, 10.dp),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                Text(
                                    text = if (activated) "LSPosed" else "未激活",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = cardFg,
                                )
                            }
                            // 左上角标题 + 版本
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp, 14.dp),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                Column {
                                    Text(
                                        text = if (activated) "已连接 LSPosed" else "LSPosed 未激活",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = cardFg,
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = if (activated) "$frameworkName · $frameworkVersion"
                                        else "请在 LSPosed 管理器中启用本模块",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = cardFg,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------- 框架信息卡片 ----------
            item(key = "frameworkTitle") {
                Spacer(modifier = Modifier.height(8.dp))
                SmallTitle("框架信息")
            }
            item(key = "framework") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    InfoRow(
                        label = "框架名称",
                        value = frameworkName,
                        tag = "LSPosed",
                        tagBg = tagColor,
                        tagFg = tagTextColor,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    InfoRow(
                        label = "框架版本",
                        value = "$frameworkVersion (code $frameworkVersionCode)",
                        tag = frameworkVersion,
                        tagBg = tagColor,
                        tagFg = tagTextColor,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    InfoRow(
                        label = "目标应用",
                        value = VOICE_ASSIST_PACKAGE,
                        tag = if (targetInScope) "已勾选" else "未勾选",
                        tagBg = if (targetInScope) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.errorContainer,
                        tagFg = if (targetInScope) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            // ---------- Hook 运行状态卡片 ----------
            item(key = "hookTitle") {
                Spacer(modifier = Modifier.height(8.dp))
                SmallTitle("Hook 运行状态")
            }
            item(key = "hook") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    val targetInfo = vaTarget
                    val statusText = when {
                        targetInfo == null -> "未运行"
                        targetInfo.state == HookedTarget.State.UP_TO_DATE -> "运行中 · 已加载"
                        targetInfo.state == HookedTarget.State.STALE -> "运行中 · 需重载"
                        targetInfo.state == HookedTarget.State.RELOADING -> "重载中"
                        targetInfo.state == HookedTarget.State.FAILED -> "加载失败"
                        else -> "未知"
                    }
                    val statusColor = when {
                        targetInfo == null -> MiuixTheme.colorScheme.errorContainer
                        targetInfo.state == HookedTarget.State.UP_TO_DATE -> MiuixTheme.colorScheme.primaryContainer
                        else -> MiuixTheme.colorScheme.tertiaryContainer
                    }
                    InfoRow(
                        label = VOICE_ASSIST_PACKAGE,
                        value = "[pid=${targetInfo?.pid ?: "?"}]",
                        tag = statusText,
                        tagBg = statusColor,
                        tagFg = MiuixTheme.colorScheme.onPrimaryContainer,
                    )
                    if (targetInfo != null) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        InfoRow(
                            label = "模块版本",
                            value = "v${targetInfo.loadedVersionCode}",
                            tag = "loaded",
                            tagBg = tagColor,
                            tagFg = tagTextColor,
                        )
                    }
                }
            }

            // ---------- 手机端小爱 (osbot 回答引擎) ----------
            item(key = "xiaoaiTitle") {
                Spacer(modifier = Modifier.height(8.dp))
                SmallTitle("手机端小爱 (osbot)")
            }
            item(key = "xiaoai") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    val st = xiaoaiStatus
                    val running = st?.running == true
                    val connected = st?.connected == true
                    InfoRow(
                        label = "com.miui.voiceassist",
                        value = if (xiaoaiChecked) (if (running) "桥已就绪" else "桥未运行") else "检测中…",
                        tag = when {
                            !xiaoaiChecked -> "检测中"
                            running -> "Hook 运行中"
                            else -> "未运行"
                        },
                        tagBg = when {
                            !xiaoaiChecked -> MiuixTheme.colorScheme.tertiaryContainer
                            running -> MiuixTheme.colorScheme.primaryContainer
                            else -> MiuixTheme.colorScheme.errorContainer
                        },
                        tagFg = MiuixTheme.colorScheme.onPrimaryContainer,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    InfoRow(
                        label = "osbot 通道",
                        value = st?.agent?.takeIf { it.isNotBlank() } ?: "-",
                        tag = if (connected) "已连接" else "未连接",
                        tagBg = if (connected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.errorContainer,
                        tagFg = MiuixTheme.colorScheme.onPrimaryContainer,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    InfoRow(
                        label = "快速引擎",
                        value = if (st?.fastReady == true) "已接入注入点" else "降级(放行原始)",
                        tag = if (st?.fastReady == true) "可用" else "S0待接入",
                        tagBg = if (st?.fastReady == true) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.tertiaryContainer,
                        tagFg = MiuixTheme.colorScheme.onPrimaryContainer,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "刷新状态",
                        summary = "重新探测 osbot 桥服务端是否在线",
                        onClick = { refreshXiaoai() },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "重启超级小爱",
                        summary = if (restartingVA) "正在重启…" else "以 Root 强制停止，系统会自动重启并重建 osbot 桥",
                        enabled = !restartingVA,
                        onClick = { showRestartVA = true },
                    )
                }
            }

            // ---------- 快速操作 ----------
            item(key = "quickTitle") {
                Spacer(modifier = Modifier.height(8.dp))
                SmallTitle("快速操作")
            }
            item(key = "quick") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "打开 LSPosed 管理器",
                        summary = "管理模块作用域与查看运行状态",
                        onClick = {
                            // 尝试打开 LSPosed 管理器（org.lsposed.manager）
                            runCatching {
                                val intent = context.packageManager.getLaunchIntentForPackage("org.lsposed.manager")
                                if (intent != null) context.startActivity(intent)
                            }
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "自启动设置",
                        summary = "为超级小爱开启自启动权限，保证后台常驻",
                        onClick = {
                            openAutoStartSettings(context, VOICE_ASSIST_PACKAGE)
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "省电策略设置",
                        summary = "设置超级小爱的省电策略，避免后台被系统限制",
                        onClick = {
                            openBatteryOptimizationSettings(context, VOICE_ASSIST_PACKAGE)
                        },
                    )
                }
            }

            item(key = "bottomSpacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            trackPadding = contentPadding,
        )
    }

    // 重启超级小爱二次确认对话框
    OverlayDialog(
        show = showRestartVA,
        title = "重启超级小爱",
        summary = "将以 Root 强制停止 com.miui.voiceassist，系统会自动重启其常驻进程并重建 osbot 桥服务端",
        onDismissRequest = { showRestartVA = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "取消",
                modifier = Modifier.weight(1f),
                onClick = { showRestartVA = false },
            )
            TextButton(
                text = "确认重启",
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    showRestartVA = false
                    restartingVA = true
                    uiScope.launch(Dispatchers.IO) {
                        val ok = restartVoiceAssistWithRoot()
                        withContext(Dispatchers.Main) {
                            restartingVA = false
                            Toast.makeText(
                                context,
                                if (ok) "已重启超级小爱，稍后 osbot 桥会自动重建" else "重启失败：请检查 Root 授权",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        // 重启后等待常驻进程带起 + 注入重建桥，再刷新状态
                        kotlinx.coroutines.delay(8000)
                        val raw = Bridge.requestStatus()
                        withContext(Dispatchers.Main) {
                            xiaoaiStatus = parseXiaoaiStatus(raw)
                            xiaoaiChecked = true
                        }
                    }
                },
            )
        }
    }
}

/** 超级小爱包名（osbot 回答引擎宿主） */
private const val VOICE_ASSIST_PACKAGE = "com.miui.voiceassist"

/** voiceassist 侧 osbot 桥服务端运行状态 */
private data class XiaoaiStatus(
    val running: Boolean,
    val connected: Boolean,
    val agent: String,
    val fastReady: Boolean,
)

/** 解析桥状态行 "STATUS|started=..|connected=..|agent=..|fastReady=.."；null/非法 → 未运行 */
private fun parseXiaoaiStatus(raw: String?): XiaoaiStatus {
    if (raw == null || !raw.startsWith("STATUS")) return XiaoaiStatus(false, false, "", false)
    val kv = raw.split("|").drop(1).mapNotNull {
        val i = it.indexOf('=')
        if (i < 0) null else it.substring(0, i) to it.substring(i + 1)
    }.toMap()
    return XiaoaiStatus(
        running = kv["started"] == "true",
        connected = kv["connected"] == "true",
        agent = kv["agent"].orEmpty(),
        fastReady = kv["fastReady"] == "true",
    )
}

/**
 * 以 Root 强制停止超级小爱。其常驻子进程会被系统重启并带起主进程，
 * 注入代码随之重建 osbot 桥服务端（voiceassist 无 LAUNCHER，故不用 monkey 拉起）。
 */
private fun restartVoiceAssistWithRoot(): Boolean = try {
    val process = ProcessBuilder("su", "-c", "am force-stop $VOICE_ASSIST_PACKAGE")
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor(10, TimeUnit.SECONDS)
    process.exitValue() == 0
} catch (_: Throwable) {
    false
}

/**
 * 跳转系统「自启动设置」。
 *
 * 优先尝试 MIUI/HyperOS 安全中心的自启动管理页（方便为指定应用开启自启动），
 * 无法解析时回退到系统应用详情页（多数系统在此页提供自启动入口）。
 * 全部失败时给出 Toast 提示，不抛出异常。
 */
private fun openAutoStartSettings(context: Context, packageName: String) {
    // MIUI/HyperOS 自启动管理（com.miui.securitycenter 的 AutoStart 管理 Activity）
    val miuiIntent = Intent("miui.intent.action.OP_AUTO_START")
    // 通用回退：系统应用详情页
    val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    }
    if (launchFirstAvailable(context, miuiIntent, detailsIntent)) return
    Toast.makeText(context, "未找到自启动设置入口", Toast.LENGTH_SHORT).show()
}

/**
 * 跳转系统「省电策略设置」。
 *
 * 优先尝试系统电池优化设置页，无法解析时回退到系统应用详情页，
 * 多数系统（含 MIUI/HyperOS）的应用详情页内置「省电策略/电池」入口。
 */
private fun openBatteryOptimizationSettings(context: Context, packageName: String) {
    // 通用电池优化设置（Android 系统设置，可选择忽略优化）
    val batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    // 通用回退：系统应用详情页（含省电策略入口）
    val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    }
    if (launchFirstAvailable(context, batteryIntent, detailsIntent)) return
    Toast.makeText(context, "未找到省电策略设置入口", Toast.LENGTH_SHORT).show()
}

/**
 * 依次尝试启动 Intent，返回是否有一个成功启动。
 * 需要以 Activity 上下文启动，故加上 NEW_TASK 标志以防缺少 Activity 栈。
 */
private fun launchFirstAvailable(context: Context, vararg intents: Intent): Boolean {
    for (intent in intents) {
        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            }
        } catch (_: Throwable) {
            // 尝试下一个候选
        }
    }
    return false
}

/** 信息行：标签 + 值 + 状态标签 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    tag: String,
    tagBg: Color,
    tagFg: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(end = 8.dp)
                .weight(1f),
            maxLines = 1,
        )
        StatusTag(
            label = tag,
            backgroundColor = tagBg,
            contentColor = tagFg,
        )
    }
}

/** 小圆角状态标签（参考 KernelSU StatusTagMiuix：圆角 6dp，9sp 字体） */
@Composable
private fun StatusTag(
    label: String,
    backgroundColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight(750),
            color = contentColor,
        )
    }
}

// ====================================================================
// Tab 1：配置 —— 基本设置（API）+ 生成参数 + 会话设置 + 主题设置
// ====================================================================

/**
 * Tab 1：配置 —— 基本设置（API）+ 生成参数 + 会话设置，各分组含预设管理。
 */
@Composable
private fun ConfigTabContent(
    config: ConfigStore?,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    contentPadding: PaddingValues,
) {
    // 未激活（无 Service）时显示占位提示
    if (config == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "LSPosed 未激活\n请在 LSPosed 管理器中启用本模块后\n配置接口服务参数",
                textAlign = TextAlign.Center,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    Box {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
        ) {
            // ---------- 分组 1：接口服务 ----------
            item(key = "serverTitle") {
                SmallTitle("接口服务")
            }
            item(key = "server") {
                var refreshTick by remember { mutableStateOf(0) }
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    key(refreshTick) {
                        // 受控组件用本地 state 驱动 UI：RemotePreferences 的读取不具备响应性，
                        // 若直接以 config 值作为 checked 会出现"点了不刷新"的问题
                        var enabled by remember { mutableStateOf(config.isEnabled()) }
                        SwitchPreference(
                            title = "启用模块",
                            summary = "关闭后不再注入超级小爱，OpenAI 兼容接口服务同时停止",
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                config.setEnabled(it)
                            },
                        )
                        NumberInputField(
                            label = "监听端口",
                            initialValue = config.getOpenAiPort(),
                            onValueChange = { config.setOpenAiPort(it) },
                        )
                        var lan by remember { mutableStateOf(config.isOpenAiLan()) }
                        SwitchPreference(
                            title = "允许局域网访问",
                            summary = "开启后监听 0.0.0.0，同一 WiFi 下其它设备可调用；关闭仅监听 127.0.0.1。修改后需重启超级小爱生效",
                            checked = lan,
                            onCheckedChange = {
                                lan = it
                                config.setOpenAiLan(it)
                            },
                        )
                        TextInputField(
                            initialValue = config.getOpenAiToken(),
                            label = "访问令牌（留空=不鉴权）",
                            placeholder = "填写后请求需带 Authorization: Bearer <令牌>",
                            onValueChange = { config.setOpenAiToken(it) },
                        )
                    }
                    PresetSection(
                        category = PresetManager.CATEGORY_SERVER,
                        title = "接口服务",
                        config = config,
                        context = context,
                        onPresetApplied = { refreshTick++ },
                    )
                }
            }

            // ---------- 分组 2：回答引擎 ----------
            item(key = "engineTitle") {
                SmallTitle("回答引擎")
            }
            item(key = "engine") {
                var refreshTick by remember { mutableStateOf(0) }
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    key(refreshTick) {
                        var engineIndex by remember {
                            mutableStateOf(if (config.getXiaoaiEngine().trim().lowercase() == "fast") 1 else 0)
                        }
                        OverlayDropdownPreference(
                            title = "回答引擎",
                            summary = "miclaw=超级小爱大模型(需登录小米账号)；fast=手机端快速云端",
                            items = listOf("miclaw(大模型)", "fast(快速)"),
                            selectedIndex = engineIndex,
                            onSelectedIndexChange = { index ->
                                engineIndex = index
                                config.setXiaoaiEngine(if (index == 1) "fast" else "miclaw")
                            },
                        )
                        NumberInputField(
                            label = "最长回答字数（0=不限制）",
                            initialValue = config.getOpenAiMaxAnswerLen(),
                            onValueChange = { config.setOpenAiMaxAnswerLen(it) },
                        )
                        NumberInputField(
                            label = "快速引擎等待时长（毫秒）",
                            initialValue = config.getFastWaitMs().toInt(),
                            onValueChange = { config.setFastWaitMs(it.toLong()) },
                        )
                        NumberInputField(
                            label = "大模型引擎等待时长（毫秒）",
                            initialValue = config.getMiclawWaitMs().toInt(),
                            onValueChange = { config.setMiclawWaitMs(it.toLong()) },
                        )
                        TextInputField(
                            initialValue = config.getSystemPrompt(),
                            label = "系统提示词",
                            placeholder = "为空时不额外注入；填写后每次提问都会带上",
                            singleLine = false,
                            onValueChange = { config.setSystemPrompt(it) },
                        )
                        var forwardSystem by remember { mutableStateOf(config.isOpenAiForwardSystem()) }
                        SwitchPreference(
                            title = "转发 system 提示词",
                            summary = "把请求 messages 中的 system 消息拼接后附加到提问前",
                            checked = forwardSystem,
                            onCheckedChange = {
                                forwardSystem = it
                                config.setOpenAiForwardSystem(it)
                            },
                        )
                        var forwardHistory by remember { mutableStateOf(config.isOpenAiForwardHistory()) }
                        SwitchPreference(
                            title = "转发多轮历史",
                            summary = "把请求 messages 中的历史轮次一并转交超级小爱，保留上下文",
                            checked = forwardHistory,
                            onCheckedChange = {
                                forwardHistory = it
                                config.setOpenAiForwardHistory(it)
                            },
                        )
                    }
                    PresetSection(
                        category = PresetManager.CATEGORY_SERVER,
                        title = "回答引擎",
                        config = config,
                        context = context,
                        onPresetApplied = { refreshTick++ },
                    )
                }
            }

            item(key = "bottomSpacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            trackPadding = contentPadding,
        )
    }
}

// ====================================================================
// Tab 2：统计 —— API 调用记录与 token 用量
// ====================================================================

/**
 * Tab 2：统计 —— 从本机接口服务的 /status 读取调用计数、平均耗时与最近调用记录。
 */
@Composable
private fun StatsTabContent(
    config: ConfigStore?,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val port = config?.getOpenAiPort() ?: DEFAULT_OPENAI_PORT
    val token = config?.getOpenAiToken().orEmpty()

    // 服务端统计（/status）；null = 未探测到服务端
    var status by remember { mutableStateOf<ServerStatus?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        loading = true
        val st = withContext(Dispatchers.IO) { fetchServerStatus(port, token) }
        status = st
        loading = false
    }

    Box {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
        ) {
            item(key = "statsTitle") {
                SmallTitle("接口调用统计")
            }
            item(key = "stats") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    val st = status
                    if (st == null) {
                        Text(
                            text = if (loading) {
                                "正在读取服务端统计…"
                            } else {
                                "未连接到接口服务。\n请确认模块已启用、超级小爱正在运行，且端口 $port 未被占用。"
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        Text(
                            text = "总调用 ${st.total} 次 · 成功 ${st.ok} 次 · 失败 ${st.fail} 次",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                        )
                        Text(
                            text = "平均耗时 ${st.avgMs} ms · 进行中 ${st.inflight} 个请求 · 引擎 ${st.engine}",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        if (st.recent.isNotEmpty()) {
                            LatencyBarChart(records = st.recent)
                        }
                    }
                    ArrowPreference(
                        title = if (loading) "正在刷新…" else "刷新统计",
                        summary = "重新从接口服务读取调用统计",
                        enabled = !loading,
                        onClick = { refreshTick++ },
                    )
                    ArrowPreference(
                        title = "清除统计",
                        summary = "清空服务端保存的调用计数与最近调用记录",
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val ok = httpPost(
                                    url = "http://127.0.0.1:$port/stats/clear",
                                    token = token,
                                    json = "{}",
                                ) != null
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        if (ok) "统计已清除" else "清除失败：服务端未响应",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    refreshTick++
                                }
                            }
                        },
                    )
                }
            }

            item(key = "recentTitle") {
                SmallTitle("最近调用")
            }
            item(key = "recent") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    val recent = status?.recent.orEmpty()
                    if (recent.isEmpty()) {
                        Text(
                            text = "暂无调用记录。\n调用接口后，这里会显示每次请求的时间、引擎、耗时与提问内容。",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        recent.takeLast(10).reversed().forEach { call ->
                            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                                .format(java.util.Date(call.at))
                            val mark = if (call.ok) "✓" else "✗"
                            Text(
                                text = "$time $mark ${call.engine} · ${call.ms}ms · ${call.len}字 · ${call.query}",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }

            item(key = "bottomSpacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            trackPadding = contentPadding,
        )
    }
}

// ====================================================================
// Tab 3：关于 —— 日志导出 + 测试连接 + 版本信息
// ====================================================================

/**
 * Tab 3：关于 —— 日志导出 + 测试连接 + 主题设置入口 + 版本信息。
 */
@Composable
private fun AboutTabContent(
    config: ConfigStore?,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    contentPadding: PaddingValues,
    onOpenThemePage: () -> Unit,
) {
    // 未激活（无 Service）时显示占位提示
    if (config == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "LSPosed 未激活\n请在 LSPosed 管理器中启用本模块",
                textAlign = TextAlign.Center,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    val port = config.getOpenAiPort()
    val token = config.getOpenAiToken()
    val lan = config.isOpenAiLan()
    val lanIp = remember { localIpv4() }
    val baseUrl = if (lan && lanIp != null) "http://$lanIp:$port" else "http://127.0.0.1:$port"

    Box {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
        ) {
            // ---------- 分组 1：接口信息 ----------
            item(key = "apiTitle") {
                SmallTitle("接口信息")
            }
            item(key = "api") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    var testing by remember { mutableStateOf(false) }
                    ArrowPreference(
                        title = if (testing) "正在测试…" else "测试接口",
                        summary = "经本机接口发一条测试提问，验证服务端与超级小爱链路是否可用",
                        enabled = !testing,
                        onClick = {
                            testing = true
                            scope.launch(Dispatchers.IO) {
                                val payload = "{\"model\":\"xiaomi-xiaoai\",\"messages\":[{\"role\":\"user\",\"content\":\"连接测试：请回答连接成功\"}],\"stream\":false}"
                                val raw = httpPost(
                                    url = "http://127.0.0.1:$port/v1/chat/completions",
                                    token = token,
                                    json = payload,
                                    timeoutMs = 60_000,
                                )
                                // 兼容非流式 JSON 与流式 SSE 两种返回
                                val reply = runCatching {
                                    val obj = JSONObject(raw!!)
                                    obj.getJSONArray("choices")
                                        .getJSONObject(0)
                                        .getJSONObject("message")
                                        .optString("content")
                                }.getOrNull() ?: raw?.lines()
                                    ?.filter { it.startsWith("data:") && !it.contains("[DONE]") }
                                    ?.joinToString("") { line ->
                                        runCatching {
                                            JSONObject(line.removePrefix("data:").trim())
                                                .getJSONArray("choices")
                                                .getJSONObject(0)
                                                .getJSONObject("delta")
                                                .optString("content")
                                        }.getOrDefault("")
                                    }
                                withContext(Dispatchers.Main) {
                                    testing = false
                                    val msg = when {
                                        raw == null -> "测试失败：服务端未响应，请确认模块已启用且超级小爱正在运行"
                                        reply.isNullOrBlank() -> "测试失败：返回内容为空（可能超级小爱未登录或未就绪）"
                                        else -> "测试成功：${reply.trim().take(40)}"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    )
                    ArrowPreference(
                        title = "复制 Base URL",
                        summary = baseUrl,
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("OpenAI Base URL", baseUrl))
                            Toast.makeText(context, "已复制：$baseUrl", Toast.LENGTH_SHORT).show()
                        },
                    )
                    Text(
                        text = "GET  /v1/models\n" +
                            "POST /v1/chat/completions（stream 可选）\n" +
                            "GET  /status\n\n" +
                            "鉴权：Authorization: Bearer <访问令牌>，令牌留空时不校验。\n" +
                            "调用接口即由本机超级小爱真实操控手机执行并返回结果。",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            // ---------- 分组 2：日志 ----------
            item(key = "logTitle") {
                SmallTitle("日志")
            }
            item(key = "log") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "导出日志",
                        summary = "通过系统分享发送日志文件",
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val file = LogCollector.exportLogFile()
                                withContext(Dispatchers.Main) {
                                    if (file != null) {
                                        // 通过 FileProvider 生成 content:// Uri，交给系统分享
                                        val uri = runCatching {
                                            FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file,
                                            )
                                        }.getOrNull()
                                        if (uri != null) {
                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_TEXT, "超级小爱 日志文件")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            runCatching {
                                                context.startActivity(Intent.createChooser(share, "分享日志"))
                                            }.onFailure {
                                                Toast.makeText(context, "未找到可分享的应用", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "日志导出失败", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "日志导出失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    )
                }
            }

            // ---------- 分组 3：关于 ----------
            item(key = "aboutTitle") {
                SmallTitle("关于")
            }
            item(key = "about") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "项目地址",
                        summary = "https://github.com/Little-White3110/mi-band-ai",
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Little-White3110/mi-band-ai"))
                                context.startActivity(intent)
                            }
                        },
                    )
                    Text(
                        text = "超级小爱 · 版本 0.3.0（手机小爱 · OpenAI 兼容接口）",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            // ---------- 分组 4：主题设置入口（KSU 风格独立页面） ----------
            item(key = "themeTitle") {
                SmallTitle("主题设置")
            }
            item(key = "theme") {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "设置主题",
                        summary = "主题模式 / 动态取色 / 种子色 / 调色板风格 / 视觉效果",
                        onClick = onOpenThemePage,
                    )
                }
            }

            item(key = "bottomSpacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            trackPadding = contentPadding,
        )
    }
}

// ====================================================================
// 预设管理 —— 每组配置 Card 底部复用的预设保存/应用/删除区块
// ====================================================================

/**
 * 预设管理区块（每个可配置分组 Card 末尾复用）。
 */
@Composable
private fun PresetSection(
    category: String,
    title: String,
    config: ConfigStore,
    context: Context,
    onPresetApplied: () -> Unit,
) {
    var presets by remember(category) { mutableStateOf(PresetManager.listPresets(category)) }
    var selected by remember(category) { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    val hasPresets = presets.isNotEmpty()

    OverlayDropdownPreference(
        title = title,
        summary = selected ?: "选择预设并应用到当前分组",
        items = presets.ifEmpty { listOf("(无预设)") },
        selectedIndex = maxOf(presets.indexOf(selected), 0),
        onSelectedIndexChange = { index ->
            if (hasPresets && index in presets.indices) {
                val name = presets[index]
                selected = name
                PresetManager.loadPreset(category, name)?.let { values ->
                    PresetManager.applyPreset(config, values)
                    onPresetApplied()
                    Toast.makeText(context, "已应用预设「$name」", Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
    ArrowPreference(
        title = "保存当前为预设",
        summary = "命名保存当前分组配置",
        onClick = { showSaveDialog = true },
    )
    ArrowPreference(
        title = "删除所选预设",
        summary = selected ?: "请先在上方选择一个预设",
        enabled = selected != null,
        onClick = {
            selected?.let { name ->
                PresetManager.deletePreset(category, name)
                presets = PresetManager.listPresets(category)
                selected = null
                Toast.makeText(context, "已删除预设「$name」", Toast.LENGTH_SHORT).show()
            }
        },
    )
    OverlayDialog(
        show = showSaveDialog,
        title = "保存预设",
        summary = "为当前${title}配置命名",
        onDismissRequest = { showSaveDialog = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = saveName,
                onValueChange = { saveName = it },
                label = "预设名称",
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    modifier = Modifier.weight(1f),
                    onClick = { showSaveDialog = false },
                )
                TextButton(
                    text = "保存",
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        val name = saveName.trim()
                        if (name.isNotEmpty()) {
                            PresetManager.savePreset(
                                category,
                                name,
                                PresetManager.exportValues(config, category),
                            )
                            presets = PresetManager.listPresets(category)
                            saveName = ""
                            showSaveDialog = false
                            Toast.makeText(context, "已保存预设「$name」", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}

// ====================================================================
// 输入组件 —— 自定义文本/数字/可空输入框
// ====================================================================

/** 单行文本输入：Base URL / 模型 等字符串配置 */
@Composable
private fun TextInputField(
    initialValue: String,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    val effectiveLabel = if (placeholder.isEmpty()) label else "$label（$placeholder）"
    TextField(
        value = text,
        onValueChange = { input ->
            text = input
            onValueChange(input)
        },
        label = effectiveLabel,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/**
 * API Key 输入：默认掩码显示，点击尾部图标可临时切换明文。
 *
 * 密钥在设置页一律不默认明文回显，避免肩窥 / 录屏 / 投屏场景下泄露；
 * 明文仅在用户主动点击后短暂展示，且不会改变已存储的值。
 */
@Composable
private fun ApiKeyField(
    initialValue: String,
    onValueChange: (String) -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    var visible by remember { mutableStateOf(false) }
    TextField(
        value = text,
        onValueChange = { input ->
            text = input
            onValueChange(input)
        },
        label = "API Key",
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) MiuixIcons.Hide else MiuixIcons.Show,
                    contentDescription = if (visible) "隐藏 API Key" else "显示 API Key",
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 数字输入：超时 / Token / 会话等必填整型配置 */
@Composable
private fun NumberInputField(
    label: String,
    initialValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue.toString()) }
    TextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.toIntOrNull()?.let { onValueChange(it) }
        },
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 可空整数输入：留空表示未设置（null，使用 API 默认值） */
@Composable
private fun NullableIntInputField(
    label: String,
    initialValue: Int?,
    onValueChange: (Int?) -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue?.toString() ?: "") }
    TextField(
        value = text,
        onValueChange = { input ->
            text = input
            if (input.isBlank()) {
                onValueChange(null)
            } else {
                input.toIntOrNull()?.let { onValueChange(it) }
            }
        },
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 可空小数输入（温度 / Top P）：留空表示未设置（null，使用 API 默认值） */
@Composable
private fun DecimalInputField(
    label: String,
    initialValue: Float?,
    onValueChange: (Float?) -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue?.toString() ?: "") }
    TextField(
        value = text,
        onValueChange = { input ->
            text = input
            if (input.isBlank()) {
                onValueChange(null)
            } else {
                input.toFloatOrNull()?.let { onValueChange(it) }
            }
        },
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

// ====================================================================
// 耗时柱状图 —— 统计页可视化组件
// ====================================================================

/**
 * 调用耗时柱状图 —— 展示最近若干次接口调用的响应耗时（毫秒），失败请求以弱化色标出。
 */
@Composable
private fun LatencyBarChart(
    records: List<ServerCallRecord>,
    maxBars: Int = 12,
) {
    if (records.isEmpty()) return

    val data = records.takeLast(maxBars)
    val maxMs = (data.maxOfOrNull { it.ms } ?: 1).coerceAtLeast(1)

    // Y 轴刻度：把最大值向上取整到 100ms 的整数倍，四等分
    val yStep = (((maxMs / 4).coerceAtLeast(1) + 99) / 100) * 100
    val yMax = yStep * 4

    val okColor = MiuixTheme.colorScheme.primary
    val failColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
    val textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val gridColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.15f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LegendDot(color = okColor, label = "成功")
            LegendDot(color = failColor, label = "失败")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            val leftPad = 44.dp.toPx()
            val bottomPad = 22.dp.toPx()
            val topPad = 8.dp.toPx()
            val chartW = size.width - leftPad
            val chartH = size.height - bottomPad - topPad

            val textPaint = Paint().apply {
                color = textColor.toArgb()
                textSize = 10.sp.toPx()
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }
            for (i in 0..4) {
                val v = yStep * i
                val y = topPad + chartH - (v.toFloat() / yMax) * chartH
                drawLine(
                    color = gridColor,
                    start = Offset(leftPad, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    v.toString(),
                    leftPad - 4.dp.toPx(),
                    y + textPaint.textSize / 3,
                    textPaint,
                )
            }

            val barCount = data.size
            val slotW = chartW / barCount
            val barW = (slotW * 0.6f).coerceAtMost(36.dp.toPx())

            val timePaint = Paint().apply {
                color = textColor.toArgb()
                textSize = 9.sp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            data.forEachIndexed { i, call ->
                val barH = if (call.ms > 0) (call.ms.toFloat() / yMax) * chartH else 0f
                val x = leftPad + i * slotW + (slotW - barW) / 2
                val bottom = topPad + chartH

                drawRect(
                    color = if (call.ok) okColor else failColor,
                    topLeft = Offset(x, bottom - barH),
                    size = androidx.compose.ui.geometry.Size(barW, barH),
                )

                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                    .format(java.util.Date(call.at))
                drawContext.canvas.nativeCanvas.drawText(
                    time,
                    leftPad + i * slotW + slotW / 2,
                    size.height - 6.dp.toPx(),
                    timePaint,
                )
            }

            drawRect(
                color = gridColor,
                topLeft = Offset(leftPad, topPad),
                size = androidx.compose.ui.geometry.Size(chartW, chartH),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** 图例小圆点 + 文字 */
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier
            .size(10.dp)
            .padding(0.dp)) {
            drawCircle(color = color, radius = 4.dp.toPx())
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

// ====================================================================
// 接口服务状态读取 —— 统计页与关于页共用
// ====================================================================

/** 接口服务默认监听端口，与 ConfigKeys.DEFAULT_OPENAI_PORT 保持一致 */
private const val DEFAULT_OPENAI_PORT = ConfigKeys.DEFAULT_OPENAI_PORT

/** 单次接口调用记录（GET /status 返回的 recent 数组元素） */
private data class ServerCallRecord(
    val at: Long,
    val model: String,
    val engine: String,
    val query: String,
    val ms: Long,
    val ok: Boolean,
    val len: Int,
)

/** 接口服务运行状态与累计统计（GET /status 的解析结果） */
private data class ServerStatus(
    val running: Boolean,
    val port: Int,
    val lan: Boolean,
    val engine: String,
    val osbotConnected: Boolean,
    val fastReady: Boolean,
    val total: Int,
    val ok: Int,
    val fail: Int,
    val avgMs: Long,
    val inflight: Int,
    val recent: List<ServerCallRecord>,
)

/** 携带鉴权头发起一次 HTTP 请求并返回响应体，网络异常或非 2xx 时返回 null */
private fun httpExchange(
    url: String,
    method: String,
    token: String,
    body: String? = null,
    timeoutMs: Int = 30_000,
): String? = runCatching {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = timeoutMs
        readTimeout = timeoutMs
        setRequestProperty("Accept", "application/json")
        if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
    }
    if (body != null) {
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    }
    val code = conn.responseCode
    val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
        ?.use { it.readBytes().toString(Charsets.UTF_8) }
    conn.disconnect()
    if (code in 200..299) raw ?: "" else null
}.getOrNull()

/** 向本机接口服务 POST 一段 JSON，返回响应体；失败返回 null */
private fun httpPost(
    url: String,
    token: String,
    json: String,
    timeoutMs: Int = 30_000,
): String? = httpExchange(url, "POST", token, json, timeoutMs)

/** 读取本机接口服务的 /status 并解析；服务未启动或响应异常时返回 null */
private fun fetchServerStatus(port: Int, token: String): ServerStatus? = runCatching {
    val text = httpExchange("http://127.0.0.1:$port/status", "GET", token, null, 4_000)
        ?: return@runCatching null
    val root = JSONObject(text)
    val stats = root.optJSONObject("stats") ?: JSONObject()
    val arr = root.optJSONArray("recent") ?: JSONArray()
    val recent = ArrayList<ServerCallRecord>(arr.length())
    for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        recent += ServerCallRecord(
            at = item.optLong("at"),
            model = item.optString("model"),
            engine = item.optString("engine"),
            query = item.optString("query"),
            ms = item.optLong("ms"),
            ok = item.optBoolean("ok"),
            len = item.optInt("len"),
        )
    }
    // 服务端 recent 为最新在前，反转为时间升序，便于柱状图从左到右按时间绘制
    recent.reverse()
    ServerStatus(
        running = root.optBoolean("running"),
        port = root.optInt("port", port),
        lan = root.optBoolean("lan"),
        engine = root.optString("engine_default", "miclaw"),
        osbotConnected = root.optBoolean("osbot_connected"),
        fastReady = root.optBoolean("fast_ready"),
        total = stats.optInt("total"),
        ok = stats.optInt("ok"),
        fail = stats.optInt("fail"),
        avgMs = stats.optLong("avg_ms"),
        inflight = stats.optInt("inflight"),
        recent = recent,
    )
}.getOrNull()

/** 取本机局域网 IPv4 地址；非局域网环境或获取失败时返回 null */
private fun localIpv4(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()
