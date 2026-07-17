package com.aditya.minsearch.shared.infrastructure.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void mapsValidationErrorsToProblemDetails() throws Exception {
    mockMvc
        .perform(
            post("/validation")
                .contentType(APPLICATION_JSON)
                .header("X-Correlation-Id", "01J2R9X9FCG8YQXK5Y5AXYPC7M")
                .content("{\"name\":\"\",\"position\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://minsearch.dev/problems/validation-failed"))
        .andExpect(jsonPath("$.title").value("Request validation failed"))
        .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.correlationId").value("01J2R9X9FCG8YQXK5Y5AXYPC7M"))
        .andExpect(jsonPath("$.position").value(0))
        .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  void mapsUnexpectedErrorsToGenericProblemDetails() throws Exception {
    mockMvc
        .perform(post("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://minsearch.dev/problems/internal-error"))
        .andExpect(jsonPath("$.title").value("Internal server error"))
        .andExpect(jsonPath("$.code").value("GENERIC_INTERNAL_ERROR"));
  }

  @RestController
  static class TestController {

    @PostMapping("/validation")
    ResponseEntity<Void> validate(@Valid @RequestBody ValidationRequest request) {
      return ResponseEntity.noContent().build();
    }

    @PostMapping("/boom")
    ResponseEntity<Void> boom() {
      throw new IllegalStateException("boom");
    }
  }

  record ValidationRequest(@NotBlank String name, @Min(1) int position) {}
}
