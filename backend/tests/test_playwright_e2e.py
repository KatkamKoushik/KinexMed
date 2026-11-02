import http.server
import socketserver
import threading
import time
import uuid
import pytest
import httpx
import uvicorn
from playwright.sync_api import sync_playwright
from backend.app.main import app

class StaticDistHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory="web/dist", **kwargs)

    def log_message(self, format, *args):
        # Suppress noisy HTTP server logs during test runs
        pass

@pytest.fixture(scope="module")
def e2e_environment():
    # 1. Start Static Frontend Server on port 5173
    web_port = 5173
    httpd = socketserver.TCPServer(("127.0.0.1", web_port), StaticDistHandler)
    web_thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    web_thread.start()

    # 2. Start FastAPI Backend on port 8000
    api_port = 8000
    config = uvicorn.Config(app, host="127.0.0.1", port=api_port, log_level="error")
    server = uvicorn.Server(config)
    api_thread = threading.Thread(target=server.run, daemon=True)
    api_thread.start()

    # Wait for both servers to be reachable
    web_ready = False
    api_ready = False
    for _ in range(30):
        try:
            if not web_ready:
                r_web = httpx.get(f"http://127.0.0.1:{web_port}/", timeout=1.0)
                if r_web.status_code == 200:
                    web_ready = True
            if not api_ready:
                r_api = httpx.get(f"http://127.0.0.1:{api_port}/health", timeout=1.0)
                if r_api.status_code == 200:
                    api_ready = True
            if web_ready and api_ready:
                break
        except Exception:
            pass
        time.sleep(0.2)

    assert web_ready, "Web static server failed to start within timeout"
    assert api_ready, "FastAPI uvicorn server failed to start within timeout"

    yield {
        "web_url": f"http://127.0.0.1:{web_port}",
        "api_url": f"http://127.0.0.1:{api_port}"
    }

    # Teardown
    httpd.shutdown()
    server.should_exit = True


def test_playwright_portal_branding_and_footer(e2e_environment):
    """Verifies that the Clinician Portal loads with branding, status, and non-diagnostic disclaimer."""
    web_url = e2e_environment["web_url"]

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        page.goto(web_url)

        # Brand header verification
        assert page.locator("span.brand-title").inner_text() == "KinexMed"
        assert "CLINICIAN PORTAL" in page.locator("span.brand-badge").inner_text().strip().upper()

        # Backend Active status dot
        page.wait_for_selector(".status-indicator:has-text('Backend Active')", timeout=6000)

        # Checklist 76-N003: Clinical observation disclaimer footer
        footer = page.locator("footer")
        assert footer.is_visible()
        assert "KinexMed is an assistive clinical movement observation tool" in footer.inner_text()

        browser.close()


def test_playwright_sidebar_navigation(e2e_environment):
    """Verifies tab transitions between Overview, Session List, Plans, and Reports."""
    web_url = e2e_environment["web_url"]

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        page.goto(web_url)

        # 1. Exercise Plans tab
        plans_btn = page.locator(".nav-item-btn:has-text('Exercise Plans')")
        plans_btn.click()
        page.wait_for_timeout(400)
        assert page.locator("text=Prescribed Exercise Plans").is_visible()

        # 2. Clinical Reports tab
        reports_btn = page.locator(".nav-item-btn:has-text('Clinical Reports')")
        reports_btn.click()
        page.wait_for_timeout(400)
        assert page.locator("text=Clinical Adherence Reports").is_visible()

        # 3. Session List tab
        sessions_btn = page.locator(".nav-item-btn:has-text('Session List')")
        sessions_btn.click()
        page.wait_for_timeout(400)
        assert "active" in sessions_btn.get_attribute("class")

        # 4. Return to Overview
        overview_btn = page.locator(".nav-item-btn:has-text('Overview')")
        overview_btn.click()
        page.wait_for_timeout(400)
        assert "active" in overview_btn.get_attribute("class")

        browser.close()


def test_playwright_real_time_session_ingestion(e2e_environment):
    """Simulates real on-device telemetry upload from phone and tests live portal refresh."""
    web_url = e2e_environment["web_url"]
    api_url = e2e_environment["api_url"]

    # 1. Post simulated clinical session to FastAPI
    test_session_id = f"test-e2e-{uuid.uuid4().hex[:8]}"
    now_ms = int(time.time() * 1000)
    payload = {
        "id": test_session_id,
        "device_id": "clinical-tablet-01",
        "exercise_name": "squat",
        "started_at": now_ms - 30000,
        "completed_at": now_ms,
        "duration_seconds": 30.0,
        "total_reps": 3,
        "valid_reps": 3,
        "avg_peak_knee_angle": 88.5,
        "min_knee_angle": 85.0,
        "max_knee_angle": 165.0,
        "evidence_failure_count": 0,
        "reps": [
            {
                "rep_number": 1,
                "is_valid": True,
                "start_timestamp_ms": now_ms - 25000,
                "peak_timestamp_ms": now_ms - 22000,
                "end_timestamp_ms": now_ms - 20000,
                "duration_ms": 5000,
                "peak_knee_angle": 88.0,
                "start_knee_angle": 165.0,
                "end_knee_angle": 165.0,
                "feedback_message": "Good squat depth",
                "failure_reasons": []
            },
            {
                "rep_number": 2,
                "is_valid": True,
                "start_timestamp_ms": now_ms - 18000,
                "peak_timestamp_ms": now_ms - 15000,
                "end_timestamp_ms": now_ms - 12000,
                "duration_ms": 6000,
                "peak_knee_angle": 89.0,
                "start_knee_angle": 165.0,
                "end_knee_angle": 165.0,
                "feedback_message": "Controlled ascent",
                "failure_reasons": []
            },
            {
                "rep_number": 3,
                "is_valid": True,
                "start_timestamp_ms": now_ms - 10000,
                "peak_timestamp_ms": now_ms - 7000,
                "end_timestamp_ms": now_ms - 4000,
                "duration_ms": 6000,
                "peak_knee_angle": 88.5,
                "start_knee_angle": 165.0,
                "end_knee_angle": 165.0,
                "feedback_message": "Solid clinical form",
                "failure_reasons": []
            }
        ],
        "evidence_events": []
    }

    resp = httpx.post(f"{api_url}/sessions", json=payload, timeout=5.0)
    assert resp.status_code in (200, 201), f"Session post failed: {resp.text}"

    # 2. Open Playwright browser and test UI ingestion
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        page.goto(web_url)

        # Trigger refresh button
        refresh_btn = page.locator("button.btn-sync")
        refresh_btn.click()
        page.wait_for_timeout(1000)

        # Device ID should now appear in the navigation bar
        assert "Device: clinical-tablet-01" in page.locator(".nav-actions").inner_text()

        # Click Session List and verify the ingested session is rendered
        sessions_btn = page.locator(".nav-item-btn:has-text('Session List')")
        sessions_btn.click()
        page.wait_for_timeout(500)

        # Ensure session appears in the table with exercise badge and device ID
        assert page.locator("td:has-text('clinical-tablet-01')").first.is_visible()
        assert page.locator("span.exercise-badge:has-text('SQUAT')").first.is_visible()

        browser.close()
