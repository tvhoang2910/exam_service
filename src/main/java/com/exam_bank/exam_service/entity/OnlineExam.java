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
}, comment = "Thông tin metadata và cấu hình thi online")
public class OnlineExam extends BaseEntity {

        @Column(name = "title", nullable = false, comment = "Tiêu đề đề thi")
        private String title;

        @Column(name = "description", columnDefinition = "TEXT", comment = "Mô tả chi tiết đề thi")
        private String description;

        @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
        @JoinTable(name = "exam_tags", joinColumns = @JoinColumn(name = "exam_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))
        private Set<Tag> tags = new HashSet<>();

        @Convert(converter = OnlineExamSourceConverter.class)
        @Column(name = "source", nullable = false, length = 30, comment = "Nguồn tạo đề thi")
        @ColumnDefault("'manual_created'")
        private OnlineExamSource source = OnlineExamSource.MANUAL_CREATED;

        @Column(name = "original_file_url", length = 500, comment = "URL file gốc nếu trích xuất/import")
        private String originalFileUrl;

        @Column(name = "original_file_type", length = 100, comment = "Loại file gốc, ví dụ application/pdf")
        private String originalFileType;

        @Column(name = "duration_minutes", comment = "Thời gian làm bài (phút)")
        @ColumnDefault("60")
        private Integer durationMinutes = 60;

        @Column(name = "total_questions", comment = "Tổng số câu hỏi (cache)")
        @ColumnDefault("0")
        private Integer totalQuestions = 0;

        @Convert(converter = OnlineExamStatusConverter.class)
        @Column(name = "status", nullable = false, length = 20, comment = "Trạng thái đề thi")
        @ColumnDefault("'draft'")
        private OnlineExamStatus status = OnlineExamStatus.DRAFT;

        @Column(name = "passing_score", comment = "Điểm đạt, null nếu chưa cấu hình")
        private Integer passingScore;

        @Column(name = "max_attempts", comment = "Số lần thi tối đa")
        @ColumnDefault("100")
        private Integer maxAttempts = 100;

        @Column(name = "is_randomized", comment = "Có trộn thứ tự câu hỏi hay không")
        @ColumnDefault("false")
        private Boolean isRandomized = false;

        @Column(name = "is_premium", nullable = false, comment = "Đánh dấu đề thi chỉ dành cho tài khoản Premium")
        @ColumnDefault("false")
        private Boolean isPremium = false;

        @Column(name = "teaser_question_count", nullable = false, comment = "Số câu xem thử cho người dùng không Premium")
        @ColumnDefault("2")
        private Integer teaserQuestionCount = 2;
}
