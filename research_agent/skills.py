import hashlib
import html
import json
import os
import re
import time
from dataclasses import dataclass
from typing import Any, Callable, Dict, List
from urllib.parse import quote, unquote, urlparse
from urllib.request import Request, urlopen

from .errors import SkillError, ValidationError
from .repository import Repository
from .security import assert_safe_url, strip_instruction_like_text


SkillExecutor = Callable[[Dict[str, Any]], Dict[str, Any]]


@dataclass(frozen=True)
class SkillDefinition:
    name: str
    description: str
    executor: SkillExecutor
    input_schema: Dict[str, Any]
    output_schema: Dict[str, Any]
    version: str = "1.0.0"
    permission_level: str = "safe"
    timeout_seconds: int = 30
    max_retries: int = 2
    enabled: bool = True


class SkillRegistry:
    def __init__(self, repository: Repository):
        self.repository = repository
        self.executors: Dict[str, SkillExecutor] = {}

    def register(self, definition: SkillDefinition) -> None:
        self.executors[definition.name] = definition.executor
        self.repository.upsert_skill_definition(
            {
                "name": definition.name,
                "version": definition.version,
                "description": definition.description,
                "input_schema": definition.input_schema,
                "output_schema": definition.output_schema,
                "permission_level": definition.permission_level,
                "timeout_seconds": definition.timeout_seconds,
                "max_retries": definition.max_retries,
                "enabled": definition.enabled,
            }
        )

    def execute(self, task_id: int, step_id: int, name: str, input_data: Dict[str, Any]) -> Dict[str, Any]:
        db_definition = self.repository.get_skill_definition(name)
        if not db_definition or name not in self.executors:
            raise SkillError(f"Skill is disabled or not registered: {name}", "SKILL_DISABLED")
        input_schema = self._loads_schema(db_definition.get("input_schema"))
        output_schema = self._loads_schema(db_definition.get("output_schema"))
        self._validate_schema(input_data, input_schema, "input")
        started = time.perf_counter()
        attempts = max(1, int(db_definition.get("max_retries") or 0) + 1)
        timeout_seconds = int(db_definition.get("timeout_seconds") or 30)
        last_exc = None
        for attempt in range(1, attempts + 1):
            try:
                output = self.executors[name](input_data)
                elapsed_ms = int((time.perf_counter() - started) * 1000)
                if elapsed_ms > timeout_seconds * 1000:
                    raise SkillError(f"Skill timed out after {timeout_seconds}s: {name}", "SKILL_TIMEOUT")
                self._validate_schema(output, output_schema, "output")
                self.repository.record_skill_call(
                    task_id,
                    step_id,
                    name,
                    {**input_data, "_attempt": attempt},
                    output,
                    "success",
                    elapsed_ms,
                )
                return output
            except Exception as exc:
                last_exc = exc
                if attempt < attempts:
                    continue
                code = getattr(exc, "code", "INTERNAL_ERROR")
                output = {"error": {"code": code, "message": str(exc)}}
                self.repository.record_skill_call(
                    task_id,
                    step_id,
                    name,
                    {**input_data, "_attempt": attempt},
                    output,
                    "failed",
                    int((time.perf_counter() - started) * 1000),
                    code,
                    str(exc),
                )
        raise last_exc

    def _loads_schema(self, raw_schema: Any) -> Dict[str, Any]:
        if isinstance(raw_schema, dict):
            return raw_schema
        if not raw_schema:
            return {}
        try:
            import json

            return json.loads(raw_schema)
        except Exception as exc:
            raise SkillError(f"Invalid schema JSON: {exc}", "SKILL_SCHEMA_INVALID")

    def _validate_schema(self, payload: Dict[str, Any], schema: Dict[str, Any], direction: str) -> None:
        for field, expected_type in schema.items():
            if field not in payload:
                raise SkillError(f"Missing {direction} field: {field}", "SKILL_SCHEMA_VALIDATION_FAILED")
            if expected_type == "array" and not isinstance(payload[field], list):
                raise SkillError(f"Invalid {direction} field type: {field}", "SKILL_SCHEMA_VALIDATION_FAILED")
            if expected_type == "object" and not isinstance(payload[field], dict):
                raise SkillError(f"Invalid {direction} field type: {field}", "SKILL_SCHEMA_VALIDATION_FAILED")
            if expected_type == "string" and not isinstance(payload[field], str):
                raise SkillError(f"Invalid {direction} field type: {field}", "SKILL_SCHEMA_VALIDATION_FAILED")
            if expected_type == "number" and not isinstance(payload[field], (int, float)):
                raise SkillError(f"Invalid {direction} field type: {field}", "SKILL_SCHEMA_VALIDATION_FAILED")


