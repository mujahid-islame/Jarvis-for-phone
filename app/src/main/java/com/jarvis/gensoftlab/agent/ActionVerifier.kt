package com.jarvis.gensoftlab.agent

import com.jarvis.gensoftlab.accessibility.AccessibilityBridge
import com.jarvis.gensoftlab.accessibility.UiSnapshot
import com.jarvis.gensoftlab.accessibility.AccessibilityNodeFinder

object ActionVerifier {

    fun verifyPackageChanged(oldPackage: String?): Boolean {
        val currentPackage = AccessibilityBridge.getCurrentPackage()
        return currentPackage != null && currentPackage != oldPackage
    }

    fun verifyTextAppeared(text: String): Boolean {
        val root = AccessibilityBridge.getRootNode() ?: return false
        val nodes = AccessibilityNodeFinder.findByText(root, text)
        return nodes.isNotEmpty()
    }

    fun verifyUIChanged(oldFingerprint: String?): Boolean {
        val root = AccessibilityBridge.getRootNode() ?: return false
        val snapshot = AccessibilityNodeFinder.captureSnapshot(root, 1080, 2400)
        // Basic fingerprint using element count and package
        val newFingerprint = "${snapshot.packageName}_${snapshot.nodes.size}"
        return newFingerprint != oldFingerprint
    }
}
