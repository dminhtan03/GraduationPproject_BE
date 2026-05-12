import time
import functools
from typing import Callable, Type
from app.core.logging import logger


def retry(
    exceptions: tuple[Type[Exception], ...] = (Exception,),
    max_attempts: int = 3,
    delay_seconds: float = 1.0,
    backoff: float = 2.0,
) -> Callable:
    """Decorator: retry a function on specified exceptions with exponential backoff."""

    def decorator(fn: Callable) -> Callable:
        @functools.wraps(fn)
        def wrapper(*args, **kwargs):
            delay = delay_seconds
            for attempt in range(1, max_attempts + 1):
                try:
                    return fn(*args, **kwargs)
                except exceptions as exc:
                    if attempt == max_attempts:
                        logger.error(f"All {max_attempts} attempts failed for {fn.__name__}: {exc}")
                        raise
                    logger.warning(f"Attempt {attempt}/{max_attempts} failed for {fn.__name__}: {exc}. Retrying in {delay}s")
                    time.sleep(delay)
                    delay *= backoff

        return wrapper

    return decorator
