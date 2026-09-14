package com.jarvis.assistant.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AssistantCommandParserTest {

    @Test
    fun `parses reminder command in Bangla`() {
        val command = AssistantCommandParser.parse("জরুরী মনে রাখো, বিকেল ৫ টায় meeting")

        assertNotNull(command)
        assertEquals(AssistantCommandType.REMINDER, command?.type)
    }

    @Test
    fun `parses note command in Bangla`() {
        val command = AssistantCommandParser.parse("নোট রাখো, আগামীকাল রিপোর্ট জমা দেব")

        assertNotNull(command)
        assertEquals(AssistantCommandType.NOTE, command?.type)
    }

    @Test
    fun `parses timer command in Bangla`() {
        val command = AssistantCommandParser.parse("৫ মিনিটের টাইমার দাও")

        assertNotNull(command)
        assertEquals(AssistantCommandType.TIMER, command?.type)
    }

    @Test
    fun `parses alarm command in Bangla`() {
        val command = AssistantCommandParser.parse("সকাল ৭ টায় অ্যালার্ম দাও")

        assertNotNull(command)
        assertEquals(AssistantCommandType.ALARM, command?.type)
    }

    @Test
    fun `parses time command in Bangla`() {
        val command = AssistantCommandParser.parse("এখন কত বাজে")

        assertNotNull(command)
        assertEquals(AssistantCommandType.TIME, command?.type)
    }

    @Test
    fun `parses web search command`() {
        val command = AssistantCommandParser.parse("search for latest Android news")

        assertNotNull(command)
        assertEquals(AssistantCommandType.WEB_SEARCH, command?.type)
    }

    @Test
    fun `parses weather command`() {
        val command = AssistantCommandParser.parse("weather in Dhaka")

        assertNotNull(command)
        assertEquals(AssistantCommandType.WEATHER, command?.type)
    }

    @Test
    fun `parses news command`() {
        val command = AssistantCommandParser.parse("show me the latest news")

        assertNotNull(command)
        assertEquals(AssistantCommandType.NEWS, command?.type)
    }

    @Test
    fun `parses device control command`() {
        val command = AssistantCommandParser.parse("open wifi settings")

        assertNotNull(command)
        assertEquals(AssistantCommandType.DEVICE_CONTROL, command?.type)
    }

    @Test
    fun `parses app automation command`() {
        val command = AssistantCommandParser.parse("open YouTube")

        assertNotNull(command)
        assertEquals(AssistantCommandType.APP_AUTOMATION, command?.type)
    }

    @Test
    fun `parses Bengali YouTube command`() {
        val command = AssistantCommandParser.parse("ইউটিউব খোলো")

        assertNotNull(command)
        assertEquals(AssistantCommandType.APP_AUTOMATION, command?.type)
    }

    @Test
    fun `parses YouTube search command`() {
        val command = AssistantCommandParser.parse("search YouTube for Kotlin tutorials")

        assertNotNull(command)
        assertEquals(AssistantCommandType.YOUTUBE_SEARCH, command?.type)
    }

    @Test
    fun `parses Bengali YouTube search command`() {
        val command = AssistantCommandParser.parse("ইউটিউবে সার্চ করো বাংলা গান")

        assertNotNull(command)
        assertEquals(AssistantCommandType.YOUTUBE_SEARCH, command?.type)
    }

    @Test
    fun `parses phone scroll command`() {
        val command = AssistantCommandParser.parse("scroll down")

        assertNotNull(command)
        assertEquals(AssistantCommandType.PHONE_NAVIGATION, command?.type)
    }

    @Test
    fun `parses phone text entry command`() {
        val command = AssistantCommandParser.parse("type hello jarvis")

        assertNotNull(command)
        assertEquals(AssistantCommandType.PHONE_NAVIGATION, command?.type)
    }

    @Test
    fun `parses home navigation command`() {
        val command = AssistantCommandParser.parse("go home")

        assertNotNull(command)
        assertEquals(AssistantCommandType.PHONE_NAVIGATION, command?.type)
    }

    @Test
    fun `parses generic app command`() {
        val command = AssistantCommandParser.parse("open Calculator")

        assertNotNull(command)
        assertEquals(AssistantCommandType.APP_AUTOMATION, command?.type)
    }

    @Test
    fun `parses polite generic app command`() {
        val command = AssistantCommandParser.parse("please open Calculator")

        assertNotNull(command)
        assertEquals(AssistantCommandType.APP_AUTOMATION, command?.type)
    }

    @Test
    fun `parses display settings command`() {
        val command = AssistantCommandParser.parse("open display settings")

        assertNotNull(command)
        assertEquals(AssistantCommandType.DEVICE_CONTROL, command?.type)
    }

    @Test
    fun `parses Bengali settings command`() {
        val command = AssistantCommandParser.parse("সেটিংস খোলো")

        assertNotNull(command)
        assertEquals(AssistantCommandType.DEVICE_CONTROL, command?.type)
    }

    @Test
    fun `parses brightness command`() {
        val command = AssistantCommandParser.parse("brightness 50 percent করো")

        assertNotNull(command)
        assertEquals(AssistantCommandType.DEVICE_CONTROL, command?.type)
    }
}
