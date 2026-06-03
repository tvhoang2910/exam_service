package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.feature.upload.dto.CreateQuestionRequest;
import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.entity.QuestionOption;
import com.exam_bank.exam_service.entity.QuestionType;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.repository.QuestionOptionRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final OnlineExamRepository onlineExamRepository;

    @Transactional
    public void createQuestion(CreateQuestionRequest request, Long contributorId) {
        // 1. Kiểm tra xem Đề thi có tồn tại không
        OnlineExam exam = onlineExamRepository.findById(request.getExamId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đề thi"));

        // 2. Tạo và lưu Câu hỏi
        QuestionType questionType = request.getQuestionType() == null
                ? QuestionType.MULTIPLE_CHOICE
                : request.getQuestionType();
        validateQuestionRequest(request, questionType);

        Question question = new Question();
        question.setExam(exam);
        question.setContent(request.getContent());
        question.setQuestionType(questionType);
        question.setExplanation(request.getExplanation());
        question.setScoreWeight(resolveScore(request.getScore(), request.getScoreWeight()));
        question.setSampleAnswer(request.getSampleAnswer());
        question.setGradingGuide(request.getGradingGuide());
        question.setDifficulty(request.getDifficulty());

        // Vì Contributor tự tạo nên mặc định duyệt luôn (APPROVED)
        // Lưu ý: Nếu entity Question của bạn đã có trường status (như hướng dẫn trước)
        // thì mở comment dòng dưới:
        // question.setStatus(Question.QuestionStatus.APPROVED);

        question.setIsHidden(false);
        Question savedQuestion = questionRepository.save(question);

        // 3. Tạo và lưu các Đáp án (Options) nếu có
        if (questionType == QuestionType.MULTIPLE_CHOICE
                && request.getOptions() != null
                && !request.getOptions().isEmpty()) {
            List<QuestionOption> options = request.getOptions().stream().map(optDto -> {
                QuestionOption option = new QuestionOption();
                option.setQuestion(savedQuestion);
                option.setContent(optDto.getContent());
                option.setIsCorrect(optDto.getIsCorrect());
                return option;
            }).collect(Collectors.toList());

            questionOptionRepository.saveAll(options);
        }

        // 4. Tăng bộ đếm tổng số câu hỏi của Đề thi lên 1
        int currentTotal = exam.getTotalQuestions() == null ? 0 : exam.getTotalQuestions();
        exam.setTotalQuestions(currentTotal + 1);
        onlineExamRepository.save(exam);

        log.info("createQuestion: Contributor ID {} đã tạo câu hỏi ID {} cho đề thi ID {}",
                contributorId, savedQuestion.getId(), exam.getId());
    }

    private void validateQuestionRequest(CreateQuestionRequest request, QuestionType questionType) {
        List<CreateQuestionRequest.OptionDto> options = request.getOptions() == null ? List.of() : request.getOptions();

        if (questionType == QuestionType.ESSAY) {
            if (!options.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi tự luận không được có đáp án lựa chọn");
            }
            if (!StringUtils.hasText(request.getSampleAnswer())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi tự luận phải có đáp án mẫu");
            }
            if (!StringUtils.hasText(request.getGradingGuide())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi tự luận phải có rubric/hướng dẫn chấm");
            }
            return;
        }

        if (options.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi trắc nghiệm phải có ít nhất 2 đáp án");
        }

        boolean hasCorrectOption = false;
        for (CreateQuestionRequest.OptionDto option : options) {
            hasCorrectOption = hasCorrectOption || Boolean.TRUE.equals(option.getIsCorrect());
        }
        if (!hasCorrectOption) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi trắc nghiệm phải có ít nhất một đáp án đúng");
        }
    }

    private double resolveScore(Double score, Double scoreWeight) {
        Double resolved = score != null ? score : scoreWeight;
        if (resolved == null || resolved <= 0) {
            return 1.0;
        }
        return resolved;
    }
}
