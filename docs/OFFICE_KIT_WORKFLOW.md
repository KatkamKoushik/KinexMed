# KinexMed — iQOO Office Kit Phone-to-Laptop Workflow

This document details the architectural integration, live demonstration procedure, and privacy boundaries for the **iQOO Office Kit Phone-to-Laptop Integration** demonstrated in the KinexMed platform.

---

## 1. Architectural Overview

KinexMed is engineered with a **Red Light / Green Light** operational model:
- **Red Light (Patient at Home - Standalone Phone):** The patient uses their iQOO smartphone as a completely autonomous, offline rehabilitation assistant. CameraX processes optical frames on-device, MediaPipe computes 33 3D skeletal landmarks on the CPU delegate, the One-Euro filter dampens joint jitter, and the Finite State Machine evaluates repetition mechanics and vocalizes real-time audio guidance. All data is persisted locally in the on-device Room SQLite database. Zero cloud dependencies, zero external network required.
- **Green Light (In-Clinic / Telehealth Review - Laptop Bridge):** When in range of a clinician workstation or during an in-office consultation, the phone bridges with the clinician's laptop via the iQOO Office Kit / wireless local bridge. The clinician immediately gains access to high-fidelity visual telemetry, deep repetition kinematic curves, Range of Motion (ROM) progression charts, and automated adherence reports.

```
+-------------------------------------------------------------+
|                      PATIENT ENVIRONMENT                    |
|                                                             |
|  +-------------------------------------------------------+  |
|  |             iQOO Android Smartphone Camera            |  |
|  +---------------------------+---------------------------+  |
|                              | (Optical Frames)             |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |     On-Device MediaPipe (CPU Delegate, 33 Landmarks)  |  |
|  +---------------------------+---------------------------+  |
|                              | (3D Coordinates)             |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |     One-Euro Smoothing Filter & Geometry Engine       |  |
|  +---------------------------+---------------------------+  |
|                              | (Clean Biomechanical Angles) |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |       Deterministic FSM & Real-Time TTS Audio         |  |
|  +---------------------------+---------------------------+  |
|                              | (Session & Rep Records)      |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |          Local Persistence: Room SQLite DB            |  |
|  +---------------------------+---------------------------+  |
+------------------------------|------------------------------+
                               |
            [WIRELESS LOCAL NETWORK / ADB REVERSE]
           Zero raw video / Only JSON telemetry
                               |
                               v
+-------------------------------------------------------------+
|                     CLINICIAN WORKSTATION                   |
|                                                             |
|  +-------------------------------------------------------+  |
|  |    iQOO Office Kit / Screen Mirroring (Visual Feed)   |  |
|  +-------------------------------------------------------+  |
|  |          FastAPI Local Ingestion Backend              |  |
|  |           (Port 8000: /sessions, /plans, /reports)     |  |
|  +---------------------------+---------------------------+  |
|                              | (REST API)                   |
|                              v                              |
|  +-------------------------------------------------------+  |
|  |       React + Vite + Chart.js Clinician Portal        |  |
|  |     - Repetition Kinematics & Depth Analysis          |  |
|  |     - Range of Motion (ROM) Trend Trajectories        |  |
|  |     - Prescribed Exercise Plans Management            |  |
|  |     - Weekly & Monthly Clinical Adherence Reports     |  |
|  |     - Clinician Feedback & Consultation Notes         |  |
|  +-------------------------------------------------------+  |
+-------------------------------------------------------------+
```

---

## 2. Supported Connection Modes

KinexMed supports two production-ready physical connectivity modes for phone-to-laptop integration:

### Mode A: Wireless Local Area Network (Wi-Fi Bridge)
1. Both the iQOO smartphone and the clinician laptop connect to the same local Wi-Fi router or mobile hotspot.
2. In the KinexMed Android app (`Settings` screen), enter the laptop's LAN IP address (e.g., `http://192.168.1.15:8000`).
3. Screen projection is activated via **iQOO Office Kit / Vivo EasyShare / Android Cast**, projecting the phone screen onto the laptop monitor for real-time observation.
4. When a session finishes, the Android `SyncManager` dispatches structured JSON payloads to the laptop's FastAPI backend asynchronously.

### Mode B: Direct USB / ADB Reverse Bridge (High-Reliability Demo Setup)
1. Connect the iQOO smartphone to the clinician laptop via USB-C cable.
2. On the laptop terminal, configure port reverse:
   ```bash
   adb reverse tcp:8000 tcp:8000
   ```
