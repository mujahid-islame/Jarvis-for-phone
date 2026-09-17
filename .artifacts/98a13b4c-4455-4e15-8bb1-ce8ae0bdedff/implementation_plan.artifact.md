# Implementation Plan: Human-Like Android Control — Phase 1 (Accessibility Control Layer)

This phase establishes the semantic UI control layer using Android's `AccessibilityService`. We will refactor the existing monolithic accessibility logic into a modular, thread-safe, and verifiable architecture. The goal is to move from "Act Only" to an "Observe → Understand → Act → Verify" loop.

## User Review Required

> [!IMPORTANT]
> - **Architectural Refactoring**: The existing `JarvisAccessibilityService.kt` will be converted into a lifecycle manager, delegating all UI logic to specialized components (`AccessibilityBridge`, `NodeFinder`, `ActionExecutor`, `GestureController`).
> - **Implicit Verifiability**: Tools will no longer report success upon dispatch. Success is only confirmed after re-observing the UI state (e.g., package changed, text appeared).
> - **Stale Node Safety**: We will enforce a "Fresh Node Only" policy, prohibiting the caching of `AccessibilityNodeInfo` across tool turns to prevent crashes or incorrect interactions.

## Proposed Changes

### 1. Accessibility Architecture & Data Models

#### [NEW] [UiElement.kt](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/java/com/jarvis/gensoftlab/accessibility/UiElement.kt) & [UiSnapshot.kt](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/java/com/jarvis/gensoftlab/accessibility/UiSnapshot.kt)
- Create immutable representations of the UI tree.
- `UiElement`: Maps text, content description, viewId, bounds, and states (clickable, editable, etc.).
- `UiSnapshot`: Captures the full screen context including package name and window dimensions.

#### [NEW] [AccessibilityBridge.kt](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/java/com/jarvis/gensoftlab/accessibility/AccessibilityBridge.kt)
- Manages a lifecycle-safe reference to the active service.
- Provides synchronized access to root nodes and window information.

#### [NEW] [AccessibilityNodeFinder.kt](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/java/com/jarvis/gensoftlab/accessibility/AccessibilityNodeFinder.kt)
- Implements deterministic search with priority: Exact Text > Content Description > View ID > Contains Text.
- Handles ambiguity by returning `AMBIGUOUS_TARGET` errors instead of guessing.

#### [NEW] [AccessibilityActionExecutor.kt](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/java/com/jarvis/gensoftlab/accessibility/AccessibilityActionExecutor.kt)
- Wraps `AccessibilityNodeInfo.performAction` for `CLICK`, `SET_TEXT`, `SCROLL`, etc.
- Implements `wait_for_ui_change` logic based on accessibility events.

#### [NEW] [GestureController.kt](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/java/com/jarvis/gensoftlab/accessibility/GestureController.kt)
- Manages `dispatchGesture()` for coordinate-based `tap`, `swipe`, `long_press`.
- Ensures serialization (one gesture at a time) and waits for completion/cancellation callbacks.

### 2. Service Implementation & Configuration

#### [MODIFY] [JarvisAccessibilityService.kt](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/java/com/jarvis/gensoftlab/accessibility/JarvisAccessibilityService.kt)
- Refactor to register with `AccessibilityBridge` on connect.
- Filter incoming `AccessibilityEvent` objects to throttle UI-change detection.

#### [MODIFY] [jarvis_accessibility_service.xml](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/res/xml/jarvis_accessibility_service.xml)
- Declared minimal meaningful events (`typeWindowStateChanged`, `typeWindowContentChanged`, `typeViewClicked`, `typeViewFocused`, `typeViewTextChanged`).
- Ensure `canRetrieveWindowContent` and `canPerformGestures` are enabled.

### 3. Tool Dispatcher & Execution

#### [MODIFY] [ToolDispatcher.kt](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/java/com/jarvis/gensoftlab/tools/ToolDispatcher.kt)
- Integrate Phase 1 tools:
    - **Inspection**: `get_current_app`, `get_ui_tree`, `get_screen_state`.
    - **Interaction**: `tap`, `swipe`, `scroll`, `set_text`, `click_node`.
    - **Navigation**: `press_back`, `press_home`, `open_recents`.
- Implement strict argument validation and actionId generation.

## Verification Plan

### Automated Tests
- Run `gradlew clean assembleDebug`.
- Validate coordinate boundary checks (0 to screen width/height).

### Real Device Verification (TECNO BG6)
- **Service Detection**: Confirm `CapabilityManager` detects Accessibility as `FULL` once enabled.
- **UI Inspection**: Verify `get_ui_tree` returns accurate data for Android Settings.
- **Gesture Loop**: Perform `tap` -> `wait_for_ui_change` -> `verify` in the Clock or Calculator app.
- **Safe Navigation**: Use `press_home` and `open_recents` to verify global actions.
- **Text Entry**: Verify `set_text` in a harmless search bar and confirm the text appeared.
