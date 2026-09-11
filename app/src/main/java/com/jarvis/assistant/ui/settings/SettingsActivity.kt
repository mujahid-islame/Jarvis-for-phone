package com.jarvis.assistant.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val preferences by lazy {
        JarvisApp.instance.preferences
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDropdowns()
        loadCurrentSettings()
        setupListeners()
    }

    // ============================================================
    // TOOLBAR
    // ============================================================

    private fun setupToolbar() {

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    // ============================================================
    // DROPDOWNS
    // ============================================================

    private fun setupDropdowns() {

        setupModelDropdown()
        setupVoiceDropdown()
        setupPersonalityDropdown()
    }

    // ------------------------------------------------------------
    // MODEL DROPDOWN
    // ------------------------------------------------------------

    private fun setupModelDropdown() {

        val modelAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            GeminiConstants.SUPPORTED_MODELS
        ).apply {
            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }

        binding.spinnerModel.adapter = modelAdapter
    }

    // ------------------------------------------------------------
    // VOICE DROPDOWN
    // ------------------------------------------------------------

    private fun setupVoiceDropdown() {

        val voiceAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            GeminiConstants.SUPPORTED_VOICES
        ).apply {
            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }

        binding.spinnerVoice.adapter = voiceAdapter
    }

    // ------------------------------------------------------------
    // PERSONALITY DROPDOWN
    // ------------------------------------------------------------

    private fun setupPersonalityDropdown() {

        val personalityAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            GeminiConstants.SUPPORTED_PERSONALITIES
        ).apply {
            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }

        binding.spinnerPersonality.adapter = personalityAdapter

        binding.spinnerPersonality.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    val selectedPersonality =
                        GeminiConstants.SUPPORTED_PERSONALITIES
                            .getOrNull(position)
                            ?: GeminiConstants.PERSONALITY_ASSISTANT

                    updatePersonalityDescription(
                        selectedPersonality
                    )
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {

                    updatePersonalityDescription(
                        GeminiConstants.PERSONALITY_ASSISTANT
                    )
                }
            }
    }

    // ============================================================
    // PERSONALITY DESCRIPTION
    // ============================================================

    private fun updatePersonalityDescription(
        personality: String
    ) {

        binding.tvPersonalityDesc.text =
            when (personality) {

                GeminiConstants.PERSONALITY_ASSISTANT -> {
                    "Professional • Fast • Helpful • Task-focused"
                }

                GeminiConstants.PERSONALITY_GIRLFRIEND -> {
                    "Warm • Caring • Playful • Emotionally attentive"
                }

                GeminiConstants.PERSONALITY_PERSONAL_AI -> {
                    "Personalized • Strategic • Proactive • Context-aware"
                }

                else -> {
                    "Choose an AI personality"
                }
            }
    }

    // ============================================================
    // LOAD CURRENT SETTINGS
    // ============================================================

    private fun loadCurrentSettings() {

        // API Key
        binding.etApiKey.setText(
            preferences.apiKey
        )

        // User Name
        binding.etUserName.setText(
            preferences.userName
        )

        // AI Model
        setSpinnerSelection(
            spinner = binding.spinnerModel,
            items = GeminiConstants.SUPPORTED_MODELS,
            value = preferences.aiModel
        )

        // Voice
        setSpinnerSelection(
            spinner = binding.spinnerVoice,
            items = GeminiConstants.SUPPORTED_VOICES,
            value = preferences.voice
        )

        // Personality / Mode
        setSpinnerSelection(
            spinner = binding.spinnerPersonality,
            items = GeminiConstants.SUPPORTED_PERSONALITIES,
            value = preferences.personality
        )

        updatePersonalityDescription(
            preferences.personality
        )
    }

    // ============================================================
    // GENERIC SPINNER SELECTION
    // ============================================================

    private fun <T> setSpinnerSelection(
        spinner: Spinner,
        items: List<T>,
        value: T?
    ) {

        if (items.isEmpty()) {
            return
        }

        val index = items.indexOf(value)

        if (index >= 0) {
            spinner.setSelection(index)
        }
    }

    // ============================================================
    // LISTENERS
    // ============================================================

    private fun setupListeners() {

        // --------------------------------------------------------
        // Paste API Key
        // --------------------------------------------------------

        binding.btnPasteApiKey.setOnClickListener {

            val clipboard =
                getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            val clip = clipboard.primaryClip

            if (clip != null && clip.itemCount > 0) {

                val pasteData =
                    clip
                        .getItemAt(0)
                        .text
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                if (pasteData.isNotEmpty()) {

                    binding.etApiKey.setText(
                        pasteData
                    )

                    Toast.makeText(
                        this,
                        "API Key pasted from clipboard",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    Toast.makeText(
                        this,
                        "Clipboard does not contain text",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } else {

                Toast.makeText(
                    this,
                    "Clipboard is empty",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ============================================================
    // SAVE SETTINGS
    // ============================================================

    private fun saveSettings() {

        // API Key
        val apiKey =
            binding.etApiKey.text
                ?.toString()
                ?.trim()
                .orEmpty()

        // User Name
        val userName =
            binding.etUserName.text
                ?.toString()
                ?.trim()
                .orEmpty()

        // AI Model
        val selectedModel =
            binding.spinnerModel.selectedItem
                ?.toString()
                ?: GeminiConstants.DEFAULT_MODEL

        // Voice
        //
        // No dependency on DEFAULT_VOICE.
        // Uses the first supported voice as fallback.
        //
        val selectedVoice =
            binding.spinnerVoice.selectedItem
                ?.toString()
                ?: GeminiConstants.SUPPORTED_VOICES
                    .firstOrNull()
                ?: "Aoede"

        // Personality / Mode
        val selectedPersonality =
            binding.spinnerPersonality.selectedItem
                ?.toString()
                ?: GeminiConstants.PERSONALITY_ASSISTANT

        // ========================================================
        // SAVE TO PREFERENCES
        // ========================================================

        preferences.apiKey = apiKey
        preferences.userName = userName
        preferences.aiModel = selectedModel
        preferences.voice = selectedVoice
        preferences.personality = selectedPersonality

        // ========================================================
        // SUCCESS MESSAGE
        // ========================================================

        Toast.makeText(
            this,
            "Settings saved successfully",
            Toast.LENGTH_SHORT
        ).show()

        finish()
    }
}

