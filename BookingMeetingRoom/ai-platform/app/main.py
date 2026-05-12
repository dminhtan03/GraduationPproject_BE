from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.core.logging import logger
from app.core.exceptions import AIPlatformError
from app.api.routes import health, meeting, jobs, chat


def create_app() -> FastAPI:
    app = FastAPI(
        title=settings.app_name,
        version=settings.app_version,
        docs_url="/docs",
        redoc_url="/redoc",
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(health.router)
    app.include_router(meeting.router, prefix="/api/v1")
    app.include_router(jobs.router,    prefix="/api/v1")
    app.include_router(chat.router,    prefix="/api/v1")

    @app.exception_handler(AIPlatformError)
    async def platform_error_handler(request: Request, exc: AIPlatformError) -> JSONResponse:
        logger.error(f"AIPlatformError [{exc.code}]: {exc.message}")
        return JSONResponse(status_code=400, content={"error": exc.code, "message": exc.message})

    @app.on_event("startup")
    async def on_startup() -> None:
        logger.info(f"Starting {settings.app_name} v{settings.app_version} on port {settings.port}")

    @app.on_event("shutdown")
    async def on_shutdown() -> None:
        logger.info("Shutting down AI Platform")

    return app


app = create_app()
