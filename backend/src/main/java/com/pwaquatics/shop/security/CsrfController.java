package com.pwaquatics.shop.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the SPA obtain the {@code XSRF-TOKEN} cookie before its first state-changing request. */
@RestController
@Tag(name = "Auth")
class CsrfController {

    @Operation(summary = "Ensure the XSRF-TOKEN cookie is set")
    @GetMapping("/api/v1/auth/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        // Reading the deferred token makes Spring Security write the cookie.
        token.getToken();
        return ResponseEntity.noContent().build();
    }
}
