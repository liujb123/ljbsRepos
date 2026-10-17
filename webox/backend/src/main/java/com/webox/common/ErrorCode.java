package com.webox.common;

import org.springframework.http.HttpStatus;

/**
 * Stable machine-readable error codes returned by the API.
 *
 * <p>Every {@code message} is user-facing and MUST be English (PRD section 1).
 */
public enum ErrorCode {

    OK(HttpStatus.OK, "Success"),

    // 400
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request contains invalid fields."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "The Idempotency-Key header is required."),
    ORDER_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "You can order at most 5 items per order."),
    OPTION_INVALID(HttpStatus.BAD_REQUEST, "Your selection for a dish option is no longer valid. Please choose again."),
    DISH_NOT_ON_MENU(HttpStatus.BAD_REQUEST, "Some dishes are not available for the selected date."),
    UPLOAD_INVALID_TYPE(HttpStatus.BAD_REQUEST, "Image must be a JPG, PNG, or WebP file under 2 MB."),
    UPLOAD_TOO_LARGE(HttpStatus.BAD_REQUEST, "Image must be a JPG, PNG, or WebP file under 2 MB."),

    // 401
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Incorrect email or password."),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "You don't have permission to access this resource."),

    // 404
    DISH_NOT_FOUND(HttpStatus.NOT_FOUND, "This dish could not be found."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "This order could not be found."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource could not be found."),

    // 409
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "This email is already registered."),
    DISH_UNAVAILABLE(HttpStatus.CONFLICT, "This dish is currently unavailable."),
    ORDER_SLOT_CONFLICT(HttpStatus.CONFLICT, "You already have an active order for this meal. Please view it instead."),
    ORDER_IN_PROGRESS(HttpStatus.CONFLICT, "Your order is still being processed. Please try again in a moment."),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "Only pending orders can be cancelled."),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
