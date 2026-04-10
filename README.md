# Exam Service (exam_bank)

Tai lieu nay mo ta module exam_service theo source code hien tai.

## 1. Tong quan

Exam Service phu trach toan bo exam flow cua he thong:

- Quan ly de thi online (CRUD, status, danh muc tag).
- Public exam listing + attempt view.
- Thi online: start attempt, luu dap an, nop bai, xem ket qua, xem lich su.
- Question reporting workflow cho USER va queue review cho ADMIN/CONTRIBUTOR.
- Content analytics cho question/exam + recalculate difficulty.
- Phat ExamSubmittedEvent qua RabbitMQ de study_service consume.
- SSE real-time event cho ADMIN/CONTRIBUTOR.

Context path mac dinh:

- /api/v1/exam

Port mac dinh:

- 8082

## 2. Tech stack

- Java 21
- Spring Boot 4.0.3
- Spring MVC + Validation
- Spring Security OAuth2 Resource Server (JWT HS256)
- Spring Data JPA (PostgreSQL)
- Redis (cache/session related flows)
- RabbitMQ (exam submitted events + admin alerts)
- Maven Wrapper (mvnw/mvnw.cmd)

## 3. Runtime requirements

Bat buoc:

- PostgreSQL
- Redis
- RabbitMQ
- JDK 21

Health check:

- GET /api/v1/exam/actuator/health

## 4. Chay local

### 4.1 Environment variables

```bash
PORT=8082

DATABASE_URL=jdbc:postgresql://localhost:5432/exam_bank_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres

JWT_ISSUER=auth_service
JWT_SECRET_BASE64=<same-base64-secret-as-auth-service>

CORS_ALLOWED_ORIGINS=http://localhost:5173

REDIS_HOST=localhost
REDIS_PORT=6379

RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

JPA_SHOW_SQL=false
HIBERNATE_SQL_LOG_LEVEL=WARN
HIBERNATE_BIND_LOG_LEVEL=WARN

REPORTING_AUTO_HIDE_THRESHOLD=5
```

Luu y quan trong:

- JWT_ISSUER va JWT_SECRET_BASE64 phai trung voi auth_service.
- CORS_ALLOWED_ORIGINS can chua origin frontend (thuong la http://localhost:5173).

### 4.2 Run service

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
./mvnw spring-boot:run
```

Build package:

```powershell
.\mvnw.cmd clean package
```

Run tests:

```powershell
.\mvnw.cmd test
```

## 5. API map

Tat ca endpoint ben duoi la relative path theo context /api/v1/exam.

### 5.1 Public endpoints

- GET /exams/public
- GET /exams/public/{examId}
- GET /exams/public/{examId}/attempt-view
- GET /actuator/health

Ghi chu SSE:

- GET /sse/events la endpoint permitAll o filter chain, nhung controller van yeu cau token hop le va chi role ADMIN/CONTRIBUTOR moi duoc stream.

### 5.2 User/Contributor/Admin endpoints

- GET /me
- POST /attempts
- PUT /attempts/{attemptId}/answers
- PUT /attempts/{attemptId}/answers/batch
- POST /attempts/{attemptId}/submit
- GET /attempts/{attemptId}/result
- GET /users/me/attempts
- POST /attempts/{attemptId}/questions/{questionId}/reports
- GET /users/me/reports

### 5.3 Contributor/Admin management endpoints

- GET /tags
- POST /tags
- POST /exams
- GET /exams/manage
- GET /exams/manage/{examId}
- PUT /exams/{examId}
- DELETE /exams/{examId}
- PATCH /exams/{examId}/status
- GET /admin/reports
- GET /admin/reports/processed
- GET /admin/reports/questions/{questionId}
- GET /admin/reports/questions/{questionId}/history
- PUT /admin/reports/questions/{questionId}/resolve

### 5.4 Admin-only endpoint

- GET /admin/ping

### 5.5 Analytics endpoints

- GET /analytics/questions/{questionId}
- GET /analytics/exams/{examId}
- POST /analytics/admin/questions/recalculate-difficulty

## 6. Messaging va event contract

### 6.1 RabbitMQ publisher

Exam service publish event khi submit attempt:

- Exchange: exam.events
- Routing key: exam.submitted
- Queue (local bind): exam.events.queue

### 6.2 ExamSubmittedEvent payload

Field chinh:

- attemptId, userId, examId, examTitle
- submittedAt, scoreRaw, scoreMax, scorePercent, durationSeconds
- questions[]:
  - questionId, isCorrect, earnedScore, maxScore
  - selectedOptionIds, correctOptionIds
  - responseTimeMs, answerChangeCount, difficulty, tagIds
- examTags[]: tagId, tagName

Study service consume payload nay de tao du lieu review + gamification.

### 6.3 SSE exam events

ExamSseController stream event qua:

- GET /sse/events

Dieu kien:

- Token co claim role = ADMIN hoac CONTRIBUTOR.
- Ho tro token qua Authorization header hoac query param token.

## 7. Security notes

- Service chay stateless.
- JWT decoder validate issuer + role claim + userId claim > 0.
- role claim duoc map thanh authority ROLE_<role>.
- Rule role chinh:
  - /attempts/** va /users/me/attempts: USER, ADMIN, CONTRIBUTOR
  - /tags/** va /exams/manage/** va /exams/**: ADMIN, CONTRIBUTOR
  - /admin/**: ADMIN (ngoai le reporting routes da cho ADMIN/CONTRIBUTOR o matcher rieng)

## 8. Test scope hien co

Module dang co test cho:

- Sm2ServiceTest
- DifficultyRecalculationServiceTest
- ExamInfrastructureIntegrationTest
- ExamSubmittedEventContractTest

Luu y:

- Cac test integration/contract co su dung Testcontainers (PostgreSQL/RabbitMQ) va can Docker runtime hoat dong o JVM context.

## 9. Source references

Cac file can doc nhanh:

- src/main/resources/application.properties
- src/main/java/com/exam_bank/exam_service/config/SecurityConfig.java
- src/main/java/com/exam_bank/exam_service/config/RabbitConfig.java
- src/main/java/com/exam_bank/exam_service/service/ExamAttemptService.java
- src/main/java/com/exam_bank/exam_service/service/RabbitMQEventPublisher.java
- src/main/java/com/exam_bank/exam_service/controller/ExamManagementController.java
- src/main/java/com/exam_bank/exam_service/controller/ExamAttemptController.java
- src/main/java/com/exam_bank/exam_service/feature/reporting/controller/QuestionReportController.java
- src/main/java/com/exam_bank/exam_service/feature/analytics/controller/ContentAnalyticsController.java
