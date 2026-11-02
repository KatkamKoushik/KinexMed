# Changelog — KinexMed Multi-Exercise Platform

## [v1.0.0] - 2026-11-02

### Initial Release — Edge-Native Clinical Movement Intelligence Platform
- **Multi-Exercise Observation (15 Exercises across 4 Categories):**
  - Lower Body (8): Sit-to-Stand, Bilateral Squat, Forward Lunge, Reverse Lunge, Calf Raise, Seated Knee Extension, Standing Hip Abduction, Standing Hip Extension.
  - Upper Body (4): Shoulder Flexion, Shoulder Abduction, Elbow Flexion (Bicep Curl), Elbow Extension (Tricep).
  - Functional & Mobility (2): Marching in Place, Heel-to-Toe Rocking.
  - Balance & Stability (1): Supported Single-Leg Balance.
- **Biomechanical Geometry & Edge Kinematics:**
  - Real-time 33-landmark pose estimation via CameraX and Google MediaPipe Pose Landmarker.
  - Deterministic 3D joint angle calculation with side-preference hysteresis and OneEuroFilter acceleration tuning.
  - Form compensation detection for knee valgus, pelvic tilt, and trunk inclination.
- **Biomechanical Finite State Machines:**
  - State-driven repetition counter (START, EXERCISING, RECOVERY, COMPLETED) with mathematical debounce and peak-angle hold verification.
  - GenericFlexionExtensionFsm and BalanceHoldFsm for low-latency observation.
- **Hands-Free Operation & Auditory Coaching:**
  - Continuous stable framing auto-triggers 5-second countdown with auto-session start.
  - Event-driven non-blocking voice coaching for completed and rejected repetitions.
- **Mobile Ergonomics & UI Architecture:**
  - Anchored 4-tab bottom navigation bar for one-handed reachability.
  - Standardized >= 48dp minimum touch targets across all 11 XML layouts.
  - TouchErgonomicsHelper providing native spring animations (0.96 scale, 80-120ms release) and tactile haptic feedback.
- **Local Rehabilitation Memory:**
  - Embedded Room SQLite telemetry engine storing per-rep kinematics, session summaries, and evidence frames.
  - Structured evidence cards and deterministic query parsing for patient progress review.
- **Prescribed Plans & Adherence Tracking:**
  - PlanRepository and PlansActivity for configuring custom rehabilitation regimens.
  - ConsistencyCalculator pure functional engine computing consecutive streaks, longest streaks, and weekly goal compliance.
  - Dedicated SessionSummaryActivity with transparent form scoring and caregiver share sheet.
- **Clinician Web Portal & Backend APIs:**
  - Async FastAPI backend with RESTful endpoints for session telemetry, plans, consultation notes, and compliance reports.
  - React 18 and TypeScript web dashboard with Vite, Tailwind CSS, real-time ROM distribution charts, and clinician feedback workflows.
- **Privacy & Phone-to-Laptop Integration:**
  - Zero raw video transmission or cloud streaming; all pose estimation executes purely on device.
  - Phone-to-laptop integration bridge via wireless casting and ADB port forwarding.
- **Documentation & Verification:**
  - Exhaustive 7-page platform wiki and system architecture diagrams.
  - 100% zero mock data and zero emojis across the entire codebase.
