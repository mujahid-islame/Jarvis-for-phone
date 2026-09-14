package com.jarvis.assistant.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AssistantCommandType {
    TIME,
    DATE,
    STATUS,
    HELP,
    WHO_ARE_YOU,
    REMINDER,
    TIMER,
    ALARM,
    NOTE,
    YOUTUBE_SEARCH,
    PHONE_NAVIGATION,
    WEB_SEARCH,
    WEATHER,
    NEWS,
    DEVICE_CONTROL,
    APP_AUTOMATION,
    UNKNOWN
}

data class AssistantCommand(
    val type: AssistantCommandType,
    val rawText: String
)

object AssistantCommandParser {

    fun parse(text: String): AssistantCommand? {
        val normalized = normalize(text)
        if (normalized.isBlank()) return null

        val lower = normalized.lowercase(Locale.getDefault())
        return when {
            matchesPhrase(lower, TIME_PHRASES) -> command(AssistantCommandType.TIME, normalized)
            matchesPhrase(lower, DATE_PHRASES) -> command(AssistantCommandType.DATE, normalized)
            matchesPhrase(lower, STATUS_PHRASES) -> command(AssistantCommandType.STATUS, normalized)
            matchesPhrase(lower, HELP_PHRASES) -> command(AssistantCommandType.HELP, normalized)
            matchesPhrase(lower, IDENTITY_PHRASES) -> command(AssistantCommandType.WHO_ARE_YOU, normalized)
            matchesPhrase(lower, YOUTUBE_SEARCH_PHRASES) -> command(AssistantCommandType.YOUTUBE_SEARCH, normalized)
            matchesPhrase(lower, PHONE_NAVIGATION_PHRASES) -> command(AssistantCommandType.PHONE_NAVIGATION, normalized)
            matchesPhrase(lower, WEB_SEARCH_PHRASES) -> command(AssistantCommandType.WEB_SEARCH, normalized)
            matchesPhrase(lower, WEATHER_PHRASES) -> command(AssistantCommandType.WEATHER, normalized)
            matchesPhrase(lower, NEWS_PHRASES) -> command(AssistantCommandType.NEWS, normalized)
            matchesPhrase(lower, DEVICE_PHRASES) -> command(AssistantCommandType.DEVICE_CONTROL, normalized)
            matchesPhrase(lower, NOTE_PHRASES) -> command(AssistantCommandType.NOTE, normalized)
            matchesPhrase(lower, REMINDER_PHRASES) -> command(AssistantCommandType.REMINDER, normalized)
            matchesPhrase(lower, TIMER_PHRASES) -> command(AssistantCommandType.TIMER, normalized)
            matchesPhrase(lower, ALARM_PHRASES) -> command(AssistantCommandType.ALARM, normalized)
            isGenericAppCommand(lower) -> command(AssistantCommandType.APP_AUTOMATION, normalized)
            else -> null
        }
    }

    fun buildAssistantResponse(command: AssistantCommand): String {
        return when (command.type) {
            AssistantCommandType.TIME -> "এখন সময় ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())}।"
            AssistantCommandType.DATE -> {
                val date = SimpleDateFormat("dd MMMM yyyy", Locale("bn", "BD")).format(Date())
                "আজকের তারিখ $date।"
            }
            AssistantCommandType.STATUS -> "আমি জারভিস, আপনার ভয়েস সহকারী। প্রস্তুত আছি।"
            AssistantCommandType.HELP -> "আমি সময়, তারিখ, আবহাওয়া, খবর, সার্চ, অ্যাপ ও সেটিংস খোলা এবং ফোন নেভিগেশনে সাহায্য করতে পারি।"
            AssistantCommandType.WHO_ARE_YOU -> "আমি জারভিস, আপনার ভয়েস-ভিত্তিক ডিজিটাল সহকারী।"
            AssistantCommandType.REMINDER -> "রিমাইন্ডারের সময় ও বিষয়টি পরিষ্কার করে বলুন। সেট হওয়ার আগে আমি আপনার তথ্য যাচাই করব।"
            AssistantCommandType.TIMER -> "টাইমারের সময় কত হবে বলুন। সফলভাবে সেট না হওয়া পর্যন্ত আমি শুরু হয়েছে বলব না।"
            AssistantCommandType.ALARM -> "অ্যালার্মের নির্দিষ্ট সময় বলুন। সেট হওয়ার আগে আমি নিশ্চিত করব।"
            AssistantCommandType.NOTE -> "নোটের বিষয়টি বলুন। সংরক্ষণ সফল হলে তবেই আমি আপনাকে জানাব।"
            AssistantCommandType.UNKNOWN -> "আমি বুঝতে পারিনি, একটু পরিষ্কার করে বলুন।"
            else -> "আমি কাজটি যাচাই করে তারপর ফল জানাব।"
        }
    }

    private fun command(type: AssistantCommandType, text: String) = AssistantCommand(type, text)

