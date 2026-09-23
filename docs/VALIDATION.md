# KinexMed — Physical Validation and Kinematic Test Matrix

Last Updated: September 2026
Target Event: iQOO Hackathon 2026 — Hyderabad City Battle
Device Profile: On-Device Android Smartphone (MediaPipe Pose Landmarker CPU Delegate)

---

## 1. Multi-Exercise Library Validation Matrix

KinexMed enforces an explicit distinction between exercises physically demonstrated on physical hardware versus exercises implemented as complete software modules awaiting physical debugging and field trials.

| # | Exercise Name | Category | Primary Kinematic Metric | Target Threshold | Validation Status | Real Hardware Test Notes |
|---|---|---|---|---|---|---|
| 1 | **Sit-to-Stand** | Lower Body | Knee Extension / Torso Line | >= 148 deg | **PHYSICALLY DEMONSTRATED** | Validated on physical device. Zero perceived delay. Direct velocity delta detection. Hands-free 5s countdown verified. |
| 2 | **Bilateral Squat** | Lower Body | Knee Flexion Angle | <= 100 deg (tgt 90 deg) | **IMPLEMENTED MODULE** | Tracking-side hysteresis (+-0.08 confidence) applied to eliminate sagittal knee oscillation. Upright lockout set to 138 deg. Under physical debugging. |
| 3 | **Forward Lunge** | Lower Body | Lead Knee Flexion | <= 100 deg (tgt 90 deg) | **IMPLEMENTED MODULE** | Implemented via GenericFlexionExtensionFsm; unit-tested. Awaiting physical trials. |
| 4 | **Reverse Lunge** | Lower Body | Lead Knee Flexion | <= 100 deg (tgt 90 deg) | **IMPLEMENTED MODULE** | Implemented via GenericFlexionExtensionFsm; unit-tested. Awaiting physical trials. |
| 5 | **Calf Raise** | Lower Body | Ankle Plantarflexion | >= 115 deg | **IMPLEMENTED MODULE** | Implemented via CalfRaiseStateMachine; unit-tested. Awaiting physical trials. |
| 6 | **Seated Knee Extension** | Lower Body | Knee Extension Angle | >= 145 deg | **IMPLEMENTED MODULE** | Implemented via GenericFlexionExtensionFsm; unit-tested. Awaiting physical trials. |
| 7 | **Standing Hip Abduction** | Lower Body | Hip Abduction Angle | <= 150 deg | **IMPLEMENTED MODULE** | Implemented via GenericFlexionExtensionFsm; unit-tested. Awaiting physical trials. |
| 8 | **Standing Hip Extension** | Lower Body | Hip Extension Angle | <= 152 deg | **IMPLEMENTED MODULE** | Implemented via GenericFlexionExtensionFsm; unit-tested. Awaiting physical trials. |
| 9 | **Marching in Place** | Functional & Mobility | Hip Flexion Angle | <= 110 deg | **IMPLEMENTED MODULE** | Implemented via GenericFlexionExtensionFsm; unit-tested. Awaiting physical trials. |
| 10 | **Shoulder Flexion** | Upper Body | Shoulder Angle | >= 90 deg | **IMPLEMENTED MODULE** | Implemented via GenericFlexionExtensionFsm; unit-tested. Awaiting physical trials. |
| 11 | **Shoulder Abduction** | Upper Body | Shoulder Angle | >= 90 deg | **IMPLEMENTED MODULE** | Implemented via GenericFlexionExtensionFsm; unit-tested. Awaiting physical trials. |
| 12 | **Elbow Flexion (Bicep Curl)** | Upper Body | Elbow Interior Angle | <= 70 deg | **IMPLEMENTED MODULE** | Implemented via GenericFlexionExtensionFsm; unit-tested. Awaiting physical trials. |
| 13 | **Elbow Extension (Tricep)** | Upper Body | Elbow Extension Angle | >= 145 deg | **IMPLEMENTED MODULE** | Implemented via GenericFlexionExtensionFsm; unit-tested. Awaiting physical trials. |
| 14 | **Heel-to-Toe Rocking** | Functional & Mobility | Ankle Motion Cycle | >= 112 deg | **IMPLEMENTED MODULE** | Implemented via GenericFlexionExtensionFsm; unit-tested. Awaiting physical trials. |
| 15 | **Supported Single-Leg Balance**| Balance & Stability | Ankle Elevation Ratio / Hold Duration | >= 10.0s hold | **IMPLEMENTED MODULE** | Implemented via BalanceHoldFsm; unit-tested. Awaiting physical trials. |

---

## 2. On-Device Real-Time Performance Benchmarks

All vision inference runs 100% locally on the device using the MediaPipe Pose Landmarker CPU delegate. Raw camera video is never saved to flash or transmitted across the network.

- **Inference Latency:** 29 ms to 36 ms per frame (CPU Delegate)
- **Sustained Frame Rate:** 27.5 to 29.2 FPS
- **One-Euro Filter Lag:** < 15 ms phase delay at beta = 0.05 (rapid velocity adaptation)
- **Visual Evidence Latency:** 1 frame evaluation (~33 ms)
- **Hands-Free Detection:** 1500 ms continuous stable framing triggers 5-second audible countdown

---

## 3. Automated Test Verification Summary

- **Backend API & Kinematics (Pytest):** 37 tests passed (0 failures)
  - Multi-exercise dynamic summary formatting
  - Session ingestion across all 15 exercises
  - Repetition idempotency and deduplication
  - Non-diagnostic clinical safety guardrails
- **Android Domain & State Machines (JUnit):** 22 tests passed (0 failures)
  - ExerciseRegistry discovery and category integrity
  - Zero-lag Sit-to-Stand state transitions
  - Squat inflection and lockout detection
  - Landmark metric extraction across all 15 exercise models
- **Web Clinician Dashboard (Vite & TypeScript):** 0 errors, production build verified in 186ms
