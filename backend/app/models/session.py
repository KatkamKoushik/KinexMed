import uuid
from datetime import datetime, timezone
from sqlalchemy import (
    Column,
    String,
    Float,
    Integer,
    Boolean,
    BigInteger,
    DateTime,
    ForeignKey,
    JSON
)
from sqlalchemy.orm import relationship
from backend.app.core.database import Base

def generate_uuid():
    return str(uuid.uuid4())

class SessionModel(Base):
    __tablename__ = "sessions"

    id = Column(String(64), primary_key=True, default=generate_uuid, index=True)
    device_id = Column(String(64), index=True, default="android-device")
    exercise_name = Column(String(32), index=True, default="squat")
    started_at = Column(BigInteger, nullable=False)
    completed_at = Column(BigInteger, nullable=False)
    duration_seconds = Column(Float, nullable=False)
    total_reps = Column(Integer, nullable=False, default=0)
    valid_reps = Column(Integer, nullable=False, default=0)
    avg_peak_knee_angle = Column(Float, default=0.0)
    min_knee_angle = Column(Float, default=0.0)
    max_knee_angle = Column(Float, default=0.0)
    evidence_failure_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), index=True)

    reps = relationship(
        "RepModel",
        back_populates="session",
        cascade="all, delete-orphan",
        order_by="RepModel.rep_number"
    )

    evidence_events = relationship(
        "EvidenceEventModel",
        back_populates="session",
        cascade="all, delete-orphan",
        order_by="EvidenceEventModel.start_time_ms"
    )

class RepModel(Base):
    __tablename__ = "session_reps"

    id = Column(String(64), primary_key=True, default=generate_uuid, index=True)
    session_id = Column(String(64), ForeignKey("sessions.id", ondelete="CASCADE"), nullable=False, index=True)
    rep_number = Column(Integer, nullable=False)
    is_valid = Column(Boolean, nullable=False)
    start_timestamp_ms = Column(BigInteger, nullable=False)
    peak_timestamp_ms = Column(BigInteger, nullable=False)
    end_timestamp_ms = Column(BigInteger, nullable=False)
    duration_ms = Column(BigInteger, nullable=False)
    peak_knee_angle = Column(Float, nullable=False)
    start_knee_angle = Column(Float, nullable=False)
    end_knee_angle = Column(Float, nullable=False)
    feedback_message = Column(String(255), default="")
    failure_reasons = Column(JSON, default=list)

    session = relationship("SessionModel", back_populates="reps")

class EvidenceEventModel(Base):
    __tablename__ = "session_evidence_events"

    id = Column(String(64), primary_key=True, default=generate_uuid, index=True)
    session_id = Column(String(64), ForeignKey("sessions.id", ondelete="CASCADE"), nullable=False, index=True)
    event_type = Column(String(64), nullable=False)
    start_time_ms = Column(BigInteger, nullable=False)
    end_time_ms = Column(BigInteger, nullable=False)
    duration_ms = Column(BigInteger, nullable=False)
    frame_count = Column(Integer, nullable=False, default=1)
    details = Column(String(255), default="")

    session = relationship("SessionModel", back_populates="evidence_events")

