package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.interaction.VillagerStyledTextRenderer;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Style.class)
public abstract class DialogueTextStyleMixin {
    @Inject(method = "equals", at = @At("HEAD"), cancellable = true)
    private void villagerretaliation$preserveGlyphEffects(Object other, CallbackInfoReturnable<Boolean> callback) {
        if (other instanceof Style style && !VillagerStyledTextRenderer.sameGlyphEffect((Style) (Object) this, style)) {
            callback.setReturnValue(false);
        }
    }
}
