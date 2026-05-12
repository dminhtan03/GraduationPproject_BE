from app.core.logging import logger


class ReviewBuilder:
    """Wraps extracted task items with review status and issue flags."""

    def build(self, items: list[dict]) -> dict:
        review_items = []
        summary = {
            "total": len(items),
            "high_confidence": 0,
            "need_review": 0,
            "missing_assigner": 0,
            "missing_assignee": 0,
        }

        for item in items:
            issues = []
            conf = item.get("ai_confidence", 0)

            if conf >= 0.85:
                summary["high_confidence"] += 1
                status = "high_confidence"
            else:
                summary["need_review"] += 1
                status = "needs_review"
                issues.append("low_confidence")

            if not item.get("assigner_user_id"):
                summary["missing_assigner"] += 1
                issues.append("missing_assigner_user_id")

            if not item.get("assignee_user_id"):
                summary["missing_assignee"] += 1
                issues.append("missing_assignee_user_id")

            review_items.append({"item": item, "status": status, "issues": issues})

        logger.info(f"ReviewBuilder: built review for {len(items)} items")
        return {"items": review_items, "summary": summary}
