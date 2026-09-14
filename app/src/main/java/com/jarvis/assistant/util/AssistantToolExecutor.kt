package com.jarvis.assistant.util

import android.app.SearchManager
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import com.google.gson.JsonParser
import com.jarvis.assistant.accessibility.JarvisAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AssistantToolResult(
    val success: Boolean,
    val message: String
)

object AssistantToolExecutor {

    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private const val MAX_RESPONSE_LENGTH = 1800
    private val WEB_PREFIXES = listOf("search for", "search", "google", "look up", "ওয়েবে খুঁজে দেখ", "ওয়েবে খুঁজে দেখ", "সার্চ কর")
    private val YOUTUBE_PREFIXES = listOf("search youtube for", "youtube search", "search on youtube", "search in youtube", "youtube এ সার্চ কর", "youtube এ খুঁজে দেখ", "ইউটিউবে সার্চ কর", "ইউটিউবে খুঁজে দেখ")
    private val phoneTasks = PhoneTaskOrchestrator(maxRetries = 2)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun execute(context: Context, command: AssistantCommand): String =
        executeResult(context, command).message

    suspend fun executeResult(
        context: Context,
        command: AssistantCommand
    ): AssistantToolResult {
        return try {
            when (command.type) {
                AssistantCommandType.WEB_SEARCH -> ioResult { webSearch(command.rawText) }
                AssistantCommandType.WEATHER -> ioResult { weather(command.rawText) }
                AssistantCommandType.NEWS -> ioResult { news() }
                AssistantCommandType.YOUTUBE_SEARCH -> withContext(Dispatchers.Main) {
                    youtubeSearch(context, command.rawText)
                }
                AssistantCommandType.PHONE_NAVIGATION -> withContext(Dispatchers.Main) {
                    phoneNavigation(command.rawText)
                }
                AssistantCommandType.DEVICE_CONTROL -> withContext(Dispatchers.Main) {
                    deviceControl(context, command.rawText)
                }
                AssistantCommandType.APP_AUTOMATION -> withContext(Dispatchers.Main) {
                    openApp(context, command.rawText)
                }
                AssistantCommandType.NOTE,
                AssistantCommandType.REMINDER,
                AssistantCommandType.TIMER,
                AssistantCommandType.ALARM -> AssistantToolResult(
                    false,
                    "এই কাজের বাস্তব scheduler/tool এখনো যুক্ত হয়নি। আমি কাজটি সম্পন্ন হয়েছে বলছি না।"
                )
                else -> AssistantToolResult(false, "এই command-এর জন্য কোনো tool পাওয়া যায়নি।")
            }
        } catch (error: Exception) {
            failure("Tool চালাতে সমস্যা হয়েছে: ${error.userMessage()}")
        }
    }

    suspend fun executeFunction(
        context: Context,
        name: String,
        arguments: Map<String, Any?>
    ): AssistantToolResult {
        return try {
            when (name) {
                "open_app" -> withContext(Dispatchers.Main) {
                    openAppByName(context, arguments.string("app_name"))
                }
                "list_apps" -> withContext(Dispatchers.IO) { listApps(context) }
                "get_current_app", "get_screen_context" -> withContext(Dispatchers.Main) {
                    screenContextResult()
                }
                "wait_for_element" -> withContext(Dispatchers.Main) {
                    actionResult(phoneTasks.waitForElement(arguments.string("text")))
                }
                "click_text" -> withContext(Dispatchers.Main) {
                    clickTextResult(arguments.string("text"))
                }
                "scroll" -> withContext(Dispatchers.Main) {
                    scrollResult(arguments.string("direction"))
                }
                "type_text" -> withContext(Dispatchers.Main) {
                    typeTextResult(arguments.string("text"), arguments.boolean("press_enter"))
                }
                "back" -> withContext(Dispatchers.Main) {
                    actionResult(phoneTasks.execute(PhoneAction.Back))
                }
                "home" -> withContext(Dispatchers.Main) {
                    actionResult(phoneTasks.execute(PhoneAction.Home))
                }
                "recents" -> withContext(Dispatchers.Main) {
                    actionResult(phoneTasks.execute(PhoneAction.Recents))
                }
                "notifications" -> withContext(Dispatchers.Main) {
                    actionResult(phoneTasks.execute(PhoneAction.Notifications))
                }
                "quick_settings" -> withContext(Dispatchers.Main) {
                    actionResult(phoneTasks.execute(PhoneAction.QuickSettings))
                }
                "youtube_search" -> withContext(Dispatchers.Main) {
                    youtubeSearch(context, arguments.string("query"))
                }
                "web_search" -> ioResult { webSearch(arguments.string("query")) }
                "open_settings" -> withContext(Dispatchers.Main) {
                    openSettingsPage(context, arguments.string("page"))
                }
                else -> failure("Unknown tool: $name")
            }
        } catch (error: Exception) {
            failure("Tool চালাতে সমস্যা হয়েছে: ${error.userMessage()}")
        }
    }

