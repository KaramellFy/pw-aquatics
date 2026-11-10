package com.pwaquatics.shop.customer.application;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.customer.application.view.AddressView;
import com.pwaquatics.shop.customer.domain.Address;
import com.pwaquatics.shop.customer.domain.AddressData;
import com.pwaquatics.shop.customer.infrastructure.AddressRepository;
import com.pwaquatics.shop.identity.domain.UserAnonymised;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A customer's address book. Every operation takes the owner's id; an address of another
 * user is reported as "not found", so its existence is never revealed.
 */
@Service
public class AddressService {

    static final int MAX_ADDRESSES = 10;
    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    private final AddressRepository addresses;
    private final Clock clock;

    AddressService(AddressRepository addresses, Clock clock) {
        this.addresses = addresses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AddressView> list(UUID userId) {
        return addresses.findAllOf(userId).stream().map(AddressService::toView).toList();
    }

    /** Adds an address. The first address becomes the default for shipping and billing. */
    @Transactional
    public AddressView create(UUID userId, AddressData data) {
        long existing = addresses.countByUserId(userId);
        if (existing >= MAX_ADDRESSES) {
            throw new BusinessRuleException(
                    "ADDRESS_LIMIT_REACHED", "You can save up to " + MAX_ADDRESSES + " addresses.");
        }
        Address address = new Address(userId, validated(data), clock.instant());
        if (existing == 0) {
            address.markDefaultShipping();
            address.markDefaultBilling();
        }
        return toView(addresses.save(address));
    }

    @Transactional
    public AddressView update(UUID userId, UUID addressId, AddressData data) {
        Address address = owned(userId, addressId);
        address.update(validated(data), clock.instant());
        return toView(address);
    }

    /** Makes the address the default for shipping and/or billing (there is always exactly one of each). */
    @Transactional
    public List<AddressView> setDefault(UUID userId, UUID addressId, boolean shipping, boolean billing) {
        owned(userId, addressId);
        // The previous default must be cleared first: a unique index allows one default per user.
        if (shipping) {
            addresses.clearDefaultShipping(userId);
        }
        if (billing) {
            addresses.clearDefaultBilling(userId);
        }
        Address address = owned(userId, addressId);
        if (shipping) {
            address.markDefaultShipping();
        }
        if (billing) {
            address.markDefaultBilling();
        }
        addresses.flush();
        return list(userId);
    }

    /** Deletes an address; if it was a default, the oldest remaining address takes over. */
    @Transactional
    public void delete(UUID userId, UUID addressId) {
        Address address = owned(userId, addressId);
        boolean wasShipping = address.isDefaultShipping();
        boolean wasBilling = address.isDefaultBilling();
        addresses.delete(address);
        addresses.flush();
        if (wasShipping || wasBilling) {
            addresses.findAllOf(userId).stream().findFirst().ifPresent(next -> {
                if (wasShipping) {
                    next.markDefaultShipping();
                }
                if (wasBilling) {
                    next.markDefaultBilling();
                }
            });
        }
    }

    /** Removes all saved addresses when an account is deleted (same transaction as the anonymisation). */
    @EventListener
    @Transactional
    public void on(UserAnonymised event) {
        addresses.deleteAllOf(event.userId());
    }

    private Address owned(UUID userId, UUID addressId) {
        return addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("Address not found."));
    }

    private static AddressData validated(AddressData data) {
        if (!ISO_COUNTRIES.contains(data.countryCode())) {
            throw new BusinessRuleException("COUNTRY_INVALID", "Unknown country.");
        }
        return data;
    }

    private static AddressView toView(Address address) {
        AddressData data = address.data();
        return new AddressView(
                address.getId(),
                data.label(),
                data.firstName(),
                data.lastName(),
                data.company(),
                data.street(),
                data.houseNumber(),
                data.addressLine2(),
                data.postalCode(),
                data.city(),
                data.countryCode(),
                data.phone(),
                address.isDefaultShipping(),
                address.isDefaultBilling());
    }
}
