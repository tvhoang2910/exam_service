package com.exam_bank.exam_service.repository;

import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OnlineExamRepository extends JpaRepository<OnlineExam, Long> {
    @EntityGraph(attributePaths = "tags")
    List<OnlineExam> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "tags")
    List<OnlineExam> findByStatusOrderByCreatedAtDesc(OnlineExamStatus status);

    @EntityGraph(attributePaths = "tags")
    List<OnlineExam> findByStatusNotOrderByCreatedAtDesc(OnlineExamStatus status);
}