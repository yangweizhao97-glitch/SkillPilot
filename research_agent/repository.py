from typing import Any, Dict, List, Optional

from .db import Database, dumps, loads


class Repository:
    def __init__(self, db: Database):
        self.db = db

    def create_task(self, query: str, depth: str, output_format: str, max_steps: int) -> int:
        return self.db.execute(
            """
            INSERT INTO research_task (query, status, depth, output_format, max_steps, message)
            VALUES (?, 'pending', ?, ?, ?, '任务已创建')
            """,
            (query, depth, output_format, max_steps),
        )

    def get_task(self, task_id: int) -> Optional[Dict[str, Any]]:
        return self.db.query_one("SELECT * FROM research_task WHERE id = ?", (task_id,))

    def update_task(self, task_id: int, **fields: Any) -> None:
        if not fields:
            return
        fields["updated_at"] = "CURRENT_TIMESTAMP"
        assignments = []
        values = []
        for key, value in fields.items():
            if value == "CURRENT_TIMESTAMP":
                assignments.append(f"{key} = CURRENT_TIMESTAMP")
            else:
                assignments.append(f"{key} = ?")
                values.append(value)
        values.append(task_id)
        self.db.execute(f"UPDATE research_task SET {', '.join(assignments)} WHERE id = ?", values)

    def clear_task_artifacts(self, task_id: int) -> None:
        for table in [
            "research_step",
            "skill_call",
            "search_result",
            "crawled_document",
            "extracted_fact",
            "citation",
            "research_report",
            "report_quality",
        ]:
            self.db.execute(f"DELETE FROM {table} WHERE task_id = ?", (task_id,))

    def start_step(self, task_id: int, name: str, input_data: Dict[str, Any]) -> int:
        return self.db.execute(
            """
            INSERT INTO research_step (task_id, step_name, status, input_json)
            VALUES (?, ?, 'running', ?)
            """,
            (task_id, name, dumps(input_data)),
        )

    def finish_step(self, step_id: int, status: str, output_data: Dict[str, Any], error_code: str = None, error_message: str = None) -> None:
        self.db.execute(
            """
            UPDATE research_step
            SET status = ?, output_json = ?, error_code = ?, error_message = ?, finished_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (status, dumps(output_data), error_code, error_message, step_id),
        )

    def list_steps(self, task_id: int) -> List[Dict[str, Any]]:
        return self.db.query_all("SELECT * FROM research_step WHERE task_id = ? ORDER BY id", (task_id,))

    def list_skill_calls(self, task_id: int) -> List[Dict[str, Any]]:
        calls = self.db.query_all("SELECT * FROM skill_call WHERE task_id = ? ORDER BY id", (task_id,))
        for call in calls:
            call["input"] = loads(call.pop("input_json", None)) or {}
            call["output"] = loads(call.pop("output_json", None)) or {}
        return calls

    def list_search_results(self, task_id: int) -> List[Dict[str, Any]]:
        return self.db.query_all("SELECT * FROM search_result WHERE task_id = ? ORDER BY id", (task_id,))

    def upsert_skill_definition(self, definition: Dict[str, Any]) -> None:
        self.db.execute(
            """
            INSERT INTO skill_definition
              (name, version, description, input_schema, output_schema, permission_level, timeout_seconds, max_retries, enabled)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(name, version) DO UPDATE SET
              description = excluded.description,
              input_schema = excluded.input_schema,
              output_schema = excluded.output_schema,
              permission_level = excluded.permission_level,
              timeout_seconds = excluded.timeout_seconds,
              max_retries = excluded.max_retries,
              enabled = excluded.enabled,
              updated_at = CURRENT_TIMESTAMP
            """,
            (
                definition["name"],
                definition.get("version", "1.0.0"),
                definition.get("description", ""),
                dumps(definition.get("input_schema", {})),
                dumps(definition.get("output_schema", {})),
                definition.get("permission_level", "safe"),
                definition.get("timeout_seconds", 30),
                definition.get("max_retries", 2),
                1 if definition.get("enabled", True) else 0,
            ),
        )

    def get_skill_definition(self, name: str) -> Optional[Dict[str, Any]]:
        return self.db.query_one(
            "SELECT * FROM skill_definition WHERE name = ? AND enabled = 1 ORDER BY id DESC LIMIT 1",
            (name,),
        )

    def record_skill_call(self, task_id: int, step_id: int, skill_name: str, input_data: Dict[str, Any], output_data: Dict[str, Any], status: str, latency_ms: int, error_code: str = None, error_message: str = None) -> None:
        self.db.execute(
            """
            INSERT INTO skill_call
              (task_id, step_id, skill_name, input_json, output_json, status, latency_ms, error_code, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (task_id, step_id, skill_name, dumps(input_data), dumps(output_data), status, latency_ms, error_code, error_message),
        )

    def save_search_result(self, task_id: int, result: Dict[str, Any]) -> int:
        return self.db.execute(
            """
            INSERT INTO search_result (task_id, query, title, url, snippet, source, rank_no)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (task_id, result.get("query"), result.get("title"), result.get("url"), result.get("snippet"), result.get("source"), result.get("rank_no")),
        )

    def save_document(self, task_id: int, document: Dict[str, Any]) -> int:
        return self.db.execute(
            """
            INSERT INTO crawled_document
              (task_id, url, title, content, summary, content_hash, published_at, credibility_score)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                task_id,
                document.get("url"),
                document.get("title"),
                document.get("content"),
                document.get("summary"),
                document.get("content_hash"),
                document.get("published_at"),
                document.get("credibility_score"),
            ),
        )

    def save_extracted_fact(self, task_id: int, fact: Dict[str, Any]) -> int:
        return self.db.execute(
            """
            INSERT INTO extracted_fact (task_id, document_id, fact_type, content, confidence)
            VALUES (?, ?, ?, ?, ?)
            """,
            (
                task_id,
                fact.get("document_id"),
                fact.get("fact_type", "summary"),
                fact.get("content"),
                fact.get("confidence", 0.8),
            ),
        )

    def list_documents(self, task_id: int) -> List[Dict[str, Any]]:
        return self.db.query_all("SELECT * FROM crawled_document WHERE task_id = ? ORDER BY id", (task_id,))

    def list_report_quality(self, task_id: int) -> List[Dict[str, Any]]:
        rows = self.db.query_all("SELECT * FROM report_quality WHERE task_id = ? ORDER BY id DESC", (task_id,))
        for row in rows:
            row["issues"] = loads(row.pop("issues_json", None)) or []
        return rows

    def save_report(self, task_id: int, title: str, content: str, output_format: str) -> int:
        return self.db.execute(
            "INSERT INTO research_report (task_id, title, content, format) VALUES (?, ?, ?, ?)",
            (task_id, title, content, output_format),
        )

    def save_citation(self, task_id: int, citation: Dict[str, Any], report_id: int = None) -> int:
        return self.db.execute(
            """
            INSERT INTO citation (task_id, report_id, document_id, url, title, quote_text)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                task_id,
                report_id,
                citation.get("document_id"),
                citation.get("url"),
                citation.get("title"),
                citation.get("quote_text"),
            ),
        )

    def update_citation_report(self, task_id: int, report_id: int) -> None:
        self.db.execute("UPDATE citation SET report_id = ? WHERE task_id = ? AND report_id IS NULL", (report_id, task_id))

    def save_report_quality(self, task_id: int, report_id: int, quality: Dict[str, Any]) -> int:
        return self.db.execute(
            """
            INSERT INTO report_quality (task_id, report_id, quality_score, issues_json, status)
            VALUES (?, ?, ?, ?, ?)
            """,
            (
                task_id,
                report_id,
                quality.get("quality_score"),
                dumps(quality.get("issues", [])),
                quality.get("status"),
            ),
        )

    def get_report(self, task_id: int) -> Optional[Dict[str, Any]]:
        return self.db.query_one("SELECT * FROM research_report WHERE task_id = ? ORDER BY id DESC LIMIT 1", (task_id,))

    def list_sources(self, task_id: int) -> List[Dict[str, Any]]:
        citations = self.db.query_all(
            "SELECT title, url, quote_text AS summary, created_at FROM citation WHERE task_id = ? ORDER BY id",
            (task_id,),
        )
        if citations:
            return citations
        return self.db.query_all(
            "SELECT title, url, summary, credibility_score, created_at FROM crawled_document WHERE task_id = ? ORDER BY credibility_score DESC, id",
            (task_id,),
        )
