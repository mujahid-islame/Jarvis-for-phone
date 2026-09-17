# Tasks: Phase 1 — Accessibility Control Layer

- [ ] Define immutable UI data models (`UiElement.kt`, `UiSnapshot.kt`)
- [ ] Implement `AccessibilityBridge.kt` for lifecycle-safe service access
- [ ] Implement `AccessibilityNodeFinder.kt` with deterministic search logic
- [ ] Implement `AccessibilityActionExecutor.kt` for semantic node interactions
- [ ] Implement `GestureController.kt` for coordinate gestures (`dispatchGesture`)
- [ ] Refactor `JarvisAccessibilityService.kt` and its XML configuration
- [ ] Integrate Phase 1 tools into `ToolDispatcher.kt`
- [ ] Implement `ActionVerifier.kt` for UI state change detection
- [ ] Verify build via `gradlew clean assembleDebug`
- [ ] Perform real device testing on TECNO BG6 for all implemented tools
- [ ] Ensure no regressions in existing voice/HUD functionality