def _parse_json_object(text: str) -> Dict[str, Any]:
    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end == -1 or end < start:
        raise ValidationError("LLM did not return a JSON object")
    return json.loads(text[start : end + 1])


def _topic_from_text(text: str) -> str:
    cleaned = str(text or "").strip()
    prefixes = ["帮我分析", "分析一下", "研究一下", "帮我研究", "请分析", "请研究"]
    for prefix in prefixes:
        cleaned = cleaned.replace(prefix, "")
    return cleaned.strip(" ：:。") or "该主题"


def _topic_profile(topic: str) -> Dict[str, Any]:
    topic_lower = topic.lower()
    if any(token in topic_lower for token in ["旅行", "旅游", "行程", "日本", "关西", "酒店", "机票"]):
        return {
            "domain": "旅行规划",
            "dimensions": ["目的地概况", "行程路线", "交通住宿", "预算结构", "风险与备选方案"],
            "players": "航司、酒店平台、当地交通、景点和旅行服务商",
            "business": "预算分配、时间成本、体验质量和风险控制",
            "opportunities": ["淡旺季错峰", "区域路线优化", "交通票券组合", "住宿位置优化"],
            "risks": ["签证和入境政策变化", "天气影响", "热门景点拥挤", "预算超支"],
            "recommendation": "建议先锁定核心城市和交通动线，再根据预算决定住宿区域，保留半天机动时间处理天气或排队波动。",
        }
    if any(token in topic_lower for token in ["笔记本", "手机", "汽车", "电脑", "对比", "购买", "预算"]):
        return {
            "domain": "消费决策",
            "dimensions": ["需求定义", "参数对比", "价格区间", "用户评价", "购买风险"],
            "players": "品牌厂商、电商平台、评测媒体和售后服务体系",
            "business": "性能、价格、可靠性、售后和长期使用成本",
            "opportunities": ["高性价比型号", "促销节点", "配置取舍", "二手或上代旗舰选择"],
            "risks": ["参数虚标", "售后差异", "价格波动", "实际体验不符合宣传"],
            "recommendation": "建议先明确刚需场景和预算上限，再用三到五个核心参数筛选候选项，最后结合售后和真实评价做决策。",
        }
    if any(token in topic_lower for token in ["竞品", "小红书", "产品", "saas", "客服", "agent", "ai"]):
        return {
            "domain": "行业/产品研究",
            "dimensions": ["行业背景", "市场现状", "主要玩家", "商业模式", "机会与风险"],
            "players": "大模型厂商、垂直 SaaS 厂商、云厂商、创业公司和系统集成商",
            "business": "订阅制、按量计费、私有化部署、项目交付和增值数据服务",
            "opportunities": ["垂直场景自动化", "存量系统智能化升级", "私有化部署", "数据分析增值服务"],
            "risks": ["模型幻觉", "数据质量不足", "系统集成复杂", "成本和效果难评估"],
            "recommendation": "建议从高频、低风险、可量化 ROI 的子场景切入，先形成可验证闭环，再扩展到更复杂的跨系统任务。",
        }
    return {
        "domain": "通用研究",
        "dimensions": ["背景与定义", "现状分析", "关键参与方", "价值链与模式", "机会与风险"],
        "players": "相关组织、平台、服务商、用户群体和监管/规则制定方",
        "business": "需求强度、供给能力、成本结构、收益模式和长期可持续性",
        "opportunities": ["需求增长", "供给升级", "效率提升", "细分场景深化"],
        "risks": ["信息不完整", "执行成本偏高", "外部环境变化", "结果不确定"],
        "recommendation": "建议先明确目标和约束，再围绕关键指标收集资料，形成可验证的小范围结论后继续加深研究。",
    }


def _http_get_text(url: str, headers: Dict[str, str] = None, timeout: int = 20) -> str:
    request_headers = {
        "User-Agent": "Mozilla/5.0 ResearchAgent/0.2",
        "Accept": "text/html,application/xhtml+xml,text/plain,application/json;q=0.9,*/*;q=0.8",
    }
    request_headers.update(headers or {})
    with urlopen(Request(url, headers=request_headers), timeout=timeout) as response:
        raw = response.read(1_500_000)
        charset = response.headers.get_content_charset() or "utf-8"
    return raw.decode(charset, "replace")


