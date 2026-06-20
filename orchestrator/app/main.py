from uuid import uuid4

from fastapi import FastAPI
from pydantic import BaseModel, Field

from .graph import WORKFLOW_STATUSES, career_plan_graph


class CareerPlanRequest(BaseModel):
    taskId: int = Field(gt=0)
    traceId: str = Field(min_length=1, max_length=80)
    enabledSteps: list[str]


class CareerPlanResponse(BaseModel):
    runId: str
    plannedStatuses: list[str]


app = FastAPI(title="SkillPilot LangGraph Orchestrator", version="0.2.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/v1/workflows/career/plan", response_model=CareerPlanResponse)
def plan_career_workflow(request: CareerPlanRequest) -> CareerPlanResponse:
    unknown = set(request.enabledSteps) - set(WORKFLOW_STATUSES[:3])
    if unknown:
        from fastapi import HTTPException

        raise HTTPException(status_code=422, detail=f"Unknown enabled steps: {sorted(unknown)}")
    result = career_plan_graph.invoke({
        "enabled_steps": request.enabledSteps,
        "planned_statuses": [],
    })
    return CareerPlanResponse(
        runId=f"{request.traceId}:{uuid4().hex}",
        plannedStatuses=result["planned_statuses"],
    )
