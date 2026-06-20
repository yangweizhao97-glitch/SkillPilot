from typing import TypedDict

from langgraph.graph import END, START, StateGraph


WORKFLOW_STATUSES = (
    "MATCHING_JOB",
    "ANALYZING_RESUME",
    "GENERATING_QUESTIONS",
    "GENERATING_FINAL_REPORT",
    "SUCCESS",
)


class CareerPlanState(TypedDict):
    enabled_steps: list[str]
    planned_statuses: list[str]


def _append_status(status: str):
    def node(state: CareerPlanState) -> dict[str, list[str]]:
        return {"planned_statuses": [*state["planned_statuses"], status]}

    return node


def build_career_plan_graph():
    graph = StateGraph(CareerPlanState)
    for status in WORKFLOW_STATUSES:
        graph.add_node(status, _append_status(status))
    graph.add_edge(START, WORKFLOW_STATUSES[0])
    for current, following in zip(WORKFLOW_STATUSES, WORKFLOW_STATUSES[1:]):
        graph.add_edge(current, following)
    graph.add_edge(WORKFLOW_STATUSES[-1], END)
    return graph.compile()


career_plan_graph = build_career_plan_graph()
