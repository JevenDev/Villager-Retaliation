package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.campaign.CampaignWorldModeSavedData;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaign;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignCatalog;
import com.jvn.villagerretaliation.client.campaign.CampaignLoadingScreen;
import com.jvn.villagerretaliation.client.campaign.CampaignResources;
import com.jvn.villagerretaliation.client.campaign.CampaignSelectScreen;
import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldCampaignMixin {
    @Shadow @Final private WorldCreationUiState uiState;
    @Shadow private Pair<Path, PackRepository> getDataPackSelectionSettings(WorldDataConfiguration configuration) { throw new AssertionError(); }
    @Shadow private void onCreate() { throw new AssertionError(); }
    @Unique private WorldCampaign villagerretaliation$campaign;
    @Unique private boolean villagerretaliation$confirmed;

    @Inject(method = "onCreate", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$selectCampaign(CallbackInfo ci) {
        if (villagerretaliation$confirmed) {
            villagerretaliation$confirmed = false;
            return;
        }
        ci.cancel();
        Minecraft minecraft = Minecraft.getInstance();
        CreateWorldScreen parent = (CreateWorldScreen) (Object) this;
        try {
            var packs = getDataPackSelectionSettings(uiState.getSettings().dataConfiguration());
            if (packs == null) throw new IllegalStateException("Could not open the selected datapacks");
            minecraft.setScreen(new CampaignLoadingScreen(parent, () -> {
                try (var resources = CampaignResources.open(packs.getSecond())) {
                    var catalog = WorldCampaignCatalog.load(resources);
                    return new CampaignSelectScreen(parent, catalog, resources,
                            () -> CampaignResources.open(packs.getSecond()), campaign -> {
                        villagerretaliation$campaign = campaign;
                        villagerretaliation$confirmed = true;
                        minecraft.setScreen(parent);
                        onCreate();
                    });
                }
            }));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            minecraft.setScreen(new AlertScreen(() -> minecraft.setScreen(parent),
                    Component.translatable("screen.villagerretaliation.campaign.error"), Component.literal(exception.getMessage())));
        }
    }

    @Inject(method = "createNewWorldDirectory", at = @At("RETURN"))
    private void villagerretaliation$saveCampaign(CallbackInfoReturnable<Optional<LevelStorageSource.LevelStorageAccess>> cir) {
        if (cir.getReturnValue().isEmpty()) return;
        if (villagerretaliation$campaign == null) throw new IllegalStateException("Select a campaign before creating this world");
        try {
            CampaignWorldModeSavedData.createSelection(cir.getReturnValue().get().getLevelPath(LevelResource.ROOT),
                    villagerretaliation$campaign, "singleplayer_owner", uiState.getSettings().worldgenRegistries().compositeAccess());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not save the permanent campaign selection", exception);
        }
    }
}
