package com.jvn.villagerretaliation.client;

import com.jvn.toucanlib.client.render.ToucanPreviewMesh;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class PreviewMeshCache {
    private static final ToucanPreviewMesh.Cache CACHE = new ToucanPreviewMesh.Cache(8, 2_000, 250);
    private final ToucanPreviewMesh mesh = new ToucanPreviewMesh();

    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> clear());
    }

    public static void tick(ClientTickEvent.Post event) {
        CACHE.tick(Minecraft.getInstance().level, Util.getMillis());
    }

    public static void clear() { CACHE.close(); }

    public static void render(Object owner, Object revision, Supplier<PreviewMeshCache> build, RenderLevelStageEvent event) {
        CACHE.render(owner, revision, Minecraft.getInstance().level, Util.getMillis(), () -> build.get().mesh,
                new ToucanPreviewMesh.Frame(event.getCamera().getPosition(), event.getModelViewMatrix(),
                        event.getProjectionMatrix(), event.getFrustum()::isVisible));
    }

    public void validateWith(BooleanSupplier valid) { mesh.validateWith(valid); }
    public ToucanPreviewMesh.Section at(BlockPos position) { return mesh.at(position); }
}
