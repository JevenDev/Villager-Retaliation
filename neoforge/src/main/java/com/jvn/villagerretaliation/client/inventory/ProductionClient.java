package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.network.ProductionSnapshotPayload;
import net.minecraft.client.Minecraft;

public final class ProductionClient {
    private ProductionClient() {
    }

    public static void accept(ProductionSnapshotPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.isSpectator()
                || !minecraft.level.dimension().location().equals(payload.dimension())) return;
        if (minecraft.screen instanceof Receiver receiver) receiver.acceptProduction(payload);
    }

    public static net.minecraft.client.gui.screens.Screen rootScreen(net.minecraft.client.gui.screens.Screen screen) {
        while (true) {
            if (screen instanceof ProductionOrdersScreen orders) screen = orders.previous();
            else if (screen instanceof ProductionRecipeScreen recipes) screen = recipes.previous();
            else if (screen instanceof ProductionStorageScreen storage) screen = storage.previous();
            else if (screen instanceof FishingSpotsScreen fishing) screen = fishing.previous();
            else return screen;
        }
    }

    public interface Receiver {
        void acceptProduction(ProductionSnapshotPayload payload);
    }
}
