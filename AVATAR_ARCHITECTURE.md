# AVATAR ARCHITECTURE

জারভিসের ৩ডি হোলোলগ্রাফিক অবতার সিস্টেমের কারিগরি স্থাপত্য নিচে বর্ণনা করা হলো।

---

## ১. ওভারভিউ (System Overview)

সিস্টেমটি মূলত তিনটি স্তরে বিভক্ত:

1.  **Audio & AI Layer (Kotlin)**: Gemini Live থেকে পাওয়া অডিও এবং ইমোশন ডাটা প্রসেস করে।
2.  **Bridge Layer (Android WebView)**: কোটলিন থেকে পাওয়া কমান্ড জাভাস্ক্রিপ্টে রূপান্তর করে।
3.  **Visual Layer (Three.js/WebGL)**: ৩ডি মডেল রেন্ডার করে এবং এনিমেশন চালায়।

---

## ২. ডাটা ফ্লো (Data Flow)

```text
Gemini Response
      ↓
Emotion Parser (MainViewModel)
      ↓
OrbHelper (updateEmotion / updateAudioLevel)
      ↓
WebView (JavaScript Bridge)
      ↓
Avatar Controller (Blinking, Lip Sync, Movement)
      ↓
WebGL Renderer (Holographic Shaders)
```

---

## ৩. গুরুত্বপূর্ণ মডিউলসমূহ

### Avatar Controller
এটি অবতারের স্বয়ংক্রিয় আচরণ (শ্বাস নেওয়া, পলক ফেলা) এবং ইউজারের সাথে ইন্টারেকশন (লিপ-সিঙ্ক) নিয়ন্ত্রণ করে।

### Hologram Shader
এটি অবতারের ওপরে একটি লেয়ার হিসেবে কাজ করে যা নিওন গ্লো, স্ক্যানলাইন এবং হালকা ডিজিটাল ডিস্টরশন (Flicker) যোগ করে।

### Lip Sync Engine
এটি অডিওর পিচ এবং ভলিউম বিশ্লেষণ করে মুখের হাঁ (Jaw opening) এবং প্রসারণ নিয়ন্ত্রণ করে।
