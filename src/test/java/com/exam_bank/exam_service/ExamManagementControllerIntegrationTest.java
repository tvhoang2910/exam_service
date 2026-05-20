package com.exam_bank.exam_service;

import com.exam_bank.exam_service.dto.CreateExamRequest;
import com.exam_bank.exam_service.dto.ExamResponse;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
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
 * Integration tests for ExamManagementController.
 * Uses running PostgreSQL and Redis.
 * Mocks RabbitMQ, MinIO, and AdminAlertPublisher.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@DisplayName("ExamManagementController Integration Tests")
class ExamManagementControllerIntegrationTest {

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

    private static final String BASE = "/api/v1/exam";

    private JwtEncoder jwtEncoder;
    private final List<Long> createdExamIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        SecretKey secretKey = new SecretKeySpec(Base64.getDecoder().decode(jwtSecretBase64), "HmacSHA256");
        jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));

        doNothing().when(rabbitMQEventPublisher).publishExamSyncEvent(any());
        doNothing().when(rabbitMQEventPublisher).publishExamSubmitted(any());
        when(authUserLookupClient.findDisplayNameByUserId(any())).thenReturn(Optional.of("Test User"));
        when(authUserLookupClient.findPremiumStatusByUserId(any())).thenReturn(Optional.of(false));
    }

    @AfterEach
    void cleanup() {
        for (Long id : createdExamIds) {
            examRepository.findById(id).ifPresent(exam -> {
                // Direct delete to avoid cascade issues
                try {
                    examRepository.deleteById(id);
                } catch (Exception ignored) {
                }
            });
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

    private ExamResponse createExamViaApi(String adminToken) {
        CreateExamRequest request = buildCreateExamRequest("Integration Test Exam " + UUID.randomUUID().toString().substring(0, 8));
        HttpHeaders headers = bearerJsonHeaders(adminToken);
        ResponseEntity<ExamResponse> response = restTemplate.exchange(
                BASE + "/exams", HttpMethod.POST,
                new HttpEntity<>(request, headers), ExamResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ExamResponse body = response.getBody();
        assertThat(body).isNotNull();
        createdExamIds.add(body.getId());
        return body;
    }

    private CreateExamRequest buildCreateExamRequest(String title) {
        CreateExamRequest request = new CreateExamRequest();
        request.setTitle(title);
        request.setDescription("A test exam description");
        request.setDurationMinutes(60);
        request.setPassingScore(5);
        request.setMaxAttempts(3);
        request.setPremium(false);
        request.setTeaserQuestionCount(2);

        // Add one question with two options
        CreateExamRequest.OptionDto opt1 = new CreateExamRequest.OptionDto("Option A", true);
        CreateExamRequest.OptionDto opt2 = new CreateExamRequest.OptionDto("Option B", false);
        CreateExamRequest.QuestionDto question = new CreateExamRequest.QuestionDto(
                "What is 2+2?", "Simple addition", 1.0, List.of(opt1, opt2));
        request.setQuestions(List.of(question));

        return request;
    }

    // ─── CREATE EXAM Tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("createExam_asAdmin_shouldReturn200WithExamResponse")
    void createExam_asAdmin_shouldReturn200WithExamResponse() {
        String adminToken = generateToken(1L, "ADMIN");
        CreateExamRequest request = buildCreateExamRequest("IT Create Test " + UUID.randomUUID().toString().substring(0, 8));

        HttpHeaders headers = bearerJsonHeaders(adminToken);
        ResponseEntity<ExamResponse> response = restTemplate.exchange(
                BASE + "/exams", HttpMethod.POST,
                new HttpEntity<>(request, headers), ExamResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isPositive();
        assertThat(response.getBody().getTitle()).isEqualTo(request.getTitle());
        assertThat(response.getBody().getStatus()).isEqualTo(OnlineExamStatus.DRAFT);

        createdExamIds.add(response.getBody().getId());
    }

    @Test
    @DisplayName("createExam_unauthenticated_shouldReturn401")
    void createExam_unauthenticated_shouldReturn401() {
        CreateExamRequest request = buildCreateExamRequest("Unauthenticated Exam");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                BASE + "/exams", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── PUBLIC EXAM Tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("getPublicExams_noAuth_shouldReturn200WithList")
    void getPublicExams_noAuth_shouldReturn200WithList() {
        ResponseEntity<List> response = restTemplate.getForEntity(
                BASE + "/exams/public", List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("getPublicExam_byId_whenPublished_shouldReturn200")
    void getPublicExam_byId_whenPublished_shouldReturn200() {
        // Create exam and publish it
        String adminToken = generateToken(1L, "ADMIN");
        ExamResponse created = createExamViaApi(adminToken);

        // Publish the exam
        restTemplate.exchange(
                BASE + "/exams/" + created.getId() + "/status?status=PUBLISHED",
                HttpMethod.PATCH,
                new HttpEntity<>(bearerHeaders(adminToken)), ExamResponse.class);

        // Now fetch public
        ResponseEntity<ExamResponse> response = restTemplate.getForEntity(
                BASE + "/exams/public/" + created.getId(), ExamResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("getPublicExam_notFound_shouldReturn404")
    void getPublicExam_notFound_shouldReturn404() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                BASE + "/exams/public/999999999", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getPublicExam_draftExam_shouldReturn404")
    void getPublicExam_draftExam_shouldReturn404() {
        String adminToken = generateToken(1L, "ADMIN");
        ExamResponse created = createExamViaApi(adminToken);

        // DRAFT exams should not be accessible from public endpoint
        ResponseEntity<Map> response = restTemplate.getForEntity(
                BASE + "/exams/public/" + created.getId(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── MANAGED EXAM Tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("getManagedExams_asAdmin_shouldReturn200WithList")
    void getManagedExams_asAdmin_shouldReturn200WithList() {
        String adminToken = generateToken(1L, "ADMIN");
        createExamViaApi(adminToken);

        HttpHeaders headers = bearerHeaders(adminToken);
        ResponseEntity<List> response = restTemplate.exchange(
                BASE + "/exams/manage", HttpMethod.GET,
                new HttpEntity<>(headers), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("getManagedExams_asRegularUser_shouldReturn403")
    void getManagedExams_asRegularUser_shouldReturn403() {
        String userToken = generateToken(2L, "USER");
        HttpHeaders headers = bearerHeaders(userToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE + "/exams/manage", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── UPDATE EXAM Tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("updateExam_asAdmin_shouldReturn200WithUpdatedExam")
    void updateExam_asAdmin_shouldReturn200WithUpdatedExam() {
        String adminToken = generateToken(1L, "ADMIN");
        ExamResponse created = createExamViaApi(adminToken);

        CreateExamRequest updateRequest = buildCreateExamRequest("Updated Title " + UUID.randomUUID().toString().substring(0, 8));

        HttpHeaders headers = bearerJsonHeaders(adminToken);
        ResponseEntity<ExamResponse> response = restTemplate.exchange(
                BASE + "/exams/" + created.getId(), HttpMethod.PUT,
                new HttpEntity<>(updateRequest, headers), ExamResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo(updateRequest.getTitle());
    }

    // ─── DELETE EXAM Tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteExam_asAdmin_shouldReturn204")
    void deleteExam_asAdmin_shouldReturn204() {
        String adminToken = generateToken(1L, "ADMIN");
        ExamResponse created = createExamViaApi(adminToken);
        // Remove from cleanup list since we're deleting manually
        createdExamIds.remove(created.getId());

        HttpHeaders headers = bearerHeaders(adminToken);
        ResponseEntity<Void> response = restTemplate.exchange(
                BASE + "/exams/" + created.getId(), HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ─── UPDATE STATUS Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("updateExamStatus_toPublished_shouldReturn200WithPublishedStatus")
    void updateExamStatus_toPublished_shouldReturn200WithPublishedStatus() {
        String adminToken = generateToken(1L, "ADMIN");
        ExamResponse created = createExamViaApi(adminToken);

        HttpHeaders headers = bearerHeaders(adminToken);
        ResponseEntity<ExamResponse> response = restTemplate.exchange(
                BASE + "/exams/" + created.getId() + "/status?status=PUBLISHED",
                HttpMethod.PATCH,
                new HttpEntity<>(headers), ExamResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(OnlineExamStatus.PUBLISHED);
    }
}
