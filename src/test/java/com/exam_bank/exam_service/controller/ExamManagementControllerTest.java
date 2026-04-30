package com.exam_bank.exam_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.exam_bank.exam_service.dto.ExamResponse;
import com.exam_bank.exam_service.service.ExamManagementService;
import com.exam_bank.exam_service.service.MinioService;
import com.exam_bank.exam_service.service.RabbitMQEventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamManagementController Upload Source Tests")
class ExamManagementControllerTest {

    @Mock
    private ExamManagementService examManagementService;

    @Mock
    private MinioService minioService;

    @Mock
    private RabbitMQEventPublisher rabbitMQEventPublisher;

    @InjectMocks
    private ExamManagementController controller;

    @Test
    @DisplayName("uploadExamSource uploads file, creates draft exam, and publishes event")
    void uploadExamSourcePublishesEventAfterCreatingDraft() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "source.pdf",
                "application/pdf",
                "fake-pdf".getBytes(StandardCharsets.UTF_8));
        Jwt jwt = jwtWithUserId(101L);

        ExamResponse draftExam = new ExamResponse();
        draftExam.setId(77L);
        draftExam.setTitle("Uploaded draft");

        when(minioService.uploadFile(file)).thenReturn("objects/source-abc.pdf");
        when(examManagementService.createUploadedDraftExam("Demo Upload", "objects/source-abc.pdf", "application/pdf"))
                .thenReturn(draftExam);

        ResponseEntity<ExamResponse> response = controller.uploadExamSource("Demo Upload", file, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(draftExam);

        verify(rabbitMQEventPublisher).publishFileUploadedEvent(argThat(event -> event != null
                && Long.valueOf(77L).equals(event.getExamId())
                && "objects/source-abc.pdf".equals(event.getFileObjectName())
                && "source.pdf".equals(event.getOriginalFileName())
                && "101".equals(event.getUploadedByUserId())));
    }

    @Test
    @DisplayName("uploadExamSource rejects request when JWT has no userId claim")
    void uploadExamSourceRejectsMissingUserIdClaim() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "source.pdf",
                "application/pdf",
                "fake-pdf".getBytes(StandardCharsets.UTF_8));
        Jwt jwtWithoutUserId = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("role", "ADMIN"));

        assertThatThrownBy(() -> controller.uploadExamSource("Demo Upload", file, jwtWithoutUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) exception;
                    assertThat(responseStatusException.getStatusCode().value()).isEqualTo(401);
                });

        verifyNoInteractions(minioService, examManagementService, rabbitMQEventPublisher);
    }

    @Test
    @DisplayName("uploadExamSource rejects request when uploaded file is empty")
    void uploadExamSourceRejectsEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]);

        assertThatThrownBy(() -> controller.uploadExamSource("Demo Upload", emptyFile, jwtWithUserId("88")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) exception;
                    assertThat(responseStatusException.getStatusCode().value()).isEqualTo(400);
                });

        verifyNoInteractions(minioService, examManagementService, rabbitMQEventPublisher);
    }

    private Jwt jwtWithUserId(Object userId) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of(
                        "role", "ADMIN",
                        "userId", userId));
    }
}
