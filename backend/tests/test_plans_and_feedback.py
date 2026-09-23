import pytest
from fastapi.testclient import TestClient
from backend.app.main import app

client = TestClient(app)

def test_plan_crud_lifecycle():
    # 1. Create a plan
    plan_payload = {
        "name": "Knee Mobility Routine",
        "description": "Post-arthroscopy recovery plan",
        "is_active": True,
        "frequency_per_week": 4,
        "exercises": [
            {
                "exercise_type": "sit_to_stand",
                "target_sets": 3,
                "target_reps": 12,
                "order_index": 0
            },
            {
                "exercise_type": "squat",
                "target_sets": 3,
                "target_reps": 10,
                "order_index": 1
            }
        ]
    }
    create_res = client.post("/plans", json=plan_payload)
    assert create_res.status_code == 201
    plan_data = create_res.json()
    assert plan_data["name"] == "Knee Mobility Routine"
    assert plan_data["is_active"] is True
    assert len(plan_data["exercises"]) == 2
    plan_id = plan_data["id"]

    # 2. Get plan by ID
    get_res = client.get(f"/plans/{plan_id}")
    assert get_res.status_code == 200
    assert get_res.json()["id"] == plan_id

    # 3. List plans
    list_res = client.get("/plans")
    assert list_res.status_code == 200
    assert any(p["id"] == plan_id for p in list_res.json())

    # 4. Update plan
    update_res = client.put(f"/plans/{plan_id}", json={"name": "Updated Knee Routine", "frequency_per_week": 5})
    assert update_res.status_code == 200
    assert update_res.json()["name"] == "Updated Knee Routine"
    assert update_res.json()["frequency_per_week"] == 5

    # 5. Delete plan
    del_res = client.delete(f"/plans/{plan_id}")
    assert del_res.status_code == 204

    # Verify deleted
    get_del_res = client.get(f"/plans/{plan_id}")
    assert get_del_res.status_code == 404

def test_feedback_and_reports():
    # 1. Create a test session
    session_payload = {
        "id": "test-session-feedback-1",
        "device_id": "test-device",
        "exercise_name": "sit_to_stand",
        "started_at": 1700000000000,
        "completed_at": 1700000100000,
        "duration_seconds": 100.0,
        "total_reps": 5,
        "valid_reps": 4,
        "avg_peak_knee_angle": 150.0,
        "min_knee_angle": 85.0,
        "max_knee_angle": 160.0,
        "evidence_failure_count": 0,
        "reps": [
            {
                "rep_number": 1,
                "is_valid": True,
                "start_timestamp_ms": 1700000010000,
                "peak_timestamp_ms": 1700000020000,
                "end_timestamp_ms": 1700000030000,
                "duration_ms": 20000,
                "peak_knee_angle": 152.0,
                "start_knee_angle": 90.0,
                "end_knee_angle": 90.0,
                "feedback_message": "Good extension",
                "failure_reasons": []
            },
            {
                "rep_number": 2,
                "is_valid": False,
                "start_timestamp_ms": 1700000040000,
                "peak_timestamp_ms": 1700000050000,
                "end_timestamp_ms": 1700000060000,
                "duration_ms": 20000,
                "peak_knee_angle": 135.0,
                "start_knee_angle": 90.0,
                "end_knee_angle": 90.0,
                "feedback_message": "Incomplete extension",
                "failure_reasons": ["Incomplete extension (<148°)"]
            }
        ]
    }
    sess_res = client.post("/sessions", json=session_payload)
    assert sess_res.status_code in [200, 201]

    # 2. Add clinician feedback
    feedback_payload = {
        "session_id": "test-session-feedback-1",
        "author": "Dr. Sarah",
        "message": "Focus on driving fully through feet at standing apex."
    }
    fb_res = client.post("/feedback", json=feedback_payload)
    assert fb_res.status_code == 201
    assert fb_res.json()["author"] == "Dr. Sarah"

    # 3. Retrieve feedback for session
    fb_get_res = client.get("/feedback/session/test-session-feedback-1")
    assert fb_get_res.status_code == 200
    assert len(fb_get_res.json()) >= 1
    assert fb_get_res.json()[0]["message"] == "Focus on driving fully through feet at standing apex."

    # 4. Test Single Session Report
    rep_res = client.get("/reports/session/test-session-feedback-1")
    assert rep_res.status_code == 200
    report_data = rep_res.json()
    assert report_data["report_type"] == "SESSION_CLINICAL_REPORT"
    assert report_data["valid_reps"] == 4
    assert report_data["total_reps"] == 5

    # 5. Test Weekly and Monthly Reports
    weekly_res = client.get("/reports/weekly")
    assert weekly_res.status_code == 200
    assert "completed_sessions" in weekly_res.json()

    monthly_res = client.get("/reports/monthly")
    assert monthly_res.status_code == 200
    assert "total_therapy_hours" in monthly_res.json()
