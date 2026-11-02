# System Architecture & Processing Pipeline

[Home](Home.md) · [System Architecture](System-Architecture-and-Pipeline.md) · [Biomechanical Geometry](Biomechanical-Geometry-and-FSM.md) · [Mobile Ergonomics](Mobile-Ergonomics-and-Touch-Architecture.md) · [Rehab Memory](Local-Rehab-Memory-and-Assistant.md) · [Clinician API & Portal](Clinician-Portal-and-Backend-API.md) · [Physical Validation & Testing](Physical-Validation-and-Troubleshooting.md)

---

KinexMed implements an edge-native, 10-stage processing pipeline that transforms raw camera photons into validated kinematic metrics and clinician dashboards.

---

## 1. End-to-End Processing Pipeline

```mermaid
flowchart TD
    subgraph CameraStage["1. Optical Capture & Hardware Decoupling"]
        C1["CameraManager (CameraX Lifecycle)"] --> C2["ImageAnalysis (30 FPS, RGBA_8888)"]
        C2 --> C3["Direct ByteBuffers in RAM"]
    end

    subgraph VisionStage["2. Spatial Pose Estimation"]
        C3 --> V1["MediaPipe Pose Landmarker (CPU Delegate)"]
        V1 --> V2["33 Anatomical 3D Landmarks (x, y, z, visibility)"]
        C3 -.->|Freed Immediately| C4[Memory Released]
    end

    subgraph ControllerStage["3. Pose Analysis Controller"]
        V2 --> CTRL["PoseAnalysisController.kt"]
        CTRL --> E1["EvidenceEngine.kt"]
        E1 --> E2{"Full Body Framed & Distance Valid?"}
        E2 -- No --> E3["Visual Warning + Audio Guidance (Refusal to Guess)"]
        E2 -- Yes --> E4["Continuous 1.5s Stability Detected"]
        E4 --> E5["Audible 5-4-3-2-1 Hands-Free Countdown"]
    end

    subgraph TemporalStage["4. Sub-Millisecond Smoothing"]
        E2 -- Yes --> T1["OneEuroFilter.kt (beta=0.05, minCutoff=1.2Hz)"]
        T1 --> T2["Jitter-Free Dynamic Joint Positions"]
    end

    subgraph GeometryStage["5. Biomechanical Geometry"]
        T2 --> G1["GeometryEngine.kt"]
        G1 --> G2["Euclidean 3D Vector Angles"]
        G1 --> G3["Side-Preference Hysteresis (+-0.08 margin)"]
    end

    subgraph StateMachineStage["6. Deterministic Exercise FSM"]
        G2 & G3 --> F1["ExerciseStateMachine (15 Modules)"]
        F1 --> F2["Phase Tracking: Idle -> Start -> Peak Inflection -> Return -> Lockout"]
        F1 --> F3{"Repetition Criteria Satisfied?"}
        F3 -- Valid --> F4["Valid Rep Count++ & Form Score Updated"]
        F3 -- Incomplete --> F5["Attempt Count++ & Evidence Rejection Logged"]
    end

    subgraph FeedbackStage["7. Real-Time Feedback"]
        F4 & F5 --> FB1["VoiceFeedbackManager (Native Android TTS)"]
        F4 & F5 --> FB2["TouchErgonomicsHelper (Tactile Haptics)"]
        F4 & F5 --> FB3["PoseOverlayView (Canvas Joint Arcs)"]
    end

    subgraph PersistenceStage["8. Local-First Memory"]
        F4 & F5 --> R1["Room SQLite Database"]
        R1 --> R2["SessionEntity, RepEntity, EvidenceEventEntity"]
    end

    subgraph SynchronizationStage["9. Telemetry Sync"]
        R2 --> S1["SyncManager.kt (Dynamic Multi-Endpoint + Jitter Backoff)"]
        S1 --> S2{"Resolve Host Candidate"}
        S2 -->|Option 1| S3["Configured API URL"]
        S2 -->|Option 2| S4["ADB Reverse 127.0.0.1:8000"]
        S2 -->|Option 3| S5["Wi-Fi Gateway IP:8000"]
    end

    subgraph ClinicianStage["10. Clinician Oversight & Database Migrations"]
        S3 & S4 & S5 --> B1["FastAPI REST Services (API Key Auth)"]
        B1 --> B2[(Backend SQLite WAL Database)]
        B1 -.-> B3["Alembic Versioned Migrations (001_initial_schema)"]
        B2 --> W1["React Vite Clinician Portal"]
        W1 --> W2["Kinematic ROM Visualizer & Consultation Feedback"]
    end
```

