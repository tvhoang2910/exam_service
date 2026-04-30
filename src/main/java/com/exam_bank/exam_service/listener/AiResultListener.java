package com.exam_bank.exam_service.listener;

import com.exam_bank.exam_service.dto.message.AiExtractionResultEvent;
import com.exam_bank.exam_service.service.ExamManagementService;
import com.exam_bank.exam_service.service.ExamSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiResultListener {

    private final ExamManagementService examManagementService;
    private final ExamSseService examSseService;

    @RabbitListener(queues = "${search.events.ai-extracted-queue:search.ai.extracted.queue}")
    public void handleAiExtractionResult(AiExtractionResultEvent event) {
        log.info("=================================================");
        log.info("🚀 Nhận được kết quả AI Extraction từ search_service cho Exam ID: {}", event.getExamId());

        String jsonResult = event.getAiJsonResult();

        // Parse userId an toàn
        Long userId = null;
        try {
            if (event.getUploadedByUserId() != null) {
                userId = Long.parseLong(event.getUploadedByUserId());
            }
        } catch (NumberFormatException ignored) {}

        // 1. KIỂM TRA THẤT BẠI
        if (jsonResult == null || jsonResult.trim().isEmpty() || jsonResult.trim().equals("[]")) {
            log.error("❌ AI Extraction thất bại với Exam ID {}", event.getExamId());

            // BẮN SSE LỖI
            examSseService.onAiExtractionCompleted(
                    event.getExamId(),
                    userId,
                    false,
                    "Hệ thống AI không tìm thấy câu hỏi nào hợp lệ trong ảnh bạn tải lên."
            );
            log.info("=================================================");
            return;
        }

        // 2. NẾU THÀNH CÔNG -> LƯU DATABASE
        try {
            log.info("Bắt đầu parse JSON và lưu vào Database...");
            examManagementService.processAiExtractionResult(event.getExamId(), jsonResult);
            log.info("🎉 Hoàn tất trọn vẹn quy trình tạo đề thi bằng AI cho Exam ID: {}", event.getExamId());

            // BẮN SSE THÀNH CÔNG
            examSseService.onAiExtractionCompleted(
                    event.getExamId(),
                    userId,
                    true,
                    "Tạo đề thi tự động hoàn tất! Các câu hỏi đã được trích xuất thành công."
            );

        } catch (Exception e) {
            log.error("💥 Lỗi parse JSON/Lưu DB: {}", e.getMessage(), e);
            // Bắn SSE lỗi hệ thống
            examSseService.onAiExtractionCompleted(
                    event.getExamId(),
                    userId,
                    false,
                    "Trích xuất thành công nhưng đã xảy ra lỗi khi lưu vào cơ sở dữ liệu."
            );
        }
        log.info("=================================================");
    }
}