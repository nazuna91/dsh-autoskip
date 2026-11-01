package com.dsh.autoskip

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * 自动点掉 Lanerc 换集时的「拯救 Lanerc 计划」弹窗。
 *
 * 原理：无障碍服务读窗口语义树，找文本含"暂时跳过 / 跳过"的节点，直接 performAction 点击。
 * 不注入目标进程、不需要 root，加固壳检测不到。
 */
class SkipService : AccessibilityService() {

    companion object {
        const val TARGET = "com.clggjv.xcjfmd.ffo"
        val SKIP_TEXTS = listOf("暂时跳过", "跳过", "跳过广告", "关闭", "以后再说")
        var clicks = 0
        var lastHit = ""
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        toast("自动跳过已开启")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != TARGET) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) return

        val root = rootInActiveWindow ?: return
        val hit = findSkip(root, 0)
        if (hit != null) {
            val label = hit.text?.toString() ?: hit.contentDescription?.toString() ?: "?"
            val ok = hit.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (ok) {
                clicks++
                lastHit = label
            }
        }
    }

    /** 广度优先找可点击的"跳过"节点 */
    private fun findSkip(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 24) return null
        val text = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
        ).joinToString(" ")

        if (text.isNotBlank() && SKIP_TEXTS.any { text.contains(it) }) {
            if (node.isClickable) return node
            // 文本节点本身不可点，找最近的可点击祖先
            var p = node.parent
            var guard = 0
            while (p != null && guard < 6) {
                if (p.isClickable) return p
                p = p.parent
                guard++
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findSkip(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun toast(msg: String) {
        try { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
    }

    override fun onInterrupt() {}
}
