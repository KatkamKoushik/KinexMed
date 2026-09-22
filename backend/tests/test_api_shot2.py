import pytest
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.core.database import Base, engine, SessionLocal
from backend.app.models.session import SessionModel, RepModel

client = TestClient(app)

from backend.tests.conftest import test_engine, TestingSessionLocal

@pytest.fixture(autouse=True)
def clean_database():
    Base.metadata.drop_all(bind=test_engine)
    Base.metadata.create_all(bind=test_engine)
    yield

def test_health_endpoint_with_db_ping():
    """Verifies that /health verifies live database connectivity."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["database"] == "connected"
    assert data["version"] == "1.0.0"

def test_duplicate_session_idempotency():
    """
    Offline sync retries may send the same session payload multiple times.
    The backend must accept it idempotently without creating duplicate reps or failing.
    """
    payload = {
        "id": "session-idempotent-001",
        "device_id": "iqoo-test-device",
        "exercise_name": "squat",
        "started_at": 1726960000000,
        "completed_at": 1726960030000,
        "duration_seconds": 30.0,
        "total_reps": 2,
        "valid_reps": 2,
        "avg_peak_knee_angle": 88.0,
        "min_knee_angle": 86.0,
        "max_knee_angle": 170.0,
        "evidence_failure_count": 0,
        "reps": [
            {
                "rep_number": 1,
                "is_valid": True,
                "start_timestamp_ms": 1726960002000,
                "peak_timestamp_ms": 1726960005000,
                "end_timestamp_ms": 1726960008000,
                "duration_ms": 6000,
                "peak_knee_angle": 88.0,
                "start_knee_angle": 169.0,
                "end_knee_angle": 168.0,
                "feedback_message": "Good depth!",
                "failure_reasons": []
            },
            {
                "rep_number": 2,
                "is_valid": True,
                "start_timestamp_ms": 1726960012000,
                "peak_timestamp_ms": 1726960015000,
                "end_timestamp_ms": 1726960018000,
                "duration_ms": 6000,
                "peak_knee_angle": 87.0,
                "start_knee_angle": 168.0,
                "end_knee_angle": 170.0,
                "feedback_message": "Good depth!",
                "failure_reasons": []
            }
        ]
    }

    # First post
    res1 = client.post("/sessions", json=payload)
    assert res1.status_code in (200, 201)
    data1 = res1.json()
    assert data1["id"] == "session-idempotent-001"
    assert len(data1["reps"]) == 2

    # Second post of the exact same session (simulate retry)
    res2 = client.post("/sessions", json=payload)
    assert res2.status_code in (200, 201)
    data2 = res2.json()
    assert data2["id"] == "session-idempotent-001"

    # Verify database directly: must have exactly 1 session and 2 reps total, NOT 4 reps
    db = TestingSessionLocal()
    try:
        session_count = db.query(SessionModel).filter(SessionModel.id == "session-idempotent-001").count()
        rep_count = db.query(RepModel).filter(RepModel.session_id == "session-idempotent-001").count()
        assert session_count == 1
        assert rep_count == 2
    finally:
        db.close()

def test_malformed_missing_fields():
    """Missing required timestamps or duration returns HTTP 422."""
    malformed_payload = {
        "device_id": "iqoo-test",
        # missing started_at, completed_at, duration_seconds
        "total_reps": 1,
        "valid_reps": 1
    }
    response = client.post("/sessions", json=malformed_payload)
    assert response.status_code == 422

def test_malformed_inverted_session_timestamps():
    """completed_at preceding started_at must be rejected with 422."""
    payload = {
        "device_id": "iqoo-test",
        "exercise_name": "squat",
        "started_at": 1726960050000,
        "completed_at": 1726960010000,  # Inverted!
        "duration_seconds": 40.0,
        "total_reps": 1,
        "valid_reps": 1,
        "reps": []
    }
    response = client.post("/sessions", json=payload)
    assert response.status_code == 422
    assert "completed_at" in response.text

def test_malformed_valid_reps_exceeds_total():
    """valid_reps greater than total_reps must be rejected with 422."""
    payload = {
        "device_id": "iqoo-test",
        "exercise_name": "squat",
        "started_at": 1726960000000,
        "completed_at": 1726960030000,
        "duration_seconds": 30.0,
        "total_reps": 2,
        "valid_reps": 5,  # Impossible!
        "reps": []
    }
    response = client.post("/sessions", json=payload)
    assert response.status_code == 422
    assert "valid_reps" in response.text

def test_malformed_negative_duration():
    """Negative duration must be rejected with 422."""
    payload = {
        "device_id": "iqoo-test",
        "exercise_name": "squat",
        "started_at": 1726960000000,
        "completed_at": 1726960030000,
        "duration_seconds": -15.0,  # Invalid
        "total_reps": 1,
        "valid_reps": 1,
        "reps": []
    }
    response = client.post("/sessions", json=payload)
    assert response.status_code == 422

def test_malformed_impossible_joint_angle():
    """Joint angle outside human biomechanical range (0-180) returns 422."""
    payload = {
        "device_id": "iqoo-test",
        "exercise_name": "squat",
        "started_at": 1726960000000,
        "completed_at": 1726960030000,
        "duration_seconds": 30.0,
        "total_reps": 1,
        "valid_reps": 1,
        "avg_peak_knee_angle": 275.0,  # Impossible knee angle
        "reps": []
    }
    response = client.post("/sessions", json=payload)
    assert response.status_code == 422

def test_malformed_rep_chronology():
    """Repetition ending before it started returns 422."""
    payload = {
        "device_id": "iqoo-test",
        "exercise_name": "squat",
        "started_at": 1726960000000,
        "completed_at": 1726960030000,
        "duration_seconds": 30.0,
        "total_reps": 1,
        "valid_reps": 1,
        "reps": [
            {
                "rep_number": 1,
                "is_valid": True,
                "start_timestamp_ms": 1726960020000,
                "peak_timestamp_ms": 1726960015000,
                "end_timestamp_ms": 1726960010000,  # Precedes start!
                "duration_ms": 2000,
                "peak_knee_angle": 90.0,
                "start_knee_angle": 170.0,
                "end_knee_angle": 170.0
            }
        ]
    }
    response = client.post("/sessions", json=payload)
    assert response.status_code == 422
    assert "end_timestamp_ms" in response.text

def test_sessions_query_filters():
    """Verify listing sessions with device_id filter."""
    p1 = {
        "id": "session-dev-1",
        "device_id": "iqoo-phone",
        "exercise_name": "squat",
        "started_at": 1726960000000,
        "completed_at": 1726960020000,
        "duration_seconds": 20.0,
        "total_reps": 1,
        "valid_reps": 1,
        "reps": []
    }
    p2 = {
        "id": "session-dev-2",
        "device_id": "realme-phone",
        "exercise_name": "squat",
        "started_at": 1726960030000,
        "completed_at": 1726960050000,
        "duration_seconds": 20.0,
        "total_reps": 2,
        "valid_reps": 2,
        "reps": []
    }

    client.post("/sessions", json=p1)
    client.post("/sessions", json=p2)

    res_all = client.get("/sessions")
    assert len(res_all.json()) == 2

    res_iqoo = client.get("/sessions?device_id=iqoo-phone")
    assert len(res_iqoo.json()) == 1
    assert res_iqoo.json()[0]["id"] == "session-dev-1"

    res_realme = client.get("/sessions?device_id=realme-phone")
    assert len(res_realme.json()) == 1
    assert res_realme.json()[0]["id"] == "session-dev-2"

def test_multi_exercise_ingestion_and_filtering():
    """Verify storing sessions for different exercises and filtering by exercise_name."""
    exercises = ["squat", "sit_to_stand", "lunge", "calf_raise"]
    for i, ex in enumerate(exercises):
        payload = {
            "id": f"session-multi-{ex}",
            "device_id": "test-device",
            "exercise_name": ex,
            "started_at": 1726960000000 + (i * 60000),
            "completed_at": 1726960030000 + (i * 60000),
            "duration_seconds": 30.0,
            "total_reps": 3,
            "valid_reps": 2,
            "reps": []
        }
        res = client.post("/sessions", json=payload)
        assert res.status_code == 201

    res_all = client.get("/sessions")
    assert len(res_all.json()) == 4

    res_lunge = client.get("/sessions?exercise_name=lunge")
    assert len(res_lunge.json()) == 1
    assert res_lunge.json()[0]["exercise_name"] == "lunge"

    res_sts = client.get("/sessions?exercise_name=sit_to_stand")
    assert len(res_sts.json()) == 1
    assert res_sts.json()[0]["exercise_name"] == "sit_to_stand"

def test_evidence_events_ingestion_and_detail():
    """Verify evidence_events array ingestion and retrieval in detail endpoint."""
    payload = {
        "id": "session-ev-001",
        "device_id": "test-device",
        "exercise_name": "squat",
        "started_at": 1726960000000,
        "completed_at": 1726960040000,
        "duration_seconds": 40.0,
        "total_reps": 2,
        "valid_reps": 2,
        "evidence_failure_count": 1,
        "reps": [
            {
                "rep_number": 1,
                "is_valid": True,
                "start_timestamp_ms": 1726960002000,
                "peak_timestamp_ms": 1726960005000,
                "end_timestamp_ms": 1726960008000,
                "duration_ms": 6000,
                "peak_knee_angle": 88.0,
                "start_knee_angle": 170.0,
                "end_knee_angle": 169.0,
                "feedback_message": "Good depth!",
                "failure_reasons": []
            }
        ],
        "evidence_events": [
            {
                "id": "ev-001",
                "event_type": "POOR_LIGHTING_OR_OCCLUSION",
                "start_time_ms": 1726960010000,
                "end_time_ms": 1726960013000,
                "duration_ms": 3000,
                "frame_count": 45,
                "details": "Confidence below threshold"
            }
        ]
    }

    res_create = client.post("/sessions", json=payload)
    assert res_create.status_code == 201

    res_detail = client.get("/sessions/session-ev-001")
    assert res_detail.status_code == 200
    data = res_detail.json()
    assert len(data["evidence_events"]) == 1
    assert data["evidence_events"][0]["event_type"] == "POOR_LIGHTING_OR_OCCLUSION"
    assert data["evidence_events"][0]["duration_ms"] == 3000
    assert data["evidence_events"][0]["frame_count"] == 45