    private fun normalize(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim(',', '.', '?', '!', '।')

    private fun matchesPhrase(value: String, phrases: List<String>): Boolean {
        return phrases.any { phrase ->
            val normalizedPhrase = phrase.lowercase(Locale.getDefault())
            if (normalizedPhrase.endsWith(" ")) {
                return@any value.startsWith(normalizedPhrase.trimEnd())
            }
            if (normalizedPhrase.any { it.code > 127 }) {
                value.contains(normalizedPhrase)
            } else {
                value == normalizedPhrase ||
                    value.startsWith("$normalizedPhrase ") ||
                    value.contains(" $normalizedPhrase ") ||
                    value.endsWith(" $normalizedPhrase")
            }
        }
    }

    private fun isGenericAppCommand(value: String): Boolean {
        return value.matches(Regex(".*\\b(open|launch|start)\\s+.+")) ||
            value.contains("খোলো") || value.contains("খুলে দাও") || value.contains("চালু কর")
    }

    private val TIME_PHRASES = listOf("what time is it", "what's the time", "current time", "এখন কী সময়", "এখন কত বাজে", "কত বাজে", "সময় জানতে চাই")
    private val DATE_PHRASES = listOf("what date is it", "today's date", "today date", "current date", "আজকের তারিখ", "তারিখ জানতে চাই", "কত তারিখ")
    private val STATUS_PHRASES = listOf("status check", "how are you", "status", "আপনি কেমন আছেন", "স্ট্যাটাস", "কেমন আছ")
    private val HELP_PHRASES = listOf("what can you do", "help", "হেল্প", "তুমি কি করতে পারো", "কি করতে পারো", "কাজ কি")
    private val IDENTITY_PHRASES = listOf("who are you", "তুমি কে", "আপনি কে", "you are jarvis", "জারভিস কে")
    private val YOUTUBE_SEARCH_PHRASES = listOf("search youtube for", "youtube search", "search on youtube", "search in youtube", "youtube এ সার্চ কর", "ইউটিউবে সার্চ কর", "ইউটিউবে খুঁজে দেখ")
    private val PHONE_NAVIGATION_PHRASES = listOf("scroll down", "scroll up", "swipe down", "swipe up", "click video", "click on", "tap on", "type ", "write ", "go back", "go home", "home screen", "recent apps", "open notifications", "open quick settings", "play music", "pause music", "next song", "previous song", "media play", "media pause", "toggle music", "mute", "unmute", "স্ক্রল নিচে", "স্ক্রল উপরে", "নিচে স্ক্রল", "উপরে স্ক্রল", "ভিডিওতে ক্লিক", "লিখে দাও", "পেছনে যাও", "হোমে যাও", "সাম্প্রতিক অ্যাপ", "নোটিফিকেশন খোলো", "কুইক সেটিংস খোলো", "গান চালাও", "গান pause করো", "পরের গান", "আগের গান", "মিউট করো", "আনমিউট করো")
    private val WEB_SEARCH_PHRASES = listOf("search for", "search", "google", "look up", "ওয়েবে খুঁজে দেখ", "ওয়েবে খুঁজে দেখ", "সার্চ কর")
    private val WEATHER_PHRASES = listOf("weather", "forecast", "আবহাওয়া", "আবহাওয়া", "বৃষ্টি হবে কি")
    private val NEWS_PHRASES = listOf("latest news", "headlines", "news", "খবর", "সর্বশেষ খবর")
    private val DEVICE_PHRASES = listOf("wifi settings", "wi-fi settings", "bluetooth settings", "volume up", "volume down", "brightness", "screen brightness", "increase brightness", "decrease brightness", "battery status", "battery percentage", "current app", "phone status", "device status", "what's my battery", "how much battery", "open settings", "open the settings", "settings open", "setting", "settings", "display settings", "sound settings", "battery settings", "app settings", "notification settings", "ওয়াইফাই সেটিংস", "ব্লুটুথ সেটিংস", "সেটিংস", "সেটিং খোলো", "ডিসপ্লে সেটিংস", "সাউন্ড সেটিংস", "ভলিউম বাড়াও", "ভলিউম কমাও", "ব্রাইটনেস", "উজ্জ্বলতা বাড়াও", "উজ্জ্বলতা কমাও", "ব্যাটারি কত", "ব্যাটারি স্ট্যাটাস", "আমার ব্যাটারি", "কোন app-এ আছি", "বর্তমান অ্যাপ", "ফোন স্ট্যাটাস", "ডিভাইস স্ট্যাটাস")
    private val NOTE_PHRASES = listOf("write note", "save note", "note that", "নোট রাখো", "নোট", "মনে রাখার নোট")
    private val REMINDER_PHRASES = listOf("set reminder", "reminder", "remember", "মনে রাখো", "মনে রাখবেন", "রিমাইন্ডার", "কাজ মনে রাখো", "meeting reminder", "ট্র্যাক রাখো")
    private val TIMER_PHRASES = listOf("timer", "countdown", "টাইমার", "কাউন্টডাউন", "minute timer", "seconds timer", "টায়মার")
    private val ALARM_PHRASES = listOf("alarm", "wake me up", "অ্যালার্ম", "এলার্ম", "জাগাও", "ঘুম ভাঙাও")
}
