package com.reservation.guest.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.guest.application.dto.GuestResult;
import com.reservation.guest.application.service.GuestApplicationService;
import com.reservation.guest.domain.exception.DuplicateEmailException;
import com.reservation.guest.domain.exception.GuestNotFoundException;
import com.reservation.guest.domain.model.Email;
import com.reservation.guest.domain.model.GuestId;
import com.reservation.guest.presentation.dto.ChangeGuestRequest;
import com.reservation.guest.presentation.dto.RegisterGuestRequest;
import com.reservation.guest.presentation.exception.GuestExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GuestController.class)
@Import(GuestExceptionHandler.class)
class GuestControllerTest {

    private static final String GUEST_ID = UUID.fromString("01970000-0000-7000-8000-000000000001").toString();

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    GuestApplicationService service;
    @MockBean
    Clock clock;

    @Test
    @DisplayName("POST /api/v1/guests: 201 + CommonResponse 래퍼")
    void register201() throws Exception {
        GuestResult result = new GuestResult(GUEST_ID, "길동", "홍", "hong@example.com", "+821012345678");
        when(service.register(any())).thenReturn(result);

        var request = new RegisterGuestRequest("길동", "홍", "hong@example.com", "+82-10-1234-5678");

        mockMvc.perform(post("/api/v1/guests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value(GUEST_ID))
            .andExpect(jsonPath("$.data.firstName").value("길동"))
            .andExpect(jsonPath("$.data.email").value("hong@example.com"))
            .andExpect(jsonPath("$.data.phoneNumber").value("+821012345678"));
    }

    @Test
    @DisplayName("POST /api/v1/guests: 이메일 중복은 409 DUPLICATE_EMAIL")
    void register409() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-23T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.register(any())).thenThrow(new DuplicateEmailException(new Email("hong@example.com")));

        var request = new RegisterGuestRequest("길동", "홍", "hong@example.com", "+82-10-1234-5678");

        mockMvc.perform(post("/api/v1/guests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("POST /api/v1/guests: 트랜잭션 커밋 시점 DB 제약 위반 → 409 DUPLICATE_EMAIL (race condition)")
    void register409_DB_integrity_violation() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-23T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        // Application Service 에서 try/catch 로 감싸지 못한 flush/commit 시점 예외가
        // Spring AOP 를 통해 Controller 바깥으로 올라오는 경로. ExceptionHandler 가 409 로 통일.
        when(service.register(any())).thenThrow(new DataIntegrityViolationException("uk_guest_email"));

        var request = new RegisterGuestRequest("길동", "홍", "hong@example.com", "+82-10-1234-5678");

        mockMvc.perform(post("/api/v1/guests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("POST /api/v1/guests: 잘못된 VO 입력은 400 VALIDATION_FAILED")
    void register400() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-23T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.register(any())).thenThrow(new IllegalArgumentException("Email format is invalid"));

        var request = new RegisterGuestRequest("길동", "홍", "not-an-email", "+82-10-1234-5678");

        mockMvc.perform(post("/api/v1/guests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /api/v1/guests/{id}: 200 + data 페이로드")
    void findById200() throws Exception {
        when(service.findById(GUEST_ID)).thenReturn(
            new GuestResult(GUEST_ID, "길동", "홍", "hong@example.com", "+821012345678"));

        mockMvc.perform(get("/api/v1/guests/" + GUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(GUEST_ID))
            .andExpect(jsonPath("$.data.email").value("hong@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/guests/{id}: 없는 id 는 404 GUEST_NOT_FOUND")
    void findById404() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-23T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.findById(GUEST_ID)).thenThrow(new GuestNotFoundException(GuestId.of(GUEST_ID)));

        mockMvc.perform(get("/api/v1/guests/" + GUEST_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("GUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/v1/guests/{id}: 200 + 변경된 값")
    void change200() throws Exception {
        when(service.change(any())).thenReturn(
            new GuestResult(GUEST_ID, "Alice", "Kim", "new@example.com", "+821099990000"));

        var request = new ChangeGuestRequest("Alice", "Kim", "new@example.com", "+82-10-9999-0000");

        mockMvc.perform(patch("/api/v1/guests/" + GUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.firstName").value("Alice"))
            .andExpect(jsonPath("$.data.email").value("new@example.com"));
    }

    @Test
    @DisplayName("PATCH /api/v1/guests/{id}: 없는 id 는 404 GUEST_NOT_FOUND")
    void change404() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-23T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.change(any())).thenThrow(new GuestNotFoundException(GuestId.of(GUEST_ID)));

        var request = new ChangeGuestRequest("Alice", "Kim", null, null);

        mockMvc.perform(patch("/api/v1/guests/" + GUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("GUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/v1/guests/{id}: 이메일 중복 변경은 409 DUPLICATE_EMAIL")
    void change409() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-23T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.change(any())).thenThrow(new DuplicateEmailException(new Email("taken@example.com")));

        var request = new ChangeGuestRequest(null, null, "taken@example.com", null);

        mockMvc.perform(patch("/api/v1/guests/" + GUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }
}
