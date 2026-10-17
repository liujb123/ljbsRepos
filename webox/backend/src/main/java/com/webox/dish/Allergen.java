package com.webox.dish;

/**
 * The fixed allergen universe (PRD 4.1 / 6).
 *
 * <p>Seafood is always expressed as {@code Fish} or {@code Shellfish}; a separate
 * {@code Seafood} value is deliberately not offered so that a user's flagged
 * allergens can always be matched against dish tags.
 */
public enum Allergen {
    PEANUTS("Peanuts"),
    DAIRY("Dairy"),
    EGG("Egg"),
    GLUTEN("Gluten"),
    SOY("Soy"),
    FISH("Fish"),
    SHELLFISH("Shellfish");

    private final String label;

    Allergen(String label) {
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
    public static Allergen fromJson(String raw) {
        Allergen value = fromLabel(raw);
        if (value == null) {
            throw new IllegalArgumentException("Unknown allergen: " + raw);
        }
        return value;
    }

    public static Allergen fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String normalized = label.trim();
        for (Allergen value : values()) {
            if (value.label.equalsIgnoreCase(normalized) || value.name().equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }
}
