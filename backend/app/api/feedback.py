from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.models.feedback import FeedbackModel
from backend.app.models.session import SessionModel
from backend.app.schemas.feedback import FeedbackCreate, FeedbackResponse

router = APIRouter(prefix="/feedback", tags=["feedback"])

@router.post("", response_model=FeedbackResponse, status_code=status.HTTP_201_CREATED, summary="Submit clinician feedback on a session")
def create_feedback(payload: FeedbackCreate, db: Session = Depends(get_db)):
    session = db.query(SessionModel).filter(SessionModel.id == payload.session_id).first()
    if not session:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Session with ID '{payload.session_id}' not found"
        )

    try:
        feedback = FeedbackModel(
            session_id=payload.session_id,
            author=payload.author or "Clinician",
            message=payload.message
        )
        db.add(feedback)
        db.commit()
        db.refresh(feedback)
        return feedback
    except Exception as e:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to submit feedback: {str(e)}"
        )

@router.get("/session/{session_id}", response_model=List[FeedbackResponse], summary="Get feedback for a specific session")
def get_session_feedback(session_id: str, db: Session = Depends(get_db)):
    return db.query(FeedbackModel).filter(FeedbackModel.session_id == session_id).order_by(FeedbackModel.created_at.desc()).all()

@router.get("", response_model=List[FeedbackResponse], summary="List all recent clinician feedback")
def list_feedback(db: Session = Depends(get_db)):
    return db.query(FeedbackModel).order_by(FeedbackModel.created_at.desc()).limit(100).all()
