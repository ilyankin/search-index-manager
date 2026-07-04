package com.izenkyt.searchindexmanager.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.izenkyt.searchindexmanager.index.ArtifactNotAvailableException;
import com.izenkyt.searchindexmanager.indexbuild.IndexBuildConflictException;
import com.izenkyt.searchindexmanager.indexbuild.IndexBuildException;
import com.izenkyt.searchindexmanager.storage.ArtifactStorageException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final URI PROBLEM_TYPE_BASE = URI.create("https://search-index-manager.invalid/problems/");

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        log.debug("Not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), "not-found", "Resource not found");
    }

    @ExceptionHandler(DuplicateNameException.class)
    public ProblemDetail handleDuplicateName(DuplicateNameException ex) {
        log.debug("Duplicate name: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, ex.getMessage(), "duplicate-name", "Duplicate resource");
    }

    @ExceptionHandler(IndexBuildConflictException.class)
    public ProblemDetail handleBuildConflict(IndexBuildConflictException ex) {
        log.debug("Build conflict: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, ex.getMessage(), "build-conflict", "Build conflict");
    }

    @ExceptionHandler(ArtifactNotAvailableException.class)
    public ProblemDetail handleArtifactNotAvailable(ArtifactNotAvailableException ex) {
        log.debug("Artifact not available: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, ex.getMessage(), "artifact-not-available", "Artifact not available");
    }

    @ExceptionHandler(IndexBuildException.class)
    public ProblemDetail handleIndexBuild(IndexBuildException ex) {
        log.error("Index build failed: {}", ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), "build-error", "Index build error");
    }

    @ExceptionHandler(ArtifactStorageException.class)
    public ProblemDetail handleArtifactStorage(ArtifactStorageException ex) {
        log.warn("Artifact storage unavailable: {}", ex.getMessage(), ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Artifact storage is temporarily unavailable",
                "artifact-storage-unavailable", "Artifact storage unavailable");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.debug("Data integrity violation: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Request conflicts with existing data", "conflict", "Conflict");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedRequest(HttpMessageNotReadableException ex) {
        log.debug("Malformed request body: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Malformed request body", "malformed-request", "Malformed request");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.debug("Invalid parameter '{}': {}", ex.getName(), ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' has invalid value",
                "invalid-parameter", "Invalid parameter");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        log.debug("Validation failed: {}", ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed", "validation-error", "Validation error");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String detail, String typeSuffix, String title) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(PROBLEM_TYPE_BASE.resolve(typeSuffix));
        problem.setTitle(title);
        return problem;
    }
}