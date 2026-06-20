package com.huatai.careeragent.agent.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.core.AgentException;
import com.huatai.careeragent.agent.schema.SchemaRepairService.RepairResult;
import com.huatai.careeragent.agent.tool.SearchUserKnowledgeBaseTool;
import com.huatai.careeragent.llm.LlmResponse.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class AgentOutputSupport {
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

    public TokenUsage totalUsage(TokenUsage primary, RepairResult repair) {
        return repair.repairResponse() == null ? primary : primary.plus(repair.repairResponse().usage());
    }

    public void validateCitations(JsonNode result, List<SearchUserKnowledgeBaseTool.Item> items) {
        Set<String> allowed = new HashSet<>();
        items.forEach(item -> allowed.add(item.citationId()));
        collectCitations(result).forEach(citation -> {
            if (!allowed.contains(citation)) {
                throw new AgentException("AGENT_CITATION_INVALID", "Model returned an unknown citation", true);
            }
        });
    }

    private Set<String> collectCitations(JsonNode node) {
        Set<String> citations = new HashSet<>();
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if (entry.getKey().equals("citations") && entry.getValue().isArray()) {
                    entry.getValue().forEach(value -> citations.add(value.asText()));
                } else {
                    citations.addAll(collectCitations(entry.getValue()));
                }
            });
        } else if (node.isArray()) {
            node.forEach(value -> citations.addAll(collectCitations(value)));
        }
        return citations;
    }
}
