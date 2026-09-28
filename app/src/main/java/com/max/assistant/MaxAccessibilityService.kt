package com.max.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MaxAccessibilityService : AccessibilityService() {

    class Item(
        val node: AccessibilityNodeInfo,
        val label: String,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean
    )

    companion object {
        var instance: MaxAccessibilityService? = null
    }

    private var items: List<Item> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun currentPackage(): String {
        return rootInActiveWindow?.packageName?.toString() ?: ""
    }

    fun dumpScreen(): String {
        val root = rootInActiveWindow ?: return "SCREEN_UNAVAILABLE"
        val list = ArrayList<Item>()
        collect(root, list, 0)
        items = list
        val sb = StringBuilder()
        sb.append("app: ").append(root.packageName).append("\n")
        for ((i, item) in list.withIndex()) {
            sb.append(i).append(": ").append(item.label)
            if (item.editable) sb.append(" [input]")
            else if (item.clickable) sb.append(" [tap]")
            if (item.scrollable) sb.append(" [scroll]")
            sb.append("\n")
        }
        return sb.toString().take(6000)
    }

    private fun collect(n: AccessibilityNodeInfo?, out: ArrayList<Item>, depth: Int) {
        if (n == null || depth > 30 || out.size > 120) return
        if (n.isVisibleToUser) {
            val t = n.text?.toString().orEmpty()
            val d = n.contentDescription?.toString().orEmpty()
            val h = n.hintText?.toString().orEmpty()
            var label = (if (t.isNotBlank()) t else if (d.isNotBlank()) d else h).trim()
            if (n.isPassword) label = "[password field]"
            if (label.isNotEmpty() || n.isEditable || n.isScrollable) {
                out.add(Item(n, label.take(80), n.isClickable, n.isEditable, n.isScrollable))
            }
        }
        for (i in 0 until n.childCount) {
            collect(n.getChild(i), out, depth + 1)
        }
    }

    fun describeTarget(index: Int): String {
        val item = items.getOrNull(index) ?: return ""
        val sb = StringBuilder(item.label)
        var n: AccessibilityNodeInfo? = item.node
        var depth = 0
        while (n != null && depth < 6) {
            sb.append(' ').append(n.text ?: "").append(' ').append(n.contentDescription ?: "")
            if (n.isClickable) break
            n = n.parent
            depth++
        }
        return sb.toString()
    }

    fun tap(index: Int): String {
        val item = items.getOrNull(index) ?: return "aisa item nahi hai"
        var n: AccessibilityNodeInfo? = item.node
        var depth = 0
        while (n != null && !n.isClickable && depth < 6) {
            n = n.parent
            depth++
        }
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "ok"
        val r = Rect()
        item.node.getBoundsInScreen(r)
        return if (tapAt(r.centerX(), r.centerY())) "ok" else "tap nahi hua"
    }

    private fun tapAt(x: Int, y: Int): Boolean {
        val p = Path()
        p.moveTo(x.toFloat(), y.toFloat())
        val stroke = GestureDescription.StrokeDescription(p, 0, 80)
        val g = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(g, null, null)
    }

    fun typeText(index: Int, text: String): String {
        val item = items.getOrNull(index) ?: return "aisa item nahi hai"
        item.node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return if (item.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "ok" else "type nahi hua"
    }

    fun scroll(down: Boolean): String {
        val target = items.firstOrNull { it.scrollable } ?: return "scroll hone wali cheez nahi mili"
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return if (target.node.performAction(action)) "ok" else "scroll nahi hua"
    }

    fun press(name: String): String {
        val action = when (name) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return "aisa button nahi hai"
        }
        return if (performGlobalAction(action)) "ok" else "nahi hua"
    }
}
