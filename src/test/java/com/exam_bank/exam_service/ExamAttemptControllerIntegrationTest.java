package com.exam_bank.exam_service;

import com.exam_bank.exam_service.dto.AttemptResultResponse;
import com.exam_bank.exam_service.dto.CreateExamRequest;
import com.exam_bank.exam_service.dto.ExamResponse;
import com.exam_bank.exam_service.dto.SaveAttemptAnswerRequest;
import com.exam_bank.exam_service.dto.StartAttemptRequest;
import com.exam_bank.exam_service.dto.StartAttemptResponse;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.service.AdminAlertPublisher;
import com.exam_bank.exam_service.service.AuthUserLookupClient;
import com.exam_bank.exam_service.service.MinioService;
import com.exam_bank.exam_service.service.RabbitMQEventPublisher;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Integration tests for ExamAttemptController.
 * Creates published exams and performs attempts against them.
 * Mocks RabbitMQ, MinIO, and external auth lookup calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@DisplayName("ExamAttemptController Integration Tests")
class ExamAttemptControllerIntegrationTest {

        @Autowired
        private TestRestTemplate restTemplate;

        @Autowired
        private OnlineExamRepository examRepository;

        @MockitoBean
        private RabbitMQEventPublisher rabbitMQEventPublisher;

        @MockitoBean
        private MinioService minioService;

        @MockitoBean
        private AdminAlertPublisher adminAlertPublisher;

        @MockitoBean
        private AuthUserLookupClient authUserLookupClient;

        @Value("${auth.jwt.secret}")
        private String jwtSecretBase64;

        @Value("${auth.jwt.issuer}")
        private String jwtIssuer;

        private static final String BASE = "";

        private JwtEncoder jwtEncoder;
        private final List<Long> createdExamIds = new ArrayList<>();

        /** User ID used for attempt tests. */
        private static final Long TEST_USER_ID = 42L;

