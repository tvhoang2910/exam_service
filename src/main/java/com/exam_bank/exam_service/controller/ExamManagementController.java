package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.CreateExamRequest;
import com.exam_bank.exam_service.dto.ExamResponse;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import com.exam_bank.exam_service.service.ExamManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/exams")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class ExamManagementController {

    private final ExamManagementService examService;

    @PostMapping
    public ResponseEntity<ExamResponse> createExam(@RequestBody CreateExamRequest request) {
        return ResponseEntity.ok(examService.createManualExam(request));
    }

    @GetMapping("/manage")
    public ResponseEntity<List<ExamResponse>> getManagedExams() {
        return ResponseEntity.ok(examService.getManagedExams());
    }

    @GetMapping("/manage/{examId}")
    public ResponseEntity<ExamResponse> getManagedExam(@PathVariable Long examId) {
        return ResponseEntity.ok(examService.getManagedExamById(examId));
    }

    @PutMapping("/{examId}")
    public ResponseEntity<ExamResponse> updateExam(@PathVariable Long examId, @RequestBody CreateExamRequest request) {
        return ResponseEntity.ok(examService.updateExam(examId, request));
    }

    @DeleteMapping("/{examId}")
    public ResponseEntity<Void> deleteExam(@PathVariable Long examId) {
        examService.deleteExam(examId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{examId}/status")
    public ResponseEntity<ExamResponse> updateExamStatus(
            @PathVariable Long examId,
            @RequestParam OnlineExamStatus status) {
        return ResponseEntity.ok(examService.updateExamStatus(examId, status));
    }

    @GetMapping("/public")
    public ResponseEntity<List<ExamResponse>> getPublicExams() {
        return ResponseEntity.ok(examService.getPublicExams());
    }

    @GetMapping("/public/{examId}")
    public ResponseEntity<ExamResponse> getPublicExam(@PathVariable Long examId) {
        return ResponseEntity.ok(examService.getPublicExamById(examId));
    }
}