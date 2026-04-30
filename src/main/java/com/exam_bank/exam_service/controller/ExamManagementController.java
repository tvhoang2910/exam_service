package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.CreateExamRequest;
import com.exam_bank.exam_service.dto.ExamResponse;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import com.exam_bank.exam_service.service.ExamManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import com.exam_bank.exam_service.dto.message.ExamSourceUploadedEvent;
import com.exam_bank.exam_service.service.MinioService;
import com.exam_bank.exam_service.service.RabbitMQEventPublisher;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/exams")
@RequiredArgsConstructor
@Slf4j
public class ExamManagementController {

    private final ExamManagementService examService;
    private final MinioService minioService;
    private final RabbitMQEventPublisher eventPublisher;

    @PostMapping
    public ResponseEntity<ExamResponse> createExam(@RequestBody CreateExamRequest request) {
        ExamResponse response = examService.createManualExam(request);
        int questionCount = request.getQuestions() == null ? 0 : request.getQuestions().size();
        log.info("createExam: examId={}, title={}, questionCount={}", response.getId(), response.getTitle(), questionCount);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/manage")
    public ResponseEntity<List<ExamResponse>> getManagedExams() {
        List<ExamResponse> exams = examService.getManagedExams();
        log.info("getManagedExams: count={}", exams.size());
        return ResponseEntity.ok(exams);
    }

    @GetMapping("/manage/{examId}")
    public ResponseEntity<ExamResponse> getManagedExam(@PathVariable Long examId) {
        ExamResponse exam = examService.getManagedExamById(examId);
        log.info("getManagedExam: examId={}, status={}", examId, exam.getStatus());
        return ResponseEntity.ok(exam);
    }

    @PutMapping("/{examId}")
    public ResponseEntity<ExamResponse> updateExam(@PathVariable Long examId, @RequestBody CreateExamRequest request) {
        ExamResponse response = examService.updateExam(examId, request);
        int questionCount = request.getQuestions() == null ? 0 : request.getQuestions().size();
        log.info("updateExam: examId={}, questionCount={}", examId, questionCount);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{examId}")
    public ResponseEntity<Void> deleteExam(@PathVariable Long examId) {
        examService.deleteExam(examId);
        log.info("deleteExam: examId={}", examId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{examId}/status")
    public ResponseEntity<ExamResponse> updateExamStatus(
            @PathVariable Long examId,
            @RequestParam OnlineExamStatus status) {
        ExamResponse response = examService.updateExamStatus(examId, status);
        log.info("updateExamStatus: examId={}, status={}", examId, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/public")
    public ResponseEntity<List<ExamResponse>> getPublicExams() {
        List<ExamResponse> exams = examService.getPublicExams();
        log.info("getPublicExams: count={}", exams.size());
        return ResponseEntity.ok(exams);
    }

    @GetMapping("/public/{examId}")
    public ResponseEntity<ExamResponse> getPublicExam(@PathVariable Long examId) {
        ExamResponse exam = examService.getPublicExamById(examId);
        log.info("getPublicExam: examId={}, title={}", examId, exam.getTitle());
        return ResponseEntity.ok(exam);
    }

    @PostMapping(value = "/upload-source", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExamResponse> uploadExamSource(
            @RequestParam("title") String title,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {

        if (!StringUtils.hasText(title)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exam title must not be empty");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file must not be empty");
        }
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication context");
        }

        String uploaderId = extractUploaderId(jwt);

        String objectName = minioService.uploadFile(file);
        ExamResponse draftExam = examService.createUploadedDraftExam(
                title,
                objectName,
                file.getContentType());

        ExamSourceUploadedEvent event = ExamSourceUploadedEvent.builder()
                .examId(draftExam.getId())
                .fileObjectName(objectName)
                .originalFileName(file.getOriginalFilename())
                .uploadedByUserId(uploaderId)
                .build();

        eventPublisher.publishFileUploadedEvent(event);

        return ResponseEntity.ok(draftExam);
    }

    private String extractUploaderId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number number && number.longValue() > 0) {
            return Long.toString(number.longValue());
        }
        if (claim instanceof String value && StringUtils.hasText(value)) {
            return value.trim();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing userId claim in JWT");
    }
}