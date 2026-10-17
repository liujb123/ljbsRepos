package com.webox.order;

import com.webox.common.BusinessException;
import com.webox.common.ErrorCode;
import com.webox.dish.Dish;
import com.webox.dish.DishOption;
import com.webox.dish.DishOptionGroup;
import com.webox.dish.SelectionType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates a cart line against the dish's option graph and resolves the chosen options.
 *
 * <p>This is the server-side authority for customization rules. The SPA performs the same
 * checks for instant feedback, but nothing the client sends is trusted: an option must belong
 * to the dish, a required group must be satisfied, a single-choice group must have exactly one
 * selection, and a discontinued option is rejected.
 */
public final class CartRuleValidator {

    private CartRuleValidator() {
    }

    /** One resolved selection, ready for pricing and for the order snapshot. */
    public record Selection(String groupName, String optionName, BigDecimal priceDelta) {
    }

    public static List<Selection> resolveSelections(Dish dish, Set<Long> requestedOptionIds) {
        Set<Long> requested = requestedOptionIds == null ? Set.of() : requestedOptionIds;

        Map<Long, DishOption> optionsById = new HashMap<>();
        for (DishOptionGroup group : dish.getOptionGroups()) {
            for (DishOption option : group.getOptions()) {
                optionsById.put(option.getId(), option);
            }
        }
        for (Long optionId : requested) {
            if (!optionsById.containsKey(optionId)) {
                // Either a tampered payload or an option that was deleted from the dish.
                throw new BusinessException(ErrorCode.OPTION_INVALID);
            }
        }

        List<Selection> selections = new ArrayList<>();
        // Iterating the groups in display order keeps the snapshot readable on the order page.
        for (DishOptionGroup group : dish.getOptionGroups()) {
            List<DishOption> chosen = group.getOptions().stream()
                    .filter(option -> requested.contains(option.getId()))
                    .toList();

            if (group.isRequired() && chosen.isEmpty()) {
                throw new BusinessException(ErrorCode.OPTION_INVALID,
                        "Please choose an option for \"" + group.getName() + "\".");
            }
            if (group.getSelectionType() == SelectionType.SINGLE && chosen.size() > 1) {
                throw new BusinessException(ErrorCode.OPTION_INVALID,
                        "Only one option can be selected for \"" + group.getName() + "\".");
            }
            for (DishOption option : chosen) {
                if (!option.isAvailable()) {
                    throw new BusinessException(ErrorCode.OPTION_INVALID,
                            "The option \"" + option.getName() + "\" is no longer available.");
                }
                selections.add(new Selection(group.getName(), option.getName(), option.getPriceDelta()));
            }
        }
        return selections;
    }

    /** PRD 4.2: at most five portions per order, counted in portions rather than distinct dishes. */
    public static void validateItemCount(int totalQuantity, int maxItemsPerOrder) {
        if (totalQuantity <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Your cart is empty.");
        }
        if (totalQuantity > maxItemsPerOrder) {
            throw new BusinessException(ErrorCode.ORDER_SIZE_EXCEEDED);
        }
    }
}
