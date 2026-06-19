from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    database_path: Path = Path("data/research_agent.db")
    max_steps: int = 12
    max_search_results: int = 10
    max_crawl_pages: int = 8
    max_page_chars: int = 20000


DEFAULT_SETTINGS = Settings()
