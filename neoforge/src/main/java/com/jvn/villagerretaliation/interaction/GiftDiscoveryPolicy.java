package com.jvn.villagerretaliation.interaction;

public final class GiftDiscoveryPolicy {
    public static final int QUESTION_REVEAL_COUNT = 1;

    private GiftDiscoveryPolicy() {
    }

    public static int questionRevealCount() {
        return Math.max(1, QUESTION_REVEAL_COUNT);
    }
}
