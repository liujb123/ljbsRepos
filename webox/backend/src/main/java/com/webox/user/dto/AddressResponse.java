package com.webox.user.dto;

import com.webox.user.UserAddress;
import java.time.Instant;

/** A previously used delivery address, offered as a pick-list at checkout. */
public record AddressResponse(Long id, String addressText, int useCount, Instant lastUsedAt) {

    public static AddressResponse from(UserAddress address) {
        return new AddressResponse(address.getId(), address.getAddressText(), address.getUseCount(),
                address.getLastUsedAt());
    }
}