    private suspend fun ioResult(block: () -> String): AssistantToolResult =
        withContext(Dispatchers.IO) {
            try {
                val message = block()
                if (message.isBlank()) failure("কোনো ফলাফল পাওয়া যায়নি।") else success(message)
            } catch (error: Exception) {
                failure(error.userMessage())
            }
        }

    private fun webSearch(rawText: String): String {
        val query = extractQuery(rawText, WEB_PREFIXES)
            ?: return "ওয়েবে কী খুঁজব, সেটা বলুন।"
        val url = "https://api.duckduckgo.com/?q=${Uri.encode(query)}&format=json&no_html=1&skip_disambig=1"
        val root = getJson(url)
        val abstractText = root?.get("AbstractText")?.asString.orEmpty().cleanText()
        if (abstractText.isNotBlank()) return "ওয়েব সার্চ ফলাফল: ${abstractText.take(900)}"

        val related = root?.getAsJsonArray("RelatedTopics")
            ?.flatMap { item ->
                val objectItem = item.takeIf { it.isJsonObject }?.asJsonObject
                if (objectItem?.has("Text") == true) {
                    listOf(objectItem.get("Text").asString)
                } else {
                    objectItem?.getAsJsonArray("Topics")?.mapNotNull { topic ->
                        topic.asJsonObject?.get("Text")?.asString
                    }.orEmpty()
                }
            }
            ?.map { it.cleanText().take(240) }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(3)
            .orEmpty()
        return if (related.isEmpty()) "এই বিষয়ে কোনো নির্ভরযোগ্য সার্চ ফলাফল পাইনি।"
        else "ওয়েব সার্চে পাওয়া গেছে: ${related.joinToString(" | ")}".limit(MAX_RESPONSE_LENGTH)
    }

    private fun youtubeSearch(context: Context, rawText: String): AssistantToolResult {
        val query = extractQuery(rawText, YOUTUBE_PREFIXES)
            ?: return failure("YouTube-এ কী খুঁজব, সেটা বলুন।")
        val nativeIntent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(YOUTUBE_PACKAGE)
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallbackIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val intent = when {
            nativeIntent.resolveActivity(context.packageManager) != null -> nativeIntent
            fallbackIntent.resolveActivity(context.packageManager) != null -> fallbackIntent
            else -> return failure("YouTube বা কোনো browser পাওয়া যায়নি।")
        }
        context.startActivity(intent)
        return success("YouTube-এ $query search খুলে দিয়েছি।")
    }