def _strip_html(raw_html: str) -> str:
    text = re.sub(r"(?is)<(script|style|noscript|svg).*?</\1>", " ", raw_html)
    title_match = re.search(r"(?is)<title[^>]*>(.*?)</title>", text)
    title = html.unescape(re.sub(r"<[^>]+>", " ", title_match.group(1))).strip() if title_match else ""
    text = re.sub(r"(?is)<br\s*/?>", "\n", text)
    text = re.sub(r"(?is)</p>|</div>|</li>|</h[1-6]>", "\n", text)
    text = re.sub(r"(?is)<[^>]+>", " ", text)
    text = html.unescape(text)
    lines = [re.sub(r"\s+", " ", line).strip() for line in text.splitlines()]
    lines = [line for line in lines if len(line) >= 20]
    body = "\n".join(lines)
    return f"{title}\n{body}" if title else body


def _extract_sentences(content: str, limit: int = 4) -> List[str]:
    cleaned = re.sub(r"\s+", " ", content)
    parts = re.split(r"(?<=[。！？.!?])\s+", cleaned)
    sentences = []
    for part in parts:
        part = part.strip()
        if 28 <= len(part) <= 220 and part not in sentences:
            sentences.append(part)
        if len(sentences) >= limit:
            break
    return sentences or [cleaned[:180]] if cleaned else []


def _bing_search(query: str, limit: int) -> List[Dict[str, Any]]:
    html_text = _http_get_text(f"https://www.bing.com/search?q={quote(query)}")
    results = []
    for match in re.finditer(r'<h2[^>]*>\s*<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>', html_text, re.S):
        url = html.unescape(match.group(1))
        title = html.unescape(re.sub(r"<[^>]+>", "", match.group(2))).strip()
        parsed = urlparse(url)
        if not parsed.scheme.startswith("http") or "bing.com" in parsed.netloc:
            continue
        if any(item["url"] == url for item in results):
            continue
        results.append(
            {
                "query": query,
                "title": title or url,
                "url": url,
                "snippet": f"来自 Bing 搜索结果：{title}",
                "source": "bing",
                "rank_no": len(results) + 1,
            }
        )
        if len(results) >= limit:
            break
    return results


def _jina_search(query: str, limit: int) -> List[Dict[str, Any]]:
    api_key = os.getenv("JINA_API_KEY")
    if not api_key:
        return []
    text = _http_get_text(
        f"https://s.jina.ai/{quote(query)}",
        headers={"Authorization": f"Bearer {api_key}", "Accept": "text/plain"},
        timeout=40,
    )
    results = []
    current = {}
    for line in text.splitlines():
        if line.startswith("Title:"):
            if current.get("url"):
                results.append(current)
            current = {"query": query, "title": line.replace("Title:", "", 1).strip(), "source": "jina-search"}
        elif line.startswith("URL Source:") or line.startswith("Url:") or line.startswith("URL:"):
            current["url"] = line.split(":", 1)[1].strip()
        elif line.startswith("Description:"):
            current["snippet"] = line.replace("Description:", "", 1).strip()
    if current.get("url"):
        results.append(current)
    for index, item in enumerate(results[:limit], start=1):
        item.setdefault("snippet", "")
        item["rank_no"] = index
    return results[:limit]


def planner_skill(input_data: Dict[str, Any], llm_provider: Any = None) -> Dict[str, Any]:
    query = (input_data.get("query") or "").strip()
    if not query:
        raise ValidationError("query is required")
    if llm_provider and getattr(llm_provider, "provider_name", "mock") != "mock":
        text = llm_provider.complete(
            [
                {"role": "system", "content": "你是研究规划 Agent。只输出 JSON，不要输出解释。"},
                {
                    "role": "user",
                    "content": (
                        "请为下面研究问题生成 JSON，字段必须包含 "
                        "research_goal:string, research_dimensions:array, "
                        "search_queries:array, expected_report_outline:array。\n"
                        f"研究问题：{query}"
                    ),
                },
            ]
        )
        result = _parse_json_object(text)
        if len(result.get("search_queries", [])) < 1:
            raise ValidationError("planner returned no search queries")
        return result
    topic = _topic_from_text(query)
    profile = _topic_profile(topic)
    dimensions = profile["dimensions"]
    search_queries = [
        f"{topic} {dimensions[0]} {dimensions[1]}",
        f"{topic} {dimensions[2]} 对比 案例",
        f"{topic} {dimensions[3]} {dimensions[4]}",
    ]
    return {
        "research_goal": f"围绕“{topic}”形成一份{profile['domain']}报告",
        "research_dimensions": dimensions,
        "search_queries": search_queries,
        "expected_report_outline": ["摘要"] + dimensions + ["结论建议", "参考来源"],
    }


