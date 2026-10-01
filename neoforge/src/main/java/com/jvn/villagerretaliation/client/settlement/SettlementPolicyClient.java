package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettlementPolicyClient {
    private SettlementPolicyClient() { }

    public static void accept(SettlementPolicySnapshotPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.isSpectator()
                || !minecraft.level.dimension().location().equals(payload.dimension())) return;
        Screen screen = minecraft.screen;
        while (!(screen instanceof Receiver) && (screen instanceof Child || screen instanceof com.jvn.villagerretaliation.client.inventory.ProductionRecipeScreen)) {
            if (screen instanceof Child child) screen = child.previousPolicy();
            else screen = ((com.jvn.villagerretaliation.client.inventory.ProductionRecipeScreen) screen).parent();
        }
        if (screen instanceof Receiver receiver) receiver.acceptPolicy(payload);
    }

    static Component text(String key, Object... args) {
        return Component.translatable("villagerretaliation.gui.settlement.policy." + key, args);
    }

    static Component needName(SettlementPolicySnapshotPayload.Need need) {
        return need.unit() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.Unit.NUTRITION
                ? text("target_food") : Component.literal(need.name());
    }

    static Component blocker(String value) {
        if (value.isEmpty()) return Component.empty();
        String key = "villagerretaliation.gui.settlement.policy.blocker." + value;
        if (net.minecraft.client.resources.language.I18n.exists(key)) return Component.translatable(key);
        if (value.startsWith("trade_status.")) return Component.translatable("villagerretaliation.gui.settlement." + value);
        String existing = "villagerretaliation.gui.settlement.failure." + value;
        return net.minecraft.client.resources.language.I18n.exists(existing) ? Component.translatable(existing) : text("blocked", value);
    }

    public interface Receiver { void acceptPolicy(SettlementPolicySnapshotPayload payload); }
    public interface Child { Screen previousPolicy(); }
}
