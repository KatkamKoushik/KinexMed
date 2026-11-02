import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Integer, Boolean, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from backend.app.core.database import Base

def generate_uuid():
    return str(uuid.uuid4())

class PlanModel(Base):
    __tablename__ = "exercise_plans"

    id = Column(String(64), primary_key=True, default=generate_uuid, index=True)
    name = Column(String(128), nullable=False)
    description = Column(String(512), default="")
    is_active = Column(Boolean, default=True, index=True)
    frequency_per_week = Column(Integer, default=5)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), index=True)
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc))

    exercises = relationship(
        "PlanExerciseModel",
        back_populates="plan",
        cascade="all, delete-orphan",
        order_by="PlanExerciseModel.order_index"
    )

class PlanExerciseModel(Base):
    __tablename__ = "plan_exercises"

    id = Column(String(64), primary_key=True, default=generate_uuid, index=True)
    plan_id = Column(String(64), ForeignKey("exercise_plans.id", ondelete="CASCADE"), nullable=False, index=True)
    exercise_type = Column(String(64), nullable=False)
    target_sets = Column(Integer, default=3)
    target_reps = Column(Integer, default=10)
    order_index = Column(Integer, default=0)

    plan = relationship("PlanModel", back_populates="exercises")
