from typing import List, Optional
from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict, field_validator, model_validator

class RepBase(BaseModel):
    rep_number: int = Field(..., ge=1, description="Sequential repetition number starting from 1")
    is_valid: bool
    start_timestamp_ms: int = Field(..., description="Epoch milliseconds when repetition began")
    peak_timestamp_ms: int = Field(..., description="Epoch milliseconds at peak joint flexion")
    end_timestamp_ms: int = Field(..., description="Epoch milliseconds when repetition completed")
    duration_ms: int = Field(..., ge=0, description="Repetition elapsed duration in milliseconds")
    peak_knee_angle: float = Field(..., ge=0.0, le=180.0, description="Peak flexion angle in degrees")
    start_knee_angle: float = Field(..., ge=0.0, le=180.0, description="Starting extension angle in degrees")
    end_knee_angle: float = Field(..., ge=0.0, le=180.0, description="Finishing extension angle in degrees")
    feedback_message: Optional[str] = ""
    failure_reasons: Optional[List[str]] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_rep_chronology(self) -> 'RepBase':
        if self.end_timestamp_ms < self.start_timestamp_ms:
            raise ValueError(
                f"end_timestamp_ms ({self.end_timestamp_ms}) cannot precede start_timestamp_ms ({self.start_timestamp_ms})"
            )
        return self

class RepCreate(RepBase):
    id: Optional[str] = None

class RepResponse(RepBase):
    id: str
    session_id: str
    model_config = ConfigDict(from_attributes=True)

class SessionBase(BaseModel):
    device_id: str = Field(default="android-device", min_length=1, max_length=64)
    exercise_name: str = Field(default="squat", min_length=1, max_length=32)
    started_at: int = Field(..., description="Session epoch start timestamp in milliseconds")
    completed_at: int = Field(..., description="Session epoch completion timestamp in milliseconds")
    duration_seconds: float = Field(..., ge=0.0, description="Active session duration in seconds")
    total_reps: int = Field(..., ge=0, description="Total repetitions attempted")
    valid_reps: int = Field(..., ge=0, description="Repetitions meeting clinical criteria")
    avg_peak_knee_angle: float = Field(default=0.0, ge=0.0, le=180.0)
    min_knee_angle: float = Field(default=0.0, ge=0.0, le=180.0)
    max_knee_angle: float = Field(default=0.0, ge=0.0, le=180.0)
    evidence_failure_count: int = Field(default=0, ge=0)

    @model_validator(mode="after")
    def validate_session_integrity(self) -> 'SessionBase':
        if self.completed_at < self.started_at:
            raise ValueError(
                f"completed_at ({self.completed_at}) cannot precede started_at ({self.started_at})"
            )
        if self.valid_reps > self.total_reps:
            raise ValueError(
                f"valid_reps ({self.valid_reps}) cannot exceed total_reps ({self.total_reps})"
            )
        return self

class EvidenceEventBase(BaseModel):
    event_type: str = Field(..., max_length=64)
    start_time_ms: int = Field(..., description="Epoch milliseconds when evidence event started")
    end_time_ms: int = Field(..., description="Epoch milliseconds when evidence event ended")
    duration_ms: int = Field(..., ge=0, description="Duration of evidence event in milliseconds")
    frame_count: int = Field(default=1, ge=1, description="Number of frames affected")
    details: Optional[str] = ""

class EvidenceEventCreate(EvidenceEventBase):
    id: Optional[str] = None

class EvidenceEventResponse(EvidenceEventBase):
    id: str
    session_id: str
    model_config = ConfigDict(from_attributes=True)

class SessionCreate(SessionBase):
    id: Optional[str] = Field(default=None, max_length=64)
    reps: Optional[List[RepCreate]] = Field(default_factory=list)
    evidence_events: Optional[List[EvidenceEventCreate]] = Field(default_factory=list)

class SessionSummaryResponse(SessionBase):
    id: str
    created_at: datetime
    model_config = ConfigDict(from_attributes=True)

class SessionDetailResponse(SessionSummaryResponse):
    reps: List[RepResponse] = Field(default_factory=list)
    evidence_events: List[EvidenceEventResponse] = Field(default_factory=list)
    model_config = ConfigDict(from_attributes=True)

