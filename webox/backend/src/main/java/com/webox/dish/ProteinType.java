package com.webox.dish;

/** Protein source (PRD 3.2). A dish may declare several. */
public enum ProteinType {
    CHICKEN("Chicken"),
    BEEF("Beef"),
    PORK("Pork"),
    FISH("Fish"),
    SHRIMP("Shrimp"),
    EGG("Egg"),
    TOFU("Tofu"),
    NONE("None");

    private final String label;

    ProteinType(String label) {
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
    public static ProteinType fromJson(String raw) {
        ProteinType value = fromLabel(raw);
        if (value == null) {
            throw new IllegalArgumentException("Unknown protein type: " + raw);
        }
        return value;
    }

    public static ProteinType fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String normalized = label.trim();
        for (ProteinType value : values()) {
            if (value.label.equalsIgnoreCase(normalized) || value.name().equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }
}