---

## 2. Decoupled Android Subsystem Architecture

To prevent monolithic Activity degradation, camera hardware and kinematic evaluation are partitioned into specialized controllers:

1. **`CameraManager.kt` (`com.kinexmed.camera`):**
   - Encapsulates CameraX `ProcessCameraProvider` binding, `PreviewView` surface provider attachment, and `ImageAnalysis` analyzer loop.
   - Executes image buffer transformation and rotation matrix correction on a dedicated background single-thread executor.
   - Protects UI from camera lifecycle leaks via explicit `shutdown()` hooks.

2. **`PoseAnalysisController.kt` (`com.kinexmed.domain.controller`):**
   - Decouples biomechanical evaluation, One-Euro filtering, and FSM dispatch from Activity UI rendering.
   - Aggregates transient visual occlusion into structured `EvidenceEpisode` records without discarding session state.
   - Provides clean headless unit testability verified via `PoseAnalysisControllerTest.kt`.

---

## 3. Latency Budget & Phase Performance

All processing stages operate on-device to maintain an overall frame latency well below human perception:

| Pipeline Stage | Module | Typical Latency | Budget Target | Implementation Method |
| :--- | :--- | :--- | :--- | :--- |
| **Image Analysis** | `CameraManager` | 3.2 ms | <= 5.0 ms | Zero-copy direct buffer handling |
| **Pose Estimation** | `PoseLandmarkerHelper` | 32.0 ms | <= 36.0 ms | CPU delegate (`pose_landmarker_full.task`) |
| **Temporal Smoothing** | `OneEuroFilter` | 0.2 ms | <= 0.5 ms | First-order low-pass with adaptive cutoff |
| **Euclidean Geometry**| `GeometryEngine` | 0.4 ms | <= 1.0 ms | 3D vector dot product with hysteresis |
| **FSM Transition** | `PoseAnalysisController`| 0.1 ms | <= 0.5 ms | Pure state transition dispatch |
| **Canvas Drawing** | `PoseOverlayView` | 2.1 ms | <= 4.0 ms | Hardware-accelerated Android Canvas |
| **Local Persistence**| `AppDatabase` (Room) | 4.5 ms | <= 10.0 ms | Asynchronous coroutine on `Dispatchers.IO` |
| **Total Pipeline** | **End-to-End** | **~42.5 ms** | **<= 50.0 ms** | **Sustained ~28-30 FPS throughput** |

---

## 4. Dynamic Multi-Endpoint Sync Resolution

The mobile application operates reliably across diverse physical deployment environments (USB debugging, local clinical Wi-Fi, remote cellular) using dynamic fallback resolution with exponential backoff and randomized full jitter:

```mermaid
sequenceDiagram
    autonumber
    participant App as Android Client (SyncManager)
    participant ADB as ADB Reverse Bridge (127.0.0.1:8000)
    participant LAN as Local Clinical Wi-Fi Gateway
    participant Cloud as Remote Clinician Server
    participant DB as Backend SQLite (WAL Mode)

    App->>App: Session Completed & Written to Room SQLite
    App->>Cloud: Attempt Configured Remote URL (/health)
    alt Remote Server Reachable
        App->>Cloud: POST /sessions (Full Telemetry JSON)
        Cloud->>DB: Ingest Idempotently
        Cloud-->>App: 200 OK (SyncStatus: SYNCHRONIZED)
    else Remote Unreachable
        App->>ADB: Attempt Localhost Bridge (/health)
        alt ADB Port Forwarding Active
            App->>ADB: POST /sessions
            ADB->>DB: Ingest Idempotently
            ADB-->>App: 200 OK
        else ADB Inactive
            App->>LAN: Query DHCP Gateway IP (/health)
            alt Clinical Wi-Fi Detected
                App->>LAN: POST /sessions
                LAN->>DB: Ingest Idempotently
                LAN-->>App: 200 OK
            else All Channels Offline
                App->>App: Mark Session as PENDING_SYNC in Room SQLite
            end
        end
    end
```

---

**Navigation:** [Home](Home.md) | [Architecture](System-Architecture-and-Pipeline.md) | [Biomechanics & FSM](Biomechanical-Geometry-and-FSM.md) | [Mobile Ergonomics](Mobile-Ergonomics-and-Touch-Architecture.md) | [Rehab Memory](Local-Rehab-Memory-and-Assistant.md) | [Clinician API & Portal](Clinician-Portal-and-Backend-API.md) | [Physical Validation](Physical-Validation-and-Troubleshooting.md)
