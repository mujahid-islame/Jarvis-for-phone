# Reconnection Loop Fix Tasks

- [ ] **Stabilize Reconnection Logic (GeminiLiveWebSocket.kt)**
    - [x] Add `MAX_RECONNECT_ATTEMPTS = 5`.
    - [x] Implement logic to stop retrying after max attempts.
    - [x] Fix `startSessionRenewal` to disconnect properly before reconnecting.
    - [x] Handle server-side JSON errors by closing the connection.
    - [x] Add detailed logs for each state transition.
- [ ] **ViewModel Enhancements (MainViewModel.kt)**
    - [x] Improve error event handling for user-facing messages.
    - [x] Ensure orb state is reset on critical failures.
- [ ] **Final Verification**
    - [ ] Deploy and test with simulated failures.
    - [ ] Verify no infinite loop on persistent failures.
