package com.webox.dish;

/** Cuisine categories (PRD 3.2 / 6). Stored and displayed in English. */
public enum DishCategory {
    CHINESE("Chinese"),
    WESTERN("Western"),
    JAPANESE("Japanese"),
    LIGHT_MEAL("Light Meal"),
    KOREAN("Korean"),
    SOUTHEAST_ASIAN("Southeast Asian");

    private final String label;

    DishCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** JSON contract uses the English label ("Light Meal"), not the Java constant name. */
    @com.fasterxml.jackson.annotation.JsonValue
    public String jsonValue() {
        return label;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static DishCategory fromJson(String raw) {
        DishCategory value = fromLabel(raw);
        if (value == null) {
            throw new IllegalArgumentException("Unknown dish category: " + raw);
        }
        return value;
    }

    /** Resolves a display label such as {@code "Light Meal"} to its enum constant. */
    public static DishCategory fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String normalized = label.trim();
        for (DishCategory value : values()) {
            if (value.label.equalsIgnoreCase(normalized) || value.name().equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }
}
