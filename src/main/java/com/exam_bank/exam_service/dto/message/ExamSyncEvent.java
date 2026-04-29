package com.exam_bank.exam_service.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSyncEvent {
    private Long id;
    private String title;
    private String status;
    private Boolean isPremium;
    private List<String> tags;
    private String action;
}