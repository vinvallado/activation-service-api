package com.deroyque.activationserviceapi.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ActivationNotFoundException.class)
    ProblemDetail notFound(ActivationNotFoundException exception, HttpServletRequest request) { return problem(HttpStatus.NOT_FOUND, exception.getMessage(), request); }
    @ExceptionHandler(ActivationConflictException.class)
    ProblemDetail conflict(ActivationConflictException exception, HttpServletRequest request) { return problem(HttpStatus.CONFLICT, exception.getMessage(), request); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var detail=problem(HttpStatus.BAD_REQUEST,"Um ou mais campos não passaram na validação.",request);
        detail.setProperty("errors",exception.getBindingResult().getFieldErrors().stream().map(this::field).toList()); return detail;
    }
    private Map<String,String> field(FieldError error) { return Map.of("field",error.getField(),"message",error.getDefaultMessage()); }
    private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        var result=ProblemDetail.forStatusAndDetail(status,detail); result.setTitle(status.getReasonPhrase());
        result.setProperty("correlationId",request.getAttribute("correlationId")); return result;
    }
}
