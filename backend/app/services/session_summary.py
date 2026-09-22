import os
import httpx
from backend.app.models.session import SessionModel

LOCAL_LLM_URL = os.getenv("LOCAL_LLM_URL", "http://localhost:11434/api/generate")

def generate_grounded_summary_prompt(session: SessionModel) -> str:
    """
    Constructs a strictly grounded prompt containing only verified kinematic metrics.
    Instructs the local model to be non-diagnostic and avoid treatment prescription.
    """
    reps_info = []
    for r in session.reps:
        status = "Valid" if r.is_valid else "Invalid"
        reps_info.append(
            f"Rep {r.rep_number}: {status}, Peak Knee Flexion: {round(r.peak_knee_angle)}°, "
            f"Duration: {round(r.duration_ms / 1000, 1)}s. Feedback: {r.feedback_message}"
        )

    reps_str = "\n".join(reps_info)

    return f"""
You are the KinexMed session summarizer.
SAFETY GUARDRAILS:
1. You must NOT diagnose any medical or anatomical condition.
2. You must NOT prescribe treatments, exercises, or medications.
3. Only summarize the objective kinematic measurements provided below.

EXERCISE: {session.exercise_name.upper()}
TOTAL REPETITIONS: {session.total_reps}
VALID REPETITIONS: {session.valid_reps}
AVERAGE PEAK KNEE FLEXION: {round(session.avg_peak_knee_angle)}°
MIN KNEE ANGLE: {round(session.min_knee_angle)}°
TOTAL DURATION: {round(session.duration_seconds)} seconds
VISUAL EVIDENCE WARNINGS: {session.evidence_failure_count}

PER-REP BREAKDOWN:
{reps_str}

Provide a concise, encouraging, and strictly objective 2-3 sentence summary of the session's range of motion and pacing for the patient.
""".strip()

def generate_deterministic_fallback_summary(session: SessionModel) -> str:
    """
    Deterministic rule-based summary when no local LLM server is active.
    100% grounded in recorded kinematic data.
    """
    pct = round((session.valid_reps / session.total_reps) * 100) if session.total_reps > 0 else 0
    depth_evaluation = (
        "reached the target depth consistently"
        if session.avg_peak_knee_angle <= 95
        else f"reached an average flexion depth of {round(session.avg_peak_knee_angle)}°"
    )

    evidence_note = ""
    if session.evidence_failure_count > 0:
        evidence_note = f" Note: {session.evidence_failure_count} frame positioning warning(s) were flagged by the camera tracker."

    return (
        f"You completed {session.total_reps} squat attempts with {session.valid_reps} fully valid repetitions ({pct}% adherence). "
        f"Your movement {depth_evaluation} over an active duration of {round(session.duration_seconds)} seconds.{evidence_note}"
    )

async def summarize_session(session: SessionModel) -> dict:
    """
    Attempts to query a local LLM daemon (e.g. Ollama); falls back deterministically
    if the service is offline.
    """
    prompt = generate_grounded_summary_prompt(session)
    fallback = generate_deterministic_fallback_summary(session)

    try:
        async with httpx.AsyncClient(timeout=3.0) as client:
            resp = await client.post(
                LOCAL_LLM_URL,
                json={
                    "model": "gemma2:2b",
                    "prompt": prompt,
                    "stream": False
                }
            )
            if resp.status_code == 200:
                data = resp.json()
                llm_response = data.get("response", "").strip()
                if llm_response:
                    return {
                        "source": "local_llm",
                        "summary": llm_response,
                        "disclaimer": "Non-diagnostic kinematic summary."
                    }
    except Exception:
        pass

    return {
        "source": "deterministic_engine",
        "summary": fallback,
        "disclaimer": "Non-diagnostic kinematic summary."
    }
