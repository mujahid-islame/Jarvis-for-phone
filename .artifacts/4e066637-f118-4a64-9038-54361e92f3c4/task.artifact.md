# JARVIS Singing Duration Fix Task List

- [x] **State Management Fixes (MainViewModel.kt)**
    - [x] Ensure `SINGING` state is preserved when new audio chunks arrive during a session.
    - [x] Prevent early `IDLE` transition during minor gaps in singing audio.
- [x] **Duration Parser Improvements**
    - [x] Update `DurationParser.kt` regex to better capture Bengali numbers and units.
- [x] **Continuation Logic Refinement**
    - [x] Update `MainViewModel.kt` to trigger continuation more reliably with a buffer.
    - [x] Improve continuation prompt in `PromptGenerator.kt` for stronger emphasis.
- [ ] **Verification**
    - [ ] Build and Deploy.
    - [ ] Test 2-minute and 5-minute singing sessions.
    - [ ] Verify Orb color persistence during singing.
