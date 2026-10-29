package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.VillagerStyledTextRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @WrapOperation(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int villagerretaliation$renderChatEffects(GuiGraphics graphics, Font font, FormattedCharSequence text,
            int x, int y, int color, Operation<Integer> original, @Local(argsOnly = true, ordinal = 0) int tickCount) {
        return VillagerStyledTextRenderer.renderChatLine(graphics, font, text, x, y, color,
                tickCount,
                (glyph, drawX, drawY, tint) -> original.call(graphics, font, glyph, drawX, drawY, tint));
    }
}
