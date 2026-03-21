package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.CreateExamRequest;
import com.exam_bank.exam_service.entity.*;
import com.exam_bank.exam_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExamManagementService {

    private final OnlineExamRepository examRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;

    @Transactional
    public OnlineExam createManualExam(CreateExamRequest request) {
        // 1. Lưu thông tin Đề thi
        OnlineExam exam = new OnlineExam();
        exam.setTitle(request.getTitle());
        exam.setDescription(request.getDescription());
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setPassingScore(request.getPassingScore());

        if (request.getQuestions() != null) {
            exam.setTotalQuestions(request.getQuestions().size());
        }

        exam.setSource(OnlineExamSource.MANUAL_CREATED);
        exam.setStatus(OnlineExamStatus.DRAFT);
        exam = examRepo.save(exam);

        if (request.getQuestions() != null) {
            for (CreateExamRequest.QuestionDto qDto : request.getQuestions()) {
                Question question = new Question();
                question.setExam(exam);
                question.setContent(qDto.getContent());
                question.setExplanation(qDto.getExplanation());
                question.setScoreWeight(qDto.getScoreWeight());

                question = questionRepo.save(question);

                if (qDto.getOptions() != null) {
                    for (CreateExamRequest.OptionDto optDto : qDto.getOptions()) {
                        QuestionOption option = new QuestionOption();
                        option.setQuestion(question);
                        option.setContent(optDto.getContent());
                        option.setIsCorrect(optDto.getIsCorrect());

                        optionRepo.save(option);
                    }
                }
            }
        }
        return exam;
    }
}