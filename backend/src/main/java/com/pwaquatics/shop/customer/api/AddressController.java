package com.pwaquatics.shop.customer.api;

import com.pwaquatics.shop.customer.application.AddressService;
import com.pwaquatics.shop.customer.application.view.AddressView;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Saved addresses of the logged-in customer (docs/API.md §2.6). */
@RestController
@RequestMapping("/api/v1/account/addresses")
@Tag(name = "Account")
class AddressController {

    private final AddressService addresses;

    AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @Operation(summary = "Saved addresses, defaults first")
    @GetMapping
    List<AddressView> list(@AuthenticationPrincipal ShopUserPrincipal principal) {
        return addresses.list(principal.userId());
    }

    @Operation(summary = "Add an address")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AddressView create(@AuthenticationPrincipal ShopUserPrincipal principal, @Valid @RequestBody AddressRequest request) {
        return addresses.create(principal.userId(), request.toData());
    }

    @Operation(summary = "Update an address")
    @PutMapping("/{id}")
    AddressView update(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddressRequest request) {
        return addresses.update(principal.userId(), id, request.toData());
    }

    @Operation(summary = "Make an address the default for shipping and/or billing")
    @PutMapping("/{id}/default")
    List<AddressView> setDefault(
            @AuthenticationPrincipal ShopUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody DefaultAddressRequest request) {
        return addresses.setDefault(principal.userId(), id, request.shipping(), request.billing());
    }

    @Operation(summary = "Delete an address")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal ShopUserPrincipal principal, @PathVariable UUID id) {
        addresses.delete(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
