package com.webox.seed;

import com.webox.config.WeBoxProperties;
import com.webox.dish.Allergen;
import com.webox.dish.Dish;
import com.webox.dish.DishOption;
import com.webox.dish.DishOptionGroup;
import com.webox.dish.DishRepository;
import com.webox.dish.ProteinType;
import com.webox.menu.DailyMenu;
import com.webox.menu.DailyMenuItem;
import com.webox.menu.DailyMenuRepository;
import com.webox.order.MealSlotResolver;
import com.webox.order.PriceCalculator;
import com.webox.user.User;
import com.webox.user.UserRepository;
import com.webox.user.UserRole;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent startup seeding (PRD 4.3 "data initialization").
 *
 * <p>Guarantees that on the day of the demo the system can be ordered from immediately: an admin
 * account exists, the dish catalog is populated, and both today and tomorrow already have a
 * published menu with daily quantities.
 *
 * <p>Written in Java rather than as a Flyway migration because "today" has to be resolved at run
 * time, and because generating the catalog and its option graph in SQL would be far less
 * maintainable than the declarative catalog it is built from.
 *
 * <p>Every step checks before writing, so restarting the application never duplicates data and
 * never overwrites changes an administrator has made.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final int DEFAULT_DAILY_QTY = 50;
    private static final int AVAILABLE_IMAGES = 20;

    private final WeBoxProperties properties;
    private final UserRepository userRepository;
    private final DishRepository dishRepository;
    private final DailyMenuRepository dailyMenuRepository;
    private final PasswordEncoder passwordEncoder;
    private final MealSlotResolver mealSlotResolver;

    public DataSeeder(WeBoxProperties properties,
                      UserRepository userRepository,
                      DishRepository dishRepository,
                      DailyMenuRepository dailyMenuRepository,
                      PasswordEncoder passwordEncoder,
                      MealSlotResolver mealSlotResolver) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.dishRepository = dishRepository;
        this.dailyMenuRepository = dailyMenuRepository;
        this.passwordEncoder = passwordEncoder;
        this.mealSlotResolver = mealSlotResolver;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.seed().enabled()) {
            log.info("Seed data is disabled (webox.seed.enabled=false)");
            return;
        }
        seedAdminUser();
        seedDishCatalog();
        seedDailyMenus();
    }

    private void seedAdminUser() {
        String email = properties.seed().adminEmail();
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User admin = new User(email,
                passwordEncoder.encode(properties.seed().adminPassword()),
                "WeBox Admin",
                UserRole.ADMIN);
        userRepository.save(admin);
        log.info("Seeded administrator account {}", email);
    }

    private void seedDishCatalog() {
        if (dishRepository.count() > 0) {
            return;
        }
        List<DishSeedCatalog.SeedDish> catalog = DishSeedCatalog.dishes();
        int index = 0;
        for (DishSeedCatalog.SeedDish seed : catalog) {
            Dish dish = new Dish();
            dish.setName(seed.name());
            dish.setDescription(seed.description());
            dish.setPrice(PriceCalculator.money(new java.math.BigDecimal(seed.price())));
            dish.setCategory(seed.category());
            dish.setSpiceLevel(seed.spiceLevel());
            dish.setAvailable(true);
            // The 20 bundled photos are reused cyclically; the SPA shows a placeholder if missing.
            dish.setImageUrl(String.format("/dishes/dish-%02d.jpg", (index % AVAILABLE_IMAGES) + 1));
            dish.getProteins().addAll(seed.proteins());
            dish.getAllergens().addAll(seed.allergens());

            for (DishSeedCatalog.SeedOptionGroup groupSeed : seed.optionGroups()) {
                DishOptionGroup group = new DishOptionGroup(groupSeed.name(), groupSeed.required(),
                        groupSeed.selectionType());
                for (DishSeedCatalog.SeedOption optionSeed : groupSeed.options()) {
                    group.addOption(new DishOption(optionSeed.name(),
                            PriceCalculator.money(new java.math.BigDecimal(optionSeed.priceDelta()))));
                }
                dish.addOptionGroup(group);
            }
            dishRepository.save(dish);
            index++;
        }
        log.info("Seeded {} dishes across {} categories", catalog.size(),
                java.util.Arrays.stream(com.webox.dish.DishCategory.values()).count());
    }

    /** Publishes a menu for today and tomorrow when the date has none yet. */
    private void seedDailyMenus() {
        LocalDate today = mealSlotResolver.today();
        seedMenuFor(today);
        seedMenuFor(today.plusDays(1));
    }

    private void seedMenuFor(LocalDate date) {
        if (dailyMenuRepository.existsByMenuDate(date)) {
            return;
        }
        List<Dish> availableDishes = dishRepository.findByAvailableTrueOrderByIdAsc();
        if (availableDishes.isEmpty()) {
            return;
        }
        DailyMenu menu = new DailyMenu(date);
        int displayOrder = 0;
        for (Dish dish : availableDishes) {
            DailyMenuItem item = new DailyMenuItem(dish.getId(), DEFAULT_DAILY_QTY);
            item.setDisplayOrder(displayOrder++);
            menu.addItem(item);
        }
        dailyMenuRepository.save(menu);
        log.info("Seeded daily menu for {} with {} dish(es), {} portions each",
                date, availableDishes.size(), DEFAULT_DAILY_QTY);
    }
}
