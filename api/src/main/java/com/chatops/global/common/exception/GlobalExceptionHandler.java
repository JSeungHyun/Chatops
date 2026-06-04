package com.chatops.global.common.exception;

import com.chatops.global.common.dto.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> messages = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.toList());

        ErrorResponse response = ErrorResponse.builder()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .message(messages)
            .error("Bad Request")
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String errorName = status != null ? status.getReasonPhrase() : "Error";

        ErrorResponse response = ErrorResponse.builder()
            .statusCode(ex.getStatusCode().value())
            .message(ex.getReason() != null ? ex.getReason() : ex.getMessage())
            .error(errorName)
            .build();

        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ErrorResponse response = ErrorResponse.builder()
            .statusCode(HttpStatus.PAYLOAD_TOO_LARGE.value())
            .message("업로드 가능한 파일 크기를 초과했습니다 (이미지/문서 10MB, 동영상 50MB)")
            .error("Payload Too Large")
            .build();

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        ErrorResponse response = ErrorResponse.builder()
            .statusCode(HttpStatus.CONFLICT.value())
            .message("Data integrity violation: resource already exists")
            .error("Conflict")
            .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception: ", ex);

        ErrorResponse response = ErrorResponse.builder()
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .message("Internal server error")
            .error("Internal Server Error")
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
