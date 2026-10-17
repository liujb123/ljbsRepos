package com.webox.dish;

/** Spice level (PRD 3.2 / 6): None, Mild, Medium, Hot. */
public enum SpiceLevel {
    NONE("None"),
    MILD("Mild"),
    MEDIUM("Medium"),
    HOT("Hot");

    private final String label;

    SpiceLevel(String label) {
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
    public static SpiceLevel fromJson(String raw) {
        SpiceLevel value = fromLabel(raw);
        if (value == null) {
            throw new IllegalArgumentException("Unknown spice level: " + raw);
        }
        return value;
    }

    public static SpiceLevel fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String normalized = label.trim();
        for (SpiceLevel value : values()) {
            if (value.label.equalsIgnoreCase(normalized) || value.name().equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }
}
