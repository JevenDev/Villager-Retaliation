package com.jvn.villagerretaliation.notification;

import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.network.VillagerWorldTextIndicatorKind;

public record ResolvedVillagerNotification(
        String text,
        int textColor,
        int chatColor,
        VillagerReputationNoticeKind noticeKind,
        VillagerWorldTextIndicatorKind worldTextKind) {
    public static final int DEFAULT_COLOR = Integer.MIN_VALUE;

    public ResolvedVillagerNotification {
        if (noticeKind == null) {
            noticeKind = VillagerReputationNoticeKind.DEFAULT;
        }
        if (worldTextKind == null) {
            worldTextKind = VillagerWorldTextIndicatorKind.DIALOGUE;
        }
    }
}