def web_search_skill(input_data: Dict[str, Any]) -> Dict[str, Any]:
    queries = input_data.get("queries") or []
    limit = min(int(input_data.get("limit", 9)), 10)
    if not queries:
        raise ValidationError("queries are required")
    results: List[Dict[str, Any]] = []
    seen = set()
    for query in queries:
        candidates = _jina_search(query, limit - len(results)) or _bing_search(query, limit - len(results))
        for candidate in candidates:
            if len(results) >= limit:
                break
            url = candidate["url"]
            if url in seen:
                continue
            seen.add(url)
            candidate["rank_no"] = len(results) + 1
            results.append(candidate)
    if not results:
        raise ValidationError("web search returned no results")
    return {"results": results}


def web_crawler_skill(input_data: Dict[str, Any]) -> Dict[str, Any]:
    urls = input_data.get("urls") or []
    max_chars = int(input_data.get("max_chars", 20000))
    if not urls:
        raise ValidationError("urls are required")
    documents = []
    seen_hashes = set()
    for url in urls:
        assert_safe_url(url)
        slug = url.rstrip("/").split("/")[-1]
        try:
            api_key = os.getenv("JINA_API_KEY")
            if api_key:
                content = _http_get_text(
                    f"https://r.jina.ai/{url}",
                    headers={"Authorization": f"Bearer {api_key}", "Accept": "text/plain"},
                    timeout=35,
                )
            else:
                content = _strip_html(_http_get_text(url, timeout=20))
        except Exception:
            continue
        content = strip_instruction_like_text(content[:max_chars])
        if len(content) < 120:
            continue
        digest = hashlib.sha256(content.encode("utf-8")).hexdigest()
        if digest in seen_hashes:
            continue
        seen_hashes.add(digest)
        title = content.splitlines()[0][:160] if content.splitlines() else f"网页资料：{slug}"
        summary_sentences = _extract_sentences(content, 1)
        documents.append(
            {
                "url": url,
                "title": title,
                "content": content,
                "summary": summary_sentences[0] if summary_sentences else content[:220],
                "content_hash": digest,
                "published_at": None,
                "credibility_score": 0.8 if url.startswith("https://") else 0.65,
            }
        )
    if not documents:
        raise ValidationError("crawler fetched no readable documents")
    return {"documents": documents}


def extractor_skill(input_data: Dict[str, Any]) -> Dict[str, Any]:
    documents = input_data.get("documents") or []
    extracted = []
    for document in documents:
        facts = _extract_sentences(document.get("content", ""), 4)
        summary = document.get("summary") or (facts[0] if facts else "")
        extracted.append(
            {
                "document_id": document.get("id"),
                "title": document["title"],
                "url": document["url"],
                "summary": summary,
                "facts": facts,
                "opportunities": [sentence for sentence in facts if any(word in sentence for word in ["机会", "增长", "提升", "优势", "价值", "需求"])][:3],
                "risks": [sentence for sentence in facts if any(word in sentence for word in ["风险", "挑战", "问题", "成本", "困难", "限制"])][:3],
            }
        )
    return {"extracted_facts": extracted}


def source_filter_skill(input_data: Dict[str, Any]) -> Dict[str, Any]:
    documents = input_data.get("documents") or []
    filtered = []
    seen_hashes = set()
    for document in documents:
        content = document.get("content", "")
        digest = document.get("content_hash")
        if not content or len(content) < 80:
            continue
        if digest in seen_hashes:
            continue
        seen_hashes.add(digest)
        score = float(document.get("credibility_score") or 0.5)
        if document.get("url", "").startswith("https://"):
            score += 0.1
        if len(content) > 200:
            score += 0.05
        filtered.append({**document, "credibility_score": min(score, 1.0)})
    filtered.sort(key=lambda item: item.get("credibility_score", 0), reverse=True)
    return {"documents": filtered, "removed_count": len(documents) - len(filtered)}


