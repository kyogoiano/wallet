package br.com.wallet.unit.interfaces.rest;

import br.com.wallet.exceptions.FraudBlockedException;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.exceptions.InsufficientFundsException;
import br.com.wallet.interfaces.rest.exception.ApiError;
import br.com.wallet.interfaces.rest.exception.ApiExceptionHandler;
import br.com.wallet.interfaces.rest.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


class ApiExceptionHandlerTest {

    private ApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApiExceptionHandler();
    }

    @Test
    @DisplayName("Should handle FraudBlockedException and return 403 Forbidden")
    void shouldHandleFraudBlockedException() {
        UUID operationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FraudBlockedException ex = new FraudBlockedException(operationId, userId);

        ResponseEntity<ApiError> response = handler.handleFraudBlocked(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.FRAUD_BLOCKED);
        assertThat(response.getBody().message()).contains("Transaction blocked due to high fraud risk");
        assertThat(response.getBody().message()).contains(operationId.toString());
        assertThat(response.getBody().message()).contains(userId.toString());
    }

    @Test
    @DisplayName("Should handle IdempotencyException and return 422 Unprocessable Content")
    void shouldHandleIdempotencyException() {
        IdempotencyException ex = new IdempotencyException("Operation already processed: 123");

        ResponseEntity<ApiError> response = handler.handleIdempotency(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.DUPLICATE_OPERATION);
        assertThat(response.getBody().message()).isEqualTo("Operation already processed: 123");
    }

    @Test
    @DisplayName("Should handle InsufficientFundsException and return 422 Unprocessable Content")
    void shouldHandleInsufficientFundsException() {
        InsufficientFundsException ex = new InsufficientFundsException();

        ResponseEntity<ApiError> response = handler.handleInsufficientFunds(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
        assertThat(response.getBody().message()).isEqualTo("Insufficient funds");
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException and return 400 Bad Request")
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ResponseEntity<ApiError> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Invalid argument");
    }

    @Test
    @DisplayName("Should handle generic Exception and return 500 Internal Server Error")
    void shouldHandleGenericException() {
        Exception ex = new RuntimeException("Something unexpected happened");

        ResponseEntity<ApiError> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Unexpected error");
    }
}
