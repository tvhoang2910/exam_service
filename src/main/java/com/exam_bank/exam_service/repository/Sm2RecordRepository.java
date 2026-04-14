package com.exam_bank.exam_service.repository;

import com.exam_bank.exam_service.entity.Sm2Record;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface Sm2RecordRepository extends JpaRepository<Sm2Record, Long> {
    Optional<Sm2Record> findByUserIdAndQuestionId(Long userId, Long questionId);

    List<Sm2Record> findByUserIdAndNextReviewAtBefore(Long userId, Instant before);

    List<Sm2Record> findByQuestionId(Long questionId);

    @Modifying(clearAutomatically = true)
    @Query("delete from Sm2Record record where record.question.id in :questionIds")
    void deleteByQuestionIdIn(List<Long> questionIds);
}