    private fun weather(rawText: String): String {
        val city = extractWeatherCity(rawText).ifBlank { "Dhaka" }
        val geo = getJson("https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(city)}&count=1&language=en&format=json")
        val location = geo?.getAsJsonArray("results")?.firstOrNull()?.asJsonObject
            ?: return "$city শহরের অবস্থান খুঁজে পাইনি।"
        val latitude = location.get("latitude")?.asDouble ?: return "আবহাওয়ার অবস্থান পাওয়া যায়নি।"
        val longitude = location.get("longitude")?.asDouble ?: return "আবহাওয়ার অবস্থান পাওয়া যায়নি।"
        val name = location.get("name")?.asString ?: city
        val current = getJson("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto")
            ?.getAsJsonObject("current") ?: return "$name-এর আবহাওয়ার তথ্য পাওয়া যায়নি।"
        val temperature = current.get("temperature_2m")?.asString ?: "?"
        val humidity = current.get("relative_humidity_2m")?.asString ?: "?"
        val wind = current.get("wind_speed_10m")?.asString ?: "?"
        return "$name-এর বর্তমান তাপমাত্রা ${temperature}°C, আর্দ্রতা $humidity% এবং বাতাসের গতি $wind km/h।"
    }

    private fun news(): String {
        val xml = getText("https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en")
        val titles = Regex("""<title>(?:<!\[CDATA\[)?(.*?)(?:]]>)?</title>""")
            .findAll(xml)
            .mapNotNull { it.groupValues.getOrNull(1)?.let(::decodeXml)?.cleanText() }
            .drop(1)
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
            .toList()
        return if (titles.isEmpty()) "সর্বশেষ খবর পাওয়া যায়নি।"
        else "সর্বশেষ খবর: ${titles.mapIndexed { index, title -> "${index + 1}. ${title.take(180)}" }.joinToString(" | ")}".limit(MAX_RESPONSE_LENGTH)
    }

    private suspend fun phoneNavigation(rawText: String): AssistantToolResult {
        if (!JarvisAccessibilityService.isEnabled()) {
            return failure("Phone Control Accessibility permission enabled নয়।")
        }
        val lower = rawText.lowercase(Locale.getDefault())
        return when {
            lower.containsAny("go back", "পেছনে যাও") -> actionResult(phoneTasks.execute(PhoneAction.Back))
            lower.containsAny("go home", "home screen", "হোমে যাও", "হোম স্ক্রিন") -> actionResult(phoneTasks.execute(PhoneAction.Home))
            lower.containsAny("recent apps", "সাম্প্রতিক অ্যাপ") -> actionResult(phoneTasks.execute(PhoneAction.Recents))
            lower.containsAny("open notifications", "নোটিফিকেশন খোলো") -> actionResult(phoneTasks.execute(PhoneAction.Notifications))
            lower.containsAny("open quick settings", "কুইক সেটিংস খোলো") -> actionResult(phoneTasks.execute(PhoneAction.QuickSettings))
            lower.startsWith("type ") || lower.startsWith("write ") || lower.contains("লিখে দাও") -> {
                val text = rawText.replace(Regex("(?i)^(type|write)\\s+"), "").replace("লিখে দাও", "").trim()
                actionResult(phoneTasks.execute(PhoneAction.TypeText(text, false)))
            }
            lower.containsAny("scroll down", "swipe down", "স্ক্রল নিচে", "নিচে স্ক্রল") -> actionResult(phoneTasks.execute(PhoneAction.Scroll(PhoneAction.Direction.DOWN)))
            lower.containsAny("scroll up", "swipe up", "স্ক্রল উপরে", "উপরে স্ক্রল") -> actionResult(phoneTasks.execute(PhoneAction.Scroll(PhoneAction.Direction.UP)))
            else -> {
                val target = rawText.replace(Regex("(?i)(click on|click|tap on|tap|click video)"), "")
                    .replace(Regex("(এতে ক্লিক কর|ভিডিওতে ক্লিক)"), "")
                    .trim().ifBlank { return failure("কোনো click target পাওয়া যায়নি।") }
                actionResult(phoneTasks.execute(PhoneAction.ClickText(target)))
            }
        }
    }

    private suspend fun clickTextResult(target: String): AssistantToolResult {
        return actionResult(phoneTasks.execute(PhoneAction.ClickText(target)))
    }

    private suspend fun scrollResult(direction: String): AssistantToolResult {
        val directionValue = if (direction == "down") PhoneAction.Direction.DOWN else PhoneAction.Direction.UP
        return actionResult(phoneTasks.execute(PhoneAction.Scroll(directionValue)))
    }

