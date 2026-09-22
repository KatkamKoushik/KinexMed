from fastapi.testclient import TestClient
import pytest
from backend.app.main import app
from backend.app.core.database import Base, engine

client = TestClient(app)

@pytest.fixture(autouse=True)
def setup_and_teardown_db():
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)

def test_health_check():
    response = client.get("/")
    assert response.status_code == 200
    assert response.json()["status"] == "healthy"

def test_empty_sessions_list():
    """Verifies that initially no sessions exist and no fake data is generated."""
    response = client.get("/sessions")
    assert response.status_code == 200
    assert response.json() == []

def test_create_and_retrieve_session():
    """Verifies recording a real completed phone session with actual kinematics."""
    session_payload = {
        "id": "session-real-12345",
        "device_id": "iqoo-test-device",
        "exercise_name": "squat",
        "started_at": 1726960000000,
        "completed_at": 1726960045000,
        "duration_seconds": 45.0,
        "total_reps": 3,
        "valid_reps": 2,
        "avg_peak_knee_angle": 88.5,
        "min_knee_angle": 85.0,
        "max_knee_angle": 172.0,
        "evidence_failure_count": 1,
        "reps": [
            {
                "rep_number": 1,
                "is_valid": True,
                "start_timestamp_ms": 1726960005000,
                "peak_timestamp_ms": 1726960007500,
                "end_timestamp_ms": 1726960010000,
                "duration_ms": 5000,
                "peak_knee_angle": 87.0,
                "start_knee_angle": 170.0,
                "end_knee_angle": 168.0,
                "feedback_message": "Rep 1 completed. Excellent depth!",
                "failure_reasons": []
            },
            {
                "rep_number": 2,
                "is_valid": True,
                "start_timestamp_ms": 1726960015000,
                "peak_timestamp_ms": 1726960017000,
                "end_timestamp_ms": 1726960019500,
                "duration_ms": 4500,
                "peak_knee_angle": 90.0,
                "start_knee_angle": 168.0,
                "end_knee_angle": 169.0,
                "feedback_message": "Rep 2 completed. Excellent depth!",
                "failure_reasons": []
            },
            {
                "rep_number": 3,
                "is_valid": False,
                "start_timestamp_ms": 1726960025000,
                "peak_timestamp_ms": 1726960026500,
                "end_timestamp_ms": 1726960028000,
                "duration_ms": 3000,
                "peak_knee_angle": 108.0,
                "start_knee_angle": 169.0,
                "end_knee_angle": 167.0,
                "feedback_message": "Squat deeper next time. Reached 108°.",
                "failure_reasons": ["Insufficient depth: reached 108°, target was 100°"]
            }
        ]
    }

    # 1. Post real session
    post_res = client.post("/sessions", json=session_payload)
    assert post_res.status_code == 201, post_res.text
    created = post_res.json()
    assert created["id"] == "session-real-12345"
    assert created["total_reps"] == 3
    assert created["valid_reps"] == 2
    assert len(created["reps"]) == 3

    # 2. Get sessions list
    list_res = client.get("/sessions")
    assert list_res.status_code == 200
    sessions = list_res.json()
    assert len(sessions) == 1
    assert sessions[0]["id"] == "session-real-12345"

    # 3. Get single session detail
    detail_res = client.get("/sessions/session-real-12345")
    assert detail_res.status_code == 200
    detail = detail_res.json()
    assert detail["id"] == "session-real-12345"
    assert len(detail["reps"]) == 3
    assert detail["reps"][2]["is_valid"] is False
    assert "Insufficient depth" in detail["reps"][2]["failure_reasons"][0]

    # 4. Phase 13 grounded summary check
    summary_res = client.get("/sessions/session-real-12345/summary")
    assert summary_res.status_code == 200
    summary_data = summary_res.json()
    assert "summary" in summary_data
    assert "Non-diagnostic" in summary_data["disclaimer"]
    assert "3 squat attempts" in summary_data["summary"]

def test_get_nonexistent_session():
    response = client.get("/sessions/nonexistent-uuid")
    assert response.status_code == 404
