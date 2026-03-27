Kế hoạch triển khai (chưa code)

Chốt phạm vi nghiệp vụ
Quy định 2 chế độ API:
preview (xem đề): không trả đáp án đúng.
attempt (làm bài): chỉ trả dữ liệu cần làm bài.
Chốt loại chấm điểm:
Trắc nghiệm 1 đáp án đúng.
Có thể mở rộng nhiều đáp án đúng sau.
Chốt policy:
giới hạn số lần làm (maxAttempts)
hết giờ tự nộp
có cho sửa đáp án trước khi nộp hay không.
Thiết kế dữ liệu BE
Thêm bảng exam_attempts
id, exam_id, user_id, status (IN_PROGRESS/SUBMITTED/AUTO_SUBMITTED)
started_at, submitted_at, duration_seconds
score_raw, score_max, score_percent, passed
metadata: source (WEB), client_version
Thêm bảng exam_attempt_answers
attempt_id, question_id
selected_option_ids (json/text)
is_correct, earned_score, max_score
response_time_ms, answer_change_count
Thêm bảng question_review_events (output trung gian cho SM2)
user_id, question_id, attempt_id, evaluated_at
quality_0_to_5, is_correct, latency_ms, confidence(optional)
tag_ids/topic_ids snapshot
API backend
GET /exams/public/{id}/attempt-view
Trả câu hỏi + options, không có isCorrect.
POST /exams/{id}/attempts
Tạo phiên làm bài, trả attemptId, expiresAt.
PUT /attempts/{attemptId}/answers
Lưu đáp án tạm (autosave).
POST /attempts/{attemptId}/submit
Chấm điểm, lưu kết quả, ghi review events cho SM2.
GET /attempts/{attemptId}/result
Trả kết quả chi tiết cho UI.
GET /users/me/attempts
Lịch sử làm bài.
UI/UX frontend
Trang PublicExams.tsx thêm nút “Làm bài”.
Tạo trang mới ExamAttempt.tsx
timer, điều hướng câu hỏi, autosave mỗi 10-15s.
Tạo trang ExamResult.tsx
điểm, pass/fail, phân tích theo tag/chủ đề.
Chặn thoát trang khi chưa nộp bài.

Khi hết giờ gọi submit tự động.

Đầu ra chuẩn cho SM2 (quan trọng nhất)

SM2 cần input theo từng “item học” (nên dùng questionId hoặc conceptId).

Output sau khi nộp bài nên chuẩn hóa thành record như sau:

userId
itemId (questionId hoặc conceptId)
attemptId
evaluatedAt
quality (0..5)
latencyMs
isCorrect
topicTagIds
difficulty
source (EXAM_SUBMISSION)
Đề xuất mapping quality ban đầu (để dùng ngay với SM2):
5: đúng, nhanh (latency <= ngưỡng), không đổi đáp án.
4: đúng, thời gian trung bình.
3: đúng nhưng chậm hoặc đổi đáp án nhiều.
2: sai nhưng gần đúng (nếu có heuristic) hoặc đúng nhờ đoán/chậm bất thường.
1: sai rõ.
0: bỏ trống.
Công thức SM2 dùng trực tiếp:
input: quality, repetition, interval, easinessFactor
output: nextReviewAt, nextIntervalDays, nextEasinessFactor, repetition.
Bảo mật và tính đúng

Tách DTO public preview khỏi DTO quản trị để không lộ đáp án đúng.

Endpoint submit bắt buộc user authenticated.

Lưu snapshot đề/câu hỏi trong attempt để chống sai lệch khi đề bị sửa sau này.

Log chống submit lặp và idempotency key cho submit.

Test plan

Unit test chấm điểm theo scoreWeight.

Test hết giờ auto-submit.

Test giới hạn số lần làm.

Test không lộ isCorrect ở API làm bài.

Test sinh output SM2 đúng format.