def analyzer_skill(input_data: Dict[str, Any], llm_provider: Any = None) -> Dict[str, Any]:
    facts = input_data.get("extracted_facts") or []
    dimensions = input_data.get("research_dimensions") or []
    if llm_provider and getattr(llm_provider, "provider_name", "mock") != "mock":
        text = llm_provider.complete(
            [
                {"role": "system", "content": "你是行业研究分析 Agent。只输出 JSON，不要输出解释。"},
                {
                    "role": "user",
                    "content": (
                        "请基于资料输出 JSON，顶层字段为 analysis，analysis 内包含 "
                        "dimensions, consensus, opportunities, risks, market_judgement, recommendation。\n"
                        f"研究维度：{json.dumps(dimensions, ensure_ascii=False)}\n"
                        f"资料：{json.dumps(facts, ensure_ascii=False)[:12000]}"
                    ),
                },
            ]
        )
        return _parse_json_object(text)
    opportunities = sorted({item for fact in facts for item in fact.get("opportunities", [])})
    risks = sorted({item for fact in facts for item in fact.get("risks", [])})
    consensus = sorted({item for fact in facts for item in fact.get("facts", [])})
    topic_text = " ".join(fact.get("summary", "") for fact in facts[:2]) or "该主题"
    profile = _topic_profile(topic_text)
    return {
        "analysis": {
            "dimensions": dimensions,
            "consensus": consensus,
            "opportunities": opportunities,
            "risks": risks,
            "market_judgement": f"从现有资料看，该主题属于{profile['domain']}，核心判断应围绕{profile['business']}展开。",
            "recommendation": profile["recommendation"],
        }
    }


def citation_manager_skill(input_data: Dict[str, Any]) -> Dict[str, Any]:
    facts = input_data.get("extracted_facts") or []
    citations = []
    seen = set()
    for fact in facts:
        url = fact.get("url")
        if not url or url in seen:
            continue
        seen.add(url)
        citations.append(
            {
                "document_id": fact.get("document_id"),
                "url": url,
                "title": fact.get("title"),
                "quote_text": fact.get("summary"),
            }
        )
    return {"citations": citations}


def report_writer_skill(input_data: Dict[str, Any], llm_provider: Any = None) -> Dict[str, Any]:
    query = input_data["query"]
    plan = input_data["plan"]
    analysis = input_data.get("analysis") or {}
    citations = input_data.get("citations") or []
    if llm_provider and getattr(llm_provider, "provider_name", "mock") != "mock":
        text = llm_provider.complete(
            [
                {"role": "system", "content": "你是研究报告写作 Agent。只输出 JSON，不要输出解释。"},
                {
                    "role": "user",
                    "content": (
                        "请生成 Markdown 研究报告，并输出 JSON，字段为 title, content, format。"
                        "content 必须包含摘要、市场现状、机会判断、风险分析、结论建议、参考来源。\n"
                        f"研究问题：{query}\n"
                        f"研究计划：{json.dumps(plan, ensure_ascii=False)}\n"
                        f"分析结果：{json.dumps(analysis, ensure_ascii=False)}\n"
                        f"引用来源：{json.dumps(citations, ensure_ascii=False)}"
                    ),
                },
            ],
            temperature=0.3,
        )
        result = _parse_json_object(text)
        result.setdefault("format", "markdown")
        return result
    title = f"{query}研究报告"
    profile = _topic_profile(query)
    sections = [
        f"# {title}",
        "## 摘要",
        f"本报告围绕“{query}”进行初步深度研究，结合公开资料梳理{profile['domain']}的关键背景、参与方、价值判断、机会和风险。",
        "## 研究背景",
        f"“{query}”的研究重点不只是信息罗列，而是判断需求强度、供给能力、约束条件和可执行路径。",
        "## 市场现状",
        f"当前相关参与方包括{profile['players']}，不同参与方在资源、成本、服务能力和交付方式上存在差异。",
        "## 主要玩家",
        profile["players"],
        "## 商业模式",
        profile["business"],
        "## 机会判断",
        analysis.get("market_judgement", "短期机会在知识库问答、工单分流和质检；中长期机会在跨系统任务执行和行业流程自动化。"),
        "### 机会列表",
        "\n".join(f"- {item}" for item in analysis.get("opportunities", [])) or "- 暂无明确机会",
        "## 风险分析",
        "\n".join(f"- {item}" for item in analysis.get("risks", [])) or "- 核心风险包括模型幻觉、数据权限、系统集成复杂、效果评估困难和推理成本波动。",
        "## 结论建议",
        analysis.get("recommendation", "建议从低风险、高频、可评估的客服子流程切入，先完成知识库问答和人工协同，再扩展到跨系统执行。"),
        "## 参考来源",
    ]
    if citations:
        for index, source in enumerate(citations, start=1):
            sections.append(f"{index}. [{source['title']}]({source['url']}) - {source['quote_text']}")
    else:
        sections.append("暂无可用来源。")
    sections.append("\n## 研究计划覆盖")
    for dimension in plan.get("research_dimensions", []):
        sections.append(f"- {dimension}")
    return {"title": title, "content": "\n\n".join(sections), "format": "markdown"}


