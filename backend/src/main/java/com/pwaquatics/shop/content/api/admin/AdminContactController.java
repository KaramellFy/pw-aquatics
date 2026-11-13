package com.pwaquatics.shop.content.api.admin;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.common.api.PageResponse;
import com.pwaquatics.shop.content.application.AdminContactService;
import com.pwaquatics.shop.content.application.view.ContactMessageView;
import com.pwaquatics.shop.content.domain.ContactMessageStatus;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The contact inbox (docs/API.md §3.7). Role ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/contact-messages")
@Tag(name = "Admin: contact inbox")
class AdminContactController {

    private final AdminContactService inbox;

    AdminContactController(AdminContactService inbox) {
        this.inbox = inbox;
    }

    record ContactStatusRequest(@NotNull ContactMessageStatus status) {}

    @Operation(summary = "Messages, newest first; without a status everything except spam")
    @GetMapping
    PageResponse<ContactMessageView> list(
            @RequestParam(required = false) ContactMessageStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return PageResponse.of(inbox.list(status, page, size));
    }

    @Operation(summary = "Number of messages per status")
    @GetMapping("/counts")
    Map<ContactMessageStatus, Long> counts() {
        return inbox.counts();
    }

    @Operation(summary = "Mark a message as new, read, answered or spam")
    @PatchMapping("/{id}")
    ContactMessageView changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ContactStatusRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return inbox.changeStatus(id, request.status(), new Actor(principal.userId(), http.getRemoteAddr()));
    }
}
