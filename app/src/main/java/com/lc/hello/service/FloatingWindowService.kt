package com.lc.hello.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import java.io.File
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

class FloatingWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var glSurfaceView: TemplateGLSurfaceView
    private lateinit var params: WindowManager.LayoutParams
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastWantTextInput = false
    private var touchMode = TOUCH_NONE
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var screenWidth = 1080
    private var screenHeight = 2400

    private companion object {
        const val CHANNEL_ID = "floating_window_channel"
        const val TOUCH_NONE = 0
        const val TOUCH_DRAG = 1
        const val DRAG_HANDLE_HEIGHT = 56f
        const val CLOSE_ZONE_WIDTH = 120f
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
        JniBridge.setScreenSize(screenWidth, screenHeight)
        setupForegroundNotification()
        prepareImGuiFonts()
        setupOverlay()
    }

    private fun setupForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Window Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Running Dear ImGui overlay template" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Overlay Template")
            .setContentText("Dear ImGui template is running.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1001, notification)
            }
        } else {
            startForeground(1001, notification)
        }
    }

    private fun prepareImGuiFonts() {
        val body = copyAssetFontToFiles("fonts/Nunito-Regular.ttf")
        val heading = copyAssetFontToFiles("fonts/Nunito-Bold.ttf")
        JniBridge.setFontPaths(body.absolutePath, heading.absolutePath)
    }

    private fun copyAssetFontToFiles(assetPath: String): File {
        val output = File(filesDir, assetPath.substringAfterLast('/'))
        assets.open(assetPath).use { input ->
            output.outputStream().use { output -> input.copyTo(output) }
        }
        return output
    }

    private fun setupOverlay() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val initialWidth = responsiveWidth(0.86f, 430, 590)
        val initialHeight = responsiveHeight(0.44f, 560, 720)

        params = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((screenWidth - initialWidth) * 0.5f).roundToInt().coerceAtLeast(0)
            y = ((screenHeight - initialHeight) * 0.5f).roundToInt().coerceAtLeast(0)
        }

        glSurfaceView = TemplateGLSurfaceView(this).apply {
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setEGLContextClientVersion(3)
            setRenderer(object : GLSurfaceView.Renderer {
                override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                    JniBridge.initImGui()
                }

                override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                    JniBridge.resizeImGui(width, height)
                }

                override fun onDrawFrame(gl: GL10?) {
                    JniBridge.stepAndRenderImGui()
                    syncActiveWindowBounds()
                    checkKeyboardFocus()
                    if (JniBridge.checkCloseRequested()) {
                        mainHandler.post { stopSelf() }
                    }
                }
            })
            holder.setFormat(PixelFormat.TRANSLUCENT)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartRawX = event.rawX
                        dragStartRawY = event.rawY
                        dragStartX = params.x
                        dragStartY = params.y
                        touchMode = if (
                            event.y <= DRAG_HANDLE_HEIGHT &&
                            event.x <= glSurfaceView.width - CLOSE_ZONE_WIDTH
                        ) TOUCH_DRAG else TOUCH_NONE
                        if (touchMode == TOUCH_DRAG) return@setOnTouchListener true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (touchMode == TOUCH_DRAG) {
                            val dx = event.rawX - dragStartRawX
                            val dy = event.rawY - dragStartRawY
                            params.x = (dragStartX + dx.roundToInt()).coerceIn(0, (screenWidth - params.width).coerceAtLeast(0))
                            params.y = (dragStartY + dy.roundToInt()).coerceIn(0, (screenHeight - params.height).coerceAtLeast(0))
                            if (glSurfaceView.isAttachedToWindow) {
                                windowManager.updateViewLayout(glSurfaceView, params)
                            }
                            return@setOnTouchListener true
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (touchMode == TOUCH_DRAG) {
                            touchMode = TOUCH_NONE
                            return@setOnTouchListener true
                        }
                        touchMode = TOUCH_NONE
                    }
                }

                val action = when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> 0
                    MotionEvent.ACTION_UP -> 1
                    MotionEvent.ACTION_MOVE -> 2
                    else -> -1
                }
                if (action != -1) JniBridge.onTouchEvent(action, event.x, event.y)
                true
            }
        }

        windowManager.addView(glSurfaceView, params)
    }

    private fun responsiveWidth(percent: Float, minValue: Int, maxValue: Int): Int {
        val available = (screenWidth - 32).coerceAtLeast(320)
        val desired = (screenWidth * percent).roundToInt().coerceIn(minValue, maxValue)
        return desired.coerceAtMost(available)
    }

    private fun responsiveHeight(percent: Float, minValue: Int, maxValue: Int): Int {
        val available = (screenHeight - 48).coerceAtLeast(420)
        val desired = (screenHeight * percent).roundToInt().coerceIn(minValue, maxValue)
        return desired.coerceAtMost(available)
    }

    private fun syncActiveWindowBounds() {
        if (touchMode == TOUCH_DRAG || !::glSurfaceView.isInitialized || !glSurfaceView.isAttachedToWindow) return
        val desired = try { JniBridge.getDesiredWindowSize() } catch (_: Exception) { return }
        if (desired.size < 2) return

        val nextW = desired[0].roundToInt().coerceIn(1, screenWidth)
        val nextH = desired[1].roundToInt().coerceIn(1, screenHeight)
        val nextX = params.x.coerceIn(0, (screenWidth - nextW).coerceAtLeast(0))
        val nextY = params.y.coerceIn(0, (screenHeight - nextH).coerceAtLeast(0))
        if (params.width == nextW && params.height == nextH && params.x == nextX && params.y == nextY) return

        mainHandler.post {
            if (!glSurfaceView.isAttachedToWindow) return@post
            params.width = nextW
            params.height = nextH
            params.x = nextX
            params.y = nextY
            windowManager.updateViewLayout(glSurfaceView, params)
        }
    }

    private fun setWindowFocusable(focusable: Boolean) {
        val isFocusable = (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0
        if (focusable == isFocusable) return
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (glSurfaceView.isAttachedToWindow) {
            windowManager.updateViewLayout(glSurfaceView, params)
        }
    }

    private fun checkKeyboardFocus() {
        val wantTextInput = try { JniBridge.wantTextInput() } catch (_: Exception) { false }
        if (wantTextInput == lastWantTextInput) return

        lastWantTextInput = wantTextInput
        mainHandler.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (wantTextInput) {
                setWindowFocusable(true)
                glSurfaceView.postDelayed({
                    glSurfaceView.requestFocus()
                    imm.showSoftInput(glSurfaceView, InputMethodManager.SHOW_IMPLICIT)
                }, 50)
            } else {
                imm.hideSoftInputFromWindow(glSurfaceView.windowToken, 0)
                setWindowFocusable(false)
            }
        }
    }

    override fun onDestroy() {
        if (::glSurfaceView.isInitialized && glSurfaceView.isAttachedToWindow) {
            windowManager.removeView(glSurfaceView)
        }
        super.onDestroy()
    }
}

class TemplateGLSurfaceView(context: Context) : GLSurfaceView(context) {
    init {
        isFocusableInTouchMode = true
        isFocusable = true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.forEach { char ->
                    JniBridge.onKeyEvent(char.code, 0, true)
                    JniBridge.onKeyEvent(char.code, 0, false)
                }
                return super.commitText(text, newCursorPosition)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) {
                    JniBridge.onKeyEvent(0, KeyEvent.KEYCODE_DEL, true)
                    JniBridge.onKeyEvent(0, KeyEvent.KEYCODE_DEL, false)
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        JniBridge.onKeyEvent(event.getUnicodeChar(), event.keyCode, isDown)
        return if (JniBridge.wantTextInput()) true else super.dispatchKeyEvent(event)
    }
}
