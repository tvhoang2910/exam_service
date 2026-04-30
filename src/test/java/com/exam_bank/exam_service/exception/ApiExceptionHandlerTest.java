package com.exam_bank.exam_service.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiExceptionHandler")
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleAsyncRequestNotUsable_whenSsePath_thenReturnNoContent() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/exam/sse/events");

        ResponseEntity<Void> response = handler.handleAsyncRequestNotUsable(
                new AsyncRequestNotUsableException("disconnected client"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void handleAsyncRequestNotUsable_whenNonSsePath_thenReturnNoContent() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/exam/uploads/mine");

        ResponseEntity<Void> response = handler.handleAsyncRequestNotUsable(
                new AsyncRequestNotUsableException("socket closed"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }
}
