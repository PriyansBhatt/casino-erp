package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Do not expose SQL, storage paths or document internals in Accounts error responses. */
@RestControllerAdvice(assignableTypes=AccountsController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccountsExceptionHandler {
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> malformed(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Invalid Accounts request body; client-controlled authority fields are not accepted."));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> conflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(ApiResponse.error("Accounts record conflicts with existing data. Refresh and review the original intent."));
    }
    @ExceptionHandler({DataAccessException.class,IllegalStateException.class,java.io.IOException.class})
    public ResponseEntity<?> unavailable(Exception e) {
        return ResponseEntity.status(503).body(ApiResponse.error("Accounts persistence or private evidence is unavailable. Preserve the original retry intent; contact the operator if storage needs configuration or recovery."));
    }
}
