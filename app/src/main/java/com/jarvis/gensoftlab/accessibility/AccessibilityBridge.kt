package com.jarvis.gensoftlab.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

object AccessibilityBridge {
    private val serviceRef = AtomicReference<WeakReference<JarvisAccessibilityService>?>(null)

    fun register(service: JarvisAccessibilityService) {
        serviceRef.set(WeakReference(service))
    }

    fun unregister() {
        serviceRef.set(null)
    }

    fun isAvailable(): Boolean {
        return getService() != null
    }

    fun getService(): JarvisAccessibilityService? {
        return serviceRef.get()?.get()
    }

    fun getRootNode(): AccessibilityNodeInfo? {
        return getService()?.rootInActiveWindow
    }

    fun getCurrentPackage(): String? {
        return getRootNode()?.packageName?.toString()
    }

    fun performGlobalAction(action: Int): Boolean {
        return getService()?.performGlobalAction(action) ?: false
    }
}