def quality_check_skill(input_data: Dict[str, Any]) -> Dict[str, Any]:
    report = input_data.get("report") or {}
    content = report.get("content", "")
    citations = input_data.get("citations") or []
    issues = []
    required_sections = ["摘要", "市场现状", "机会判断", "风险分析", "结论建议", "参考来源"]
    for section in required_sections:
        if f"## {section}" not in content:
            issues.append(f"缺少章节：{section}")
    if not citations:
        issues.append("缺少引用来源")
    if len(content) < 500:
        issues.append("报告内容过短")
    score = max(0.0, 1.0 - len(issues) * 0.2)
    return {
        "quality_score": score,
        "issues": issues,
        "status": "pass" if score >= 0.7 else "failed",
    }


def _bind_llm(executor: Callable[..., Dict[str, Any]], llm_provider: Any) -> SkillExecutor:
    return lambda input_data: executor(input_data, llm_provider)


def register_builtin_skills(registry: SkillRegistry, llm_provider: Any = None) -> None:
    registry.register(
        SkillDefinition(
            "planner",
            "生成研究目标、研究维度和搜索关键词",
            _bind_llm(planner_skill, llm_provider),
            {"query": "string"},
            {
                "research_goal": "string",
                "research_dimensions": "array",
                "search_queries": "array",
                "expected_report_outline": "array",
            },
        )
    )
    registry.register(
        SkillDefinition(
            "web_search",
            "根据关键词搜索公开网页资料",
            web_search_skill,
            {"queries": "array", "limit": "number"},
            {"results": "array"},
        )
    )
    registry.register(
        SkillDefinition(
            "web_crawler",
            "抓取网页正文和基础元数据",
            web_crawler_skill,
            {"urls": "array", "max_chars": "number"},
            {"documents": "array"},
        )
    )
    registry.register(
        SkillDefinition(
            "extractor",
            "从网页资料中抽取事实、机会和风险",
            extractor_skill,
            {"documents": "array"},
            {"extracted_facts": "array"},
        )
    )
    registry.register(
        SkillDefinition(
            "source_filter",
            "过滤重复、低质量和可信度不足的资料",
            source_filter_skill,
            {"documents": "array"},
            {"documents": "array", "removed_count": "number"},
        )
    )
    registry.register(
        SkillDefinition(
            "analyzer",
            "对多来源资料进行综合分析",
            _bind_llm(analyzer_skill, llm_provider),
            {"extracted_facts": "array", "research_dimensions": "array"},
            {"analysis": "object"},
        )
    )
    registry.register(
        SkillDefinition(
            "citation_manager",
            "维护报告结论和资料来源的引用关系",
            citation_manager_skill,
            {"extracted_facts": "array"},
            {"citations": "array"},
        )
    )
    registry.register(
        SkillDefinition(
            "report_writer",
            "生成结构化 Markdown 研究报告",
            _bind_llm(report_writer_skill, llm_provider),
            {"query": "string", "plan": "object", "analysis": "object", "citations": "array"},
            {"title": "string", "content": "string", "format": "string"},
        )
    )
    registry.register(
        SkillDefinition(
            "quality_check",
            "检查报告完整性、引用完整性和主题相关性",
            quality_check_skill,
            {"report": "object", "citations": "array"},
            {"quality_score": "number", "issues": "array", "status": "string"},
        )
    )
