package com.webox.order;

/** Order lifecycle (PRD 3.4). Displayed verbatim in English. */
public enum OrderStatus {
    Pending,
    Confirmed,
    Completed,
    Cancelled;

    /** An order that still occupies its meal slot and blocks a duplicate order. */
    public boolean isActive() {
        return this == Pending || this == Confirmed;
    }

    public boolean isTerminal() {
        return this == Completed || this == Cancelled;
    }
}
