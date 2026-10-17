package com.webox.admin;

import com.webox.admin.dto.DishAdminView;
import com.webox.admin.dto.DishUpsertRequest;
import com.webox.common.BusinessException;
import com.webox.common.ErrorCode;
import com.webox.common.PageResponse;
import com.webox.common.SearchKeyword;
import com.webox.dish.Allergen;
import com.webox.dish.Dish;
import com.webox.dish.DishCategory;
import com.webox.dish.DishOption;
import com.webox.dish.DishOptionGroup;
import com.webox.dish.DishRepository;
import com.webox.dish.ProteinType;
import com.webox.dish.SelectionType;
import com.webox.menu.MenuCacheService;
import com.webox.order.PriceCalculator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Console dish management (PRD 4.3).
 *
 * <p>Every mutation invalidates the whole menu snapshot cache, because changing a dish affects
 * every date it appears on. The invalidation runs inside the same transaction as the write it
 * belongs to, which means a reader can never observe a new dish alongside a stale menu.
 */
@Service
public class AdminDishService {

    private static final Logger log = LoggerFactory.getLogger(AdminDishService.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final DishRepository dishRepository;
    private final MenuCacheService menuCache;

    public AdminDishService(DishRepository dishRepository, MenuCacheService menuCache) {
        this.dishRepository = dishRepository;
        this.menuCache = menuCache;
    }

    @Transactional(readOnly = true)
    public PageResponse<DishAdminView> list(String keyword, DishCategory category, Boolean available,
                                            int page, int size) {
        String normalized = SearchKeyword.normalize(keyword);
        String pattern = SearchKeyword.toLikePattern(normalized);
        Page<DishAdminView> result = dishRepository
                .searchForAdmin(pattern, category, available,
                        PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by(Sort.Direction.ASC, "id")))
                .map(DishAdminView::from);
        return PageResponse.of(result);
    }

    @Transactional(readOnly = true)
    public DishAdminView get(Long id) {
        return DishAdminView.from(requireDish(id));
    }

    @Transactional
    public DishAdminView create(DishUpsertRequest request) {
        Dish dish = new Dish();
        apply(dish, request);
        Dish saved = dishRepository.saveAndFlush(dish);
        menuCache.evictAll();
        log.info("Created dish id={} name={}", saved.getId(), saved.getName());
        return DishAdminView.from(saved);
    }

    @Transactional
    public DishAdminView update(Long id, DishUpsertRequest request) {
        Dish dish = requireDish(id);
        apply(dish, request);
        Dish saved = dishRepository.saveAndFlush(dish);
        menuCache.evictAll();
        log.info("Updated dish id={} name={}", saved.getId(), saved.getName());
        return DishAdminView.from(saved);
    }

    @Transactional
    public DishAdminView setAvailability(Long id, boolean available) {
        Dish dish = requireDish(id);
        dish.setAvailable(available);
        Dish saved = dishRepository.saveAndFlush(dish);
        // An off-shelf dish must disappear from employee menus immediately (PRD 4.3).
        menuCache.evictAll();
        log.info("Dish id={} availability set to {}", id, available);
        return DishAdminView.from(saved);
    }

    private Dish requireDish(Long id) {
        return dishRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DISH_NOT_FOUND));
    }

    private void apply(Dish dish, DishUpsertRequest request) {
        dish.setName(request.name().trim());
        dish.setDescription(request.description().trim());
        dish.setPrice(PriceCalculator.money(request.price()));
        dish.setCategory(request.category());
        dish.setSpiceLevel(request.spiceLevel());
        dish.setImageUrl(request.imageUrl() == null || request.imageUrl().isBlank()
                ? null : request.imageUrl().trim());
        if (request.available() != null) {
            dish.setAvailable(request.available());
        }

        dish.getProteins().clear();
        dish.getProteins().addAll(request.proteins() == null
                ? new LinkedHashSet<ProteinType>() : new LinkedHashSet<>(request.proteins()));
        dish.getAllergens().clear();
        dish.getAllergens().addAll(request.allergens() == null
                ? new LinkedHashSet<Allergen>() : new LinkedHashSet<>(request.allergens()));

        // Replace the option graph wholesale; existing orders keep their own snapshots.
        dish.getOptionGroups().clear();
        if (request.optionGroups() != null) {
            for (DishUpsertRequest.OptionGroupRequest groupRequest : request.optionGroups()) {
                if (groupRequest.options() == null || groupRequest.options().isEmpty()) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "A required group must have at least one option.");
                }
                SelectionType type = groupRequest.selectionType();
                DishOptionGroup group = new DishOptionGroup(groupRequest.name().trim(),
                        groupRequest.required(), type);
                for (DishUpsertRequest.OptionRequest optionRequest : groupRequest.options()) {
                    BigDecimal delta = optionRequest.priceDelta() == null
                            ? BigDecimal.ZERO : PriceCalculator.money(optionRequest.priceDelta());
                    DishOption option = new DishOption(optionRequest.name().trim(), delta);
                    if (optionRequest.available() != null) {
                        option.setAvailable(optionRequest.available());
                    }
                    group.addOption(option);
                }
                dish.addOptionGroup(group);
            }
        }
    }
}
