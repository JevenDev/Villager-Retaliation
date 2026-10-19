package com.jvn.villagerretaliation.authoring.client;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

public final class AuthoringEditorClient {
    private AuthoringEditorClient() {
    }

    public static void register(IEventBus modBus) {
        if (!ModList.get().isLoaded("imguimc")) return;
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) ->
                event.register(ImGuiAuthoringEditor.OPEN_EDITOR));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> EditorTextPreview.resourcesReloaded()));
        ImGuiAuthoringEditor.initialize();
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
            ImGuiAuthoringEditor.tick();
            while (ImGuiAuthoringEditor.OPEN_EDITOR.consumeClick()) {
                if (Minecraft.getInstance().screen == null) ImGuiAuthoringEditor.open();
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                ImGuiAuthoringEditor.disconnect());
    }
}
