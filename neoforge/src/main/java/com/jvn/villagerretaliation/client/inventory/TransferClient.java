package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.network.TransferSnapshotPayload;
import net.minecraft.client.Minecraft;

public final class TransferClient {
    private TransferClient() {
    }

    public static void accept(TransferSnapshotPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.isSpectator()
                || !minecraft.level.dimension().location().equals(payload.dimension())) return;
        if (minecraft.screen instanceof Receiver receiver) receiver.acceptTransfer(payload);
    }

    public interface Receiver {
        void acceptTransfer(TransferSnapshotPayload payload);
    }
}
