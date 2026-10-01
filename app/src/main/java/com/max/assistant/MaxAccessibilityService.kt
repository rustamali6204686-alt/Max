package com.max.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MaxAccessibilityService : AccessibilityService() {

    private data class Item(
        val node: AccessibilityNodeInfo,
        val label: String,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val password: Boolean
    )

    companion object {

        @Volatile
        var instance: MaxAccessibilityService? = null
    }

    @Volatile
    private var items: List<Item> = emptyList()

    override fun onServiceConnected() {

        super.onServiceConnected()

        instance = this

        /*
         * Configure the service programmatically as well as through
         * accessibility_config.xml.
         *
         * This gives Max access to:
         * - window content
         * - interactive windows
         * - view IDs
         * - gestures
         */
        serviceInfo =
            serviceInfo.apply {

                eventTypes =
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED

                feedbackType =
                    AccessibilityServiceInfo.FEEDBACK_GENERIC

                flags =
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

                notificationTimeout = 100
            }
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        /*
         * The screen is intentionally not rebuilt on every event.
         *
         * dumpScreen() explicitly creates a fresh snapshot immediately
         * before the agent decides what to do.
         */
    }

    override fun onInterrupt() {
        items = emptyList()
    }

    override fun onUnbind(
        intent: Intent?
    ): Boolean {

        items = emptyList()
        instance = null

        return super.onUnbind(intent)
    }

    override fun onDestroy() {

        items = emptyList()
        instance = null

        super.onDestroy()
    }

    fun currentPackage(): String {

        return try {

            rootInActiveWindow
                ?.packageName
                ?.toString()
                ?: ""

        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Creates a fresh numbered screen snapshot.
     *
     * The indexes returned here are the indexes the AI should use
     * for tap/type operations.
     */
    fun dumpScreen(): String {

        val root =
            try {
                rootInActiveWindow
            } catch (_: Exception) {
                null
            }

        if (root == null) {

            items = emptyList()

            return "SCREEN_UNAVAILABLE"
        }

        val list =
            ArrayList<Item>()

        try {

            collect(
                root,
                list,
                0
            )

            items = list

            val sb =
                StringBuilder()

            sb.append("app: ")
                .append(
                    root.packageName
                        ?.toString()
                        ?: ""
                )
                .append("\n")

            for (
                (index, item) in
                list.withIndex()
            ) {

                sb.append(index)
                    .append(": ")
                    .append(item.label)

                if (item.password) {

                    sb.append(
                        " [password field]"
                    )

                } else if (item.editable) {

                    sb.append(
                        " [input]"
                    )

                } else if (item.clickable) {

                    sb.append(
                        " [tap]"
                    )

                }

                if (item.scrollable) {
                    sb.append(" [scroll]")
                }

                sb.append("\n")
            }

            return sb
                .toString()
                .take(8000)

        } catch (_: Exception) {

            items = emptyList()

            return "SCREEN_UNAVAILABLE"
        }
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        out: ArrayList<Item>,
        depth: Int
    ) {

        if (
            node == null ||
            depth > 35 ||
            out.size >= 150
        ) {
            return
        }

        try {

            if (node.isVisibleToUser) {

                val text =
                    node.text
                        ?.toString()
                        .orEmpty()

                val description =
                    node.contentDescription
                        ?.toString()
                        .orEmpty()

                val hint =
                    node.hintText
                        ?.toString()
                        .orEmpty()

                val password =
                    node.isPassword

                val label =
                    if (password) {
                        "[password field]"
                    } else {
                        when {
                            text.isNotBlank() ->
                                text.trim()

                            description.isNotBlank() ->
                                description.trim()

                            hint.isNotBlank() ->
                                hint.trim()

                            else ->
                                ""
                        }
                    }

                if (
                    label.isNotEmpty() ||
                    node.isEditable ||
                    node.isScrollable
                ) {

                    out.add(
                        Item(
                            node = node,
                            label = label.take(100),
                            clickable = node.isClickable,
                            editable = node.isEditable,
                            scrollable = node.isScrollable,
                            password = password
                        )
                    )
                }
            }

            val childCount =
                node.childCount

            for (
                i in 0 until childCount
            ) {

                if (out.size >= 150) {
                    break
                }

                collect(
                    node.getChild(i),
                    out,
                    depth + 1
                )
            }

        } catch (_: Exception) {
            /*
             * Some third-party apps expose accessibility nodes that
             * disappear while traversing. Ignore that node instead
             * of crashing the entire service.
             */
        }
    }

    fun describeTarget(
        index: Int
    ): String {

        val item =
            items.getOrNull(index)
                ?: return ""

        return try {

            val sb =
                StringBuilder()

            sb.append(
                item.label
            )

            if (item.password) {
                sb.append(
                    " [password field]"
                )
            }

            var node: AccessibilityNodeInfo? =
                item.node

            var depth = 0

            while (
                node != null &&
                depth < 6
            ) {

                val text =
                    node.text
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                val description =
                    node.contentDescription
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                if (
                    text.isNotBlank() &&
                    text != item.label
                ) {
                    sb.append(" ")
                        .append(text)
                }

                if (
                    description.isNotBlank() &&
                    description != item.label
                ) {
                    sb.append(" ")
                        .append(description)
                }

                if (node.isClickable) {
                    break
                }

                node =
                    try {
                        node.parent
                    } catch (_: Exception) {
                        null
                    }

                depth++
            }

            sb.toString()
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()
                .take(500)

        } catch (_: Exception) {

            item.label
        }
    }

    fun tap(
        index: Int
    ): String {

        val item =
            items.getOrNull(index)
                ?: return "aisa item nahi hai"

        if (item.password) {
            return "password field par tap blocked hai"
        }

        try {

            /*
             * First try the node itself.
             */
            if (
                item.node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            ) {
                return "ok"
            }

            /*
             * If the node itself is not clickable, walk upward
             * to its clickable parent.
             */
            var parent =
                try {
                    item.node.parent
                } catch (_: Exception) {
                    null
                }

            var depth = 0

            while (
                parent != null &&
                depth < 6
            ) {

                if (parent.isClickable) {

                    if (
                        parent.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )
                    ) {
                        return "ok"
                    }

                    break
                }

                parent =
                    try {
                        parent.parent
                    } catch (_: Exception) {
                        null
                    }

                depth++
            }

            /*
             * Last fallback: physical gesture at the node's
             * screen coordinates.
             */
            val rect =
                Rect()

            item.node.getBoundsInScreen(
                rect
            )

            if (
                rect.width() > 0 &&
                rect.height() > 0
            ) {

                return if (
                    tapAt(
                        rect.centerX(),
                        rect.centerY()
                    )
                ) {
                    "ok"
                } else {
                    "tap nahi hua"
                }
            }

        } catch (_: Exception) {
        }

        return "tap nahi hua"
    }

    private fun tapAt(
        x: Int,
        y: Int
    ): Boolean {

        if (x < 0 || y < 0) {
            return false
        }

        return try {

            val path =
                Path()

            path.moveTo(
                x.toFloat(),
                y.toFloat()
            )

            val stroke =
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    80L
                )

            val gesture =
                GestureDescription.Builder()
                    .addStroke(stroke)
                    .build()

            dispatchGesture(
                gesture,
                null,
                null
            )

        } catch (_: Exception) {

            false
        }
    }

    fun typeText(
        index: Int,
        text: String
    ): String {

        val item =
            items.getOrNull(index)
                ?: return "aisa item nahi hai"

        if (item.password) {
            return "password field mein typing blocked hai"
        }

        if (!item.editable) {
            return "ye input field nahi hai"
        }

        try {

            item.node.performAction(
                AccessibilityNodeInfo.ACTION_FOCUS
            )

            val args =
                Bundle()

            args.putCharSequence(
                AccessibilityNodeInfo
                    .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )

            return if (
                item.node.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    args
                )
            ) {
                "ok"
            } else {
                "type nahi hua"
            }

        } catch (_: Exception) {

            return "type nahi hua"
        }
    }

    fun scroll(
        down: Boolean
    ): String {

        /*
         * Prefer the first visible scrollable container.
         */
        val target =
            items.firstOrNull {
                it.scrollable
            }
                ?: return "scroll hone wali cheez nahi mili"

        return try {

            val action =
                if (down) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }

            if (
                target.node.performAction(
                    action
                )
            ) {
                "ok"
            } else {
                "scroll nahi hua"
            }

        } catch (_: Exception) {

            "scroll nahi hua"
        }
    }

    fun press(
        name: String
    ): String {

        val action =
            when (
                name
                    .lowercase()
                    .trim()
            ) {

                "back" ->
                    GLOBAL_ACTION_BACK

                "home" ->
                    GLOBAL_ACTION_HOME

                "recents" ->
                    GLOBAL_ACTION_RECENTS

                "notifications" ->
                    GLOBAL_ACTION_NOTIFICATIONS

                "quick_settings" ->
                    GLOBAL_ACTION_QUICK_SETTINGS

                else ->
                    return "aisa button nahi hai"
            }

        return try {

            if (
                performGlobalAction(action)
            ) {
                "ok"
            } else {
                "nahi hua"
            }

        } catch (_: Exception) {

            "nahi hua"
        }
    
