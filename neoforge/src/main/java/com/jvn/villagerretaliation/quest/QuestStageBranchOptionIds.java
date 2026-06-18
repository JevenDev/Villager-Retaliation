package com.jvn.villagerretaliation.quest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

final class QuestStageBranchOptionIds {
    private static final String PREFIX = "vr_stage_branch:";

    private QuestStageBranchOptionIds() {
    }

    static String create(ResourceLocation questId, String branchId) {
        return PREFIX + encodePart(questId.toString()) + ":" + encodePart(branchId);
    }

    static Optional<Key> parse(String optionId) {
        if (optionId == null || !optionId.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String payload = optionId.substring(PREFIX.length());
        int separator = payload.indexOf(':');
        if (separator <= 0 || separator >= payload.length() - 1) {
            return Optional.empty();
        }
        ResourceLocation questId = ResourceLocation.tryParse(decodePart(payload.substring(0, separator)));
        String branchId = decodePart(payload.substring(separator + 1));
        if (questId == null || branchId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Key(questId, branchId));
    }

    private static String encodePart(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    record Key(ResourceLocation questId, String branchId) {
    }
}
