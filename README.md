# 🤖 JARVIS — Advanced AI Voice Assistant

JARVIS is a state-of-the-art, low-latency AI voice assistant built for Android using the **Gemini Multimodal Live API**. It features natural conversation, advanced voice activity detection, emotion awareness, and a unique continuous singing mode.

---

## 🚀 Key Features

### 1. Real-time Voice Interaction
- Powered by **Gemini 3.1 Flash Live Preview**.
- Sub-second response latency with **bidirectional streaming** (WebSockets).
- Supports real-time input/output transcription.

### 2. Advanced Voice Recognition (VAD & Barge-in)
- **Adaptive Noise Calibration**: Automatically adjusts to background noise (fans, AC) to prevent false triggers.
- **Natural Turn-taking**: Intelligent silence detection that distinguishes between short pauses and the end of speech.
- **Barge-in Support**: Interrupt JARVIS at any time; it stops speaking immediately to listen to you.

### 3. Continuous Singing Mode (2–5 Minutes)
- **Long-duration Performance**: Sing for up to 5 minutes based on user requests.
- **Seamless Continuation**: Automatically handles model turn limits to continue songs without breaks.
- **Musical Expressiveness**: Uses dynamic musical prompting for melody, rhythm, and soulful delivery.

### 4. Emotion Awareness
- **Contextual Understanding**: JARVIS detects user emotions (Happy, Sad, Excited, etc.) and adjusts its voice tone.
- **Metadata Cleaning**: Automatically removes tags like `[EMOTION:HAPPY]` from spoken output while preserving the sentiment.

### 5. Premium Cyberpunk UI/UX
- **Pill-shaped App Bar**: Sleek top bar displaying real-time **RAM Usage** and **Battery Percentage**.
- **Energy Orb Visualizer**: Interactive WebView-based visualizer that changes states (Idle, Listening, Thinking, Speaking, Singing).
- **Gestures**: Swipe from Left to Right to instantly view conversation history.

---

## 🛠️ Technical Architecture

- **Language**: 100% Kotlin.
- **Pattern**: MVVM (Model-View-ViewModel) with Repository pattern.
- **Audio Pipeline**:
  - Input: 16-bit Mono PCM, 16kHz.
  - Output: 16-bit Mono PCM, 24kHz.
- **Networking**: OkHttp WebSocket with HTTP/2 support for optimized framing.
- **State Management**: Kotlin Flow and StateFlow for reactive UI updates.

---

## 📦 Project Structure

- `audio/`: VAD, Singing Session Manager, and low-level AudioTrack/Record handling.
- `network/`: Gemini Live WebSocket implementation and JSON parsing.
- `ui/`: MainActivity, Visualizer (Orb), and Chat History components.
- `util/`: Duration parsing, Prompt generation, and formatting.

---

## ⚙️ Setup & Configuration

1. **Gemini API Key**: Obtain a key from [Google AI Studio](https://aistudio.google.com/).
2. **Configuration**: Open the app settings (gear icon) and paste your API key.
3. **Model Selection**: Default is `gemini-3.1-flash-live-preview` for optimal latency.

---

## 📝 License
This project is developed for educational and personal assistant purposes. All rights reserved.
