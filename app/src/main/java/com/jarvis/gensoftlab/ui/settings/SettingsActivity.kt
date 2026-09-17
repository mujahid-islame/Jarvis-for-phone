package com.jarvis.gensoftlab.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.gensoftlab.accessibility.JarvisAccessibilityService
import com.jarvis.gensoftlab.JarvisApp
import com.jarvis.gensoftlab.data.model.GeminiConstants
import com.jarvis.gensoftlab.databinding.ActivitySettingsBinding
import com.jarvis.gensoftlab.util.DeviceContextSnapshotBuilder

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
        refreshPermissionStatuses()
        refreshDeviceContextCards()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            refreshPermissionStatuses()
            refreshDeviceContextCards()
        }
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

        binding.btnMicrophonePermission.setOnClickListener {
            if (hasMicrophonePermission()) {
                openAppPermissionSettings()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    REQUEST_MICROPHONE
                )
            }
        }

        binding.btnAccessibilityPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnWriteSettingsPermission.setOnClickListener {
            if (hasWriteSettingsPermission()) {
                openWriteSettingsPermissionSettings()
            } else {
                val intent = Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    private fun refreshPermissionStatuses() {
        val microphoneGranted = hasMicrophonePermission()
        binding.tvMicrophonePermissionStatus.text =
            if (microphoneGranted) "Granted • voice input is ready" else "Not granted • voice input is unavailable"
        binding.btnMicrophonePermission.text = if (microphoneGranted) "Manage" else "Allow"

        val accessibilityGranted = JarvisAccessibilityService.isEnabled()
        binding.tvAccessibilityPermissionStatus.text =
            if (accessibilityGranted) "Enabled • scroll and click controls are ready" else "Disabled • required for phone control"
        binding.btnAccessibilityPermission.text = if (accessibilityGranted) "Manage" else "Enable"

        val writeSettingsGranted = hasWriteSettingsPermission()
        binding.tvWriteSettingsPermissionStatus.text =
            if (writeSettingsGranted) "Granted • brightness control is ready" else "Not granted • brightness control is unavailable"
        binding.btnWriteSettingsPermission.text = if (writeSettingsGranted) "Manage" else "Allow"
    }

    private fun refreshDeviceContextCards() {
        val snapshot = DeviceContextSnapshotBuilder.build(this)
        binding.tvDeviceMeta.text = "${snapshot.timeText} • ${snapshot.dateText}"
        binding.tvBatteryStatus.text = "Battery ${snapshot.batteryPercent}% • ${if (snapshot.isCharging) "charging" else "discharging"}"
        binding.tvConnectionStatus.text = "Network ${snapshot.connectionType} • brightness ${snapshot.brightnessPercent}% • volume ${snapshot.volumePercent}%"
        binding.tvCurrentAppStatus.text = "Current app: ${snapshot.currentApp}"
        binding.tvMemoryVaultStatus.text = "Memory vault: ${JarvisApp.instance.memoryVaultRepository.snapshot().size} tracked facts"
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasWriteSettingsPermission(): Boolean {
        return Settings.System.canWrite(this)
    }

    private fun openWriteSettingsPermissionSettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun openAppPermissionSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE) refreshPermissionStatuses()
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

    companion object {
        private const val REQUEST_MICROPHONE = 4101
    }
}

