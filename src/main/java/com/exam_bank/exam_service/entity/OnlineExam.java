package com.exam_bank.exam_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "online_exams", indexes = {
        @Index(name = "idx_online_exams_status", columnList = "status"),
        @Index(name = "idx_online_exams_source", columnList = "source")
}, check = {
        @CheckConstraint(name = "chk_online_exams_status", constraint = "status in ('draft','published','archived')"),
        @CheckConstraint(name = "chk_online_exams_source", constraint = "source in ('manual_created','ai_extracted','imported')")
}, comment = "Thong tin metadata va cau hinh thi online")
public class OnlineExam extends BaseEntity {

    @Column(name = "title", nullable = false, comment = "Tieu de de thi")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT", comment = "Mo ta chi tiet de thi")
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[] default '{}'::text[]", comment = "Nhan de tim kiem va loc")
    private List<String> tags = new ArrayList<>();

    @Convert(converter = OnlineExamSourceConverter.class)
    @Column(name = "source", nullable = false, length = 30, comment = "Nguon tao de thi")
    @ColumnDefault("'manual_created'")
    private OnlineExamSource source = OnlineExamSource.MANUAL_CREATED;

    @Column(name = "original_file_url", length = 500, comment = "URL file goc neu trich xuat/import")
    private String originalFileUrl;

    @Column(name = "original_file_type", length = 100, comment = "Loai file goc, vi du application/pdf")
    private String originalFileType;

    @Column(name = "duration_minutes", comment = "Thoi gian lam bai (phut)")
    @ColumnDefault("60")
    private Integer durationMinutes = 60;

    @Column(name = "total_questions", comment = "Tong so cau hoi (cache)")
    @ColumnDefault("0")
    private Integer totalQuestions = 0;

    @Convert(converter = OnlineExamStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20, comment = "Trang thai de thi")
    @ColumnDefault("'draft'")
    private OnlineExamStatus status = OnlineExamStatus.DRAFT;

    @Column(name = "passing_score", comment = "Diem dat, null neu chua cau hinh")
    private Integer passingScore;

    @Column(name = "max_attempts", comment = "So lan thi toi da")
    @ColumnDefault("1")
    private Integer maxAttempts = 1;

    @Column(name = "is_randomized", comment = "Co tron thu tu cau hoi hay khong")
    @ColumnDefault("false")
    private Boolean isRandomized = false;
}
