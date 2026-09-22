from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import text
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.models.session import SessionModel, RepModel, EvidenceEventModel
from backend.app.schemas.session import (
    SessionCreate,
    SessionSummaryResponse,
    SessionDetailResponse
)
from backend.app.services.session_summary import summarize_session

router = APIRouter(tags=["sessions"])

@router.post(
    "/sessions",
    response_model=SessionDetailResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Record a completed exercise session (idempotent)"
)
def create_session(payload: SessionCreate, db: Session = Depends(get_db)):
    """
    Idempotent session ingestion.
    If a session with payload.id already exists (e.g. offline sync retry),
    returns the existing session without duplicating reps or creating errors.
    """
    try:
        if payload.id:
            existing = db.query(SessionModel).filter(SessionModel.id == payload.id).first()
            if existing:
                return existing

        session_data = payload.model_dump(exclude={"reps", "evidence_events"})
        db_session = SessionModel(**session_data)
        db.add(db_session)
        db.flush()

        if payload.reps:
            for rep in payload.reps:
                rep_data = rep.model_dump()
                db_rep = RepModel(
                    session_id=db_session.id,
                    **rep_data
                )
                db.add(db_rep)

        if payload.evidence_events:
            for ev in payload.evidence_events:
                ev_data = ev.model_dump()
                db_ev = EvidenceEventModel(
                    session_id=db_session.id,
                    **ev_data
                )
                db.add(db_ev)

        db.commit()
        db.refresh(db_session)
        return db_session
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Database persistence failure: {str(e)}"
        )

@router.get(
    "/sessions",
    response_model=List[SessionSummaryResponse],
    summary="Retrieve all recorded exercise sessions"
)
def list_sessions(
    exercise_name: Optional[str] = Query(None, description="Filter by exercise name (e.g. squat)"),
    device_id: Optional[str] = Query(None, description="Filter by patient device identifier"),
    limit: int = Query(100, ge=1, le=500, description="Maximum sessions to return"),
    offset: int = Query(0, ge=0, description="Offset for pagination"),
    db: Session = Depends(get_db)
):
    query = db.query(SessionModel)
    if exercise_name:
        query = query.filter(SessionModel.exercise_name == exercise_name.lower())
    if device_id:
        query = query.filter(SessionModel.device_id == device_id)

    return (
        query
        .order_by(SessionModel.created_at.desc())
        .offset(offset)
        .limit(limit)
        .all()
    )

@router.get(
    "/sessions/{session_id}",
    response_model=SessionDetailResponse,
    summary="Retrieve single session with detailed kinematics and rep breakdown"
)
def get_session(session_id: str, db: Session = Depends(get_db)):
    session = db.query(SessionModel).filter(SessionModel.id == session_id).first()
    if not session:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Session with ID '{session_id}' not found"
        )
    return session

@router.get(
    "/sessions/{session_id}/summary",
    summary="Generate grounded, non-diagnostic natural language summary of completed session"
)
async def get_session_summary(session_id: str, db: Session = Depends(get_db)):
    session = db.query(SessionModel).filter(SessionModel.id == session_id).first()
    if not session:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Session with ID '{session_id}' not found"
        )
    return await summarize_session(session)

@router.get(
    "/health",
    summary="Health check with database connectivity ping",
    tags=["health"]
)
def health_check(db: Session = Depends(get_db)):
    try:
        db.execute(text("SELECT 1"))
        db_status = "connected"
    except Exception as e:
        db_status = f"error: {str(e)}"

    is_healthy = db_status == "connected"
    if not is_healthy:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"status": "unhealthy", "database": db_status}
        )

    return {
        "status": "healthy",
        "database": db_status,
        "service": "KinexMed Rehabilitation API",
        "version": "1.0.0"
    }
