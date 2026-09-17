package com.jarvis.gensoftlab.data.repository

import com.jarvis.gensoftlab.data.model.AssistantNote
import com.jarvis.gensoftlab.data.model.AssistantReminder
import com.jarvis.gensoftlab.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AssistantMemoryRepository(private val preferences: AppPreferences) {

    private val _notes = MutableStateFlow(preferences.loadNotes())
    val notes: StateFlow<List<AssistantNote>> = _notes.asStateFlow()

    private val _reminders = MutableStateFlow(preferences.loadReminders())
    val reminders: StateFlow<List<AssistantReminder>> = _reminders.asStateFlow()

    fun addNote(title: String, content: String) {
        val trimmedTitle = title.trim()
        val trimmedContent = content.trim()
        if (trimmedTitle.isBlank() && trimmedContent.isBlank()) return

        val note = AssistantNote(
            title = trimmedTitle.ifBlank { "নোট" },
            content = trimmedContent
        )

        val updated = (notes.value + note).takeLast(50)
        _notes.value = updated
        preferences.saveNotes(updated)
    }

    fun addReminder(title: String, message: String, scheduledAt: Long) {
        val reminder = AssistantReminder(
            title = title.trim().ifBlank { "রিমাইন্ডার" },
            message = message.trim(),
            scheduledAt = scheduledAt
        )

        val updated = (reminders.value + reminder).takeLast(50)
        _reminders.value = updated
        preferences.saveReminders(updated)
    }

    fun updateNote(id: String, title: String, content: String) {
        val updated = notes.value.map { note ->
            if (note.id == id) {
                note.copy(
                    title = title.trim().ifBlank { "নোট" },
                    content = content.trim()
                )
            } else {
                note
            }
        }
        _notes.value = updated
        preferences.saveNotes(updated)
    }

    fun updateReminder(id: String, title: String, message: String, scheduledAt: Long) {
        val updated = reminders.value.map { reminder ->
            if (reminder.id == id) {
                reminder.copy(
                    title = title.trim().ifBlank { "রিমাইন্ডার" },
                    message = message.trim(),
                    scheduledAt = scheduledAt
                )
            } else {
                reminder
            }
        }
        _reminders.value = updated
        preferences.saveReminders(updated)
    }

    fun deleteNote(id: String) {
        val updated = notes.value.filterNot { it.id == id }
        _notes.value = updated
        preferences.saveNotes(updated)
    }

    fun deleteReminder(id: String) {
        val updated = reminders.value.filterNot { it.id == id }
        _reminders.value = updated
        preferences.saveReminders(updated)
    }

    fun getNotes(): List<AssistantNote> = _notes.value
    fun getReminders(): List<AssistantReminder> = _reminders.value
}
