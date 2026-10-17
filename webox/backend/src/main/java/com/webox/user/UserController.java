package com.webox.user;

import com.webox.common.ApiResponse;
import com.webox.security.AuthPrincipal;
import com.webox.user.dto.AddressResponse;
import com.webox.user.dto.PreferencesRequest;
import com.webox.user.dto.PreferencesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Employee profile")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/preferences")
    @Operation(summary = "Read dietary preferences")
    public ApiResponse<PreferencesResponse> getPreferences(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(userService.getPreferences(principal.userId()));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Replace dietary preferences")
    public ApiResponse<PreferencesResponse> savePreferences(@AuthenticationPrincipal AuthPrincipal principal,
                                                            @Valid @RequestBody PreferencesRequest request) {
        return ApiResponse.ok(userService.savePreferences(principal.userId(), request));
    }

    @GetMapping("/addresses")
    @Operation(summary = "Delivery addresses used previously, most recent first")
    public ApiResponse<List<AddressResponse>> getAddresses(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(userService.getAddresses(principal.userId()));
    }
}
