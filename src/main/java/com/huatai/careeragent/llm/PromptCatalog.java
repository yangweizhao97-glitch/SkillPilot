package com.huatai.careeragent.llm;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PromptCatalog {
    public static final PromptContract JOB_MATCH = new PromptContract(
            "job-match-v1",
            "You are a career matching analyst. Return strict JSON only.",
            "Compare the resume with the job. Required keys: matchScore, summary, strengths, weaknesses, "
                    + "missingSkills, suggestedResumeChanges, citations."
    );
    public static final PromptContract RESUME_ANALYSIS = new PromptContract(
            "resume-analysis-v1",
            "You are a resume reviewer. Return strict JSON only.",
            "Analyze the resume. Required keys: summary, highlights, weaknesses, projectIssues, suggestions, "
                    + "risks, nextActions, citations."
    );
    public static final PromptContract INTERVIEW_QUESTIONS = new PromptContract(
            "interview-questions-v1",
            "You generate personalized interview questions. Return strict JSON only.",
            "Return a questions array. Each item requires question, questionType, difficulty, expectedPoints, "
                    + "citations and noCitationReason. Use a supplied citationId or explain why no citation applies."
    );
    public static final PromptContract ANSWER_EVALUATION = new PromptContract(
            "answer-evaluation-v1",
            "You are a rigorous but constructive technical interviewer. Return strict JSON only.",
            "Score the answer against the question and expected points. Return exactly accuracy, relevance, "
                    + "depth, and communication with 0-100 integer scores. The server weights them 35%, "
                    + "25%, 25%, and 15%. Give evidence-based strengths, specific improvements, and a concise "
                    + "improved Chinese answer. Set followUp=true with one short question only when a material "
                    + "gap needs clarification; otherwise set followUp=false and followUpQuestion to an empty string."
    );
    public static final PromptContract INTERVIEW_FOLLOW_UP = new PromptContract(
            "interview-follow-up-v1",
            "你是一位严谨、自然且有建设性的中文技术面试官。只输出面试官要说的话，不要输出 JSON。",
            "根据评分结果提出一个简短、具体、只聚焦最大信息缺口的追问。只输出一个问题。"
    );
    public static final PromptContract SESSION_REVIEW = new PromptContract(
            "session-review-v1",
            "你是一位严谨、务实的技术面试教练。只返回符合 Schema 的 JSON。",
            "基于整场面试记录和逐题评分生成中文复盘。总结必须有证据，行动计划要具体可执行，"
                    + "练习题要针对真实缺口。不要改变服务端给出的总分与维度分。"
    );

    private PromptCatalog() { }

    public static Map<String, PromptContract> contracts() {
        Map<String, PromptContract> contracts = new LinkedHashMap<>();
        for (PromptContract contract : new PromptContract[]{
                JOB_MATCH, RESUME_ANALYSIS, INTERVIEW_QUESTIONS, ANSWER_EVALUATION,
                INTERVIEW_FOLLOW_UP, SESSION_REVIEW
        }) {
            contracts.put(contract.id(), contract);
        }
        return Map.copyOf(contracts);
    }

    public record PromptContract(String id, String systemPrompt, String instruction) { }
}
