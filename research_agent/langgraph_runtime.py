from typing import Any, Dict, List, TypedDict

from langgraph.graph import END, START, StateGraph

from .config import DEFAULT_SETTINGS, Settings
from .errors import AgentError
from .repository import Repository
from .runtime import AgentRuntime
from .skills import SkillRegistry


class ResearchState(TypedDict, total=False):
    task_id: int
    user_query: str
    research_plan: Dict[str, Any]
    search_results: List[Dict[str, Any]]
    crawled_documents: List[Dict[str, Any]]
    filtered_documents: List[Dict[str, Any]]
    extracted_facts: List[Dict[str, Any]]
    analysis: Dict[str, Any]
    citations: List[Dict[str, Any]]
    report: Dict[str, Any]
    quality: Dict[str, Any]
    errors: List[Dict[str, Any]]
    step_count: int


class LangGraphResearchRuntime(AgentRuntime):
    def __init__(self, repository: Repository, registry: SkillRegistry, settings: Settings = DEFAULT_SETTINGS):
        super().__init__(repository, registry, settings)
        self.graph = self._build_graph()

    def run(self, task_id: int) -> Dict[str, Any]:
        task = self.repository.get_task(task_id)
        if not task:
            raise AgentError("task not found", "TASK_NOT_FOUND")
        if task["status"] == "cancelled":
            return {"task_id": task_id, "errors": [{"code": "TASK_CANCELLED", "message": "task cancelled"}]}
        state: ResearchState = {
            "task_id": task_id,
            "user_query": task["query"],
            "step_count": 0,
            "errors": [],
        }
        try:
            result = self.graph.invoke(state)
            self.repository.update_task(task_id, status="completed", current_step="completed", progress=100, message="报告生成完成")
            return dict(result)
        except Exception as exc:
            code = getattr(exc, "code", "INTERNAL_ERROR")
            state["errors"].append({"code": code, "message": str(exc)})
            if code == "TASK_CANCELLED":
                self.repository.update_task(task_id, status="cancelled", current_step="cancelled", message="任务已取消")
            else:
                self.repository.update_task(task_id, status="failed", current_step="failed", message=str(exc))
            return dict(state)

    def _build_graph(self):
        graph = StateGraph(ResearchState)
        graph.add_node("planner", self._planner_node)
        graph.add_node("search", self._search_node)
        graph.add_node("crawl", self._crawl_node)
        graph.add_node("source_filter", self._source_filter_node)
        graph.add_node("extract", self._extract_node)
        graph.add_node("analyze", self._analyze_node)
        graph.add_node("cite", self._cite_node)
        graph.add_node("report", self._report_node)
        graph.add_node("quality", self._quality_node)
        graph.add_edge(START, "planner")
        graph.add_edge("planner", "search")
        graph.add_edge("search", "crawl")
        graph.add_edge("crawl", "source_filter")
        graph.add_edge("source_filter", "extract")
        graph.add_edge("extract", "analyze")
        graph.add_edge("analyze", "cite")
        graph.add_edge("cite", "report")
        graph.add_edge("report", "quality")
        graph.add_edge("quality", END)
        return graph.compile()

    def _touch(self, state: ResearchState) -> int:
        step_count = int(state.get("step_count", 0)) + 1
        if step_count > self.settings.max_steps:
            raise AgentError("Agent exceeded max_steps", "MAX_STEPS_EXCEEDED")
        self._guard_cancelled(state["task_id"])
        return step_count

    def _planner_node(self, state: ResearchState) -> Dict[str, Any]:
        step_count = self._touch(state)
        self.repository.update_task(state["task_id"], status="planning", current_step="planner", progress=10, message="正在生成研究计划")
        return {
            "step_count": step_count,
            "research_plan": self._run_step(state["task_id"], "planner", {"query": state["user_query"]}),
        }

    def _search_node(self, state: ResearchState) -> Dict[str, Any]:
        step_count = self._touch(state)
        self.repository.update_task(state["task_id"], status="searching", current_step="web_search", progress=30, message="正在搜索资料")
        output = self._run_step(
            state["task_id"],
            "web_search",
            {"queries": state["research_plan"]["search_queries"], "limit": self.settings.max_search_results},
        )
        for result in output["results"]:
            self.repository.save_search_result(state["task_id"], result)
        return {"step_count": step_count, "search_results": output["results"]}

    def _crawl_node(self, state: ResearchState) -> Dict[str, Any]:
        step_count = self._touch(state)
        self.repository.update_task(state["task_id"], status="crawling", current_step="web_crawler", progress=50, message="正在抓取网页")
        urls = [item["url"] for item in state["search_results"][: self.settings.max_crawl_pages]]
        output = self._run_step(
            state["task_id"],
            "web_crawler",
            {"urls": urls, "max_chars": self.settings.max_page_chars, "topic": state["user_query"]},
        )
        documents = []
        for document in output["documents"]:
            document_id = self.repository.save_document(state["task_id"], document)
            documents.append({**document, "id": document_id})
        return {"step_count": step_count, "crawled_documents": documents}

    def _source_filter_node(self, state: ResearchState) -> Dict[str, Any]:
        step_count = self._touch(state)
        self.repository.update_task(state["task_id"], status="filtering", current_step="source_filter", progress=60, message="正在过滤资料")
        output = self._run_step(state["task_id"], "source_filter", {"documents": state["crawled_documents"]})
        return {"step_count": step_count, "filtered_documents": output["documents"]}

    def _extract_node(self, state: ResearchState) -> Dict[str, Any]:
        step_count = self._touch(state)
        self.repository.update_task(state["task_id"], status="extracting", current_step="extractor", progress=70, message="正在抽取事实")
        output = self._run_step(state["task_id"], "extractor", {"documents": state["filtered_documents"]})
        for fact_group in output["extracted_facts"]:
            for fact in fact_group.get("facts", []):
                self.repository.save_extracted_fact(
                    state["task_id"],
                    {"document_id": fact_group.get("document_id"), "fact_type": "fact", "content": fact, "confidence": 0.8},
                )
        return {"step_count": step_count, "extracted_facts": output["extracted_facts"]}

    def _analyze_node(self, state: ResearchState) -> Dict[str, Any]:
        step_count = self._touch(state)
        self.repository.update_task(state["task_id"], status="analyzing", current_step="analyzer", progress=78, message="正在综合分析")
        output = self._run_step(
            state["task_id"],
            "analyzer",
            {"extracted_facts": state["extracted_facts"], "research_dimensions": state["research_plan"]["research_dimensions"]},
        )
        return {"step_count": step_count, "analysis": output["analysis"]}

    def _cite_node(self, state: ResearchState) -> Dict[str, Any]:
        step_count = self._touch(state)
        self.repository.update_task(state["task_id"], status="citing", current_step="citation_manager", progress=84, message="正在整理引用")
        output = self._run_step(state["task_id"], "citation_manager", {"extracted_facts": state["extracted_facts"]})
        for citation in output["citations"]:
            self.repository.save_citation(state["task_id"], citation)
        return {"step_count": step_count, "citations": output["citations"]}

    def _report_node(self, state: ResearchState) -> Dict[str, Any]:
        step_count = self._touch(state)
        self.repository.update_task(state["task_id"], status="writing", current_step="report_writer", progress=90, message="正在生成报告")
        output = self._run_step(
            state["task_id"],
            "report_writer",
            {
                "query": state["user_query"],
                "plan": state["research_plan"],
                "analysis": state["analysis"],
                "citations": state["citations"],
            },
        )
        report_id = self.repository.save_report(state["task_id"], output["title"], output["content"], output["format"])
        self.repository.update_citation_report(state["task_id"], report_id)
        return {"step_count": step_count, "report": {**output, "id": report_id}}

    def _quality_node(self, state: ResearchState) -> Dict[str, Any]:
        step_count = self._touch(state)
        self.repository.update_task(state["task_id"], status="quality_checking", current_step="quality_check", progress=96, message="正在检查报告质量")
        output = self._run_step(state["task_id"], "quality_check", {"report": state["report"], "citations": state["citations"]})
        self.repository.save_report_quality(state["task_id"], state["report"]["id"], output)
        if output["status"] != "pass":
            raise AgentError("report quality check failed", "REPORT_QUALITY_FAILED")
        return {"step_count": step_count, "quality": output}
