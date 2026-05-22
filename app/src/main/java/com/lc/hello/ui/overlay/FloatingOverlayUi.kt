package com.lc.hello.ui.overlay

import android.content.Context
import android.os.BatteryManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.lc.hello.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.sin
import kotlin.random.Random

private val gradientHeader = Brush.horizontalGradient(listOf(Accent.copy(alpha = 0.12f), Accent.copy(alpha = 0.0f)))

private val accentGradient = Brush.horizontalGradient(listOf(Accent, AccentAlt))

@Composable
fun SleekCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Surface,
    borderColor: Color = Border,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
            .then(clickableModifier)
    ) {
        content()
    }
}

@Composable
fun SleekSwitch(
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    val trackWidth = 34.dp
    val trackHeight = 18.dp
    val thumbSize = 14.dp
    val padding = 2.dp
    val thumbOffset = if (checked) 16.dp else 0.dp

    Box(
        modifier = Modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(if (checked) Accent else SurfaceAlt)
            .border(1.dp, if (checked) Accent else Border, CircleShape)
            .clickable { onCheckedChange() }
            .padding(padding),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(TextPrimary)
        )
    }
}

enum class ScreenState {
    LOGIN,
    INSTANCE_PICKER,
    TOOL_MENU
}

data class MenuItem(
    val id: String,
    val type: String,
    val label: String,
    val defaultValue: Any,
    val min: Double = 0.0,
    val max: Double = 1.0
)

data class MenuCategory(
    val name: String,
    val items: List<MenuItem>
)

data class MenuConfig(
    val title: String,
    val categories: List<MenuCategory>
)

fun parseMenuConfig(jsonStr: String): MenuConfig {
    return try {
        val json = JSONObject(jsonStr)
        val title = json.optString("title", "NEO CORE")
        val categories = mutableListOf<MenuCategory>()
        val catsArr = json.optJSONArray("categories")
        if (catsArr != null) {
            for (i in 0 until catsArr.length()) {
                val catObj = catsArr.getJSONObject(i)
                val name = catObj.optString("name", "SYSTEM")
                val itemsList = mutableListOf<MenuItem>()
                val itemsArr = catObj.optJSONArray("items")
                if (itemsArr != null) {
                    for (j in 0 until itemsArr.length()) {
                        val itemObj = itemsArr.getJSONObject(j)
                        val id = itemObj.optString("id", "")
                        val type = itemObj.optString("type", "button")
                        val label = itemObj.optString("label", "")
                        val defaultVal = itemObj.opt("default") ?: false
                        val min = itemObj.optDouble("min", 0.0)
                        val max = itemObj.optDouble("max", 1.0)
                        itemsList.add(MenuItem(id, type, label, defaultVal, min, max))
                    }
                }
                categories.add(MenuCategory(name, itemsList))
            }
        }
        MenuConfig(title, categories)
    } catch (e: Exception) {
        MenuConfig("NEO CORE ERROR", emptyList())
    }
}

@Composable
fun SleekFpsCounter(
    modifier: Modifier = Modifier
) {
    var fpsDisplay by remember { mutableStateOf(60) }
    LaunchedEffect(Unit) {
        var frameCount = 0
        var lastTime = System.nanoTime()
        while (true) {
            withFrameNanos {
                frameCount++
            }
            val currentTime = System.nanoTime()
            val elapsedNanos = currentTime - lastTime
            if (elapsedNanos >= 500_000_000L) {
                fpsDisplay = (frameCount * 1_000_000_000L / elapsedNanos).toInt()
                frameCount = 0
                lastTime = currentTime
            }
        }
    }
    Box(
        modifier = modifier
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "$fpsDisplay FPS",
            color = Accent,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}

@Composable
fun FloatingOverlayUi(
    isExpanded: Boolean,
    opacity: Float,
    accentIndex: Int,
    bubbleX: Float,
    bubbleY: Float,
    menuX: Float,
    menuY: Float,
    menuWidth: Int,
    menuHeight: Int,
    onToggleExpand: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onAccentChange: (Int) -> Unit,
    onCloseService: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onResize: (Float, Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(opacity)
    ) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.96f, animationSpec = tween(180))) togetherWith
                    (fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.96f, animationSpec = tween(120)))
            },
            label = "ui_transition"
        ) { expanded ->
            if (expanded) {
                SleekPanel(
                    opacity = opacity,
                    menuX = menuX,
                    menuY = menuY,
                    menuWidth = menuWidth,
                    menuHeight = menuHeight,
                    onToggleExpand = onToggleExpand,
                    onDrag = onDrag,
                    onOpacityChange = onOpacityChange,
                    onAccentChange = onAccentChange,
                    onCloseService = onCloseService,
                    onFocusChange = onFocusChange,
                    onResize = onResize
                )
            } else {
                SleekBubble(
                    bubbleX = bubbleX,
                    bubbleY = bubbleY,
                    onToggleExpand = onToggleExpand,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd
                )
            }
        }
    }
}

