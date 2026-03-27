package com.exam_bank.exam_service.repository;

import com.exam_bank.exam_service.entity.QuestionReviewEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionReviewEventRepository extends JpaRepository<QuestionReviewEvent, Long> {
    @Modifying
    @Query("delete from QuestionReviewEvent event where event.attemptId = :attemptId")
    void deleteByAttemptId(Long attemptId);

    @Modifying
    @Query("delete from QuestionReviewEvent event where event.attemptId in :attemptIds")
    void deleteByAttemptIdIn(List<Long> attemptIds);
}
