package com.jvn.villagerretaliation.mixin.client;

import com.jvn.villagerretaliation.client.config.VillagerRetaliationServerConfigClient;
import com.jvn.villagerretaliation.client.trade.VillagerTradeScreenBackground;
import com.jvn.villagerretaliation.client.trade.VillagerTradeScreenLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> implements VillagerTradeScreenLayout.Host {
    @Unique private final VillagerTradeScreenLayout villagerretaliation$layout = new VillagerTradeScreenLayout();

    protected MerchantScreenMixin(MerchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void renderTransparentBackground(GuiGraphics graphics) {
        if (!villagerretaliation$layout.split()) super.renderTransparentBackground(graphics);
    }

    @Override
    public VillagerTradeScreenLayout villagerretaliation$tradeLayout() {
        return villagerretaliation$layout;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void villagerretaliation$positionTrades(CallbackInfo callback) {
        villagerretaliation$layout.update((MerchantScreen) (Object) this);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void villagerretaliation$updateLayout(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo callback) {
        villagerretaliation$layout.update((MerchantScreen) (Object) this);
    }

    // vanilla recalculates the centered origin when drawing offers and the background
    @ModifyVariable(method = {"render", "renderBg"}, at = @At("STORE"), ordinal = 2)
    private int villagerretaliation$tradeOrigin(int original) {
        return ((MerchantScreen) (Object) this).getGuiLeft();
    }

    @ModifyVariable(method = "mouseClicked", at = @At("STORE"), ordinal = 1)
    private int villagerretaliation$scrollbarOrigin(int original) {
        return leftPos;
    }

    @Inject(method = "renderBg", at = @At("HEAD"))
    private void villagerretaliation$renderExtendedTradeBackground(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY,
            CallbackInfo callbackInfo) {
        if (!VillagerRetaliationServerConfigClient.skillTradeFeaturesEnabled()) {
            return;
        }
        VillagerTradeScreenBackground.render(graphics, (MerchantScreen) (Object) this);
    }
}
