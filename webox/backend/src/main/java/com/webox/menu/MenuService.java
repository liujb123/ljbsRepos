package com.webox.menu;

import com.webox.common.BusinessException;
import com.webox.common.ErrorCode;
import com.webox.common.PageResponse;
import com.webox.common.SearchKeyword;
import com.webox.config.WeBoxProperties;
import com.webox.dish.Dish;
import com.webox.dish.DishCategory;
import com.webox.dish.DishRepository;
import com.webox.dish.SpiceLevel;
import com.webox.menu.dto.DishDetailView;
import com.webox.menu.dto.MenuDishView;
import com.webox.menu.dto.MenuItemView;
import com.webox.user.UserPreference;
import com.webox.user.UserPreferenceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menu read path (PRD 3.2).
 *
 * <p>The caching strategy is "bounded hot data, whole-day snapshot": the menu is small
 * (tens to a few hundred dishes) and read by everyone at once during the 09:30-10:00 peak.
 * One cache entry per date therefore serves every concurrent reader, and filtering, search,
 * preference sorting and paging are done in memory on the snapshot. The alternative - a cache
 * key per filter combination - would explode in cardinality for no benefit at this size.
 */
@Service
public class MenuService {

    public static final String TAG_PREFERRED_CUISINE = "Preferred cuisine";
    public static final String TAG_SPICE_MATCH = "Matches your spice level";

    private final DailyMenuRepository dailyMenuRepository;
    private final DishRepository dishRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final MenuCacheService menuCache;
    private final Clock clock;
    private final WeBoxProperties properties;

    public MenuService(DailyMenuRepository dailyMenuRepository,
                       DishRepository dishRepository,
                       UserPreferenceRepository preferenceRepository,
                       MenuCacheService menuCache,
                       Clock clock,
                       WeBoxProperties properties) {
        this.dailyMenuRepository = dailyMenuRepository;
        this.dishRepository = dishRepository;
        this.preferenceRepository = preferenceRepository;
        this.menuCache = menuCache;
        this.clock = clock;
        this.properties = properties;
    }

