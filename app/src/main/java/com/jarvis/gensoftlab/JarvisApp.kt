package com.jarvis.gensoftlab

import android.app.Application
import com.jarvis.gensoftlab.data.preferences.AppPreferences
import com.jarvis.gensoftlab.data.repository.AssistantMemoryRepository
import com.jarvis.gensoftlab.data.repository.ChatRepository
import com.jarvis.gensoftlab.data.repository.MemoryVaultRepository

class JarvisApp : Application() {

    lateinit var preferences: AppPreferences
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var assistantMemoryRepository: AssistantMemoryRepository
        private set

    lateinit var memoryVaultRepository: MemoryVaultRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = AppPreferences(this)
        chatRepository = ChatRepository(preferences)
        assistantMemoryRepository = AssistantMemoryRepository(preferences)
        memoryVaultRepository = MemoryVaultRepository(preferences)
    }

    companion object {
        lateinit var instance: JarvisApp
            private set
    }
}
