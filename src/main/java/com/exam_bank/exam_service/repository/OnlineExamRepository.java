package com.exam_bank.exam_service.repository;

import com.exam_bank.exam_service.entity.OnlineExam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OnlineExamRepository extends JpaRepository<OnlineExam, Long> {
}