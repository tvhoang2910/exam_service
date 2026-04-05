package com.exam_bank.exam_service.feature.reporting.controller;

import com.exam_bank.exam_service.exception.ApiExceptionHandler;
import com.exam_bank.exam_service.feature.reporting.dto.CreateReportRequest;
import com.exam_bank.exam_service.feature.reporting.dto.QuestionReportHistoryResponse;
import com.exam_bank.exam_service.feature.reporting.dto.QuestionReportResponse;
import com.exam_bank.exam_service.feature.reporting.dto.ReportQueueItem;
import com.exam_bank.exam_service.feature.reporting.dto.ResolveReportRequest;
import com.exam_bank.exam_service.feature.reporting.entity.ReportStatus;
import com.exam_bank.exam_service.feature.reporting.entity.ReportType;
import com.exam_bank.exam_service.feature.reporting.service.QuestionReportService;
import com.exam_bank.exam_service.service.AuthenticatedUserService;
import com.exam_bank.exam_service.service.ExamAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QuestionReportController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class QuestionReportControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private QuestionReportService questionReportService;

        @MockitoBean
        private AuthenticatedUserService authenticatedUserService;

        @MockitoBean
        private ExamAuditService examAuditService;

        @Test
        void createReport_shouldReturnCreatedReport() throws Exception {
                CreateReportRequest request = new CreateReportRequest();
                request.setReportType(ReportType.WRONG_ANSWER);
                request.setDescription("Đáp án hiện tại sai");

                QuestionReportResponse response = QuestionReportResponse.builder()
                                .id(99L)
                                .questionId(20L)
                                .attemptId(10L)
                                .examId(2L)
                                .examTitle("Đề kiểm tra")
                                .reportType(ReportType.WRONG_ANSWER)
                                .reportTypeLabel("Sai đáp án")
                                .status(ReportStatus.REPORTED)
                                .statusLabel("Mới")
                                .createdAt(Instant.now())
                                .build();

                when(authenticatedUserService.getCurrentUserId()).thenReturn(7L);
                when(questionReportService.createReport(eq(20L), eq(10L), eq(7L), any(CreateReportRequest.class)))
                                .thenReturn(response);

                mockMvc.perform(post("/attempts/{attemptId}/questions/{questionId}/reports", 10L, 20L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(99))
                                .andExpect(jsonPath("$.reportType").value("WRONG_ANSWER"));

                verify(questionReportService).createReport(eq(20L), eq(10L), eq(7L), any(CreateReportRequest.class));
        }

        @Test
        void getMyReports_shouldReturnCurrentUserHistory() throws Exception {
                when(authenticatedUserService.getCurrentUserId()).thenReturn(9L);
                when(questionReportService.getMyReports(9L)).thenReturn(List.of(
                                QuestionReportResponse.builder()
                                                .id(1L)
                                                .questionId(3L)
                                                .reportType(ReportType.TYPO)
                                                .status(ReportStatus.REVIEWING)
                                                .createdAt(Instant.now())
                                                .build()));

                mockMvc.perform(get("/users/me/reports"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(1))
                                .andExpect(jsonPath("$[0].status").value("REVIEWING"));
        }

        @Test
        void getReportQueue_shouldReturnQueuePage() throws Exception {
                ReportQueueItem item = ReportQueueItem.builder()
                                .questionId(8L)
                                .examId(2L)
                                .examTitle("Đề kiểm tra")
                                .topReportType(ReportType.INVALID_QUESTION)
                                .topReportTypeLabel("Đề sai")
                                .totalReportCount(3)
                                .uniqueReportersCount(2)
                                .reportTypeCounts(Map.of("INVALID_QUESTION", 3))
                                .build();

                when(authenticatedUserService.getCurrentUserId()).thenReturn(12L);
                when(questionReportService.getReportQueue(any(), eq(12L)))
                                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

                mockMvc.perform(get("/admin/reports?page=0&size=20"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].questionId").value(8))
                                .andExpect(jsonPath("$.content[0].topReportType").value("INVALID_QUESTION"));

                verify(questionReportService).getReportQueue(any(), eq(12L));
        }

        @Test
        void getProcessedReportQueue_shouldReturnQueuePage() throws Exception {
                ReportQueueItem item = ReportQueueItem.builder()
                                .questionId(18L)
                                .examId(3L)
                                .examTitle("Đề đã xử lý")
                                .topReportType(ReportType.WRONG_ANSWER)
                                .topReportTypeLabel("Sai đáp án")
                                .totalReportCount(4)
                                .uniqueReportersCount(3)
                                .reportTypeCounts(Map.of("WRONG_ANSWER", 4))
                                .build();

                when(authenticatedUserService.getCurrentUserId()).thenReturn(12L);
                when(questionReportService.getProcessedReportQueue(any(), eq(12L)))
                                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

                mockMvc.perform(get("/admin/reports/processed?page=0&size=20"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].questionId").value(18))
                                .andExpect(jsonPath("$.content[0].topReportType").value("WRONG_ANSWER"));

                verify(questionReportService).getProcessedReportQueue(any(), eq(12L));
        }

        @Test
        void getQuestionReports_shouldReturnDetailList() throws Exception {
                when(authenticatedUserService.getCurrentUserId()).thenReturn(13L);
                when(questionReportService.getReportsForQuestion(20L, 13L)).thenReturn(List.of(
                                QuestionReportResponse.builder()
                                                .id(5L)
                                                .questionId(20L)
                                                .reportType(ReportType.MISSING_INFORMATION)
                                                .status(ReportStatus.REPORTED)
                                                .createdAt(Instant.now())
                                                .build()));

                mockMvc.perform(get("/admin/reports/questions/{questionId}", 20L))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(5));

                verify(questionReportService).getReportsForQuestion(20L, 13L);
        }

        @Test
        void getReportHistory_shouldReturnProcessingHistory() throws Exception {
                QuestionReportHistoryResponse history = QuestionReportHistoryResponse.builder()
                                .id(50L)
                                .action("RESOLVED")
                                .actionLabel("Đã xử lý")
                                .previousStatus(ReportStatus.REVIEWING)
                                .newStatus(ReportStatus.RESOLVED)
                                .note("Đã cập nhật nội dung câu hỏi")
                                .processedBy(9L)
                                .processedAt(Instant.parse("2026-04-05T12:00:00Z"))
                                .build();

                when(authenticatedUserService.getCurrentUserId()).thenReturn(13L);
                when(questionReportService.getReportHistoryForQuestion(20L, 13L)).thenReturn(List.of(history));

                mockMvc.perform(get("/admin/reports/questions/{questionId}/history", 20L))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(50))
                                .andExpect(jsonPath("$[0].action").value("RESOLVED"));

                verify(questionReportService).getReportHistoryForQuestion(20L, 13L);
        }

        @Test
        void resolveReports_shouldReturnNoContent() throws Exception {
                ResolveReportRequest request = new ResolveReportRequest();
                request.setStatus(ReportStatus.RESOLVED);
                request.setResolutionNote("Đã sửa nội dung");
                request.setUnhideQuestion(true);

                when(authenticatedUserService.getCurrentUserId()).thenReturn(99L);

                mockMvc.perform(put("/admin/reports/questions/{questionId}/resolve", 20L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isNoContent());

                verify(questionReportService).resolveQuestionReports(eq(20L), eq(99L), any(ResolveReportRequest.class));
        }
}