        @BeforeEach
        void setup() {
                SecretKey secretKey = new SecretKeySpec(Base64.getDecoder().decode(jwtSecretBase64), "HmacSHA256");
                jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));

                doNothing().when(rabbitMQEventPublisher).publishExamSyncEvent(any());
                doNothing().when(rabbitMQEventPublisher).publishExamSubmitted(any());
                doNothing().when(adminAlertPublisher).publishExamSubmittedAlert(any(), any(), any(), any());
                when(authUserLookupClient.findDisplayNameByUserId(any())).thenReturn(Optional.of("Test Student"));
                when(authUserLookupClient.findPremiumStatusByUserId(any())).thenReturn(Optional.of(false));
        }

        @AfterEach
        void cleanup() {
                for (Long id : createdExamIds) {
                        try {
                                examRepository.deleteById(id);
                        } catch (Exception ignored) {
                        }
                }
                createdExamIds.clear();
        }

        // ─── Helpers ──────────────────────────────────────────────────────────────

        private String generateToken(Long userId, String role) {
                Instant now = Instant.now();
                JwtClaimsSet claims = JwtClaimsSet.builder()
                                .issuer(jwtIssuer)
                                .subject("user" + userId + "@test.com")
                                .issuedAt(now)
                                .expiresAt(now.plusSeconds(3600))
                                .claim("userId", userId)
                                .claim("role", role)
                                .build();
                JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
                return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        }

        private HttpHeaders bearerHeaders(String token) {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(token);
                return headers;
        }

        private HttpHeaders bearerJsonHeaders(String token) {
                HttpHeaders headers = bearerHeaders(token);
                headers.setContentType(MediaType.APPLICATION_JSON);
                return headers;
        }

        /**
         * Creates a published exam with 1 question and returns its ID.
         * Uses admin token for creation and status change.
         */
        private Long createPublishedExam() {
                String adminToken = generateToken(1L, "ADMIN");
                String title = "Attempt Test Exam " + UUID.randomUUID().toString().substring(0, 8);

                CreateExamRequest request = new CreateExamRequest();
                request.setTitle(title);
                request.setDescription("For attempt tests");
                request.setDurationMinutes(60);
                request.setPassingScore(5);
                request.setMaxAttempts(10);
                request.setPremium(false);
                request.setTeaserQuestionCount(1);

                CreateExamRequest.OptionDto opt1 = new CreateExamRequest.OptionDto("Correct Answer", true);
                CreateExamRequest.OptionDto opt2 = new CreateExamRequest.OptionDto("Wrong Answer", false);
                CreateExamRequest.QuestionDto question = new CreateExamRequest.QuestionDto(
                                "What is 1+1?", "Basic arithmetic", 1.0, List.of(opt1, opt2));
                request.setQuestions(List.of(question));

                HttpHeaders headers = bearerJsonHeaders(adminToken);
                ResponseEntity<ExamResponse> createResponse = restTemplate.exchange(
                                BASE + "/exams", HttpMethod.POST,
                                new HttpEntity<>(request, headers), ExamResponse.class);
                assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                Long examId = createResponse.getBody().getId();
                createdExamIds.add(examId);

                // Publish
                ResponseEntity<ExamResponse> publishResponse = restTemplate.exchange(
                                BASE + "/exams/" + examId + "/status?status=PUBLISHED",
                                HttpMethod.PATCH,
                                new HttpEntity<>(bearerHeaders(adminToken)), ExamResponse.class);
                assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(publishResponse.getBody().getStatus()).isEqualTo(OnlineExamStatus.PUBLISHED);

                return examId;
        }

        private ExamResponse getPublicExamWithQuestions(Long examId) {
                ResponseEntity<ExamResponse> response = restTemplate.exchange(
                                BASE + "/exams/manage/" + examId,
                                HttpMethod.GET,
                                new HttpEntity<>(bearerHeaders(generateToken(1L, "ADMIN"))),
                                ExamResponse.class);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                return response.getBody();
        }

        // ─── START ATTEMPT Tests ──────────────────────────────────────────────────

        @Test
        @DisplayName("startAttempt_success_shouldReturn200WithAttemptResponse")
        void startAttempt_success_shouldReturn200WithAttemptResponse() {
                Long examId = createPublishedExam();
                String userToken = generateToken(TEST_USER_ID, "USER");

                StartAttemptRequest request = new StartAttemptRequest();
                request.setExamId(examId);

                HttpHeaders headers = bearerJsonHeaders(userToken);
                ResponseEntity<StartAttemptResponse> response = restTemplate.exchange(
                                BASE + "/attempts", HttpMethod.POST,
                                new HttpEntity<>(request, headers), StartAttemptResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getAttemptId()).isPositive();
                assertThat(response.getBody().getExamId()).isEqualTo(examId);
                assertThat(response.getBody().getStartedAt()).isNotNull();
                assertThat(response.getBody().getExpiresAt()).isNotNull();
        }

        @Test
        @DisplayName("startAttempt_unauthenticated_shouldReturn401")
        void startAttempt_unauthenticated_shouldReturn401() {
                StartAttemptRequest request = new StartAttemptRequest();
                request.setExamId(1L);

                ResponseEntity<Map> response = restTemplate.postForEntity(
                                BASE + "/attempts", request, Map.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("startAttempt_examNotFound_shouldReturn404")
        void startAttempt_examNotFound_shouldReturn404() {
                String userToken = generateToken(TEST_USER_ID, "USER");

                StartAttemptRequest request = new StartAttemptRequest();
                request.setExamId(999999999L);

                HttpHeaders headers = bearerJsonHeaders(userToken);
                ResponseEntity<Map> response = restTemplate.exchange(
                                BASE + "/attempts", HttpMethod.POST,
                                new HttpEntity<>(request, headers), Map.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        // ─── SAVE ANSWER Tests ────────────────────────────────────────────────────

        @Test
        @DisplayName("saveAnswer_success_shouldReturn204")
        void saveAnswer_success_shouldReturn204() {
                Long examId = createPublishedExam();
                String userToken = generateToken(TEST_USER_ID, "USER");

                // Start an attempt
                StartAttemptRequest startRequest = new StartAttemptRequest();
                startRequest.setExamId(examId);
                HttpHeaders headers = bearerJsonHeaders(userToken);
                ResponseEntity<StartAttemptResponse> startResponse = restTemplate.exchange(
                                BASE + "/attempts", HttpMethod.POST,
                                new HttpEntity<>(startRequest, headers), StartAttemptResponse.class);
                Long attemptId = startResponse.getBody().getAttemptId();

                // Get first question
                ExamResponse examDetail = getPublicExamWithQuestions(examId);
                assertThat(examDetail.getQuestions()).isNotEmpty();
                Long questionId = examDetail.getQuestions().get(0).getId();
                Long optionId = examDetail.getQuestions().get(0).getOptions().get(0).getId();

                // Save an answer
                SaveAttemptAnswerRequest answerRequest = new SaveAttemptAnswerRequest();
                answerRequest.setQuestionId(questionId);
                answerRequest.setSelectedOptionIds(List.of(optionId));
                answerRequest.setResponseTimeMs(5000L);
                answerRequest.setAnswerChangeCount(0);

                ResponseEntity<Void> saveResponse = restTemplate.exchange(
                                BASE + "/attempts/" + attemptId + "/answers", HttpMethod.PUT,
                                new HttpEntity<>(answerRequest, headers), Void.class);

                assertThat(saveResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        // ─── SUBMIT ATTEMPT Tests ─────────────────────────────────────────────────

        @Test
        @DisplayName("submitAttempt_success_shouldReturn200WithResultResponse")
        void submitAttempt_success_shouldReturn200WithResultResponse() {
                Long examId = createPublishedExam();
                // Use a unique user ID so this test doesn't interfere with others
                Long uniqueUserId = 1000L + (long) (Math.random() * 9000);
                String userToken = generateToken(uniqueUserId, "USER");

                // Start an attempt
                StartAttemptRequest startRequest = new StartAttemptRequest();
                startRequest.setExamId(examId);
                HttpHeaders headers = bearerJsonHeaders(userToken);
                ResponseEntity<StartAttemptResponse> startResponse = restTemplate.exchange(
                                BASE + "/attempts", HttpMethod.POST,
                                new HttpEntity<>(startRequest, headers), StartAttemptResponse.class);
                assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                Long attemptId = startResponse.getBody().getAttemptId();

                // Submit
                ResponseEntity<AttemptResultResponse> submitResponse = restTemplate.exchange(
                                BASE + "/attempts/" + attemptId + "/submit", HttpMethod.POST,
                                new HttpEntity<>(bearerHeaders(userToken)), AttemptResultResponse.class);

                assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(submitResponse.getBody()).isNotNull();
                assertThat(submitResponse.getBody().getAttemptId()).isEqualTo(attemptId);
                assertThat(submitResponse.getBody().getExamId()).isEqualTo(examId);
                assertThat(submitResponse.getBody().getScoreRaw()).isNotNull();
                assertThat(submitResponse.getBody().getScoreMax()).isPositive();
        }

        // ─── GET ATTEMPT RESULT Tests ──────────────────────────────────────────────

        @Test
        @DisplayName("getAttemptResult_afterSubmit_shouldReturn200WithResult")
        void getAttemptResult_afterSubmit_shouldReturn200WithResult() {
                Long examId = createPublishedExam();
                Long uniqueUserId = 2000L + (long) (Math.random() * 9000);
                String userToken = generateToken(uniqueUserId, "USER");

                // Start attempt
                StartAttemptRequest startRequest = new StartAttemptRequest();
                startRequest.setExamId(examId);
                HttpHeaders headers = bearerJsonHeaders(userToken);
                ResponseEntity<StartAttemptResponse> startResponse = restTemplate.exchange(
                                BASE + "/attempts", HttpMethod.POST,
                                new HttpEntity<>(startRequest, headers), StartAttemptResponse.class);
                Long attemptId = startResponse.getBody().getAttemptId();

                // Submit
                restTemplate.exchange(BASE + "/attempts/" + attemptId + "/submit",
                                HttpMethod.POST, new HttpEntity<>(bearerHeaders(userToken)),
                                AttemptResultResponse.class);

                // Get result
                ResponseEntity<AttemptResultResponse> resultResponse = restTemplate.exchange(
                                BASE + "/attempts/" + attemptId + "/result", HttpMethod.GET,
                                new HttpEntity<>(bearerHeaders(userToken)), AttemptResultResponse.class);

                assertThat(resultResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(resultResponse.getBody()).isNotNull();
                assertThat(resultResponse.getBody().getAttemptId()).isEqualTo(attemptId);
                assertThat(resultResponse.getBody().getStatus()).isNotNull();
        }

        @Test
        @DisplayName("getAttemptResult_forAnotherUser_shouldReturn404")
        void getAttemptResult_forAnotherUser_shouldReturn404() {
                Long examId = createPublishedExam();
                Long ownerUserId = 3000L + (long) (Math.random() * 9000);
                Long otherUserId = 4000L + (long) (Math.random() * 9000);

                String ownerToken = generateToken(ownerUserId, "USER");
                String otherToken = generateToken(otherUserId, "USER");

                // Owner starts and submits
                StartAttemptRequest startRequest = new StartAttemptRequest();
                startRequest.setExamId(examId);
                ResponseEntity<StartAttemptResponse> startResponse = restTemplate.exchange(
                                BASE + "/attempts", HttpMethod.POST,
                                new HttpEntity<>(startRequest, bearerJsonHeaders(ownerToken)),
                                StartAttemptResponse.class);
                Long attemptId = startResponse.getBody().getAttemptId();

                restTemplate.exchange(BASE + "/attempts/" + attemptId + "/submit",
                                HttpMethod.POST, new HttpEntity<>(bearerHeaders(ownerToken)),
                                AttemptResultResponse.class);

                // Other user tries to access
                ResponseEntity<Map> response = restTemplate.exchange(
                                BASE + "/attempts/" + attemptId + "/result", HttpMethod.GET,
                                new HttpEntity<>(bearerHeaders(otherToken)), Map.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
}
