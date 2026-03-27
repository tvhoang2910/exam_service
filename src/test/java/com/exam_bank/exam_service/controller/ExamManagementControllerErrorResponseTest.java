package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.CreateExamRequest;
import com.exam_bank.exam_service.exception.ApiExceptionHandler;
import com.exam_bank.exam_service.service.ExamManagementService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExamManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class ExamManagementControllerErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExamManagementService examManagementService;

    @Test
    void updateExam_shouldReturnReasonInErrorBody() throws Exception {
        Long examId = 3L;
        when(examManagementService.updateExam(eq(examId), any(CreateExamRequest.class)))
                .thenThrow(new ResponseStatusException(BAD_REQUEST,
                        "Cannot update questions because attempts already exist. You can still update exam metadata (title, duration, passing score, max attempts, tags, status). Create a new exam version to change questions."));

        CreateExamRequest request = new CreateExamRequest();
        request.setTitle("Draft update");

        mockMvc.perform(put("/exams/{examId}", examId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Cannot update questions because attempts already exist. You can still update exam metadata (title, duration, passing score, max attempts, tags, status). Create a new exam version to change questions."))
                .andExpect(jsonPath("$.path").value("/exams/3"));
    }
}
