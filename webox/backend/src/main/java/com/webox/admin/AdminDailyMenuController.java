package com.webox.admin;

import com.webox.admin.dto.DailyMenuUpsertRequest;
import com.webox.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Set daily menu" (PRD 4.3): which dishes are served on a date, and how many of each. */
@RestController
@RequestMapping("/api/admin/daily-menus")
@Tag(name = "Console - daily menu")
public class AdminDailyMenuController {

    private final AdminDailyMenuService adminDailyMenuService;

    public AdminDailyMenuController(AdminDailyMenuService adminDailyMenuService) {
        this.adminDailyMenuService = adminDailyMenuService;
    }

    @GetMapping("/{date}")
    @Operation(summary = "Menu published for a date (empty list when nothing is scheduled)")
    public ApiResponse<DailyMenuUpsertRequest.View> get(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(adminDailyMenuService.get(date));
    }

    @PutMapping("/{date}")
    @Operation(summary = "Replace the menu for a date, with per-dish daily quantities")
    public ApiResponse<DailyMenuUpsertRequest.View> save(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody DailyMenuUpsertRequest request) {
        return ApiResponse.ok(adminDailyMenuService.save(date, request));
    }
}
