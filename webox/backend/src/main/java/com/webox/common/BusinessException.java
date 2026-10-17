package com.webox.common;

/**
 * Business rule violation mapped to a stable {@link ErrorCode}.
 *
 * <p>Carries optional {@code data} so clients can act on the failure
 * (for example {@code existingOrderId} on an {@code ORDER_SLOT_CONFLICT}).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object data() {
        return data;
    }
}
