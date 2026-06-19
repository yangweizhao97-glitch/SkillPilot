package com.huatai.careeragent.common.error;

import com.huatai.careeragent.common.security.JwtService;
import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.trace.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, GlobalExceptionHandlerTest.TestControllerConfig.class})
class GlobalExceptionHandlerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser
    void mapsBusinessExceptionToStructuredError() throws Exception {
        mockMvc.perform(get("/test/business-error")
                        .header("X-Trace-Id", "trace_test_123"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Trace-Id", "trace_test_123"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.error.message").value("Resource already exists"))
                .andExpect(jsonPath("$.traceId").value("trace_test_123"));
    }

    @Test
    @WithMockUser
    void mapsValidationErrorsWithoutStackTrace() throws Exception {
        mockMvc.perform(post("/test/validation-error")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", startsWith("trace_")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.email").exists())
                .andExpect(jsonPath("$.error.details.name").exists())
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    @Test
    @WithMockUser
    void mapsUnknownExceptionToGenericServerError() throws Exception {
        mockMvc.perform(get("/test/unknown-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Internal server error"))
                .andExpect(jsonPath("$.error.message").value(not("database password is secret")))
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    @TestConfiguration
    static class TestControllerConfig {
        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    public static class TestController {
        @GetMapping("/test/business-error")
        ApiResponse<Map<String, String>> businessError() {
            throw new BusinessException(
                    "DUPLICATE_RESOURCE",
                    "Resource already exists",
                    HttpStatus.CONFLICT
            );
        }

        @PostMapping("/test/validation-error")
        ApiResponse<Map<String, String>> validationError(@Valid @RequestBody TestRequest request) {
            return ApiResponse.ok(Map.of("email", request.email()));
        }

        @GetMapping("/test/unknown-error")
        ApiResponse<Map<String, String>> unknownError() {
            throw new IllegalStateException("database password is secret");
        }
    }

    record TestRequest(
            @Email String email,
            @NotBlank String name
    ) {
    }
}
