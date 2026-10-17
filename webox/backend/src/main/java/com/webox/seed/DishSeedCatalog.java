package com.webox.seed;

import com.webox.dish.Allergen;
import com.webox.dish.DishCategory;
import com.webox.dish.ProteinType;
import com.webox.dish.SelectionType;
import com.webox.dish.SpiceLevel;
import java.util.List;

/**
 * The dish catalog written on first start.
 *
 * <p>The first nine entries are exactly the dishes specified in PRD section 6, including their
 * option groups. The remaining entries are supplementary English dishes: PRD section 6 explicitly
 * allows adding more, and a larger catalog is what makes paging and list performance meaningful
 * to demonstrate.
 *
 * <p>Every string here is English, as required by PRD section 1.
 */
final class DishSeedCatalog {

    private DishSeedCatalog() {
    }

    record SeedOption(String name, String priceDelta) {
        static SeedOption free(String name) {
            return new SeedOption(name, "0.00");
        }
    }

    record SeedOptionGroup(String name, boolean required, SelectionType selectionType,
                           List<SeedOption> options) {

        static SeedOptionGroup single(String name, boolean required, SeedOption... options) {
            return new SeedOptionGroup(name, required, SelectionType.SINGLE, List.of(options));
        }

        static SeedOptionGroup multi(String name, SeedOption... options) {
            return new SeedOptionGroup(name, false, SelectionType.MULTI, List.of(options));
        }
    }

    record SeedDish(String name, String description, String price, DishCategory category,
                    SpiceLevel spiceLevel, List<ProteinType> proteins, List<Allergen> allergens,
                    List<SeedOptionGroup> optionGroups) {
    }

    private static SeedDish dish(String name, String description, String price, DishCategory category,
                                 SpiceLevel spiceLevel, List<ProteinType> proteins,
                                 List<Allergen> allergens, SeedOptionGroup... groups) {
        return new SeedDish(name, description, price, category, spiceLevel, proteins, allergens,
                List.of(groups));
    }

    static List<SeedDish> dishes() {
        return List.of(

                // ------------------------------------------------------------------
                // PRD section 6 - the nine authoritative dishes
                // ------------------------------------------------------------------
                dish("Kung Pao Chicken",
                        "Classic Sichuan dish, chicken stir-fried with peanuts and dried chili",
                        "22.00", DishCategory.CHINESE, SpiceLevel.MEDIUM,
                        List.of(ProteinType.CHICKEN), List.of(Allergen.PEANUTS)),

                dish("Caesar Salad",
                        "Fresh romaine lettuce with Parmesan and Caesar dressing",
                        "28.50", DishCategory.LIGHT_MEAL, SpiceLevel.NONE,
                        List.of(ProteinType.NONE), List.of(Allergen.DAIRY, Allergen.EGG),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Grilled Chicken", "6.00"),
                                new SeedOption("Bacon", "5.00"),
                                new SeedOption("Avocado", "4.00"))),

                dish("Salmon Sashimi Set",
                        "Fresh salmon sashimi with rice and miso soup",
                        "45.00", DishCategory.JAPANESE, SpiceLevel.NONE,
                        List.of(ProteinType.FISH), List.of(Allergen.FISH)),

                dish("Tomato Pasta",
                        "Classic Italian tomato pasta with fresh basil",
                        "26.50", DishCategory.WESTERN, SpiceLevel.NONE,
                        List.of(ProteinType.NONE), List.of(Allergen.GLUTEN),
                        SeedOptionGroup.single("Pasta Type", true,
                                SeedOption.free("Spaghetti"),
                                SeedOption.free("Fusilli"),
                                SeedOption.free("Penne")),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Bacon", "5.00"),
                                new SeedOption("Cheese", "3.00"))),

                dish("Tom Yum Soup",
                        "Thai hot & sour shrimp soup with lemongrass, galangal, lime leaves",
                        "32.00", DishCategory.SOUTHEAST_ASIAN, SpiceLevel.HOT,
                        List.of(ProteinType.SHRIMP), List.of(Allergen.SHELLFISH)),

