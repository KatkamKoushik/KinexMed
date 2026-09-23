import pytest
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.models.session import SessionModel, RepModel
from backend.app.services.session_summary import generate_deterministic_fallback_summary

client = TestClient(app)

MULTI_EXERCISES = [
    ("sit_to_stand", "Sit To Stand", 150.0),
    ("squat", "Squat", 90.0),
    ("forward_lunge", "Forward Lunge", 92.0),
    ("reverse_lunge", "Reverse Lunge", 94.0),
    ("calf_raise", "Calf Raise", 122.0),
    ("knee_extension", "Knee Extension", 155.0),
    ("hip_abduction", "Hip Abduction", 142.0),
    ("hip_extension", "Hip Extension", 148.0),
    ("marching_in_place", "Marching In Place", 102.0),
    ("shoulder_flexion", "Shoulder Flexion", 120.0),
    ("shoulder_abduction", "Shoulder Abduction", 115.0),
    ("elbow_flexion", "Elbow Flexion", 62.0),
    ("elbow_extension", "Elbow Extension", 152.0),
    ("heel_toe_raise", "Heel Toe Raise", 118.0),
    ("single_leg_balance", "Single Leg Balance", 10.0),
]

@pytest.mark.parametrize("exercise_id,display_name,target_val", MULTI_EXERCISES)
def test_deterministic_summary_contains_dynamic_exercise_name(exercise_id, display_name, target_val):
    session = SessionModel(
        id=f"test-{exercise_id}-001",
        device_id="test-phone-01",
        exercise_name=exercise_id,
        started_at=1000000,
        completed_at=1015000,
        duration_seconds=15.0,
        total_reps=5,
        valid_reps=4,
        avg_peak_knee_angle=target_val,
        min_knee_angle=0.0,
        max_knee_angle=180.0,
        evidence_failure_count=0
    )
    summary = generate_deterministic_fallback_summary(session)
    assert display_name.lower() in summary.lower()
    assert "4 valid repetitions" in summary
    assert "80% adherence" in summary
    assert "squat attempts" not in summary or exercise_id == "squat"

def test_multi_exercise_session_ingestion_and_retrieval():
    for ex_id, _, target_val in MULTI_EXERCISES[:5]:
        session_id = f"integ-{ex_id}-test"
        payload = {
            "id": session_id,
            "device_id": "iQOO-Neo9-Pro-01",
            "exercise_name": ex_id,
            "started_at": 1710000000000,
            "completed_at": 1710000030000,
            "duration_seconds": 30.0,
            "total_reps": 3,
            "valid_reps": 3,
            "avg_peak_knee_angle": target_val,
            "min_knee_angle": target_val - 10.0,
            "max_knee_angle": 180.0,
            "evidence_failure_count": 0,
            "reps": [
                {
                    "rep_number": 1,
                    "is_valid": True,
                    "start_timestamp_ms": 1710000001000,
                    "peak_timestamp_ms": 1710000003000,
                    "end_timestamp_ms": 1710000005000,
                    "duration_ms": 4000,
                    "peak_knee_angle": target_val,
                    "start_knee_angle": 175.0,
                    "end_knee_angle": 175.0,
                    "feedback_message": "Rep 1 completed successfully",
                    "failure_reasons": []
                }
            ],
            "evidence_events": []
        }

        # 1. Post Session
        resp = client.post("/sessions", json=payload)
        assert resp.status_code in [200, 201]
        data = resp.json()
        assert data["id"] == session_id
        assert data["exercise_name"] == ex_id

        # 2. Re-post (Idempotency test)
        resp_dup = client.post("/sessions", json=payload)
        assert resp_dup.status_code in [200, 201]

        # 3. Retrieve Session
        get_resp = client.get(f"/sessions/{session_id}")
        assert get_resp.status_code == 200
        get_data = get_resp.json()
        assert len(get_data["reps"]) == 1
        assert get_data["reps"][0]["rep_number"] == 1
