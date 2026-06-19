from pathlib import Path
from typing import Any, Dict, List

from .config import DEFAULT_SETTINGS, Settings
from .db import Database
from .errors import NotFoundError, ValidationError
from .llm import create_llm_provider
from .repository import Repository
from .runtime_factory import create_runtime
from .skills import SkillRegistry, register_builtin_skills


class ResearchService:
    def __init__(self, database_path: Path = None, settings: Settings = DEFAULT_SETTINGS):
        self.settings = settings
        self.db = Database(database_path or settings.database_path)
        self.repository = Repository(self.db)
        self.registry = SkillRegistry(self.repository)
        self.llm_provider = create_llm_provider()
        register_builtin_skills(self.registry, self.llm_provider)
        self.runtime = create_runtime(self.repository, self.registry, settings)

    def create_task(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        query = (payload.get("query") or "").strip()
        if not query:
            raise ValidationError("query is required")
        depth = payload.get("depth") or "standard"
        output_format = payload.get("output_format") or "markdown"
        task_id = self.repository.create_task(query, depth, output_format, self.settings.max_steps)
        return {"task_id": task_id, "status": "pending"}

    def run_task(self, task_id: int) -> Dict[str, Any]:
        if not self.repository.get_task(task_id):
            raise NotFoundError("task not found")
        return self.runtime.run(task_id)

    def cancel_task(self, task_id: int) -> Dict[str, Any]:
        task = self.repository.get_task(task_id)
        if not task:
            raise NotFoundError("task not found")
        if task["status"] == "completed":
            raise ValidationError("completed task cannot be cancelled", "TASK_ALREADY_COMPLETED")
        self.repository.update_task(task_id, status="cancelled", current_step="cancelled", message="任务已取消")
        return {"task_id": task_id, "status": "cancelled"}

    def retry_task(self, task_id: int) -> Dict[str, Any]:
        task = self.repository.get_task(task_id)
        if not task:
            raise NotFoundError("task not found")
        if task["status"] not in {"failed", "cancelled"}:
            raise ValidationError("only failed or cancelled tasks can be retried", "TASK_CANNOT_RETRY")
        self.repository.clear_task_artifacts(task_id)
        self.repository.update_task(task_id, status="pending", current_step=None, progress=0, message="任务已重新进入队列")
        state = self.runtime.run(task_id)
        return {"task_id": task_id, "status": self.get_task(task_id)["status"], "state": state}

    def get_task(self, task_id: int) -> Dict[str, Any]:
        task = self.repository.get_task(task_id)
        if not task:
            raise NotFoundError("task not found")
        return {
            "task_id": task["id"],
            "status": task["status"],
            "current_step": task["current_step"],
            "progress": task["progress"],
            "message": task["message"],
            "query": task["query"],
        }

    def get_report(self, task_id: int) -> Dict[str, Any]:
        if not self.repository.get_task(task_id):
            raise NotFoundError("task not found")
        report = self.repository.get_report(task_id)
        if not report:
            raise NotFoundError("report not ready", "REPORT_NOT_READY")
        return {
            "task_id": task_id,
            "title": report["title"],
            "content": report["content"],
            "format": report["format"],
        }

    def get_sources(self, task_id: int) -> List[Dict[str, Any]]:
        if not self.repository.get_task(task_id):
            raise NotFoundError("task not found")
        return self.repository.list_sources(task_id)

    def get_events(self, task_id: int) -> List[Dict[str, Any]]:
        if not self.repository.get_task(task_id):
            raise NotFoundError("task not found")
        return [
            {
                "step": step["step_name"],
                "status": step["status"],
                "error_code": step["error_code"],
                "error_message": step["error_message"],
            }
            for step in self.repository.list_steps(task_id)
        ]

    def get_task_details(self, task_id: int) -> Dict[str, Any]:
        task = self.get_task(task_id)
        report = self.repository.get_report(task_id)
        return {
            "task": task,
            "steps": self.repository.list_steps(task_id),
            "skill_calls": self.repository.list_skill_calls(task_id),
            "search_results": self.repository.list_search_results(task_id),
            "documents": [
                {
                    "id": document["id"],
                    "title": document["title"],
                    "url": document["url"],
                    "summary": document["summary"],
                    "credibility_score": document["credibility_score"],
                    "created_at": document["created_at"],
                }
                for document in self.repository.list_documents(task_id)
            ],
            "sources": self.repository.list_sources(task_id),
            "quality": self.repository.list_report_quality(task_id),
            "report": {
                "title": report["title"],
                "content": report["content"],
                "format": report["format"],
            }
            if report
            else None,
        }
