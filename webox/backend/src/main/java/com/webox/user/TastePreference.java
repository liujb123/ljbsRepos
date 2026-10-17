package com.webox.user;

/** Taste intensity (PRD 4.1). Feeds AI recommendations only; it never reorders the menu. */
public enum TastePreference {
    LIGHT("Light"),
    BALANCED("Balanced"),
    RICH("Rich");

    private final String label;

    TastePreference(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @com.fasterxml.jackson.annotation.JsonValue
    public String jsonValue() {
        return label;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static TastePreference fromJson(String raw) {
        TastePreference value = fromLabel(raw);
        if (value == null) {
            throw new IllegalArgumentException("Unknown taste preference: " + raw);
        }
        return value;
    }

    public static TastePreference fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String normalized = label.trim();
        for (TastePreference value : values()) {
            if (value.label.equalsIgnoreCase(normalized) || value.name().equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }
}
