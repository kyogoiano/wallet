package br.com.wallet.interfaces.rest.exception;

import br.com.wallet.exceptions.InsufficientFundsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Basic global exception handler,
 * TODO: when fine graining exceptions, evolve this handler to include other error codes
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCode.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleBusiness(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError(ErrorCode.INSUFFICIENT_FUNDS, ex.getMessage()));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(ErrorCode.INTERNAL_ERROR, "Unexpected error"));
    }

    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity
                .badRequest()
                .body(new ApiError(
                        ErrorCode.MISSING_HEADER,
                        "Required header '%s' is missing".formatted(ex.getHeaderName())
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCode.VALIDATION_ERROR, "Invalid request body"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCode.VALIDATION_ERROR, "Invalid request!"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleValidation(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCode.VALIDATION_ERROR, "Invalid request parameters: %s".formatted(ex.getParameterName())));
    }


    @ExceptionHandler(HttpMessageConversionException.class)
    public ResponseEntity<ApiError> handleValidation(HttpMessageConversionException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCode.BAD_REQUEST, "Invalid request: %s".formatted(ex.getMessage())));
    }

}
