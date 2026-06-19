from typing import Any, Dict, List

from .config import DEFAULT_SETTINGS, Settings
from .errors import AgentError
from .repository import Repository
from .skills import SkillRegistry


class AgentRuntime:
    def __init__(self, repository: Repository, registry: SkillRegistry, settings: Settings = DEFAULT_SETTINGS):
        self.repository = repository
        self.registry = registry
        self.settings = settings

    def run(self, task_id: int) -> Dict[str, Any]:
        task = self.repository.get_task(task_id)
        if not task:
            raise AgentError("task not found", "TASK_NOT_FOUND")
        if task["status"] == "cancelled":
            return {"task_id": task_id, "errors": [{"code": "TASK_CANCELLED", "message": "task cancelled"}]}
        state: Dict[str, Any] = {
            "task_id": task_id,
            "user_query": task["query"],
            "step_count": 0,
            "errors": [],
        }
        try:
            self._guard_cancelled(task_id)
            self.repository.update_task(task_id, status="planning", current_step="planner", progress=10, message="正在生成研究计划")
            plan = self._run_step(task_id, "planner", {"query": task["query"]})
            state["research_plan"] = plan

            self._guard_steps(state)
            self._guard_cancelled(task_id)
            self.repository.update_task(task_id, status="searching", current_step="web_search", progress=30, message="正在搜索资料")
            search_output = self._run_step(
                task_id,
                "web_search",
                {"queries": plan["search_queries"], "limit": self.settings.max_search_results},
            )
            search_results = search_output["results"]
            for result in search_results:
                self.repository.save_search_result(task_id, result)
            state["search_results"] = search_results

            self._guard_steps(state)
            self._guard_cancelled(task_id)
            self.repository.update_task(task_id, status="crawling", current_step="web_crawler", progress=50, message="正在抓取网页")
            urls = [item["url"] for item in search_results[: self.settings.max_crawl_pages]]
            crawl_output = self._run_step(
                task_id,
                "web_crawler",
                {"urls": urls, "max_chars": self.settings.max_page_chars, "topic": task["query"]},
            )
            documents = []
            for document in crawl_output["documents"]:
                document_id = self.repository.save_document(task_id, document)
                documents.append({**document, "id": document_id})
            state["crawled_documents"] = documents

            self._guard_steps(state)
            self._guard_cancelled(task_id)
            self.repository.update_task(task_id, status="filtering", current_step="source_filter", progress=60, message="正在过滤资料")
            filter_output = self._run_step(task_id, "source_filter", {"documents": documents})
            state["filtered_documents"] = filter_output["documents"]

            self._guard_steps(state)
            self._guard_cancelled(task_id)
            self.repository.update_task(task_id, status="extracting", current_step="extractor", progress=70, message="正在抽取事实")
            extract_output = self._run_step(task_id, "extractor", {"documents": state["filtered_documents"]})
            for fact_group in extract_output["extracted_facts"]:
                for fact in fact_group.get("facts", []):
                    self.repository.save_extracted_fact(
                        task_id,
                        {
                            "document_id": fact_group.get("document_id"),
                            "fact_type": "fact",
                            "content": fact,
                            "confidence": 0.8,
                        },
                    )
            state["extracted_facts"] = extract_output["extracted_facts"]

            self._guard_steps(state)
            self._guard_cancelled(task_id)
            self.repository.update_task(task_id, status="analyzing", current_step="analyzer", progress=78, message="正在综合分析")
            analysis_output = self._run_step(
                task_id,
                "analyzer",
                {"extracted_facts": state["extracted_facts"], "research_dimensions": plan["research_dimensions"]},
            )
            state["analysis"] = analysis_output["analysis"]

            self._guard_steps(state)
            self._guard_cancelled(task_id)
            self.repository.update_task(task_id, status="citing", current_step="citation_manager", progress=84, message="正在整理引用")
            citation_output = self._run_step(task_id, "citation_manager", {"extracted_facts": state["extracted_facts"]})
            for citation in citation_output["citations"]:
                self.repository.save_citation(task_id, citation)
            state["citations"] = citation_output["citations"]

            self._guard_steps(state)
            self._guard_cancelled(task_id)
            self.repository.update_task(task_id, status="writing", current_step="report_writer", progress=90, message="正在生成报告")
            report_output = self._run_step(
                task_id,
                "report_writer",
                {
                    "query": task["query"],
                    "plan": plan,
                    "analysis": state["analysis"],
                    "citations": state["citations"],
                },
            )
            report_id = self.repository.save_report(
                task_id,
                report_output["title"],
                report_output["content"],
                report_output["format"],
            )
            self.repository.update_citation_report(task_id, report_id)
            state["report"] = {**report_output, "id": report_id}

            self._guard_steps(state)
            self._guard_cancelled(task_id)
            self.repository.update_task(task_id, status="quality_checking", current_step="quality_check", progress=96, message="正在检查报告质量")
            quality_output = self._run_step(task_id, "quality_check", {"report": report_output, "citations": state["citations"]})
            self.repository.save_report_quality(task_id, report_id, quality_output)
            state["quality"] = quality_output
            if quality_output["status"] != "pass":
                raise AgentError("report quality check failed", "REPORT_QUALITY_FAILED")
            self.repository.update_task(task_id, status="completed", current_step="completed", progress=100, message="报告生成完成")
            return state
        except Exception as exc:
            code = getattr(exc, "code", "INTERNAL_ERROR")
            state["errors"].append({"code": code, "message": str(exc)})
            if code == "TASK_CANCELLED":
                self.repository.update_task(task_id, status="cancelled", current_step="cancelled", message="任务已取消")
            else:
                self.repository.update_task(task_id, status="failed", current_step="failed", message=str(exc))
            return state

    def _run_step(self, task_id: int, skill_name: str, input_data: Dict[str, Any]) -> Dict[str, Any]:
        step_id = self.repository.start_step(task_id, skill_name, input_data)
        try:
            result = self.registry.execute(task_id, step_id, skill_name, input_data)
            self.repository.finish_step(step_id, "success", result)
            return result
        except Exception as exc:
            self.repository.finish_step(
                step_id,
                "failed",
                {},
                getattr(exc, "code", "INTERNAL_ERROR"),
                str(exc),
            )
            raise

    def _guard_steps(self, state: Dict[str, Any]) -> None:
        state["step_count"] += 1
        if state["step_count"] > self.settings.max_steps:
            raise AgentError("Agent exceeded max_steps", "MAX_STEPS_EXCEEDED")

    def _guard_cancelled(self, task_id: int) -> None:
        task = self.repository.get_task(task_id)
        if task and task["status"] == "cancelled":
            raise AgentError("task cancelled", "TASK_CANCELLED")
