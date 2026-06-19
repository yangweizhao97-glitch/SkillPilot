class AgentError(Exception):
    code = "INTERNAL_ERROR"

    def __init__(self, message: str, code: str = None):
        super().__init__(message)
        if code:
            self.code = code


class ValidationError(AgentError):
    code = "VALIDATION_ERROR"


class NotFoundError(AgentError):
    code = "TASK_NOT_FOUND"


class SkillError(AgentError):
    pass

