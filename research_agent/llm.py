import json
import os
import urllib.request
from typing import Dict, List


class LLMProvider:
    provider_name = "base"

    def complete(self, messages: List[Dict[str, str]], temperature: float = 0.2) -> str:
        raise NotImplementedError


class MockLLMProvider(LLMProvider):
    provider_name = "mock"

    def complete(self, messages: List[Dict[str, str]], temperature: float = 0.2) -> str:
        return messages[-1]["content"]


class OpenAICompatibleProvider(LLMProvider):
    provider_name = "openai_compatible"

    def __init__(self, base_url: str, api_key: str, model: str):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model = model

    def complete(self, messages: List[Dict[str, str]], temperature: float = 0.2) -> str:
        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": temperature,
        }
        request = urllib.request.Request(
            f"{self.base_url}/chat/completions",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "content-type": "application/json",
                "authorization": f"Bearer {self.api_key}",
            },
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=60) as response:
            body = json.loads(response.read().decode("utf-8"))
        return body["choices"][0]["message"]["content"]


def create_llm_provider() -> LLMProvider:
    provider = os.getenv("LLM_PROVIDER", "mock").lower()
    if provider == "deepseek":
        api_key = os.getenv("DEEPSEEK_API_KEY")
        if not api_key:
            return MockLLMProvider()
        return OpenAICompatibleProvider(
            os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
            api_key,
            os.getenv("DEEPSEEK_MODEL", "deepseek-v4-flash"),
        )
    if provider == "openai_compatible":
        api_key = os.getenv("OPENAI_COMPATIBLE_API_KEY")
        base_url = os.getenv("OPENAI_COMPATIBLE_BASE_URL")
        model = os.getenv("OPENAI_COMPATIBLE_MODEL")
        if api_key and base_url and model:
            return OpenAICompatibleProvider(base_url, api_key, model)
    return MockLLMProvider()
