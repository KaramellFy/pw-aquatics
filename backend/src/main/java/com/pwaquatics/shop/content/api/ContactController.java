package com.pwaquatics.shop.content.api;

import com.pwaquatics.shop.content.application.ContactService;
import com.pwaquatics.shop.content.application.SubmitContactMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Content")
class ContactController {

    private final ContactService contactService;

    ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @Operation(summary = "Send a message through the contact form")
    @PostMapping("/api/v1/contact")
    ResponseEntity<Void> submit(@Valid @RequestBody ContactMessageRequest request, HttpServletRequest http) {
        contactService.submit(
                new SubmitContactMessage(
                        request.name().strip(),
                        request.email().strip(),
                        request.subject().strip(),
                        request.message().strip(),
                        blankToNull(request.orderNumber()),
                        request.website()),
                // Behind the reverse proxy, X-Forwarded-For is applied by server.forward-headers-strategy.
                http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase();
    }
}
