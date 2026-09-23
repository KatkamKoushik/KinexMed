# Changelog — KinexMed Multi-Exercise Platform

## [v1.1.0] - 2026-09-23

### Multi-Exercise Expansion (15 Exercises across 4 Categories)
- Added `ExerciseCategory` enum: `LOWER_BODY`, `UPPER_BODY`, `FUNCTIONAL_MOBILITY`, `BALANCE`.
- Expanded `ExerciseRegistry` to 15 rehabilitation exercises:
  - **Lower Body (8):** Sit-to-Stand, Bilateral Squat, Forward Lunge, Reverse Lunge, Calf Raise, Seated Knee Extension, Standing Hip Abduction, Standing Hip Extension.
  - **Upper Body (4):** Shoulder Flexion, Shoulder Abduction, Elbow Flexion (Bicep Curl), Elbow Extension (Tricep).
  - **Functional & Mobility (2):** Marching in Place, Heel-to-Toe Rocking.
  - **Balance & Stability (1):** Supported Single-Leg Balance.
- Registered explicit validation status for all exercises:
  - `PHYSICALLY_DEMONSTRATED`: Sit-to-Stand (verified on physical device).
  - `IMPLEMENTED_MODULE`: Bilateral Squat and 13 other modules (code complete and unit-tested).

### Real-Time Responsiveness & Kinematics Engine
- **Sit-to-Stand Overhaul:** Removed 3-frame artificial dwell buffer (eliminating 150-200ms latency); implemented direct velocity delta detection supporting both seated-start and stand-start cycles.
- **Squat Side Oscillation Fix:** Added side-preference hysteresis (+-0.08 margin) in `GeometryEngine.getPrimaryKneeAngle` to prevent frame-to-frame switching between left and right legs.
- **Lockout Tuning:** Adjusted squat completion extension to 138 deg for smooth upright detection.
- **Filter Tuning:** Tuned `OneEuroFilter` beta parameter from 0.008 to 0.05 for immediate acceleration tracking while preserving static stability.
- **Generic State Machines:** Added `GenericFlexionExtensionFsm` and `BalanceHoldFsm` for deterministic, low-latency repetition tracking.

### Hands-Free Patient Experience & Auditory Coaching
- **Hands-Free Start:** 1.5 seconds of continuous stable framing triggers an audible 5-second countdown ("5, 4, 3, 2, 1, Begin!") with auto-session start.
- **Non-blocking TTS Coaching:** Event-driven voice announcements for completed and rejected repetitions.
- **Resilient Persistence:** Session saving and sync dispatched to an application supervisor scope (`CoroutineScope(Dispatchers.IO + SupervisorJob())`).

### Clinician Portal & Backend Dynamic Metrics
- **Dynamic Summaries:** Updated `session_summary.py` to format session summaries dynamically using `session.exercise_name` across all 15 exercises.
- **ROM Charting:** Expanded `RomChart.tsx` to display custom target lines and joint labels for all 15 exercises.
- **Clean Networking:** Removed hardcoded developer LAN IPs in `SyncManager.kt`; added dynamic Wi-Fi DHCP default gateway resolution.

### Testing & Quality Assurance
- 37 Pytest unit and integration tests passing in backend.
- 22 Android unit tests passing in app test suite.
- Web dashboard production build verified with Vite and TypeScript (0 errors).
- Added GitHub Actions CI workflow in `.github/workflows/ci.yml`.
