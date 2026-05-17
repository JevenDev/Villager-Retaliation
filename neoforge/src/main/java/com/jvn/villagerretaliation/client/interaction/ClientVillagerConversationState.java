package com.jvn.villagerretaliation.client.interaction;

public final class ClientVillagerConversationState {
    private static int focusedVillagerEntityId = -1;
    private static String responseText = "Choose an option.";
    private static int cameraFocusTicks;

    private ClientVillagerConversationState() {
    }

    public static void start(int entityId) {
        focusedVillagerEntityId = entityId;
        responseText = "Choose an option.";
        cameraFocusTicks = 0;
    }

    public static void setResponseText(String text) {
        responseText = text;
    }

    public static String responseText() {
        return responseText;
    }

    public static int focusedVillagerEntityId() {
        return focusedVillagerEntityId;
    }

    public static boolean active() {
        return focusedVillagerEntityId >= 0;
    }

    public static void tickCameraFocus() {
        if (active()) {
            cameraFocusTicks++;
        }
    }

    public static int cameraFocusTicks() {
        return cameraFocusTicks;
    }

    public static void clear() {
        focusedVillagerEntityId = -1;
        responseText = "Choose an option.";
        cameraFocusTicks = 0;
    }
}