    private suspend fun typeTextResult(text: String, pressEnter: Boolean): AssistantToolResult {
        return actionResult(phoneTasks.execute(PhoneAction.TypeText(text, pressEnter)))
    }

    private suspend fun actionResult(result: ActionResult): AssistantToolResult =
        if (result.success) success(result.message) else failure(result.message)


    private fun globalResult(label: String, executed: Boolean, action: String): AssistantToolResult {
        return if (executed) success("$label $action সফল হয়েছে।")
        else failure("$label $action করা যায়নি।")
    }

    private fun deviceControl(context: Context, rawText: String): AssistantToolResult {
        val lower = rawText.lowercase(Locale.getDefault())
        if (lower.containsAny("volume up", "ভলিউম বাড়াও")) {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return failure("Audio service পাওয়া যায়নি।")
            audio.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            return success("ভলিউম বাড়ানো হয়েছে।")
        }
        if (lower.containsAny("volume down", "ভলিউম কমাও")) {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return failure("Audio service পাওয়া যায়নি।")
            audio.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            return success("ভলিউম কমানো হয়েছে।")
        }
        val intent = when {
            lower.containsAny("wifi", "wi-fi", "ওয়াইফাই") -> Intent(Settings.ACTION_WIFI_SETTINGS)
            lower.containsAny("bluetooth", "ব্লুটুথ") -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            lower.containsAny("display", "ডিসপ্লে") -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            lower.containsAny("sound", "সাউন্ড") -> Intent(Settings.ACTION_SOUND_SETTINGS)
            lower.containsAny("battery", "ব্যাটারি") -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            lower.containsAny("notification", "নোটিফিকেশন") -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            lower.containsAny("app settings", "অ্যাপ সেটিংস") -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}"))
            else -> Intent(Settings.ACTION_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (intent.resolveActivity(context.packageManager) == null) return failure("এই Settings page পাওয়া যায়নি।")
        context.startActivity(intent)
        return success("Device settings খুলে দিয়েছি।")
    }

    private fun openSettingsPage(context: Context, page: String): AssistantToolResult =
        deviceControl(context, "$page settings")

    private fun openApp(context: Context, rawText: String): AssistantToolResult {
        val lower = rawText.lowercase(Locale.getDefault())
        val appName = extractAppName(rawText)
        val knownPackages = when {
            lower.containsAny("youtube", "ইউটিউব") -> listOf(YOUTUBE_PACKAGE)
            lower.containsAny("whatsapp", "হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপ") -> listOf("com.whatsapp")
            lower.containsAny("chrome", "ক্রোম") -> listOf("com.android.chrome", "com.google.android.apps.chrome")
            lower.containsAny("maps", "ম্যাপ") -> listOf("com.google.android.apps.maps")
            lower.containsAny("gmail", "জিমেইল") -> listOf("com.google.android.gm")
            else -> emptyList()
        }
        val launchIntent = knownPackages.asSequence().mapNotNull { context.packageManager.getLaunchIntentForPackage(it) }.firstOrNull()
            ?: findInstalledAppIntent(context, appName)
            ?: if (lower.containsAny("youtube", "ইউটিউব")) Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")) else null
        if (launchIntent == null) return failure("$appName appটি খুঁজে পাইনি।")
        val intent = launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return failure("$appName appটি চালু করা যায়নি।")
        context.startActivity(intent)
        return success("$appName app খুলে দিয়েছি।")
    }

    private fun openAppByName(context: Context, appName: String): AssistantToolResult =
        if (appName.isBlank()) failure("App-এর নাম পাওয়া যায়নি।") else openApp(context, "open $appName")

    private fun listApps(context: Context): AssistantToolResult {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = context.packageManager.queryIntentActivities(launcher, 0)
            .map { context.packageManager.getApplicationLabel(it.activityInfo.applicationInfo).toString() }
            .distinct().sorted().take(80)
        return success("Installed apps: ${apps.joinToString(", ")}")
    }

    private fun screenContextResult(): AssistantToolResult {
        val screen = JarvisAccessibilityService.getScreenContext()
            ?: return failure("Accessibility permission enabled নয় বা screen context পাওয়া যায়নি।")
        val text = screen.nodes.joinToString(" | ") { node ->
            listOfNotNull(node.text, node.description).joinToString("/") +
                "[role=${node.role},class=${node.className},click=${node.clickable},edit=${node.editable},scroll=${node.scrollable},enabled=${node.enabled},selected=${node.selected},checked=${node.checked},focused=${node.focused},bounds=${node.bounds.flattenToString()}]"
        }.take(7000)
        return success("Foreground package: ${screen.packageName}\n$text")
    }

    private fun findInstalledAppIntent(context: Context, appName: String): Intent? {
        if (appName.isBlank()) return null
        val normalized = appName.lowercase(Locale.getDefault())
        return context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence().mapNotNull { info ->
                val label = context.packageManager.getApplicationLabel(info).toString()
                if (label.lowercase(Locale.getDefault()).contains(normalized)) context.packageManager.getLaunchIntentForPackage(info.packageName) else null
            }.firstOrNull()
    }

    private fun extractAppName(rawText: String): String = rawText
        .replace(Regex("(?i)\\b(please|can you|could you|open|launch|start|the|app|application)\\b"), "")
        .replace(Regex("(খোলো|খুলে দাও|চালু কর|দয়া করে|দাও)"), "")
        .trim().trimEnd('.', '?', '!').ifBlank { "app" }

    private fun extractQuery(rawText: String, prefixes: List<String>): String? {
        var value = rawText.trim()
        prefixes.sortedByDescending { it.length }.forEach { prefix ->
            value = value.replace(Regex("(?i)^${Regex.escape(prefix)}\\b?"), "").trim()
        }
        value = value.replace(Regex("(?i)^(hey jarvis|ok jarvis|jarvis)\\s*[,:-]?\\s*"), "")
            .replace(Regex("^(জারভিস)\\s*[,:-]?\\s*"), "")
            .replace(Regex("(?i)^(for|on|in)\\s+"), "")
            .trim().trim(',', '.', '?', '!', '।')
        return value.takeIf { it.length >= 2 }
    }

    private fun extractWeatherCity(rawText: String): String = rawText
        .replace(Regex("(?i)\\b(weather|forecast)\\b"), "")
        .replace(Regex("(?i)\\b(in|for|at|of)\\b"), "")
        .replace(Regex("আবহাওয়া|আবহাওয়া|বৃষ্টি হবে কি"), "")
        .trim().trim(',', '.', '?', '!', '।')

    private fun getJson(url: String) = runCatching { JsonParser.parseString(getText(url)).asJsonObject }.getOrNull()

    private fun getText(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun decodeXml(value: String): String = URLDecoder.decode(
        value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">"),
        Charsets.UTF_8.name()
    )

    private fun String.cleanText(): String = replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()
    private fun String.limit(max: Int): String = if (length <= max) this else take(max - 1) + "…"
    private fun String.containsAny(vararg values: String): Boolean = values.any { contains(it) }
    private fun success(message: String) = AssistantToolResult(true, message.limit(MAX_RESPONSE_LENGTH))
    private fun failure(message: String) = AssistantToolResult(false, message.limit(MAX_RESPONSE_LENGTH))

    private fun Throwable.userMessage(): String = when (this) {
        is ActivityNotFoundException -> "প্রয়োজনীয় app বা Settings page পাওয়া যায়নি।"
        is SecurityException -> "এই কাজের জন্য প্রয়োজনীয় permission দেওয়া নেই।"
        else -> message?.takeIf { it.isNotBlank() } ?: "অজানা সমস্যা হয়েছে।"
    }

    private fun Map<String, Any?>.string(key: String): String = this[key]?.toString()?.trim().orEmpty()
    private fun Map<String, Any?>.boolean(key: String): Boolean = this[key] as? Boolean ?: false
}