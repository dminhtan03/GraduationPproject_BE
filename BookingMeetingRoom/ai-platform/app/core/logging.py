import logging
import sys
from app.core.config import settings


def setup_logging() -> logging.Logger:
    log_level = logging.DEBUG if settings.debug else logging.INFO

    logging.basicConfig(
        level=log_level,
        format="%(asctime)s | %(levelname)-8s | %(name)s | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
        handlers=[logging.StreamHandler(sys.stdout)],
    )

    # Suppress noisy third-party loggers
    for noisy in ("httpx", "httpcore", "openai", "multipart"):
        logging.getLogger(noisy).setLevel(logging.WARNING)

    return logging.getLogger("ai_platform")


logger = setup_logging()
