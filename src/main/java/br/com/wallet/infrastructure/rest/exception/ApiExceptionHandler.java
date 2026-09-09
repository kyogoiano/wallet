package br.com.wallet.infrastructure.rest.exception;

import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.ledger.api.exceptions.FraudBlockedException;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Basic global exception handler.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCode.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<ApiError> handleNotFound(java.util.NoSuchElementException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(ErrorCode.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        log.warn("Response status exception [{}]: {}", ex.getStatusCode(), ex.getReason());
        ErrorCode code = ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                ? ErrorCode.NOT_FOUND
                : ErrorCode.BAD_REQUEST;
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiError(code, message));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFunds(InsufficientFundsException ex) {
        log.warn("Insufficient funds: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError(ErrorCode.INSUFFICIENT_FUNDS, ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyException.class)
    public ResponseEntity<ApiError> handleIdempotency(IdempotencyException ex) {
        log.warn("Idempotency error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError(ErrorCode.DUPLICATE_OPERATION, ex.getMessage()));
    }

    @ExceptionHandler(FraudBlockedException.class)
    public ResponseEntity<ApiError> handleFraudBlocked(FraudBlockedException ex) {
        log.warn("Fraud blocked: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN) // 403 Forbidden or 422 Unprocessable Entity
                .body(new ApiError(ErrorCode.FRAUD_BLOCKED, ex.getMessage()));
    }

    @ExceptionHandler(br.com.wallet.core.exceptions.AccountBlockedException.class)
    public ResponseEntity<ApiError> handleAccountBlocked(br.com.wallet.core.exceptions.AccountBlockedException ex) {
        log.warn("Account blocked: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(ErrorCode.ACCOUNT_BLOCKED, ex.getMessage()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex) {
        log.warn("Method validation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError(ErrorCode.VALIDATION_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<ApiError> handleMissingValue(MissingRequestValueException ex) {
        String message = ex instanceof MissingRequestHeaderException headerEx 
                ? "Required header '%s' is missing".formatted(headerEx.getHeaderName())
                : "Required request value is missing";
        
        log.warn("Missing request value: {}", message);
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCode.MISSING_HEADER, message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("Argument not valid: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCode.VALIDATION_ERROR, "Invalid request body"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCode.VALIDATION_ERROR, "Invalid request value type"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing parameter: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCode.VALIDATION_ERROR, "Invalid request parameters: %s".formatted(ex.getParameterName())));
    }

    @ExceptionHandler(HttpMessageConversionException.class)
    public ResponseEntity<ApiError> handleMessageConversion(HttpMessageConversionException ex) {
        log.warn("Message conversion error: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCode.BAD_REQUEST, "Invalid request format"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(ErrorCode.INTERNAL_ERROR, "Unexpected error"));
    }
}
