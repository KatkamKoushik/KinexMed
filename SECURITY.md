# Security Policy — KinexMed Movement Intelligence Platform

KinexMed is an edge-native clinical movement intelligence platform designed for on-device biomechanical tracking and clinician-guided rehabilitation. Security, data privacy, and patient safety are fundamental architectural requirements.

---

## 1. Supported Versions

Security updates, patches, and vulnerability remediations are maintained for the following releases:

| Version | Supported | Maintenance Status |
| :--- | :---: | :--- |
| `1.0.x` | Yes | Active Production Release |
| `< 1.0.0` | No | Prototype / Experimental (Deprecated) |

---

## 2. Reporting a Vulnerability

If you discover a security vulnerability, clinical safety flaw, or data privacy issue within KinexMed, please report it privately:

- **Primary Contact:** `koushikkatkam@gmail.com`
- **Subject:** `[VULNERABILITY] KinexMed Security Report - <Component>`
- **Encryption:** If disclosing sensitive exploit details, please request our PGP/SSH public key.

Please include:
1. A detailed description of the vulnerability (e.g. IDOR, biometric leakage, FSM manipulation, authentication bypass).
2. Step-by-step reproduction instructions or a minimal proof-of-concept (PoC).
3. Impact assessment on patient privacy or clinical movement safety.
4. Affected platform components (Android edge, FastAPI backend, or React clinician portal).

### Response SLA
- **Initial Acknowledgment:** Within 48 hours of receipt.
- **Triage & Impact Assessment:** Within 5 business days.
- **Patch & Release Deployment:** Targeted within 14 business days for critical issues.

---

## 3. Core Security Principles & Guardrails

### A. Zero Raw Biometric Exfiltration
- **On-Device Only:** Raw camera frames are ingested into in-memory buffers via Android CameraX, processed by Google MediaPipe Pose Landmarker, and immediately freed.
- **No Disk or Network Persistence:** Raw camera frames or uncompressed video feeds are strictly prohibited from being written to disk, SQLite Room, log files, crash reports, or cloud synchronization payloads.

### B. Network & Transport Security
- **Cleartext Restrictions:** Plaintext HTTP traffic is blocked by default (`network_security_config.xml`). Cleartext is restricted strictly to local loopback proxies (`127.0.0.1`, `10.0.2.2`) during development.
- **Payload Bounding:** Ingestion endpoints enforce strict content-length ceilings (5 MB maximum) and bounded repetition counts (maximum 500 reps per session) to prevent resource exhaustion attacks.
- **CORS Protection:** Wildcard CORS with credentials is forbidden. Production deployments must specify exact trusted clinician web origins.

### C. Android Component Isolation
- **Non-Exported Activities:** All internal activities (`ExerciseActivity`, `SessionHistoryActivity`, `ChatActivity`, etc.) are declared with `android:exported="false"` to prevent unauthorized explicit intent invocations from malicious third-party apps.
- **Backup Protections:** Application backup is disabled (`android:allowBackup="false"`) to prevent local Room database extraction via unauthenticated device bridges.
