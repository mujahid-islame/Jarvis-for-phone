package com.jarvis.gensoftlab.util

import com.jarvis.gensoftlab.data.model.GeminiConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptGeneratorTest {

    @Test
    fun `generateSystemPrompt includes memory context`() {
        val prompt = PromptGenerator.generateSystemPrompt(
            personality = GeminiConstants.PERSONALITY_ASSISTANT,
            userName = "Nafi",
            memoryContext = "User likes short actionable answers and Bengali mode."
        )

        assertTrue(prompt.contains("USER MEMORY"))
        assertTrue(prompt.contains("Bengali mode"))
    }

    @Test
    fun `generateSystemPrompt describes device action capabilities`() {
        val prompt = PromptGenerator.generateSystemPrompt(
            personality = GeminiConstants.PERSONALITY_ASSISTANT,
            userName = "Nafi"
        )

        assertTrue(prompt.contains("open YouTube"))
        assertTrue(prompt.contains("Android Settings"))
        assertTrue(prompt.contains("Do not say that you cannot open YouTube"))
    }

    @Test
    fun `tool registry exposes structured action names`() {
        val names = JarvisToolRegistry.declarations.mapNotNull { it["name"]?.toString() }

        assertTrue(names.contains("open_app"))
        assertTrue(names.contains("get_screen_context"))
        assertTrue(names.contains("wait_for_element"))
        assertTrue(names.contains("youtube_search"))
        assertTrue(names.contains("open_settings"))
    }

    @Test
    fun `action result defaults to unverified failure safely`() {
        val result = ActionResult(
            success = false,
            action = "click_text",
            message = "not found",
            verification = VerificationStatus.FAILED,
            retryable = true
        )

        assertFalse(result.success)
        assertTrue(result.shouldReobserve)
        assertTrue(result.confidence == 0.0)
    }

    @Test
    fun `wake word detector recognizes jarvis trigger`() {
        assertTrue(WakeWordDetector.detect("Hey Jarvis, what time is it?"))
        assertTrue(WakeWordDetector.detect("Jarvis, please open my notes."))
        assertFalse(WakeWordDetector.detect("Tell me about the weather."))
    }
}
