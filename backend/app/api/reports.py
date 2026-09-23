from typing import Dict, Any, List
from datetime import datetime, timezone, timedelta
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.models.session import SessionModel, RepModel

router = APIRouter(prefix="/reports", tags=["reports"])

@router.get("/session/{session_id}", summary="Generate structured clinical adherence report for a single session")
def get_session_report(session_id: str, db: Session = Depends(get_db)) -> Dict[str, Any]:
    session = db.query(SessionModel).filter(SessionModel.id == session_id).first()
    if not session:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Session with ID '{session_id}' not found"
        )

    reps = db.query(RepModel).filter(RepModel.session_id == session_id).order_by(RepModel.rep_number).all()
    rejected_reps = [r for r in reps if not r.is_valid]

    rejection_breakdown = {}
    for r in rejected_reps:
        msg = r.feedback_message or "Range of motion limit"
        rejection_breakdown[msg] = rejection_breakdown.get(msg, 0) + 1

    adherence_ratio = (session.valid_reps / session.total_reps * 100.0) if session.total_reps > 0 else 0.0

    return {
        "report_type": "SESSION_CLINICAL_REPORT",
        "session_id": session.id,
        "exercise_name": session.exercise_name,
        "device_id": session.device_id,
        "started_at": session.started_at,
        "completed_at": session.completed_at,
        "duration_seconds": session.duration_seconds,
        "total_reps": session.total_reps,
        "valid_reps": session.valid_reps,
        "adherence_ratio_percent": round(adherence_ratio, 1),
        "avg_peak_angle": session.avg_peak_knee_angle,
        "min_angle": session.min_knee_angle,
        "max_angle": session.max_knee_angle,
        "evidence_alerts": session.evidence_failure_count,
        "rejection_breakdown": rejection_breakdown,
        "clinical_guardrail": "Assistive observation only; non-diagnostic."
    }

@router.get("/weekly", summary="Generate aggregated clinical adherence report for the current week")
def get_weekly_report(
    target_sessions: int = Query(5, ge=1, le=14),
    db: Session = Depends(get_db)
) -> Dict[str, Any]:
    now = datetime.now(timezone.utc)
    # Start of week (Monday)
    start_of_week = now - timedelta(days=now.weekday())
    start_of_week_dt = datetime(start_of_week.year, start_of_week.month, start_of_week.day, tzinfo=timezone.utc)
    start_ms = int(start_of_week_dt.timestamp() * 1000)

    sessions = db.query(SessionModel).filter(SessionModel.started_at >= start_ms).all()

    total_sessions = len(sessions)
    total_valid = sum(s.valid_reps for s in sessions)
    total_attempted = sum(s.total_reps for s in sessions)
    total_duration = sum(s.duration_seconds for s in sessions)
    adherence = (total_valid / total_attempted * 100.0) if total_attempted > 0 else 0.0

    exercise_dist = {}
    for s in sessions:
        exercise_dist[s.exercise_name] = exercise_dist.get(s.exercise_name, 0) + s.valid_reps

    return {
        "report_type": "WEEKLY_CLINICAL_ADHERENCE",
        "start_date": start_of_week_dt.isoformat(),
        "completed_sessions": total_sessions,
        "target_sessions": target_sessions,
        "goal_met": total_sessions >= target_sessions,
        "total_valid_reps": total_valid,
        "total_attempted_reps": total_attempted,
        "adherence_ratio_percent": round(adherence, 1),
        "total_duration_seconds": round(total_duration, 1),
        "exercise_distribution": exercise_dist
    }

@router.get("/monthly", summary="Generate aggregated clinical adherence report for the current month")
def get_monthly_report(db: Session = Depends(get_db)) -> Dict[str, Any]:
    now = datetime.now(timezone.utc)
    start_of_month_dt = datetime(now.year, now.month, 1, tzinfo=timezone.utc)
    start_ms = int(start_of_month_dt.timestamp() * 1000)

    sessions = db.query(SessionModel).filter(SessionModel.started_at >= start_ms).all()

    total_sessions = len(sessions)
    total_valid = sum(s.valid_reps for s in sessions)
    total_attempted = sum(s.total_reps for s in sessions)
    total_duration = sum(s.duration_seconds for s in sessions)
    adherence = (total_valid / total_attempted * 100.0) if total_attempted > 0 else 0.0

    exercise_dist = {}
    for s in sessions:
        exercise_dist[s.exercise_name] = exercise_dist.get(s.exercise_name, 0) + s.valid_reps

    return {
        "report_type": "MONTHLY_CLINICAL_SUMMARY",
        "month": now.strftime("%B %Y"),
        "completed_sessions": total_sessions,
        "total_valid_reps": total_valid,
        "total_attempted_reps": total_attempted,
        "adherence_ratio_percent": round(adherence, 1),
        "total_therapy_hours": round(total_duration / 3600.0, 2),
        "exercise_distribution": exercise_dist
    }
