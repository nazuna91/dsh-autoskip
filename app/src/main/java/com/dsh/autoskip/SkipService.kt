package com.dsh.autoskip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * 自动点掉 Lanerc 换集弹窗的「暂时跳过」。
 *
 * v2 改动（针对"要等 5 秒"）：
 *   · 事件驱动 → 轮询驱动：每 300ms 扫一次界面，倒计时一结束立刻点，比手快
 *   · 倒计时期间不点（避免误触"立即观看"），只等文本里的秒数变成可点状态
 *   · 兜底：实在点不动时，按屏幕比例点「暂时跳过」的位置（可关）
 */
class SkipService : AccessibilityService() {

    companion object {
        const val TAG = "DSH-AutoSkip"
        const val TARGET = "com.clggjv.xcjfmd.ffo"
        val SKIP_TEXTS = listOf("暂时跳过", "跳过广告", "跳过")
        val POPUP_HINTS = listOf("拯救 Lanerc", "拯救Lanerc", "支持我们", "立即观看")

        var clicks = 0
        var lastHit = ""
        var debug = false

        /** 兜底坐标（相对屏幕比例）：截图里「暂时跳过」的位置 */
        const val RATIO_X = 0.353f
        const val RATIO_Y = 0.527f
    }

    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var lastClickAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!polling) return
            try { scan() } catch (e: Throwable) { Log.w(TAG, "scan: ${e.message}") }
            handler.postDelayed(this, 300)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        polling = true
        handler.post(tick)
        toast("自动跳过已开启")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != TARGET) return
        // 事件到了先扫一次，抢一手
        if (System.currentTimeMillis() - lastClickAt > 500) {
            try { scan() } catch (_: Throwable) {}
        }
    }

    private fun scan() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != TARGET) return

        val popup = containsAny(root, POPUP_HINTS, 0)
        if (debug && popup) logTree(root, 0)

        val node = findSkip(root, 0) ?: return
        val label = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            .joinToString(" ")

        // 倒计时中（文本里还有数字）先不点，等它变成可点
        val counting = label.any { it.isDigit() }
        if (counting) return

        val now = System.currentTimeMillis()
        if (now - lastClickAt < 400) return

        val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (ok) {
            clicks++
            lastHit = label.ifEmpty { "跳过" }
            lastClickAt = now
            Log.i(TAG, "clicked: $label")
            return
        }

        // 兜底：节点点不动，按比例点一次
        if (popup) {
            tapRatio(RATIO_X, RATIO_Y)
            lastClickAt = now
            clicks++
            lastHit = "坐标兜底"
            Log.i(TAG, "tap fallback")
        }
    }

    private fun tapRatio(rx: Float, ry: Float) {
        try {
            val dm = resources.displayMetrics
            val x = dm.widthPixels * rx
            val y = dm.heightPixels * ry
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 60)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        } catch (e: Throwable) {
            Log.w(TAG, "tap: ${e.message}")
        }
    }

    private fun containsAny(node: AccessibilityNodeInfo?, keys: List<String>, depth: Int): Boolean {
        if (node == null || depth > 20) return false
        val t = listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" ")
        if (t.isNotBlank() && keys.any { t.contains(it) }) return true
        for (i in 0 until node.childCount) {
            if (containsAny(node.getChild(i), keys, depth + 1)) return true
        }
        return false
    }

    private fun findSkip(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 24) return null
        val text = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            .joinToString(" ")

        if (text.isNotBlank() && SKIP_TEXTS.any { text.contains(it) }) {
            if (node.isClickable && node.isEnabled) return node
            var p = node.parent
            var guard = 0
            while (p != null && guard < 6) {
                if (p.isClickable && p.isEnabled) return p
                p = p.parent
                guard++
            }
            // 自身不可点但文本已经是最终态：也返回，让调用方决定兜底
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findSkip(node.getChild(i), depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun logTree(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null || depth > 12) return
        val t = listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString("|")
        if (t.isNotBlank()) Log.d(TAG, "  ".repeat(depth) + "[" + t + "] clickable=" + node.isClickable)
        for (i in 0 until node.childCount) logTree(node.getChild(i), depth + 1)
    }

    private fun toast(msg: String) {
        try { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        polling = false
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
