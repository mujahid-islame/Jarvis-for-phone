package com.jarvis.gensoftlab.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.fragment.app.Fragment
import com.jarvis.gensoftlab.JarvisApp
import com.jarvis.gensoftlab.data.model.AssistantNote
import com.jarvis.gensoftlab.data.model.AssistantReminder
import com.jarvis.gensoftlab.databinding.SheetChatHistoryBinding
import kotlinx.coroutines.launch

class ChatHistoryBottomSheet : Fragment() {

    private var _binding: SheetChatHistoryBinding? = null
    private val binding get() = _binding!!
    private val adapter = ChatAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetChatHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvChatHistory.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvChatHistory.adapter = adapter

        val chatRepo = JarvisApp.instance.chatRepository
        val turns = chatRepo.turns.value

        updateList(turns)
        updateMemoryList()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    chatRepo.turns.collect { updatedTurns ->
                        updateList(updatedTurns)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    JarvisApp.instance.assistantMemoryRepository.notes.collect {
                        updateMemoryList()
                    }
                }
                launch {
                    JarvisApp.instance.assistantMemoryRepository.reminders.collect {
                        updateMemoryList()
                    }
                }
            }
        }

        binding.btnAddMemory.setOnClickListener {
            showMemoryTypeChooser()
        }

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear the conversation history?")
                .setPositiveButton("Clear") { _, _ ->
                    chatRepo.clearHistory()
                    updateList(emptyList())
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateList(turns: List<com.jarvis.gensoftlab.data.model.ChatTurn>) {
        if (turns.isEmpty()) {
            binding.tvEmptyHistory.visibility = View.VISIBLE
            binding.rvChatHistory.visibility = View.GONE
        } else {
            binding.tvEmptyHistory.visibility = View.GONE
            binding.rvChatHistory.visibility = View.VISIBLE
            adapter.submitList(turns) {
                binding.rvChatHistory.scrollToPosition(turns.size - 1)
            }
        }
    }

    private fun updateMemoryList() {
        val memoryRepo = JarvisApp.instance.assistantMemoryRepository
        val notes = memoryRepo.notes.value
        val reminders = memoryRepo.reminders.value

        if (notes.isEmpty() && reminders.isEmpty()) {
            binding.memoryContainer.visibility = View.GONE
            binding.tvMemoryEmpty.visibility = View.VISIBLE
            return
        }

        binding.memoryContainer.removeAllViews()
        binding.tvMemoryEmpty.visibility = View.GONE
        binding.memoryContainer.visibility = View.VISIBLE

        notes.forEach { note ->
            addMemoryRow(
                icon = "📝",
                title = note.title,
                content = note.content,
                onEdit = { showMemoryEditor(note = note) },
                onDelete = { confirmDeleteNote(note) }
            )
        }
        reminders.forEach { reminder ->
            addMemoryRow(
                icon = "⏰",
                title = reminder.title,
                content = reminder.message,
                onEdit = { showMemoryEditor(reminder = reminder) },
                onDelete = { confirmDeleteReminder(reminder) }
            )
        }
    }

    private fun addMemoryRow(
        icon: String,
        title: String,
        content: String,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(12, 8, 4, 8)
            setBackgroundResource(com.jarvis.gensoftlab.R.drawable.bg_input_field)
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
        }

        val textView = TextView(requireContext()).apply {
            text = "$icon  $title\n$content"
            setTextColor(resources.getColor(com.jarvis.gensoftlab.R.color.text_primary, null))
            textSize = 12f
            setLineSpacing(3f, 1f)
            maxLines = 4
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(textView)

        row.addView(createMemoryAction("EDIT", onEdit))
        row.addView(createMemoryAction("DELETE", onDelete))
        binding.memoryContainer.addView(row)
    }

    private fun createMemoryAction(label: String, action: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            text = label
            setTextColor(
                resources.getColor(
                    if (label == "DELETE") com.jarvis.gensoftlab.R.color.status_red
                    else com.jarvis.gensoftlab.R.color.primary_cyan,
                    null
                )
            )
            textSize = 9f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(6, 4, 6, 4)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun showMemoryTypeChooser() {
        val choices = arrayOf("Note", "Reminder")
        AlertDialog.Builder(requireContext())
            .setTitle("Add to Memory Vault")
            .setItems(choices) { _, which ->
                if (which == 0) showMemoryEditor(isNote = true) else showMemoryEditor(isNote = false)
            }
            .show()
    }

    private fun showMemoryEditor(
        isNote: Boolean = true,
        note: AssistantNote? = null,
        reminder: AssistantReminder? = null
    ) {
        val editor = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }
        val titleInput = EditText(requireContext()).apply {
            hint = "Title"
            setText(note?.title ?: reminder?.title.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val contentInput = EditText(requireContext()).apply {
            hint = if (isNote) "What should I remember?" else "Reminder details"
            setText(note?.content ?: reminder?.message.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            gravity = android.view.Gravity.TOP
        }
        editor.addView(titleInput)
        editor.addView(contentInput)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (note == null && reminder == null) "Add Memory" else "Edit Memory")
            .setView(editor)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim()
                val content = contentInput.text.toString().trim()
                if (content.isBlank()) {
                    contentInput.error = "Required"
                    return@setOnClickListener
                }

                val memoryRepo = JarvisApp.instance.assistantMemoryRepository
                if (isNote) {
                    if (note == null) memoryRepo.addNote(title, content)
                    else memoryRepo.updateNote(note.id, title, content)
                } else {
                    val scheduledAt = reminder?.scheduledAt
                        ?: System.currentTimeMillis() + 60 * 60 * 1000L
                    if (reminder == null) memoryRepo.addReminder(title, content, scheduledAt)
                    else memoryRepo.updateReminder(reminder.id, title, content, scheduledAt)
                }
                dialog.dismiss()
            }
        }
        dialog.show()
        dialog.window?.setSoftInputMode(InputMethodManager.SHOW_IMPLICIT)
    }

    private fun confirmDeleteNote(note: AssistantNote) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete note?")
            .setMessage(note.content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                JarvisApp.instance.assistantMemoryRepository.deleteNote(note.id)
            }
            .show()
    }

    private fun confirmDeleteReminder(reminder: AssistantReminder) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete reminder?")
            .setMessage(reminder.message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                JarvisApp.instance.assistantMemoryRepository.deleteReminder(reminder.id)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ChatHistoryBottomSheet"
        fun newInstance() = ChatHistoryBottomSheet()
    }
}
