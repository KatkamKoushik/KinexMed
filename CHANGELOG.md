# Changelog — KinexMed Multi-Exercise Platform

## [v1.2.0] - 2026-09-23

### PPT ↔ Codebase Gap Closure & Platform Unification
- **Prescribed Exercise Plans:**
  - Android: `ExercisePlanEntity`, `PlanExerciseEntity`, `ExercisePlanDao`, `PlanRepository`, and `PlansActivity` allowing patients and clinicians to configure custom rehabilitation regimens (target sets, reps, and weekly frequency).
  - Backend: RESTful CRUD endpoints at `/plans` with SQLAlchemy models and Pydantic schemas.
  - Web: `PlansView` with interactive prescription builder and active status toggling.
- **Consistency & Streak System:**
  - `ConsistencyCalculator.kt` pure functional engine aggregating real SQLite session timestamps into consecutive workout streaks, longest streak, weekly goal adherence %, and exercise volume.
  - Home screen active streak and weekly goal completion card with zero mock data.
- **Weekly & Monthly Patient Progress:**
  - `ProgressActivity.kt` providing interactive WEEK and MONTH analytical views with total volume, valid vs attempted ratio, and rejection cause breakdown.
- **Comprehensive Session Summary & Scoring:**
  - `SessionSummaryActivity.kt` delivering transparent form scoring (validity ratio + depth achievement), duration metrics, and common form pitfalls.
- **Caregiver Share Sheet Integration:**
  - `CaregiverShareHelper.kt` generating privacy-safe clinical text summaries and launching native Android `Intent.ACTION_SEND` (zero raw video exposure).
- **Automated Clinical Adherence Reports:**
  - Backend `/reports/session/{id}`, `/reports/weekly`, and `/reports/monthly` endpoints computing structured adherence metrics.
  - Web `ReportsView` component with interactive KPI cards, exercise distribution, and formatted export to clipboard and print.
- **Clinician Feedback & Notes:**
  - Backend `/feedback` endpoints persisting consultation notes by session ID.
  - Interactive feedback submission form and chronological note thread in `SessionDetailView.tsx`.
- **iQOO Office Kit Phone-to-Laptop Workflow:**
  - Created `docs/OFFICE_KIT_WORKFLOW.md` detailing wireless screen casting, ADB reverse port forwarding, and privacy boundaries.
- **Engineering Hygiene & Architecture Cleanup:**
  - Removed unused Retrofit, Gson, and logging-interceptor dependencies from `android/app/build.gradle.kts`.
  - Removed orphaned `SessionList.tsx` from `web/src/components/`.
  - Upgraded Room SQLite database to version 4 with schema migrations.
  - Zero mock data and zero emojis strictly enforced across the entire codebase.

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
