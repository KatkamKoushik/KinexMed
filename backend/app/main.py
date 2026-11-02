import os
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from backend.app.core.database import Base, engine
from backend.app.models.session import SessionModel, RepModel, EvidenceEventModel
from backend.app.models.plan import PlanModel, PlanExerciseModel
from backend.app.models.feedback import FeedbackModel
from backend.app.api.endpoints import router as sessions_router
from backend.app.api.plans import router as plans_router
from backend.app.api.feedback import router as feedback_router
from backend.app.api.reports import router as reports_router

logger = logging.getLogger("kinexmed.api")

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize database schema safely during application startup lifecycle
    logger.info("Initializing KinexMed SQLite schema via lifespan event")
    Base.metadata.create_all(bind=engine)
    yield

app = FastAPI(
    title="KinexMed Rehabilitation API",
    description="Real-time on-device session recording, clinician dashboard, plans, and feedback backend.",
    version="1.0.0",
    lifespan=lifespan
)

# 1. P0 Security Fix: Replace wildcard with explicit trusted origins
# Modern browsers reject CORS preflight when allow_origins=["*"] is paired with allow_credentials=True.
DEFAULT_ALLOWED_ORIGINS = [
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    "http://127.0.0.1:8000",
    "http://localhost:8000"
]
env_origins = os.getenv("ALLOWED_ORIGINS", "")
allowed_origins = [o.strip() for o in env_origins.split(",") if o.strip()] if env_origins else DEFAULT_ALLOWED_ORIGINS

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)

# 2. P1 Security Fix: Enforce bounded request payload size (5 MB ceiling)
MAX_REQUEST_SIZE_BYTES = 5 * 1024 * 1024  # 5 MB

@app.middleware("http")
async def enforce_payload_size_limit(request: Request, call_next):
    content_length = request.headers.get("content-length")
    if content_length and int(content_length) > MAX_REQUEST_SIZE_BYTES:
        return JSONResponse(
            status_code=413,
            content={"detail": "Payload size exceeds maximum allowed limit of 5MB."}
        )
    return await call_next(request)


app.include_router(sessions_router)
app.include_router(plans_router)
app.include_router(feedback_router)
app.include_router(reports_router)

@app.get("/", tags=["health"])
def health_check():
    return {
        "status": "healthy",
        "service": "KinexMed API",
        "version": "1.0.0"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend.app.main:app", host="0.0.0.0", port=8000, reload=True)
