from .config import DEFAULT_SETTINGS, Settings
from .repository import Repository
from .runtime import AgentRuntime
from .skills import SkillRegistry


def create_runtime(repository: Repository, registry: SkillRegistry, settings: Settings = DEFAULT_SETTINGS):
    try:
        from .langgraph_runtime import LangGraphResearchRuntime

        return LangGraphResearchRuntime(repository, registry, settings)
    except Exception:
        return AgentRuntime(repository, registry, settings)

