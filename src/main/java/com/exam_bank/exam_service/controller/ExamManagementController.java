package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.CreateExamRequest;
import com.exam_bank.exam_service.dto.ExamResponse;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import com.exam_bank.exam_service.service.ExamManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.exam_bank.exam_service.dto.message.ExamSourceUploadedEvent;
import com.exam_bank.exam_service.service.MinioService;
import com.exam_bank.exam_service.service.RabbitMQEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/exams")
@CrossOrigin(origins = "http://localhost:5173")
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
            @AuthenticationPrincipal Jwt jwt) { // Lấy JWT của user đang thực hiện request

        // 1. Lấy định danh người dùng
        String uploaderId = jwt.getClaimAsString("userId");

        // 2. Upload vật lý lên MinIO
        String objectName = minioService.uploadFile(file);

        // 3. Tạo Draft Exam trong Database
        CreateExamRequest draftRequest = new CreateExamRequest();
        draftRequest.setTitle(title);
        // Lưu ý: Trong phương thức createManualExam (hoặc phương thức mới bạn tự viết),
        // hãy đảm bảo gán exam.setCreatedBy(uploaderId), exam.setStatus(OnlineExamStatus.DRAFT),
        // và exam.setSource(OnlineExamSource.AI_EXTRACTED) trước khi lưu xuống DB.
        ExamResponse draftExam = examService.createManualExam(draftRequest);

        // 4. Đóng gói Event và Gửi vào Queue
        ExamSourceUploadedEvent event = ExamSourceUploadedEvent.builder()
                .examId(draftExam.getId())
                .fileObjectName(objectName)
                .originalFileName(file.getOriginalFilename())
                .uploadedByUserId(uploaderId) // Chuyển giao quyền sở hữu cho search_service xử lý
                .build();

        eventPublisher.publishFileUploadedEvent(event);

        return ResponseEntity.ok(draftExam);
    }
}