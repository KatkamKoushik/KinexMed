from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from backend.app.core.database import Base, engine
from backend.app.models.session import SessionModel, RepModel, EvidenceEventModel
from backend.app.api.endpoints import router as sessions_router

# Initialize database schema
Base.metadata.create_all(bind=engine)

app = FastAPI(
    title="KinexMed Rehabilitation API",
    description="Real-time on-device session recording and clinician dashboard backend.",
    version="1.0.0"
)

# Configure CORS for local web dashboard and Android app
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(sessions_router)

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
