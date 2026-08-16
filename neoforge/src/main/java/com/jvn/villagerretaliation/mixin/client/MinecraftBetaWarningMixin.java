package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.BetaWarningScreen;
import com.jvn.villagerretaliation.client.BetaWarningState;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftBetaWarningMixin {
    @Shadow
    @Nullable
    public Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$showBetaWarningOnce(@Nullable Screen nextScreen, CallbackInfo callbackInfo) {
        if (nextScreen == null
                || nextScreen instanceof BetaWarningScreen
                || BetaWarningState.isAcknowledged()
                || !isProtectedDestination(nextScreen)) {
            return;
        }

        Minecraft minecraft = (Minecraft) (Object) this;
        Screen parent = nextScreen instanceof CreateWorldScreen
                ? ((CreateWorldScreenAccessor) nextScreen).villagerretaliation$getLastScreen()
                : this.screen;
        minecraft.setScreen(new BetaWarningScreen(parent, nextScreen));
        callbackInfo.cancel();
    }

    private static boolean isProtectedDestination(Screen screen) {
        return screen instanceof CreateWorldScreen
                || screen instanceof JoinMultiplayerScreen
                || screen instanceof SafetyScreen;
    }
}
