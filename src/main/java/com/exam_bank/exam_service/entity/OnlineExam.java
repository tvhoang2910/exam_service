package com.exam_bank.exam_service.entity;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

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

        @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
        @JoinTable(name = "exam_tags", joinColumns = @JoinColumn(name = "exam_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))
        private Set<Tag> tags = new HashSet<>();

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
        @ColumnDefault("100")
        private Integer maxAttempts = 100;

        @Column(name = "is_randomized", comment = "Co tron thu tu cau hoi hay khong")
        @ColumnDefault("false")
        private Boolean isRandomized = false;
}
