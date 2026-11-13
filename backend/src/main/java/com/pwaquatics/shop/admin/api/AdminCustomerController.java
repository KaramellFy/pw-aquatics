package com.pwaquatics.shop.admin.api;

import static com.pwaquatics.shop.common.text.Texts.trimToNull;

import com.pwaquatics.shop.admin.application.AdminCustomerService;
import com.pwaquatics.shop.admin.application.view.CustomerDetailView;
import com.pwaquatics.shop.admin.application.view.CustomerSummaryView;
import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.common.api.PageResponse;
import com.pwaquatics.shop.common.error.BadRequestException;
import com.pwaquatics.shop.identity.domain.UserStatus;
import com.pwaquatics.shop.security.SessionManager;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Customer accounts (docs/API.md §3.7). Role ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/customers")
@Tag(name = "Admin: customers")
class AdminCustomerController {

    private final AdminCustomerService customers;
    private final SessionManager sessions;

    AdminCustomerController(AdminCustomerService customers, SessionManager sessions) {
        this.customers = customers;
        this.sessions = sessions;
    }

    /** @param reason internal note why, kept in the audit log */
    record LockRequest(@Size(max = 500) String reason) {}

    @Operation(summary = "Customers, newest first; search by email or name")
    @GetMapping
    PageResponse<CustomerSummaryView> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        if (status == UserStatus.ANONYMISED) {
            throw new BadRequestException("VALIDATION_FAILED", "Deleted accounts are not listed.");
        }
        return PageResponse.of(customers.list(trimToNull(q), status, page, size));
    }

    @Operation(summary = "A customer with addresses, latest orders and what they have spent")
    @GetMapping("/{id}")
    CustomerDetailView get(@PathVariable UUID id) {
        return customers.get(id);
    }

    @Operation(summary = "Lock an account: it cannot sign in, and its sessions end now")
    @PostMapping("/{id}/lock")
    CustomerDetailView lock(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) LockRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        CustomerDetailView view = customers.lock(id, request == null ? null : trimToNull(request.reason()),
                actor(principal, http));
        sessions.endAllSessions(id);
        return view;
    }

    @Operation(summary = "Unlock an account")
    @PostMapping("/{id}/unlock")
    CustomerDetailView unlock(
            @PathVariable UUID id, @AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http) {
        return customers.unlock(id, actor(principal, http));
    }

    private static Actor actor(ShopUserPrincipal principal, HttpServletRequest http) {
        return new Actor(principal.userId(), http.getRemoteAddr());
    }
}
