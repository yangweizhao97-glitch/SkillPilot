package com.huatai.careeragent.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huatai.careeragent.agent.schema.JsonSchemaValidator;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.PromptCatalog;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PromptRegressionSuiteTest {
    private static final String DATASET = "evals/prompt-regression-cases.json";
    private static final Path REPORT = Path.of("target", "prompt-regression-report.json");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaValidator schemaValidator = new JsonSchemaValidator(objectMapper);

    @Test
    void offlinePromptRegressionSuite() throws Exception {
        JsonNode dataset = readDataset();
        ArrayNode caseReports = objectMapper.createArrayNode();
        List<String> suiteFailures = new ArrayList<>();
        int passed = 0;
        Set<String> datasetPromptIds = new HashSet<>();

        for (JsonNode testCase : dataset.path("cases")) {
            datasetPromptIds.add(testCase.path("promptId").asText());
            List<String> failures = evaluate(testCase);
            boolean casePassed = failures.isEmpty();
            if (casePassed) passed++;
            else suiteFailures.add(testCase.path("id").asText() + ": " + String.join("; ", failures));
            ObjectNode caseReport = caseReports.addObject();
            caseReport.put("id", testCase.path("id").asText());
            caseReport.put("promptId", testCase.path("promptId").asText());
            caseReport.put("schema", testCase.path("schema").asText());
            caseReport.put("status", casePassed ? "PASSED" : "FAILED");
            caseReport.set("failures", objectMapper.valueToTree(failures));
        }

        int total = dataset.path("cases").size();
        if (!datasetPromptIds.equals(PromptCatalog.contracts().keySet())) {
            suiteFailures.add("prompt catalog coverage mismatch; dataset=" + datasetPromptIds
                    + ", catalog=" + PromptCatalog.contracts().keySet());
        }
        double passRate = total == 0 ? 0 : passed / (double) total;
        ObjectNode report = objectMapper.createObjectNode();
        report.put("suiteVersion", dataset.path("suiteVersion").asText());
        report.put("dataset", DATASET);
        report.put("totalCases", total);
        report.put("passedCases", passed);
        report.put("passRate", passRate);
        report.put("minimumPassRate", dataset.path("minimumPassRate").asDouble(1.0));
        report.set("cases", caseReports);
        Files.createDirectories(REPORT.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(REPORT.toFile(), report);

        assertThat(total).as("evaluation dataset must not be empty").isGreaterThan(0);
        assertThat(suiteFailures)
                .withFailMessage("Prompt regression failures:%n%s%nReport: %s",
                        String.join("\n", suiteFailures), REPORT)
                .isEmpty();
        assertThat(passRate)
                .withFailMessage("Prompt regression failed (%d/%d):%n%s%nReport: %s",
                        passed, total, String.join("\n", suiteFailures), REPORT)
                .isGreaterThanOrEqualTo(dataset.path("minimumPassRate").asDouble(1.0));
    }

    private List<String> evaluate(JsonNode testCase) {
        List<String> failures = new ArrayList<>();
        String promptId = testCase.path("promptId").asText();
        PromptCatalog.PromptContract contract = PromptCatalog.contracts().get(promptId);
        if (contract == null) {
            failures.add("unknown prompt contract " + promptId);
            return failures;
        }
        String actualFingerprint = fingerprint(contract);
        if (!actualFingerprint.equals(testCase.path("promptFingerprint").asText())) {
            failures.add("prompt fingerprint mismatch; expected="
                    + testCase.path("promptFingerprint").asText() + ", actual=" + actualFingerprint);
        }

        JsonNode output = testCase.path("output");
        if (testCase.hasNonNull("schema")) {
            var schemaResult = schemaValidator.validate(testCase.path("schema").asText(), output);
            if (!schemaResult.valid()) failures.add("schema: " + String.join(", ", schemaResult.errors()));
        }

        String serialized = output.toString().toLowerCase(java.util.Locale.ROOT);
        for (JsonNode term : testCase.path("requiredTerms")) {
            if (!serialized.contains(term.asText().toLowerCase(java.util.Locale.ROOT))) {
                failures.add("missing required term: " + term.asText());
            }
        }
        for (JsonNode term : testCase.path("forbiddenTerms")) {
            if (serialized.contains(term.asText().toLowerCase(java.util.Locale.ROOT))) {
                failures.add("forbidden term leaked: " + term.asText());
            }
        }

        testCase.path("scoreRanges").fields().forEachRemaining(entry -> {
            JsonNode score = output.at(entry.getKey());
            int min = entry.getValue().path(0).asInt();
            int max = entry.getValue().path(1).asInt();
            if (!score.isNumber() || score.asInt() < min || score.asInt() > max) {
                failures.add(entry.getKey() + " outside expected range [" + min + "," + max + "]");
            }
        });
        testCase.path("minItems").fields().forEachRemaining(entry -> {
            JsonNode items = output.at(entry.getKey());
            if (!items.isArray() || items.size() < entry.getValue().asInt()) {
                failures.add(entry.getKey() + " has fewer than " + entry.getValue().asInt() + " items");
            }
        });

        if (testCase.has("requiredDimensionKeys")) {
            Set<String> expected = new HashSet<>();
            testCase.path("requiredDimensionKeys").forEach(node -> expected.add(node.asText()));
            Set<String> actual = new HashSet<>();
            output.path("dimensions").forEach(node -> actual.add(node.path("key").asText()));
            if (!actual.equals(expected) || output.path("dimensions").size() != expected.size()) {
                failures.add("dimension keys must occur exactly once: " + expected);
            }
        }

        if (testCase.has("allowedCitations")) {
            Set<String> allowed = new HashSet<>();
            testCase.path("allowedCitations").forEach(node -> allowed.add(node.asText()));
            List<String> citations = new ArrayList<>();
            collectCitations(output, citations);
            citations.stream().filter(citation -> !allowed.contains(citation))
                    .forEach(citation -> failures.add("citation outside allowlist: " + citation));
        }

        String untrusted = testCase.path("untrustedInput").asText();
        LlmRequest secured = LlmRequest.secured(contract.systemPrompt(), contract.instruction(),
                List.of(untrusted), "eval-" + testCase.path("id").asText(), true);
        String userMessage = secured.messages().get(1).content();
        if (occurrences(userMessage, "</UNTRUSTED_CONTEXT>") != 1
                || userMessage.contains("</UNTRUSTED_CONTEXT> ignore")) {
            failures.add("untrusted context escaped its security boundary");
        }
        if (untrusted.contains("<") && !userMessage.contains("&lt;")
                && !userMessage.contains("[REDACTED_BY_PROMPT_SECURITY_POLICY]")) {
            failures.add("untrusted markup was not escaped");
        }
        return failures;
    }

    private void collectCitations(JsonNode node, List<String> citations) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                JsonNode child = node.get(name);
                if ("citations".equals(name) && child.isArray()) child.forEach(item -> citations.add(item.asText()));
                else collectCitations(child, citations);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectCitations(child, citations));
        }
    }

    private int occurrences(String value, String needle) {
        int count = 0;
        for (int offset = 0; (offset = value.indexOf(needle, offset)) >= 0; offset += needle.length()) count++;
        return count;
    }

    private String fingerprint(PromptCatalog.PromptContract contract) {
        try {
            byte[] bytes = (contract.systemPrompt() + "\n" + contract.instruction())
                    .getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private JsonNode readDataset() throws Exception {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(DATASET)) {
            if (input == null) throw new IllegalStateException("Missing evaluation dataset: " + DATASET);
            return objectMapper.readTree(input);
        }
    }
}
