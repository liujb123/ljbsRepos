package com.webox.admin;

import com.webox.admin.dto.DailyMenuUpsertRequest;
import com.webox.common.BusinessException;
import com.webox.common.ErrorCode;
import com.webox.dish.Dish;
import com.webox.dish.DishRepository;
import com.webox.menu.DailyMenu;
import com.webox.menu.DailyMenuItem;
import com.webox.menu.DailyMenuRepository;
import com.webox.menu.MenuCacheService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Set daily menu" (PRD 4.3): choose which dishes appear on a given date, with a per-dish supply
 * quantity. The default date offered by the UI is tomorrow, and the same operation is used to
 * initialize or adjust today's menu.
 *
 * <p>Saving replaces the day's dish list, so submitting the same body twice is a no-op. Existing
 * rows are updated in place rather than deleted and re-inserted, which preserves {@code soldQty}
 * when an admin edits today's menu mid-service.
 */
@Service
public class AdminDailyMenuService {

    private static final Logger log = LoggerFactory.getLogger(AdminDailyMenuService.class);

    private final DailyMenuRepository dailyMenuRepository;
    private final DishRepository dishRepository;
    private final MenuCacheService menuCache;

    public AdminDailyMenuService(DailyMenuRepository dailyMenuRepository,
                                 DishRepository dishRepository,
                                 MenuCacheService menuCache) {
        this.dailyMenuRepository = dailyMenuRepository;
        this.dishRepository = dishRepository;
        this.menuCache = menuCache;
    }

    @Transactional(readOnly = true)
    public DailyMenuUpsertRequest.View get(LocalDate date) {
        return dailyMenuRepository.findByMenuDate(date)
                .map(this::toView)
                .orElseGet(() -> new DailyMenuUpsertRequest.View(date, List.of()));
    }

    @Transactional
    public DailyMenuUpsertRequest.View save(LocalDate date, DailyMenuUpsertRequest request) {
        List<Long> dishIds = request.items().stream().map(DailyMenuUpsertRequest.Item::dishId).distinct().toList();
        if (dishIds.size() != request.items().size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The same dish cannot appear twice.");
        }
        Map<Long, Dish> dishes = new HashMap<>();
        dishRepository.findAllById(dishIds).forEach(dish -> dishes.put(dish.getId(), dish));
        for (Long dishId : dishIds) {
            if (!dishes.containsKey(dishId)) {
                throw new BusinessException(ErrorCode.DISH_NOT_FOUND);
            }
        }

        DailyMenu menu = dailyMenuRepository.findByMenuDate(date).orElseGet(() -> new DailyMenu(date));

        // Update in place: reuse the row of a dish that stays on the menu so soldQty survives.
        Map<Long, DailyMenuItem> existingByDish = new HashMap<>();
        for (DailyMenuItem item : menu.getItems()) {
            existingByDish.put(item.getDishId(), item);
        }

        List<DailyMenuItem> rebuilt = new ArrayList<>(request.items().size());
        Set<Long> retained = new LinkedHashSet<>();
        int displayOrder = 0;
        for (DailyMenuUpsertRequest.Item requested : request.items()) {
            DailyMenuItem item = existingByDish.get(requested.dishId());
            if (item == null) {
                item = new DailyMenuItem(requested.dishId(), requested.totalQty());
                item.setDailyMenu(menu);
            }
            item.setTotalQty(requested.totalQty());
            item.setDisplayOrder(displayOrder++);
            retained.add(item.getDishId());
            rebuilt.add(item);
        }

        // Anything the admin removed is dropped (orphanRemoval), the rest are updated.
        menu.getItems().removeIf(item -> !retained.contains(item.getDishId()));
        menu.getItems().addAll(rebuilt);
        DailyMenu saved = dailyMenuRepository.saveAndFlush(menu);

        menuCache.evict(date);
        log.info("Daily menu for {} saved with {} dish(es)", date, saved.getItems().size());
        return toView(saved);
    }

    private DailyMenuUpsertRequest.View toView(DailyMenu menu) {
        List<Long> dishIds = menu.getItems().stream().map(DailyMenuItem::getDishId).toList();
        Map<Long, Dish> dishes = new HashMap<>();
        dishRepository.findAllById(dishIds).forEach(dish -> dishes.put(dish.getId(), dish));

        List<DailyMenuUpsertRequest.ItemView> items = menu.getItems().stream()
                .map(item -> {
                    Dish dish = dishes.get(item.getDishId());
                    return new DailyMenuUpsertRequest.ItemView(
                            item.getDishId(),
                            dish == null ? "(deleted dish)" : dish.getName(),
                            dish == null || dish.getCategory() == null ? null : dish.getCategory().label(),
                            dish != null && dish.isAvailable(),
                            item.getTotalQty(),
                            item.getSoldQty(),
                            item.getDisplayOrder());
                })
                .toList();
        return new DailyMenuUpsertRequest.View(menu.getMenuDate(), items);
    }
}