@Composable
fun SleekBubble(
    bubbleX: Float,
    bubbleY: Float,
    onToggleExpand: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    var breatheScale by remember { mutableStateOf(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            breatheScale = 1f + sin(System.nanoTime() / 500_000_000.0).toFloat() * 0.02f
            delay(16)
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(bubbleX.toInt(), bubbleY.toInt()) }
            .size(64.dp)
            .scale(breatheScale)
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .background(DeepBg, RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {},
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .clickable { onToggleExpand() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Accent)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "CORE",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun SleekPanel(
    opacity: Float,
    menuX: Float,
    menuY: Float,
    menuWidth: Int,
    menuHeight: Int,
    onToggleExpand: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onAccentChange: (Int) -> Unit,
    onCloseService: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onResize: (Float, Float) -> Unit
) {
    var currentScreen by remember { mutableStateOf(ScreenState.LOGIN) }
    var selectedInstance by remember { mutableStateOf("GLOBAL EDITION") }

    Box(
        modifier = Modifier
            .offset { IntOffset(menuX.toInt(), menuY.toInt()) }
            .size(menuWidth.dp, menuHeight.dp)
            .shadow(6.dp, RoundedCornerShape(6.dp))
            .background(DeepBg, RoundedCornerShape(6.dp))
            .border(1.dp, Border, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─ Header bar ─
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        )
                    }
                    .background(gradientHeader)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Accent)
                    )
                    Text(
                        text = if (currentScreen == ScreenState.TOOL_MENU) "NEO CORE :: $selectedInstance" else "NEO CORE :: LOGIN",
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SleekFpsCounter()
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceAlt)
                            .border(1.dp, Border, RoundedCornerShape(4.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Collapse",
                            tint = TextPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    IconButton(
                        onClick = onCloseService,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceAlt)
                            .border(1.dp, Border, RoundedCornerShape(4.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Unload",
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Border)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(10.dp)
            ) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        slideInHorizontally(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)) togetherWith
                            slideOutHorizontally(animationSpec = tween(150)) { -it } + fadeOut(animationSpec = tween(150))
                    },
                    label = "screens"
                ) { target ->
                    when (target) {
                        ScreenState.LOGIN -> {
                            SleekLoginScreen(
                                onLoginSuccess = { currentScreen = ScreenState.INSTANCE_PICKER },
                                onFocusChange = onFocusChange
                            )
                        }
                        ScreenState.INSTANCE_PICKER -> {
                            SleekInstancePicker(
                                selectedInstance = selectedInstance,
                                onInstanceSelect = { selectedInstance = it },
                                onConfirm = { currentScreen = ScreenState.TOOL_MENU }
                            )
                        }
                        ScreenState.TOOL_MENU -> {
                            SleekToolsMenu(
                                opacity = opacity,
                                selectedInstance = selectedInstance,
                                onDisconnect = { currentScreen = ScreenState.LOGIN },
                                onFocusChange = onFocusChange,
                                onOpacityChange = onOpacityChange,
                                onAccentChange = onAccentChange,
                                onCloseService = onCloseService
                            )
                        }
                    }
                }
            }
        }

        // ─ Resize handle ─
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(12.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onResize(dragAmount.x, dragAmount.y)
                        }
                    )
                }
                .background(Surface, RoundedCornerShape(3.dp))
                .border(1.dp, Border, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(TextMuted)
            )
        }
    }
}

