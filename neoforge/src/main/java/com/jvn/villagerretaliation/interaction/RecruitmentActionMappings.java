package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload;

/** Canonical decoding for recruitment actions shared by preflight and execution. */
public final class RecruitmentActionMappings {
    private RecruitmentActionMappings() {
    }

    public static int hireDays(VillagerRecruitRequestPayload.Action action) {
        return switch (action) {
            case HIRE_ONE_DAY -> 1;
            case HIRE_THREE_DAYS -> 3;
            case HIRE_FIVE_DAYS -> 5;
            case HIRE_SEVEN_DAYS -> 7;
            case HIRE_FIFTEEN_DAYS -> 15;
            case HIRE_THIRTY_DAYS -> 30;
            default -> 0;
        };
    }

    public static int extensionDays(VillagerRecruitRequestPayload.Action action) {
        return switch (action) {
            case EXTEND_ONE_DAY -> 1;
            case EXTEND_THREE_DAYS -> 3;
            case EXTEND_FIVE_DAYS -> 5;
            case EXTEND_SEVEN_DAYS -> 7;
            case EXTEND_FIFTEEN_DAYS -> 15;
            case EXTEND_THIRTY_DAYS -> 30;
            default -> 0;
        };
    }

    public static HiredVillagerRole role(VillagerRecruitRequestPayload.Action action) {
        return switch (action) {
            case SET_ROLE_COMBAT -> HiredVillagerRole.COMBAT;
            case SET_ROLE_HUNTING -> HiredVillagerRole.HUNTING;
            case SET_ROLE_MINING -> HiredVillagerRole.MINING;
            case SET_ROLE_LOGGING -> HiredVillagerRole.LOGGING;
            case SET_ROLE_FARMING -> HiredVillagerRole.FARMING;
            case SET_ROLE_FISHING -> HiredVillagerRole.FISHING;
            case SET_ROLE_BREWING -> HiredVillagerRole.BREWING;
            case SET_ROLE_CRAFTSMAN -> HiredVillagerRole.CRAFTSMAN;
            case SET_ROLE_BUILDER -> HiredVillagerRole.BUILDER;
            case SET_ROLE_ANIMAL_HANDLING -> HiredVillagerRole.ANIMAL_HANDLING;
            case SET_ROLE_NITWIT -> HiredVillagerRole.NITWIT;
            case SET_ROLE_COOK -> HiredVillagerRole.COOK;
            case SET_ROLE_SMELTER -> HiredVillagerRole.SMELTER;
            case SET_ROLE_COURIER -> HiredVillagerRole.COURIER;
            default -> null;
        };
    }
}
