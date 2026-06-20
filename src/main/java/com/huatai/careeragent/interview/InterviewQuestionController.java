package com.huatai.careeragent.interview;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.interview.InterviewQuestionService.InterviewQuestionResponse;
import com.huatai.careeragent.task.CareerTaskDtos.CareerTaskResponse;
import com.huatai.careeragent.task.CareerTaskService;
import com.huatai.careeragent.task.WorkflowStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interview/questions")
public class InterviewQuestionController {
    private final CareerTaskService careerTaskService;
    private final InterviewQuestionService questionService;

    public InterviewQuestionController(CareerTaskService careerTaskService, InterviewQuestionService questionService) {
        this.careerTaskService = careerTaskService;
        this.questionService = questionService;
    }

    @PostMapping("/generate")
    public ApiResponse<CareerTaskResponse> generate(CurrentUser currentUser,
                                                     @Valid @RequestBody GenerateQuestionsRequest request) {
        return ApiResponse.ok(careerTaskService.createForSteps(
                currentUser.userId(), request.resumeId(), request.jobId(),
                List.of(WorkflowStatus.GENERATING_QUESTIONS)
        ));
    }

    @GetMapping
    public ApiResponse<List<InterviewQuestionResponse>> list(
            CurrentUser currentUser,
            @RequestParam(required = false) Long resumeId,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) QuestionDifficulty difficulty,
            @RequestParam(required = false) QuestionType questionType
    ) {
        return ApiResponse.ok(questionService.list(currentUser.userId(), resumeId, jobId, difficulty, questionType));
    }

    public record GenerateQuestionsRequest(@NotNull Long resumeId, @NotNull Long jobId) { }
}