@Composable
fun SleekLoginScreen(
    onLoginSuccess: () -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    var licenseKey by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    val checkLogs = remember { mutableStateListOf<String>() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(checkLogs.size) {
        if (checkLogs.isNotEmpty()) {
            listState.animateScrollToItem(checkLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ─ Gradient header accent bar ─
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(accentGradient)
                .clip(RoundedCornerShape(1.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "LICENSE VALIDATION REQUIRED",
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        if (isChecking) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(checkLogs) { log ->
                        Text(
                            text = log,
                            color = when {
                                log.contains("SUCCESS") -> Success
                                log.contains("ERR") -> Warning
                                else -> TextSecondary
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ENTER SERIAL ACTIVATION KEY:",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )

            var isFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(SurfaceAlt, RoundedCornerShape(4.dp))
                    .border(
                        1.dp,
                        if (isFocused) Accent else Border,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                BasicTextField(
                    value = licenseKey,
                    onValueChange = { licenseKey = it },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        onFocusChange(false)
                    }),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier
                        .fillMaxSize()
                        .onFocusChanged {
                            isFocused = it.isFocused
                            onFocusChange(it.isFocused)
                        }
                )

                if (licenseKey.isEmpty()) {
                    Text(
                        text = "XXXX-XXXX-XXXX-XXXX",
                        color = TextMuted.copy(alpha = 0.3f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "Binds current key signature cryptographically to device local HWID structure.",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        val buttonBg = if (licenseKey.isNotBlank()) Accent else SurfaceAlt
        val buttonText = if (licenseKey.isNotBlank()) TextPrimary else TextMuted
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(buttonBg)
                .border(1.dp, Border, RoundedCornerShape(4.dp))
                .clickable(enabled = licenseKey.isNotBlank()) {
                    focusManager.clearFocus()
                    onFocusChange(false)

                    coroutineScope.launch {
                        isChecking = true
                        checkLogs.clear()
                        checkLogs.add("[SYS] Connecting auth socket...")
                        delay(250)
                        checkLogs.add("[SYS] Exchanging DH keys session...")
                        delay(350)
                        checkLogs.add("[HWID] Captured signature: 9F2C-A4E0")
                        delay(300)
                        checkLogs.add("[AUTH] Validating serial activation...")
                        delay(400)
                        checkLogs.add("[AUTH] SUCCESS: Access approved.")
                        delay(200)
                        checkLogs.add("[SYS] Access tokens bound successfully.")
                        delay(250)
                        isChecking = false
                        onLoginSuccess()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "VALIDATE SERIAL KEY",
                color = buttonText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun SleekInstancePicker(
    selectedInstance: String,
    onInstanceSelect: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val instances = listOf(
        "GLOBAL EDITION",
        "KOREAN EDITION",
        "VIETNAM EDITION",
        "TAIWAN EDITION"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(accentGradient)
                .clip(RoundedCornerShape(1.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "SELECT TARGET INSTANCE",
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        instances.forEach { instance ->
            val isSelected = selectedInstance == instance
            SleekCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                backgroundColor = if (isSelected) Accent.copy(alpha = 0.10f) else Surface,
                borderColor = if (isSelected) Accent else Border,
                cornerRadius = 4.dp,
                onClick = { onInstanceSelect(instance) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = instance,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Accent else SurfaceAlt)
                            .border(1.dp, if (isSelected) Accent else Border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = TextPrimary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Accent)
                .border(1.dp, Border, RoundedCornerShape(4.dp))
                .clickable { onConfirm() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CONFIRM & DEPLOY TARGET",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun SleekToolsMenu(
    opacity: Float,
    selectedInstance: String,
    onDisconnect: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onAccentChange: (Int) -> Unit,
    onCloseService: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) }
    val sharedPrefs = remember { context.getSharedPreferences("floating_notes", Context.MODE_PRIVATE) }
    var notesText by remember { mutableStateOf(sharedPrefs.getString("saved_notes", "") ?: "") }

    LaunchedEffect(notesText) {
        delay(800)
        sharedPrefs.edit().putString("saved_notes", notesText).apply()
    }

    var cpuLoad by remember { mutableStateOf(10) }
    var ramUsage by remember { mutableStateOf(2.4f) }
    val batteryPct = remember {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    val menuConfig = remember {
        try {
            val file = context.filesDir.resolve("menu_config.json")
            if (file.exists()) {
                parseMenuConfig(file.readText())
            } else {
                MenuConfig("NEO CORE v1.3", emptyList())
            }
        } catch (e: Exception) {
            MenuConfig("NEO CORE ERROR", emptyList())
        }
    }

    val toggleStates = remember { mutableStateMapOf<String, Boolean>() }
    val sliderStates = remember { mutableStateMapOf<String, Float>() }

    val terminalLogs = remember {
        mutableStateListOf(
            "[INIT] Deploying module onto $selectedInstance...",
            "[INIT] Dynamic configuration compiled successfully.",
            "[NET] Handshake synchronized active."
        )
    }

    val terminalListState = rememberLazyListState()

    LaunchedEffect(menuConfig) {
        menuConfig.categories.forEach { cat ->
            cat.items.forEach { item ->
                if (item.type == "toggle") {
                    toggleStates[item.id] = item.defaultValue as? Boolean ?: false
                } else if (item.type == "slider") {
                    val defaultVal = (item.defaultValue as? Number)?.toFloat() ?: 50f
                    sliderStates[item.id] = defaultVal
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            cpuLoad = Random.nextInt(4, 28)
            ramUsage = 2.3f + Random.nextFloat() * 0.3f
        }
    }

    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty()) {
            terminalListState.animateScrollToItem(terminalLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ─ Tab bar ─
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceAlt)
                .border(1.dp, Border, RoundedCornerShape(4.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val tabs = listOf("TOOLS", "NOTEPAD", "SETTINGS")
            tabs.forEachIndexed { index, tabTitle ->
                val isSelected = activeTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isSelected) Surface else Color.Transparent)
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) Border else Color.Transparent,
                            shape = RoundedCornerShape(3.dp)
                        )
                        .clickable { activeTab = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabTitle,
                        color = if (isSelected) TextPrimary else TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (activeTab) {
                0 -> { // TOOLS
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SleekStatItem(label = "CPU", value = "$cpuLoad%", progress = cpuLoad / 100f, modifier = Modifier.weight(1f))
                            SleekStatItem(label = "RAM", value = String.format("%.2fG", ramUsage), progress = ramUsage / 6.0f, modifier = Modifier.weight(1f))
                            SleekStatItem(label = "BAT", value = "$batteryPct%", progress = batteryPct / 100f, modifier = Modifier.weight(1f))
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            menuConfig.categories.forEach { category ->
                                item(key = "cat_${category.name}") {
                                    Text(
                                        text = ":: ${category.name}",
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }

                                items(
                                    items = category.items,
                                    key = { "item_${it.id}" }
                                ) { item ->
                                    when (item.type) {
                                        "toggle" -> {
                                            val isChecked = toggleStates[item.id] ?: false
                                            SleekCard(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(40.dp),
                                                backgroundColor = if (isChecked) Surface else SurfaceAlt,
                                                borderColor = if (isChecked) Accent else Border,
                                                cornerRadius = 4.dp,
                                                onClick = {
                                                    val nextState = !isChecked
                                                    toggleStates[item.id] = nextState
                                                    terminalLogs.add("[ACTION] Toggle '${item.label}' => ${if (nextState) "ENABLED" else "DISABLED"}")
                                                    Toast.makeText(context, "${item.label}: ${if (nextState) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = item.label,
                                                        color = TextPrimary,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                    SleekSwitch(
                                                        checked = isChecked,
                                                        onCheckedChange = {
                                                            val nextState = !isChecked
                                                            toggleStates[item.id] = nextState
                                                            terminalLogs.add("[ACTION] Toggle '${item.label}' => ${if (nextState) "ENABLED" else "DISABLED"}")
                                                            Toast.makeText(context, "${item.label}: ${if (nextState) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        "slider" -> {
                                            val currentVal = sliderStates[item.id] ?: 75f
                                            SleekCard(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(56.dp),
                                                backgroundColor = Surface,
                                                borderColor = Border,
                                                cornerRadius = 4.dp
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = item.label,
                                                            color = TextPrimary,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        )
                                                        Text(
                                                            text = "${currentVal.toInt()}",
                                                            color = Accent,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    Slider(
                                                        value = currentVal,
                                                        onValueChange = {
                                                            sliderStates[item.id] = it
                                                        },
                                                        onValueChangeFinished = {
                                                            terminalLogs.add("[ACTION] Slider '${item.label}' => ${currentVal.toInt()}")
                                                        },
                                                        valueRange = item.min.toFloat()..item.max.toFloat(),
                                                        colors = SliderDefaults.colors(
                                                            thumbColor = TextPrimary,
                                                            activeTrackColor = Accent,
                                                            inactiveTrackColor = SurfaceAlt
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                        "button" -> {
                                            SleekCard(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(40.dp),
                                                backgroundColor = Accent.copy(alpha = 0.10f),
                                                borderColor = Accent,
                                                cornerRadius = 4.dp,
                                                onClick = {
                                                    terminalLogs.add("[ACTION] Clicked: '${item.label}' successfully.")
                                                    Toast.makeText(context, "${item.label} Executed", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text(
                                                    text = item.label,
                                                    color = TextPrimary,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.fillMaxSize(),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ─ Console ─
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(DeepBg)
                                .border(1.dp, Border, RoundedCornerShape(4.dp))
                                .padding(6.dp)
                        ) {
                            LazyColumn(
                                state = terminalListState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(terminalLogs) { log ->
                                    val logColor = when {
                                        log.contains("[ACTION]") -> Accent
                                        log.contains("[INIT]") -> TextPrimary
                                        log.contains("[NET]") -> Success
                                        else -> TextSecondary
                                    }
                                    Text(
                                        text = log,
                                        color = logColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> { // NOTEPAD
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceAlt)
                                .border(1.dp, Border, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            BasicTextField(
                                value = notesText,
                                onValueChange = { notesText = it },
                                textStyle = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(Accent),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onFocusChanged { onFocusChange(it.isFocused) }
                            )

                            if (notesText.isEmpty()) {
                                Text(
                                    text = "Capture temporary values here...",
                                    color = TextMuted.copy(alpha = 0.3f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Surface)
                                    .border(1.dp, Border, RoundedCornerShape(4.dp))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Overlay Notes", notesText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied scratchpad", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("COPY SCRATCHPAD", color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceAlt)
                                    .border(1.dp, Border, RoundedCornerShape(4.dp))
                                    .clickable {
                                        notesText = ""
                                        sharedPrefs.edit().putString("saved_notes", "").apply()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("CLEAR NOTES", color = Warning, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }
                        }
                    }
                }
                2 -> { // SETTINGS
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "INTERFACE PREFERENCES",
                            fontSize = 9.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        SleekCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            backgroundColor = Surface,
                            borderColor = Border,
                            cornerRadius = 4.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Overlay Translucency", color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    Text(String.format("%.0f%%", opacity * 100f), color = Accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                                Slider(
                                    value = opacity,
                                    onValueChange = onOpacityChange,
                                    valueRange = 0.35f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = TextPrimary,
                                        activeTrackColor = Accent,
                                        inactiveTrackColor = SurfaceAlt
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(4.dp))
                    .clickable { onDisconnect() },
                contentAlignment = Alignment.Center
            ) {
                Text("DISCONNECT", color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceAlt)
                    .border(1.dp, Border, RoundedCornerShape(4.dp))
                    .clickable { onCloseService() },
                contentAlignment = Alignment.Center
            ) {
                Text("UNLOAD CORE", color = Warning, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun SleekStatItem(
    label: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(4.dp))
            .padding(6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = TextSecondary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                Text(value, color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(SurfaceAlt)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Accent)
                )
            }
        }
    }
}
