import pytest
from fastapi.testclient import TestClient
from backend.app.main import app

client = TestClient(app)

def test_api_key_unauthorized_when_configured(monkeypatch):
    """P0 Security: When KINEXMED_API_KEY is configured, missing or invalid key must return 401."""
    monkeypatch.setenv("KINEXMED_API_KEY", "prod-secret-key-12345")

    # 1. Missing header -> 401
    resp = client.get("/sessions")
    assert resp.status_code == 401
    assert "Invalid or missing X-API-Key" in resp.json()["detail"]

    # 2. Invalid key -> 401
    resp = client.get("/sessions", headers={"X-API-Key": "wrong-key"})
    assert resp.status_code == 401

    # 3. Valid key -> 200
    resp = client.get("/sessions", headers={"X-API-Key": "prod-secret-key-12345"})
    assert resp.status_code == 200

def test_health_check_remains_public(monkeypatch):
    """Readiness/health probes must remain accessible without API keys."""
    monkeypatch.setenv("KINEXMED_API_KEY", "prod-secret-key-12345")
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "healthy"
