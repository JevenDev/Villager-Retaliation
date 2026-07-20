package com.jvn.villagerretaliation.client.villager;

import net.minecraft.world.entity.Entity;

/** Tracks GUI-only entity renders so world-space overlays can stay out of model previews. */
public final class VillagerModelPreviewRenderContext {
    private static int renderedEntityId = -1;
    private static PreviewType previewType;

    private VillagerModelPreviewRenderContext() {
    }

    public static Scope begin(Entity entity, PreviewType type) {
        int previousEntityId = renderedEntityId;
        PreviewType previousType = previewType;
        renderedEntityId = entity.getId();
        previewType = type;
        return () -> {
            renderedEntityId = previousEntityId;
            previewType = previousType;
        };
    }

    public static boolean isRendering(Entity entity) {
        return entity != null && entity.getId() == renderedEntityId;
    }

    public static boolean isRenderingInventoryPreview(Entity entity) {
        return isRendering(entity) && previewType == PreviewType.INVENTORY;
    }

    public enum PreviewType {
        INVENTORY,
        INTERACTION
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