                dish("Chicken Quinoa Bowl",
                        "Low-fat high-protein grilled chicken with quinoa, avocado, veggies",
                        "35.80", DishCategory.LIGHT_MEAL, SpiceLevel.NONE,
                        List.of(ProteinType.CHICKEN), List.of(),
                        SeedOptionGroup.single("Base", true,
                                SeedOption.free("Quinoa"),
                                SeedOption.free("Brown Rice"),
                                SeedOption.free("Mixed Grains"))),

                dish("Mapo Tofu",
                        "Sichuan classic, soft tofu with spicy minced meat",
                        "18.00", DishCategory.CHINESE, SpiceLevel.MEDIUM,
                        List.of(ProteinType.TOFU, ProteinType.PORK), List.of(Allergen.SOY)),

                dish("Korean Bibimbap",
                        "Stone-pot mixed rice with veggies, fried egg, and chili sauce",
                        "30.00", DishCategory.KOREAN, SpiceLevel.MILD,
                        List.of(ProteinType.EGG), List.of(Allergen.EGG, Allergen.SOY),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Cheese", "3.00"),
                                new SeedOption("Fried Egg", "2.00"),
                                new SeedOption("Beef Slices", "8.00"))),

                dish("Classic Beef Burger",
                        "Angus beef patty with lettuce, tomato, onion",
                        "38.00", DishCategory.WESTERN, SpiceLevel.NONE,
                        List.of(ProteinType.BEEF), List.of(Allergen.GLUTEN, Allergen.DAIRY),
                        SeedOptionGroup.single("Bun", true,
                                SeedOption.free("Plain"),
                                SeedOption.free("Whole Wheat")),
                        SeedOptionGroup.single("Sauce", true,
                                SeedOption.free("Ketchup"),
                                SeedOption.free("Mustard"),
                                SeedOption.free("Mayo"),
                                SeedOption.free("BBQ")),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Cheese", "3.00"),
                                new SeedOption("Bacon", "5.00"),
                                new SeedOption("Fried Egg", "2.00"))),

                // ------------------------------------------------------------------
                // Supplementary catalog (PRD section 6 allows adding more dishes)
                // ------------------------------------------------------------------
                dish("Sweet and Sour Pork",
                        "Crispy pork loin tossed with pineapple, bell pepper and a tangy sauce",
                        "24.00", DishCategory.CHINESE, SpiceLevel.MILD,
                        List.of(ProteinType.PORK), List.of(Allergen.SOY, Allergen.GLUTEN)),

                dish("Beef Noodle Soup",
                        "Slow-braised beef shank with hand-pulled noodles in clear broth",
                        "36.00", DishCategory.CHINESE, SpiceLevel.MILD,
                        List.of(ProteinType.BEEF), List.of(Allergen.GLUTEN, Allergen.SOY),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Extra Beef", "9.00"),
                                new SeedOption("Extra Noodles", "4.00"))),

                dish("Steamed Sea Bass",
                        "Whole sea bass steamed with ginger, scallion and light soy",
                        "48.00", DishCategory.CHINESE, SpiceLevel.NONE,
                        List.of(ProteinType.FISH), List.of(Allergen.FISH, Allergen.SOY)),

                dish("Egg Fried Rice",
                        "Wok-fried rice with egg, peas, carrot and scallion",
                        "16.50", DishCategory.CHINESE, SpiceLevel.NONE,
                        List.of(ProteinType.EGG), List.of(Allergen.EGG, Allergen.SOY),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Fried Egg", "3.00"),
                                new SeedOption("BBQ Pork", "7.00"))),

                dish("Dan Dan Noodles",
                        "Sichuan noodles with sesame paste, chili oil and minced pork",
                        "20.00", DishCategory.CHINESE, SpiceLevel.HOT,
                        List.of(ProteinType.PORK), List.of(Allergen.GLUTEN, Allergen.SOY, Allergen.PEANUTS)),

                dish("Braised Pork Belly",
                        "Twice-cooked pork belly with preserved vegetables",
                        "34.00", DishCategory.CHINESE, SpiceLevel.MILD,
                        List.of(ProteinType.PORK), List.of(Allergen.SOY)),

                dish("Hot and Sour Soup",
                        "Traditional soup with tofu, bamboo shoots and black vinegar",
                        "14.00", DishCategory.CHINESE, SpiceLevel.MEDIUM,
                        List.of(ProteinType.TOFU), List.of(Allergen.SOY, Allergen.EGG)),

                dish("Margherita Pizza",
                        "Wood-fired pizza with tomato, mozzarella and fresh basil",
                        "42.00", DishCategory.WESTERN, SpiceLevel.NONE,
                        List.of(ProteinType.NONE), List.of(Allergen.GLUTEN, Allergen.DAIRY),
                        SeedOptionGroup.single("Size", true,
                                SeedOption.free("9 inch"),
                                new SeedOption("12 inch", "8.00")),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Extra Cheese", "4.00"),
                                new SeedOption("Pepperoni", "6.00"))),

                dish("Grilled Chicken Sandwich",
                        "Grilled chicken breast, lettuce and aioli in a toasted bun",
                        "29.00", DishCategory.WESTERN, SpiceLevel.NONE,
                        List.of(ProteinType.CHICKEN), List.of(Allergen.GLUTEN, Allergen.EGG)),

                dish("Creamy Mushroom Soup",
                        "Velvety soup of button and porcini mushrooms with cream",
                        "18.50", DishCategory.WESTERN, SpiceLevel.NONE,
                        List.of(ProteinType.NONE), List.of(Allergen.DAIRY)),

                dish("Fish and Chips",
                        "Beer-battered cod with thick-cut chips and tartar sauce",
                        "39.00", DishCategory.WESTERN, SpiceLevel.NONE,
                        List.of(ProteinType.FISH), List.of(Allergen.GLUTEN, Allergen.FISH, Allergen.EGG)),

                dish("Steak Frites",
                        "Grilled sirloin with herb butter and shoestring fries",
                        "68.00", DishCategory.WESTERN, SpiceLevel.NONE,
                        List.of(ProteinType.BEEF), List.of(Allergen.DAIRY),
                        SeedOptionGroup.single("Doneness", true,
                                SeedOption.free("Medium Rare"),
                                SeedOption.free("Medium"),
                                SeedOption.free("Well Done"))),

                dish("Club Sandwich",
                        "Triple-decker sandwich with chicken, bacon, egg and tomato",
                        "32.00", DishCategory.WESTERN, SpiceLevel.NONE,
                        List.of(ProteinType.CHICKEN, ProteinType.EGG, ProteinType.PORK),
                        List.of(Allergen.GLUTEN, Allergen.EGG)),

                dish("Vegetarian Lasagna",
                        "Layered pasta with roasted vegetables, tomato and bechamel",
                        "34.00", DishCategory.WESTERN, SpiceLevel.NONE,
                        List.of(ProteinType.NONE), List.of(Allergen.GLUTEN, Allergen.DAIRY)),

                dish("Chicken Teriyaki Don",
                        "Grilled chicken glazed with teriyaki sauce over steamed rice",
                        "32.00", DishCategory.JAPANESE, SpiceLevel.NONE,
                        List.of(ProteinType.CHICKEN), List.of(Allergen.SOY, Allergen.GLUTEN),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Extra Chicken", "8.00"),
                                new SeedOption("Avocado", "4.00"))),

                dish("Salmon Avocado Roll",
                        "Eight-piece roll with salmon, avocado and sesame",
                        "38.00", DishCategory.JAPANESE, SpiceLevel.NONE,
                        List.of(ProteinType.FISH), List.of(Allergen.FISH, Allergen.SOY)),

                dish("Tonkotsu Ramen",
                        "Rich pork bone broth with chashu, egg and nori",
                        "42.00", DishCategory.JAPANESE, SpiceLevel.MILD,
                        List.of(ProteinType.PORK, ProteinType.EGG),
                        List.of(Allergen.GLUTEN, Allergen.EGG, Allergen.SOY),
                        SeedOptionGroup.single("Noodle Firmness", true,
                                SeedOption.free("Soft"),
                                SeedOption.free("Standard"),
                                SeedOption.free("Firm")),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Extra Chashu", "8.00"),
                                new SeedOption("Soft-Boiled Egg", "3.00"))),

                dish("Chicken Katsu Curry",
                        "Breaded chicken cutlet with Japanese curry and rice",
                        "36.00", DishCategory.JAPANESE, SpiceLevel.MILD,
                        List.of(ProteinType.CHICKEN), List.of(Allergen.GLUTEN, Allergen.EGG)),

                dish("Miso Soup",
                        "Traditional soybean paste soup with tofu and seaweed",
                        "10.00", DishCategory.JAPANESE, SpiceLevel.NONE,
                        List.of(ProteinType.TOFU), List.of(Allergen.SOY)),

                dish("Beef Gyudon",
                        "Simmered beef and onion over steamed rice",
                        "33.00", DishCategory.JAPANESE, SpiceLevel.NONE,
                        List.of(ProteinType.BEEF), List.of(Allergen.SOY)),

                dish("Vegetable Tempura",
                        "Assorted seasonal vegetables in crisp tempura batter",
                        "26.00", DishCategory.JAPANESE, SpiceLevel.NONE,
                        List.of(ProteinType.NONE), List.of(Allergen.GLUTEN, Allergen.EGG)),

                dish("Korean Fried Chicken",
                        "Double-fried chicken with a sweet and spicy gochujang glaze",
                        "38.00", DishCategory.KOREAN, SpiceLevel.HOT,
                        List.of(ProteinType.CHICKEN), List.of(Allergen.GLUTEN, Allergen.SOY)),

                dish("Kimchi Jjigae",
                        "Spicy kimchi stew with pork belly and tofu",
                        "31.00", DishCategory.KOREAN, SpiceLevel.HOT,
                        List.of(ProteinType.PORK, ProteinType.TOFU), List.of(Allergen.SOY, Allergen.FISH)),

                dish("Bulgogi Beef Rice Bowl",
                        "Marinated beef with onion over rice and sesame",
                        "37.00", DishCategory.KOREAN, SpiceLevel.MILD,
                        List.of(ProteinType.BEEF), List.of(Allergen.SOY)),

                dish("Tteokbokki",
                        "Chewy rice cakes in a sweet and spicy chili sauce",
                        "22.00", DishCategory.KOREAN, SpiceLevel.HOT,
                        List.of(ProteinType.NONE), List.of(Allergen.SOY, Allergen.FISH)),

                dish("Japchae",
                        "Glass noodles stir-fried with vegetables and sesame oil",
                        "27.00", DishCategory.KOREAN, SpiceLevel.NONE,
                        List.of(ProteinType.NONE), List.of(Allergen.SOY)),

                dish("Korean Army Stew",
                        "Shared hot pot with ham, sausage, kimchi and instant noodles",
                        "45.00", DishCategory.KOREAN, SpiceLevel.HOT,
                        List.of(ProteinType.PORK), List.of(Allergen.SOY, Allergen.GLUTEN)),

                dish("Bibim Guksu",
                        "Cold wheat noodles with a spicy gochujang dressing",
                        "24.00", DishCategory.KOREAN, SpiceLevel.MEDIUM,
                        List.of(ProteinType.EGG), List.of(Allergen.GLUTEN, Allergen.EGG)),

                dish("Greek Salad",
                        "Cucumber, tomato, olives and feta with oregano dressing",
                        "26.00", DishCategory.LIGHT_MEAL, SpiceLevel.NONE,
                        List.of(ProteinType.NONE), List.of(Allergen.DAIRY),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Grilled Chicken", "6.00"),
                                new SeedOption("Extra Feta", "4.00"))),

                dish("Grilled Salmon Salad",
                        "Warm salmon fillet over mixed leaves with lemon dressing",
                        "39.00", DishCategory.LIGHT_MEAL, SpiceLevel.NONE,
                        List.of(ProteinType.FISH), List.of(Allergen.FISH)),

                dish("Avocado Toast",
                        "Sourdough with smashed avocado, chili flakes and poached egg",
                        "24.00", DishCategory.LIGHT_MEAL, SpiceLevel.MILD,
                        List.of(ProteinType.EGG), List.of(Allergen.GLUTEN, Allergen.EGG),
                        SeedOptionGroup.single("Bread", true,
                                SeedOption.free("Sourdough"),
                                SeedOption.free("Whole Wheat")),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Smoked Salmon", "9.00"))),

                dish("Chicken Caesar Wrap",
                        "Caesar salad and grilled chicken in a whole wheat wrap",
                        "27.00", DishCategory.LIGHT_MEAL, SpiceLevel.NONE,
                        List.of(ProteinType.CHICKEN),
                        List.of(Allergen.GLUTEN, Allergen.DAIRY, Allergen.EGG)),

                dish("Tofu Poke Bowl",
                        "Marinated tofu with edamame, cucumber and sushi rice",
                        "29.00", DishCategory.LIGHT_MEAL, SpiceLevel.NONE,
                        List.of(ProteinType.TOFU), List.of(Allergen.SOY)),

                dish("Quinoa Tabbouleh",
                        "Herbed quinoa salad with tomato, parsley and lemon",
                        "22.00", DishCategory.LIGHT_MEAL, SpiceLevel.NONE,
                        List.of(ProteinType.NONE), List.of(Allergen.GLUTEN)),

                dish("Yogurt Berry Parfait",
                        "Greek yogurt layered with granola and mixed berries",
                        "18.00", DishCategory.LIGHT_MEAL, SpiceLevel.NONE,
                        List.of(ProteinType.NONE), List.of(Allergen.DAIRY, Allergen.GLUTEN)),

                dish("Chicken Pad Thai",
                        "Rice noodles with tamarind, peanut and lime",
                        "30.00", DishCategory.SOUTHEAST_ASIAN, SpiceLevel.MILD,
                        List.of(ProteinType.CHICKEN),
                        List.of(Allergen.PEANUTS, Allergen.EGG, Allergen.FISH),
                        SeedOptionGroup.single("Spice Level", true,
                                SeedOption.free("Mild"),
                                SeedOption.free("Medium"),
                                SeedOption.free("Hot")),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Extra Peanuts", "3.00"))),

                dish("Beef Rendang",
                        "Slow-cooked beef in coconut and spice paste",
                        "40.00", DishCategory.SOUTHEAST_ASIAN, SpiceLevel.HOT,
                        List.of(ProteinType.BEEF), List.of()),

                dish("Nasi Goreng",
                        "Indonesian fried rice with chicken satay and prawn crackers",
                        "32.00", DishCategory.SOUTHEAST_ASIAN, SpiceLevel.MEDIUM,
                        List.of(ProteinType.CHICKEN, ProteinType.SHRIMP),
                        List.of(Allergen.SOY, Allergen.SHELLFISH, Allergen.EGG)),

                dish("Green Curry Chicken",
                        "Thai green curry with coconut milk, basil and bamboo shoots",
                        "34.00", DishCategory.SOUTHEAST_ASIAN, SpiceLevel.HOT,
                        List.of(ProteinType.CHICKEN), List.of(Allergen.FISH)),

                dish("Hainanese Chicken Rice",
                        "Poached chicken with fragrant rice and ginger sauce",
                        "31.00", DishCategory.SOUTHEAST_ASIAN, SpiceLevel.NONE,
                        List.of(ProteinType.CHICKEN), List.of(Allergen.SOY, Allergen.GLUTEN)),

                dish("Vietnamese Pho",
                        "Beef broth with rice noodles, herbs and bean sprouts",
                        "33.00", DishCategory.SOUTHEAST_ASIAN, SpiceLevel.NONE,
                        List.of(ProteinType.BEEF), List.of(Allergen.FISH, Allergen.SOY),
                        SeedOptionGroup.multi("Add-ons",
                                new SeedOption("Extra Beef", "9.00"),
                                new SeedOption("Extra Noodles", "4.00"))));
    }
}
