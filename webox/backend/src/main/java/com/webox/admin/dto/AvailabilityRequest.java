package com.webox.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Shelf toggle for a dish (PRD 4.3: off-shelf dishes are invisible to employees). */
public record AvailabilityRequest(

        @NotNull(message = "Availability is required.")
        Boolean available) {
}
