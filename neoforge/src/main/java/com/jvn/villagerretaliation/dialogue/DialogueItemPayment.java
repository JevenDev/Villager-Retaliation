package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.util.VillagerInventoryItemRemoval;
import java.util.List;
import net.minecraft.util.RandomSource;

public record DialogueItemPayment(
        VillagerInventoryItemRemoval removal,
        List<String> successResponses,
        List<String> failureResponses,
        DialogueItemDestination destination,
        DialogueItemDestination overflowDestination,
        boolean requireSpace) {
    private static final DialogueItemPayment EMPTY = new DialogueItemPayment(
            VillagerInventoryItemRemoval.empty(),
            List.of(),
            List.of(),
            DialogueItemDestination.DISCARD,
            null,
            true);

    public static DialogueItemPayment empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return this.removal.isEmpty();
    }

    public String selectSuccessResponse(RandomSource random) {
        return selectResponse(this.successResponses, random);
    }

    public String selectFailureResponse(RandomSource random) {
        return selectResponse(this.failureResponses, random);
    }

    private static String selectResponse(List<String> responses, RandomSource random) {
        if (responses.isEmpty()) {
            return "";
        }
        return responses.get(random.nextInt(responses.size()));
    }

    public enum DialogueItemDestination {
        DISCARD,
        VILLAGER_INVENTORY,
        DROP_AT_VILLAGER
    }
}
