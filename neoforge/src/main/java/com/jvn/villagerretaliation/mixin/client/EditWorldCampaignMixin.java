package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.campaign.CampaignWorldModeSavedData;
import com.jvn.villagerretaliation.client.campaign.CampaignAddonManager;
import java.nio.file.Files;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditWorldScreen.class)
public abstract class EditWorldCampaignMixin extends Screen {
    @Shadow @Final private LevelStorageSource.LevelStorageAccess levelAccess;
    @Unique private Button villagerretaliation$addonsButton;

    protected EditWorldCampaignMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void villagerretaliation$addAddonButton(CallbackInfo ci) {
        if (villagerretaliation$addonsButton != null) removeWidget(villagerretaliation$addonsButton);
        if (!Files.isRegularFile(levelAccess.getLevelPath(LevelResource.ROOT).resolve("data")
                .resolve(CampaignWorldModeSavedData.DATA_NAME + ".dat"))) return;
        villagerretaliation$addonsButton = addRenderableWidget(Button.builder(Component.translatable("screen.villagerretaliation.addons.title"),
                button -> CampaignAddonManager.open(this, levelAccess)).bounds(width - 116, 6, 110, 20).build());
    }
}
