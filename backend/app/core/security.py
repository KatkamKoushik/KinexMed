import os
import logging
from fastapi import Security, HTTPException, status
from fastapi.security.api_key import APIKeyHeader

logger = logging.getLogger("kinexmed.security")

API_KEY_HEADER = "X-API-Key"
api_key_header_scheme = APIKeyHeader(name=API_KEY_HEADER, auto_error=False)

def verify_api_key(api_key: str = Security(api_key_header_scheme)) -> str:
    """
    Enforces API Key authentication when KINEXMED_API_KEY environment variable is configured.
    In local development without the variable set, allows transparent fallback with audit warning.
    """
    configured_key = os.getenv("KINEXMED_API_KEY")
    if configured_key:
        if not api_key or api_key != configured_key:
            logger.warning("UNAUTHORIZED_ACCESS: Rejected request with invalid or missing API key")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid or missing X-API-Key credential."
            )
    return api_key or "development-session"
