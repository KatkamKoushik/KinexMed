from typing import List, Optional
from datetime import datetime
from pydantic import BaseModel, ConfigDict

class PlanExerciseSchema(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: Optional[str] = None
    exercise_type: str
    target_sets: int = 3
    target_reps: int = 10
    order_index: int = 0

class PlanCreate(BaseModel):
    id: Optional[str] = None
    name: str
    description: Optional[str] = ""
    is_active: bool = True
    frequency_per_week: int = 5
    exercises: List[PlanExerciseSchema] = []

class PlanUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    is_active: Optional[bool] = None
    frequency_per_week: Optional[int] = None
    exercises: Optional[List[PlanExerciseSchema]] = None

class PlanResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    name: str
    description: Optional[str] = ""
    is_active: bool
    frequency_per_week: int
    created_at: datetime
    updated_at: Optional[datetime] = None
    exercises: List[PlanExerciseSchema] = []
