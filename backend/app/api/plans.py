from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from backend.app.core.database import get_db
from backend.app.models.plan import PlanModel, PlanExerciseModel
from backend.app.schemas.plan import PlanCreate, PlanUpdate, PlanResponse

router = APIRouter(prefix="/plans", tags=["plans"])

@router.get("", response_model=List[PlanResponse], summary="List all exercise plans")
def list_plans(
    active_only: bool = Query(False, description="Filter for active plans only"),
    db: Session = Depends(get_db)
):
    query = db.query(PlanModel)
    if active_only:
        query = query.filter(PlanModel.is_active == True)
    return query.order_by(PlanModel.created_at.desc()).all()

@router.get("/{plan_id}", response_model=PlanResponse, summary="Get exercise plan by ID")
def get_plan(plan_id: str, db: Session = Depends(get_db)):
    plan = db.query(PlanModel).filter(PlanModel.id == plan_id).first()
    if not plan:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Plan with ID '{plan_id}' not found"
        )
    return plan

@router.post("", response_model=PlanResponse, status_code=status.HTTP_201_CREATED, summary="Create a new exercise plan")
def create_plan(payload: PlanCreate, db: Session = Depends(get_db)):
    try:
        if payload.is_active:
            # Deactivate other active plans if this one is marked active
            db.query(PlanModel).filter(PlanModel.is_active == True).update({"is_active": False})

        plan_data = payload.model_dump(exclude={"exercises", "id"})
        plan_id = payload.id
        db_plan = PlanModel(id=plan_id, **plan_data) if plan_id else PlanModel(**plan_data)
        db.add(db_plan)
        db.flush()

        for idx, ex in enumerate(payload.exercises):
            ex_data = ex.model_dump(exclude={"id", "order_index"})
            db_ex = PlanExerciseModel(
                plan_id=db_plan.id,
                order_index=ex.order_index if ex.order_index is not None else idx,
                **ex_data
            )
            db.add(db_ex)

        db.commit()
        db.refresh(db_plan)
        return db_plan
    except Exception as e:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to create exercise plan: {str(e)}"
        )

@router.put("/{plan_id}", response_model=PlanResponse, summary="Update an existing exercise plan")
def update_plan(plan_id: str, payload: PlanUpdate, db: Session = Depends(get_db)):
    plan = db.query(PlanModel).filter(PlanModel.id == plan_id).first()
    if not plan:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Plan with ID '{plan_id}' not found"
        )

    try:
        if payload.name is not None:
            plan.name = payload.name
        if payload.description is not None:
            plan.description = payload.description
        if payload.frequency_per_week is not None:
            plan.frequency_per_week = payload.frequency_per_week

        if payload.is_active is not None:
            if payload.is_active:
                db.query(PlanModel).filter(PlanModel.id != plan_id, PlanModel.is_active == True).update({"is_active": False})
            plan.is_active = payload.is_active

        if payload.exercises is not None:
            db.query(PlanExerciseModel).filter(PlanExerciseModel.plan_id == plan_id).delete()
            for idx, ex in enumerate(payload.exercises):
                ex_data = ex.model_dump(exclude={"id", "order_index"})
                db_ex = PlanExerciseModel(
                    plan_id=plan_id,
                    order_index=ex.order_index if ex.order_index is not None else idx,
                    **ex_data
                )
                db.add(db_ex)

        db.commit()
        db.refresh(plan)
        return plan
    except Exception as e:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to update exercise plan: {str(e)}"
        )

@router.delete("/{plan_id}", status_code=status.HTTP_204_NO_CONTENT, summary="Delete an exercise plan")
def delete_plan(plan_id: str, db: Session = Depends(get_db)):
    plan = db.query(PlanModel).filter(PlanModel.id == plan_id).first()
    if not plan:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Plan with ID '{plan_id}' not found"
        )
    db.delete(plan)
    db.commit()
    return None
