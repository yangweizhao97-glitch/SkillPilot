from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_plans_the_state_machine_contract_and_preserves_trace_id():
    response = client.post("/v1/workflows/career/plan", json={
        "taskId": 42,
        "traceId": "trace-42",
        "enabledSteps": ["MATCHING_JOB", "ANALYZING_RESUME"],
    })

    assert response.status_code == 200
    payload = response.json()
    assert payload["runId"].startswith("trace-42:")
    assert payload["plannedStatuses"] == [
        "MATCHING_JOB",
        "ANALYZING_RESUME",
        "GENERATING_QUESTIONS",
        "GENERATING_FINAL_REPORT",
        "SUCCESS",
    ]


def test_rejects_unknown_workflow_steps():
    response = client.post("/v1/workflows/career/plan", json={
        "taskId": 42,
        "traceId": "trace-42",
        "enabledSteps": ["DELETE_EVERYTHING"],
    })
    assert response.status_code == 422
