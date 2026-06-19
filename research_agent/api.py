import json
import threading
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict
from urllib.parse import urlparse

from .config import DEFAULT_SETTINGS
from .errors import AgentError, ValidationError
from .service import ResearchService


def make_service() -> ResearchService:
    return ResearchService(DEFAULT_SETTINGS.database_path)


SERVICE = make_service()


def error_body(exc: Exception) -> Dict[str, Any]:
    return {
        "error": {
            "code": getattr(exc, "code", "INTERNAL_ERROR"),
            "message": str(exc),
        }
    }


class ResearchRequestHandler(BaseHTTPRequestHandler):
    server_version = "ResearchAgent/0.1"

    def do_GET(self) -> None:
        try:
            parsed = urlparse(self.path)
            if parsed.path == "/health":
                self._json({"status": "ok"})
                return
            if parsed.path == "/" or parsed.path == "/index.html":
                self._static_index()
                return
            parts = [part for part in parsed.path.split("/") if part]
            if len(parts) == 4 and parts[:3] == ["api", "research", "tasks"]:
                self._json(SERVICE.get_task(self._parse_task_id(parts[3])))
                return
            if len(parts) == 5 and parts[:3] == ["api", "research", "tasks"] and parts[4] == "report":
                self._json(SERVICE.get_report(self._parse_task_id(parts[3])))
                return
            if len(parts) == 5 and parts[:3] == ["api", "research", "tasks"] and parts[4] == "sources":
                self._json({"sources": SERVICE.get_sources(self._parse_task_id(parts[3]))})
                return
            if len(parts) == 5 and parts[:3] == ["api", "research", "tasks"] and parts[4] == "events":
                self._events(SERVICE.get_events(self._parse_task_id(parts[3])))
                return
            if len(parts) == 5 and parts[:3] == ["api", "research", "tasks"] and parts[4] == "details":
                self._json(SERVICE.get_task_details(self._parse_task_id(parts[3])))
                return
            self._json({"error": {"code": "NOT_FOUND", "message": "not found"}}, HTTPStatus.NOT_FOUND)
        except Exception as exc:
            self._handle_error(exc)

    def do_POST(self) -> None:
        try:
            parsed = urlparse(self.path)
            parts = [part for part in parsed.path.split("/") if part]
            if parts == ["api", "research", "tasks"]:
                payload = self._read_json()
                result = SERVICE.create_task(payload)
                thread = threading.Thread(target=SERVICE.run_task, args=(result["task_id"],), daemon=True)
                thread.start()
                self._json(result, HTTPStatus.CREATED)
                return
            if len(parts) == 5 and parts[:3] == ["api", "research", "tasks"] and parts[4] == "cancel":
                self._json(SERVICE.cancel_task(self._parse_task_id(parts[3])))
                return
            if len(parts) == 5 and parts[:3] == ["api", "research", "tasks"] and parts[4] == "retry":
                self._json(SERVICE.retry_task(self._parse_task_id(parts[3])))
                return
            self._json({"error": {"code": "NOT_FOUND", "message": "not found"}}, HTTPStatus.NOT_FOUND)
        except Exception as exc:
            self._handle_error(exc)

    def _read_json(self) -> Dict[str, Any]:
        length = int(self.headers.get("content-length") or "0")
        if length <= 0:
            return {}
        return json.loads(self.rfile.read(length).decode("utf-8"))

    def _json(self, payload: Dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("content-type", "application/json; charset=utf-8")
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _parse_task_id(self, raw: str) -> int:
        try:
            task_id = int(raw)
        except ValueError as exc:
            raise ValidationError("task_id must be an integer") from exc
        if task_id <= 0:
            raise ValidationError("task_id must be positive")
        return task_id

    def _events(self, events: Any) -> None:
        lines = []
        for event in events:
            lines.append("event: step")
            lines.append(f"data: {json.dumps(event, ensure_ascii=False)}")
            lines.append("")
        body = "\n".join(lines).encode("utf-8")
        self.send_response(HTTPStatus.OK)
        self.send_header("content-type", "text/event-stream; charset=utf-8")
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _static_index(self) -> None:
        path = Path(__file__).resolve().parent.parent / "web" / "index.html"
        body = path.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("content-type", "text/html; charset=utf-8")
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _handle_error(self, exc: Exception) -> None:
        status = HTTPStatus.BAD_REQUEST if isinstance(exc, AgentError) else HTTPStatus.INTERNAL_SERVER_ERROR
        if getattr(exc, "code", "") in {"TASK_NOT_FOUND", "REPORT_NOT_READY"}:
            status = HTTPStatus.NOT_FOUND
        self._json(error_body(exc), status)

    def log_message(self, format: str, *args: Any) -> None:
        return


def run(host: str = "127.0.0.1", port: int = 8000) -> None:
    server = ThreadingHTTPServer((host, port), ResearchRequestHandler)
    print(f"Research Agent running at http://{host}:{port}")
    server.serve_forever()


if __name__ == "__main__":
    run()