3. Start the laptop backend:
   ```bash
   cd backend
   .venv\Scripts\uvicorn backend.app.main:app --host 0.0.0.0 --port 8000
   ```
4. Start the clinician dashboard:
   ```bash
   cd web
   pnpm dev
   ```
5. In the KinexMed app, the default server URL (`http://127.0.0.1:8000`) communicates over the USB reverse tunnel directly.
6. Screen mirroring can be observed via iQOO PC Assistant or `scrcpy`.

---

## 3. Live Demonstration Protocol (Step-by-Step)

Follow this script during hackathon evaluations to showcase the full end-to-end integration:

1. **Pre-Demo Preparation:**
   - Launch backend server on laptop: `uvicorn backend.app.main:app --host 0.0.0.0 --port 8000`.
   - Launch web dashboard: `pnpm dev` in `web/` and open `http://localhost:5173`.
   - Verify connection indicator on the web navbar displays **"API ONLINE"**.
   - Prop the iQOO phone on a desk or tripod 2 to 2.5 meters from the user, facing sideways or frontally.

2. **Exercise Selection & Preparation ("Learn"):**
   - On the phone Home screen, browse the 15 available exercises using the category filters (**Lower Body**, **Upper Body**, **Functional**, **Balance**).
   - Tap **"Learn"** on **Sit to Stand** or **Bilateral Squat** to review clinical movement cues, setup distance, and common rejection pitfalls.
   - Tap **"Start Exercise"**.

3. **Hands-Free Countdown & Movement Execution:**
   - Stand in view of the phone camera.
   - The on-device `EvidenceEngine` checks landmark visibility, presence, and distance.
   - Once stable framing is maintained for 1.5 seconds, the phone announces: *"Patient framed. Starting in 5... 4... 3... 2... 1... Begin!"*
   - Perform 5 repetitions:
     - Perform Rep 1 with clean, deep knee flexion (< 95°). App TTS vocalizes: *"Rep 1! Good depth."*
     - Perform Rep 2 shallowly (> 110°). App TTS vocalizes: *"Rep incomplete. Go lower."*
     - Perform Rep 3, 4, and 5 cleanly.
   - Complete the session.

4. **Session Summary on Phone:**
   - The phone navigates to the **Session Summary** screen.
   - View transparent form score, completed vs attempted rep breakdown, and primary rejection causes.
   - Tap **"Share with Caregiver"** to launch the native Android share sheet with an encrypted, text-only clinical summary.

5. **Live Clinician Review on Laptop:**
   - On the laptop web portal, observe the session automatically arriving within 5 seconds via background polling.
   - Click the session in the **Session List**:
     - View repetition velocity and joint angle breakdown.
     - Inspect the **Range of Motion (ROM)** curves comparing starting angle, peak inflection, and terminal extension.
     - Review the **Evidence Quality** flags showing lighting and framing compliance.
   - In the **Clinician Consultation & Session Feedback** card, type a therapist note (e.g., *"Patient exhibited slight fatigue on Rep 2; depth recovered in Reps 3-5. Approved to advance to Phase 2."*) and click **"Add Note"**.
   - Navigate to **Exercise Plans** to modify the patient's weekly prescription.
   - Navigate to **Clinical Reports** to copy or print the formatted weekly adherence compliance record.

---

## 4. Privacy & Security Boundaries

KinexMed enforces strict patient privacy safeguards across the phone-to-laptop boundary:

1. **Zero Video / Image Transmission:**
   - No optical camera frames, video recordings, or depth maps are ever transmitted to the laptop backend or stored in external storage.
   - Frame buffers allocated by CameraX are immediately processed in RAM and closed before the next frame is requested.
2. **Encrypted Numerical Telemetry:**
   - Only mathematical coordinates, joint angles (degrees), timestamps (milliseconds), and categorical failure codes are transmitted.
   - Even if the network connection is intercepted, no visual patient imagery exists in the transmission payload.
3. **Offline Resilience:**
   - If the laptop disconnects or the Wi-Fi bridge drops, the phone continues functioning without interruption.
   - Telemetry remains queued safely in the on-device SQLite database and syncs once reconnection occurs.

---

## 5. Non-Diagnostic Medical Guardrail

KinexMed is strictly an **assistive rehabilitation telemetry observer**. It does not diagnose musculoskeletal pathologies, prescribe medications, or replace the clinical judgment of a licensed physical therapist or physician. All reports and summaries generated by the system include mandatory clinical non-diagnostic disclaimers.
