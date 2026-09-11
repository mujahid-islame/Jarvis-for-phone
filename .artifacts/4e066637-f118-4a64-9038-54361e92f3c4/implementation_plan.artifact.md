# Bulletproof WebSocket Reconnection & Stability Plan

এই পরিকল্পনার লক্ষ্য হলো "Reconnecting Loop" সমস্যাটি স্থায়ীভাবে সমাধান করা এবং জারভিসের কানেকশনকে আরও শক্তিশালী ও নির্ভরযোগ্য করে তোলা।

## User Review Required

> [!IMPORTANT]
> **Max Attempts**: আমরা ৫টি ব্যর্থ চেষ্টার পর অটো-রিকানেক্ট বন্ধ করে দেব এবং ইউজারকে সেটিংস চেক করতে বলব। এটি অ্যাপকে "Infinite Loop" থেকে বাঁচাবে।
> **Server Error Handling**: যদি Gemini থেকে কোনো এরর আসে (যেমন: API Key ইনভ্যালিড), অ্যাপটি সাথে সাথে কানেকশন বন্ধ করে দিবে এবং সঠিক কারণ জানাবে।
> **Session Refresh Fix**: ৯-মিনিট পর পর সেশন রিনিউ করার লজিকটি ফিক্স করা হবে যাতে পুরনো সেশনটি পুরোপুরি বন্ধ হওয়ার পর নতুনটি শুরু হয়।

## Proposed Changes

### [Component: Network]

#### [MODIFY] [GeminiLiveWebSocket.kt](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/java/com/jarvis/assistant/network/GeminiLiveWebSocket.kt)
- **Attempt Limiting**: `MAX_RECONNECT_ATTEMPTS = 5` যোগ করা।
- **Graceful Failure**: ৫ বার ব্যর্থ হলে "RECONNECTING" মোড থেকে বেরিয়ে এসে "FAILED" স্টেট সেট করা।
- **Server-Side Error Close**: সার্ভার থেকে `error` অবজেক্ট পেলে সেশনটি `cancel()` করা এবং রিকানেক্ট না করা (যদি কারণটি ফিক্সড হয় যেমন: 401/403)।
- **Session Renewal Correction**: রিনিউ করার সময় আগে পুরনো `webSocket` ডিসকানেক্ট করা।
- **Improved Logging**: প্রতিটি কানেকশন এবং এরর লগে টাইমস্ট্যাম্প যোগ করা।

### [Component: UI/State]

#### [MODIFY] [MainViewModel.kt](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/java/com/jarvis/assistant/ui/home/MainViewModel.kt)
- কানেকশন লস্ট হলে অর্বের (Orb) এনিমেশন এবং স্ট্যাটাস টেক্সট ট্রানজিশন আরও স্মুথ করা।
- ইউজারের অ্যাকশন (যেমন: মাইক্রোফোন অন করা) যাতে পেন্ডিং কানেকশনের সাথে কনফ্লিক্ট না করে তা নিশ্চিত করা।

## Verification Plan

### Manual Verification
1. **Bad API Key Test**: ভুল কী দিয়ে চেক করা যে সে লুপে না পড়ে সঠিক এরর দেখাচ্ছে কিনা।
2. **Offline Test**: ইন্টারনেট বন্ধ করে দেখা যে ৫ বার পর সে শান্ত হয়ে যাচ্ছে কিনা।
3. **Recovery Test**: ৩য় বা ৪র্থ চেষ্টার সময় ইন্টারনেট অন করলে সে সফলভাবে ব্যাক করছে কিনা।
4. **Stability Test**: ১০ মিনিটের বেশি সেশন চালিয়ে দেখা রিনিউয়াল ঠিকমতো কাজ করছে কিনা।
