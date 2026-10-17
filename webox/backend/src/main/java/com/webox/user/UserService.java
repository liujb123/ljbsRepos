package com.webox.user;

import com.webox.common.BusinessException;
import com.webox.common.ErrorCode;
import com.webox.user.dto.AddressResponse;
import com.webox.user.dto.PreferencesRequest;
import com.webox.user.dto.PreferencesResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserPreferenceRepository preferenceRepository;
    private final UserAddressRepository addressRepository;
    private final UserRepository userRepository;

    public UserService(UserPreferenceRepository preferenceRepository,
                       UserAddressRepository addressRepository,
                       UserRepository userRepository) {
        this.preferenceRepository = preferenceRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PreferencesResponse getPreferences(Long userId) {
        return preferenceRepository.findById(userId)
                .map(PreferencesResponse::from)
                .orElseGet(PreferencesResponse::empty);
    }

    @Transactional
    public PreferencesResponse savePreferences(Long userId, PreferencesRequest request) {
        BigDecimal min = request.budgetMin();
        BigDecimal max = request.budgetMax();
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Minimum budget cannot be greater than maximum budget.");
        }
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        UserPreference preference = preferenceRepository.findById(userId)
                .orElseGet(() -> new UserPreference(userId));

        // Whole-object replace keeps the endpoint idempotent.
        preference.getAllergens().clear();
        preference.getAllergens().addAll(request.allergensOrEmpty());
        preference.getPreferredCategories().clear();
        preference.getPreferredCategories().addAll(request.preferredCategoriesOrEmpty());
        preference.setSpicePreference(request.spicePreference());
        preference.setTastePreference(request.tastePreference());
        preference.setBudgetMin(min);
        preference.setBudgetMax(max);
        preference.setUpdatedAt(Instant.now());

        return PreferencesResponse.from(preferenceRepository.save(preference));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByLastUsedAtDesc(userId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    /**
     * Remembers a delivery address after a successful order so it can be picked next time.
     * Existing entries only have their usage counters bumped.
     */
    @Transactional
    public void rememberAddress(Long userId, String addressText) {
        String normalized = addressText.trim();
        addressRepository.findByUserIdAndAddressText(userId, normalized)
                .ifPresentOrElse(existing -> {
                    existing.setUseCount(existing.getUseCount() + 1);
                    existing.setLastUsedAt(Instant.now());
                    addressRepository.save(existing);
                }, () -> addressRepository.save(new UserAddress(userId, normalized)));
    }
}
