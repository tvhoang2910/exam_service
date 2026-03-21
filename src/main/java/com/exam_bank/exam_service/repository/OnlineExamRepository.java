package com.exam_bank.exam_service.repository;

import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OnlineExamRepository extends JpaRepository<OnlineExam, Long> {
    List<OnlineExam> findAllByOrderByCreatedAtDesc();

    List<OnlineExam> findByStatusOrderByCreatedAtDesc(OnlineExamStatus status);
}