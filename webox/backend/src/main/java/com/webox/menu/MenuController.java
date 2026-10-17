package com.webox.menu;

import com.webox.common.ApiResponse;
import com.webox.common.PageResponse;
import com.webox.dish.DishCategory;
import com.webox.menu.dto.DishDetailView;
import com.webox.menu.dto.MenuItemView;
import com.webox.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu")
@Validated
@Tag(name = "Daily menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * @param categories repeatable query parameter, so several cuisines can be selected at once
     *                   (for example {@code ?categories=Chinese&categories=Japanese})
     * @param prefer     applies the caller's cuisine and spice preferences to ordering/highlighting
     */
    @GetMapping
    @Operation(summary = "Menu for a date, filtered by cuisine and keyword")
    public ApiResponse<PageResponse<MenuItemView>> getMenu(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) List<DishCategory> categories,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "false") boolean prefer) {
        return ApiResponse.ok(menuService.getMenu(date, categories, keyword, page, size, prefer,
                principal == null ? null : principal.userId()));
    }

    @GetMapping("/dates")
    @Operation(summary = "Dates that have a published menu, within the orderable window")
    public ApiResponse<List<LocalDate>> getAvailableDates() {
        return ApiResponse.ok(menuService.getAvailableDates());
    }

    @GetMapping("/dishes/{id}")
    @Operation(summary = "Dish detail with ingredients and customization groups")
    public ApiResponse<DishDetailView> getDish(@PathVariable Long id,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(menuService.getDishDetail(id, date));
    }
}
