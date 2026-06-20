package com.huatai.careeragent.agent.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.core.AgentException;
import com.huatai.careeragent.agent.schema.SchemaRepairService.RepairResult;
import com.huatai.careeragent.agent.tool.GetJobDescriptionTool;
import com.huatai.careeragent.agent.tool.GetResumeTool;
import com.huatai.careeragent.agent.tool.SearchUserKnowledgeBaseTool;
import com.huatai.careeragent.llm.LlmResponse.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AgentOutputSupport {
    private static final Logger log = LoggerFactory.getLogger(AgentOutputSupport.class);
    private static final String NO_VERIFIED_CITATION = "No verified source citation was available";

    private final ObjectMapper objectMapper;

    public AgentOutputSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AgentException("AGENT_CONTEXT_SERIALIZATION_FAILED", "Could not serialize agent context", false, exception);
        }
    }

    public String citedJson(String citationId, Object value) {
        return json(Map.of("citationId", citationId, "source", value));
    }

    public String resumeCitationId(GetResumeTool.Output resume) {
        return "resume_" + resume.resumeId();
    }

    public String jobCitationId(GetJobDescriptionTool.Output job) {
        return "job_" + job.jobId();
    }

    public Set<String> allowedCitationIds(GetResumeTool.Output resume, GetJobDescriptionTool.Output job,
                                          List<SearchUserKnowledgeBaseTool.Item> items) {
        Set<String> allowed = new LinkedHashSet<>();
        allowed.add(resumeCitationId(resume));
        if (job != null) {
            allowed.add(jobCitationId(job));
        }
        items.forEach(item -> allowed.add(item.citationId()));
        return allowed;
    }

    public String citationInstruction(Collection<String> allowed) {
        return "Allowed citation IDs: " + String.join(", ", allowed)
                + ". Use only these exact values. If no source supports a claim, use an empty citations array.";
    }

    public TokenUsage totalUsage(TokenUsage primary, RepairResult repair) {
        return repair.repairResponse() == null ? primary : primary.plus(repair.repairResponse().usage());
    }

    public void normalizeCitations(JsonNode result, Set<String> allowed, String traceId) {
        Set<String> removed = new HashSet<>();
        normalizeCitations(result, allowed, removed);
        if (!removed.isEmpty()) {
            log.warn("Removed unknown model citations: citations={}, traceId={}", removed, traceId);
        }
    }

    private void normalizeCitations(JsonNode node, Set<String> allowed, Set<String> removed) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            JsonNode citations = object.get("citations");
            if (citations instanceof ArrayNode array) {
                for (int index = array.size() - 1; index >= 0; index--) {
                    String citation = array.get(index).asText();
                    if (!allowed.contains(citation)) {
                        removed.add(citation);
                        array.remove(index);
                    }
                }
                JsonNode reason = object.get("noCitationReason");
                if (array.isEmpty() && reason != null && (reason.isNull() || reason.asText().isBlank())) {
                    object.put("noCitationReason", NO_VERIFIED_CITATION);
                }
            }
            object.properties().forEach(entry -> normalizeCitations(entry.getValue(), allowed, removed));
        } else if (node.isArray()) {
            node.forEach(value -> normalizeCitations(value, allowed, removed));
        }
    }
}
