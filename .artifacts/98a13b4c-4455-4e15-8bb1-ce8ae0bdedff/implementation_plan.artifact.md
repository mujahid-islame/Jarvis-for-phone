# Package Name Change to `com.jarvis.gensoftlab`

This plan outlines the steps required to change the application's package name and application ID from `com.jarvis.assistant` to `com.jarvis.gensoftlab`. This is a critical change as it affects the app's identity on the Play Store and its internal code structure.

## User Review Required

> [!IMPORTANT]
> Changing the `applicationId` will treat the app as a **new application** on Android devices. If you have an existing version installed, it will not be updated; instead, a new app will be installed alongside it. Data stored under the old package name (Preferences, Database) will not be accessible to the new package unless migrated manually.

> [!WARNING]
> If this app is already published on the Google Play Store, changing the `applicationId` means you cannot update the existing listing. You would have to create a new app listing.

## Proposed Changes

The changes will be applied across the `:app` module.

---

### Build Configuration

#### [MODIFY] [build.gradle.kts](file:///C:/Users/islam/Desktop/JarvisAssistant/app/build.gradle.kts)
- Update `namespace` to `com.jarvis.gensoftlab`.
- Update `applicationId` to `com.jarvis.gensoftlab`.

---

### Source Code and Directories

#### [MOVE] `app/src/main/java/com/jarvis/assistant` -> `app/src/main/java/com/jarvis/gensoftlab`
#### [MOVE] `app/src/test/java/com/jarvis/assistant` -> `app/src/test/java/com/jarvis/gensoftlab`

#### [MODIFY] All Kotlin/Java files in `src/main/java` and `src/test/java`
- Update `package` declarations from `com.jarvis.assistant...` to `com.jarvis.gensoftlab...`.
- Update `import` statements referencing the old package.

---

### Resources and Manifest

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/AndroidManifest.xml)
- Update any hardcoded activity or service references if they use the full package name.

#### [MODIFY] [jarvis_accessibility_service.xml](file:///C:/Users/islam/Desktop/JarvisAssistant/app/src/main/res/xml/jarvis_accessibility_service.xml)
- Update `android:settingsActivity` reference.

#### [MODIFY] Layout XML files
- Update custom view references or Data Binding/View Binding references if any.

---

### Other Configurations

#### [MODIFY] [proguard-rules.pro](file:///C:/Users/islam/Desktop/JarvisAssistant/app/proguard-rules.pro)
- Update `-keep` rules for the new package name.

## Verification Plan

### Automated Tests
- Run `gradlew clean assembleDebug` to ensure the project builds successfully.
- Run unit tests: `gradlew test`.

### Manual Verification
- Deploy the app to a device/emulator and verify it launches.
- Verify that the Accessibility Service can be enabled (since it uses the package name).