    /** @return the menu for one business date, filtered, optionally preference-sorted and paged. */
    @Transactional(readOnly = true)
    public PageResponse<MenuItemView> getMenu(LocalDate requestedDate,
                                              List<DishCategory> categories,
                                              String keyword,
                                              int page,
                                              int size,
                                              boolean preferRecommendations,
                                              Long userId) {
        LocalDate date = requestedDate == null ? today() : requestedDate;
        List<MenuDishView> snapshot = loadSnapshot(date);

        Set<DishCategory> categoryFilter = toCategorySet(categories);
        String normalizedKeyword = SearchKeyword.normalize(keyword);

        List<MenuDishView> filtered = snapshot.stream()
                .filter(dish -> categoryFilter.isEmpty() || categoryFilter.contains(dish.category()))
                .filter(dish -> SearchKeyword.matches(normalizedKeyword, dish.name(), dish.description()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        PreferenceContext preferences = resolvePreferences(preferRecommendations, userId);
        if (preferences != null) {
            // Stable sort: dishes of equal rank keep their daily-menu display order.
            filtered.sort(Comparator
                    .comparingInt((MenuDishView dish) ->
                            preferences.categories().contains(dish.category()) ? 0 : 1)
                    .thenComparingInt(dish ->
                            dish.spiceLevel() == preferences.spice() ? 0 : 1));
        }

        List<MenuItemView> items = filtered.stream()
                .map(dish -> decorate(dish, preferences))
                .toList();

        int total = items.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<MenuItemView> pageContent = items.subList(fromIndex, toIndex);

        return PageResponse.of(pageContent, page, size, total);
    }

    @Transactional(readOnly = true)
    public DishDetailView getDishDetail(Long dishId, LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? today() : requestedDate;
        Dish dish = dishRepository.findById(dishId)
                .filter(Dish::isAvailable)
                // An off-shelf dish is invisible to employees, so it is reported as missing.
                .orElseThrow(() -> new BusinessException(ErrorCode.DISH_NOT_FOUND));

        int dailyQty = 0;
        int remainingQty = 0;
        Optional<DailyMenu> menu = dailyMenuRepository.findByMenuDate(date);
        if (menu.isPresent()) {
            Optional<DailyMenuItem> item = menu.get().getItems().stream()
                    .filter(i -> i.getDishId().equals(dishId))
                    .findFirst();
            if (item.isPresent()) {
                dailyQty = item.get().getTotalQty();
                remainingQty = Math.max(0, item.get().getTotalQty() - item.get().getSoldQty());
            }
        }

        List<DishDetailView.OptionGroupView> groups = dish.getOptionGroups().stream()
                .map(group -> new DishDetailView.OptionGroupView(
                        group.getId(),
                        group.getName(),
                        group.isRequired(),
                        group.getSelectionType(),
                        group.getOptions().stream()
                                .map(option -> new DishDetailView.OptionView(
                                        option.getId(), option.getName(), option.getPriceDelta(), option.isAvailable()))
                                .toList()))
                .toList();

        return new DishDetailView(dish.getId(), dish.getName(), dish.getDescription(), dish.getPrice(),
                dish.getCategory(), dish.getSpiceLevel(), dish.getImageUrl(),
                new LinkedHashSet<>(dish.getProteins()), new LinkedHashSet<>(dish.getAllergens()),
                dish.isAvailable(), dailyQty, remainingQty, groups);
    }

    /** Dates in {@code [today, today + maxAdvanceDays]} that have a published menu. */
    @Transactional(readOnly = true)
    public List<LocalDate> getAvailableDates() {
        LocalDate from = today();
        LocalDate to = from.plusDays(properties.order().maxAdvanceDays());
        return dailyMenuRepository.findMenuDatesBetween(from, to);
    }

    public MenuCacheService.CacheStats cacheStats() {
        return menuCache.stats();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Reads the snapshot from Redis, falling back to MySQL and repopulating the cache.
     * Never throws because of Redis.
     */
    private List<MenuDishView> loadSnapshot(LocalDate date) {
        Optional<List<MenuDishView>> cached = menuCache.get(date);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<MenuDishView> loaded = loadSnapshotFromDatabase(date);
        menuCache.put(date, loaded);
        return loaded;
    }

    /** Single batch load: 1 query for the menu rows, 1 for the dishes, plus batched collections. */
    private List<MenuDishView> loadSnapshotFromDatabase(LocalDate date) {
        Optional<DailyMenu> menuOpt = dailyMenuRepository.findByMenuDate(date);
        if (menuOpt.isEmpty()) {
            return List.of();
        }
        List<DailyMenuItem> menuItems = menuOpt.get().getItems();
        if (menuItems.isEmpty()) {
            return List.of();
        }

        List<Long> dishIds = menuItems.stream().map(DailyMenuItem::getDishId).distinct().toList();
        Map<Long, Dish> dishes = new HashMap<>();
        dishRepository.findAllById(dishIds).forEach(dish -> dishes.put(dish.getId(), dish));

        List<MenuDishView> snapshot = new ArrayList<>(menuItems.size());
        for (DailyMenuItem menuItem : menuItems) {
            Dish dish = dishes.get(menuItem.getDishId());
            // Visibility = on this date's menu AND currently on the shelf (PRD 4.3).
            if (dish == null || !dish.isAvailable()) {
                continue;
            }
            int remaining = Math.max(0, menuItem.getTotalQty() - menuItem.getSoldQty());
            snapshot.add(MenuDishView.from(dish, menuItem.getTotalQty(), remaining));
        }
        return snapshot;
    }

    private MenuItemView decorate(MenuDishView dish, PreferenceContext preferences) {
        if (preferences == null) {
            return dish.decorate(false, List.of());
        }
        List<String> tags = new ArrayList<>(2);
        if (preferences.categories().contains(dish.category())) {
            tags.add(TAG_PREFERRED_CUISINE);
        }
        if (dish.spiceLevel() != null && dish.spiceLevel() == preferences.spice()) {
            tags.add(TAG_SPICE_MATCH);
        }
        return dish.decorate(!tags.isEmpty(), tags);
    }

    private PreferenceContext resolvePreferences(boolean preferRecommendations, Long userId) {
        if (!preferRecommendations || userId == null) {
            return null;
        }
        return preferenceRepository.findById(userId)
                .filter(pref -> !pref.getPreferredCategories().isEmpty() || pref.getSpicePreference() != null)
                .map(pref -> new PreferenceContext(new LinkedHashSet<>(pref.getPreferredCategories()),
                        pref.getSpicePreference()))
                .orElse(null);
    }

    private Set<DishCategory> toCategorySet(List<DishCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return EnumSet.noneOf(DishCategory.class);
        }
        return EnumSet.copyOf(categories);
    }

    /** Read-only projection of the preference fields that influence menu ordering. */
    private record PreferenceContext(Set<DishCategory> categories, SpiceLevel spice) {
    }

    /** Exposed for tests: verifies the "@JsonValue" round-trip of a price. */
    static BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
