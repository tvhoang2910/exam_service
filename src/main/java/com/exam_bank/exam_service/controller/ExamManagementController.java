package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.CreateExamRequest;
import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.service.ExamManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exams")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class ExamManagementController {

    private final ExamManagementService examService;

    @PostMapping
    public ResponseEntity<?> createExam(@RequestBody CreateExamRequest request) {
        OnlineExam createdExam = examService.createManualExam(request);
        return ResponseEntity.ok("tao de thi thanh cong ID: " + createdExam.getId());
    }
}