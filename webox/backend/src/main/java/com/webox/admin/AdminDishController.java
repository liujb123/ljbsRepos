package com.webox.admin;

import com.webox.admin.dto.AvailabilityRequest;
import com.webox.admin.dto.DishAdminView;
import com.webox.admin.dto.DishUpsertRequest;
import com.webox.common.ApiResponse;
import com.webox.common.PageResponse;
import com.webox.dish.DishCategory;
import com.webox.menu.MenuCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Console dish management (PRD 4.3). Every endpoint requires the ADMIN role. */
@RestController
@RequestMapping("/api/admin/dishes")
@Validated
@Tag(name = "Console - dishes")
public class AdminDishController {

    private final AdminDishService adminDishService;
    private final MenuCacheService menuCacheService;

    public AdminDishController(AdminDishService adminDishService, MenuCacheService menuCacheService) {
        this.adminDishService = adminDishService;
        this.menuCacheService = menuCacheService;
    }

    @GetMapping
    @Operation(summary = "Paged dish table with keyword, category and shelf-status filters")
    public ApiResponse<PageResponse<DishAdminView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DishCategory category,
            @RequestParam(required = false) Boolean available,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(adminDishService.list(keyword, category, available, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Single dish including its option groups")
    public ApiResponse<DishAdminView> get(@PathVariable Long id) {
        return ApiResponse.ok(adminDishService.get(id));
    }

    @PostMapping
    @Operation(summary = "Create a dish")
    public ResponseEntity<ApiResponse<DishAdminView>> create(@Valid @RequestBody DishUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(adminDishService.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a dish, replacing its option groups")
    public ApiResponse<DishAdminView> update(@PathVariable Long id,
                                             @Valid @RequestBody DishUpsertRequest request) {
        return ApiResponse.ok(adminDishService.update(id, request));
    }

    @PatchMapping("/{id}/availability")
    @Operation(summary = "Put a dish on or off the shelf")
    public ApiResponse<DishAdminView> setAvailability(@PathVariable Long id,
                                                      @Valid @RequestBody AvailabilityRequest request) {
        return ApiResponse.ok(adminDishService.setAvailability(id, request.available()));
    }

    /** Cache observability for the demo: hit rate of the menu snapshot. */
    @GetMapping("/cache-stats")
    @Operation(summary = "Menu cache counters (hits, misses, Redis-unavailable fallbacks)")
    public ApiResponse<MenuCacheService.CacheStats> cacheStats() {
        return ApiResponse.ok(menuCacheService.stats());
    }
}
