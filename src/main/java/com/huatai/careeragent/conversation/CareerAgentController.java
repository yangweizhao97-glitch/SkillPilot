package com.huatai.careeragent.conversation;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.conversation.CareerAgentDtos.IntentRequest;
import com.huatai.careeragent.conversation.CareerAgentDtos.IntentResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.PlanRequest;
import com.huatai.careeragent.conversation.CareerAgentDtos.PlanResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.CareerProfileResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.AgentConversationResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.AgentMessageResponse;
import com.huatai.careeragent.conversation.CareerAgentDtos.AppendAgentMessageRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/career-agent")
public class CareerAgentController {
    private final CareerIntentService intentService;
    private final CareerAgentPlanningService planningService;
    private final CareerProfileService profileService;
    private final CareerAgentConversationService conversationService;
    private final CareerAgentStreamService streamService;

    public CareerAgentController(CareerIntentService intentService, CareerAgentPlanningService planningService,
                                 CareerProfileService profileService,
                                 CareerAgentConversationService conversationService,
                                 CareerAgentStreamService streamService) {
        this.intentService = intentService;
        this.planningService = planningService;
        this.profileService = profileService;
        this.conversationService = conversationService;
        this.streamService = streamService;
    }

    @PostMapping("/intent")
    public ApiResponse<IntentResponse> intent(@Valid @RequestBody IntentRequest request) {
        return ApiResponse.ok(intentService.classify(request));
    }

    @PostMapping("/plan")
    public ApiResponse<PlanResponse> plan(CurrentUser currentUser, @Valid @RequestBody PlanRequest request) {
        PlanResponse plan = planningService.plan(currentUser.userId(), request);
        var messages = conversationService.recordPlan(currentUser.userId(), request, plan);
        return ApiResponse.ok(new PlanResponse(
                plan.intent(),
                plan.selectedResources(),
                plan.missingResources(),
                plan.nextAction(),
                plan.canStartWorkflow(),
                plan.workflowSteps(),
                plan.resumeId(),
                plan.jobId(),
                plan.reportId(),
                plan.task(),
                plan.learningPlanId(),
                plan.interviewSessionId(),
                plan.profile(),
                plan.suggestedPrompts(),
                messages,
                plan.assistantMessage()
        ));
    }

    @PostMapping(value = "/plan/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter planStream(CurrentUser currentUser, @Valid @RequestBody PlanRequest request) {
        return streamService.plan(currentUser.userId(), request);
    }

    @GetMapping("/profile")
    public ApiResponse<CareerProfileResponse> profile(CurrentUser currentUser) {
        return ApiResponse.ok(profileService.get(currentUser.userId()));
    }

    @GetMapping("/conversation")
    public ApiResponse<AgentConversationResponse> conversation(CurrentUser currentUser) {
        return ApiResponse.ok(conversationService.getDefault(currentUser.userId()));
    }

    @PostMapping("/messages")
    public ApiResponse<AgentMessageResponse> appendMessage(CurrentUser currentUser,
                                                           @Valid @RequestBody AppendAgentMessageRequest request) {
        return ApiResponse.ok(conversationService.append(currentUser.userId(), request));
    }
}
