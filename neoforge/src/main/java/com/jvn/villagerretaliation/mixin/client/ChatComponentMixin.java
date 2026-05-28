package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.VillagerChatEffectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$renderCustomChat(
            GuiGraphics graphics,
            int tickCount,
            int mouseX,
            int mouseY,
            boolean focused,
            CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!VillagerChatEffectRenderer.shouldHijack(minecraft)) {
            return;
        }

        VillagerChatEffectRenderer.render(graphics, minecraft);
        callbackInfo.cancel();
    }
}
