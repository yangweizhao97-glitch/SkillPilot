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


def test_replans_from_verifier_feedback_and_preserves_trace_id():
    response = client.post("/v1/workflows/career/replan", json={
        "taskId": 42,
        "traceId": "trace-42",
        "enabledSteps": ["MATCHING_JOB", "ANALYZING_RESUME"],
        "currentPlan": ["MATCHING_JOB", "ANALYZING_RESUME", "GENERATING_FINAL_REPORT"],
        "failedStep": "ANALYZING_RESUME",
        "failedAgent": "RESUME_ANALYSIS_AGENT",
        "verification": {
            "passed": False,
            "nextAction": "REPLAN",
            "reason": "artifact missing",
            "metrics": {"summaryPresent": False},
        },
        "runtime": {"attempts": {"ANALYZING_RESUME": 1}},
    })

    assert response.status_code == 200
    payload = response.json()
    assert payload["runId"].startswith("trace-42:replan:")
    assert payload["plannedStatuses"][-1] == "SUCCESS"


def test_replan_rejects_unknown_statuses():
    response = client.post("/v1/workflows/career/replan", json={
        "taskId": 42,
        "traceId": "trace-42",
        "enabledSteps": ["MATCHING_JOB"],
        "currentPlan": ["MATCHING_JOB", "DELETE_EVERYTHING"],
        "failedStep": "MATCHING_JOB",
        "verification": {"passed": False, "nextAction": "REPLAN"},
    })

    assert response.status_code == 422
