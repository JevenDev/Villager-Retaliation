package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.VillageRelationsPayload;
import net.minecraft.client.Minecraft;

public final class VillageRelationsClient {
    private static int sequence;
    private VillageRelationsClient() { }

    public static int nextRequest() { return sequence = sequence == Integer.MAX_VALUE ? 1 : sequence + 1; }

    public static void accept(VillageRelationsPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()) return;
        if (minecraft.screen instanceof VillageRelationsScreen screen) screen.accept(payload);
        com.jvn.villagerretaliation.client.exploration.ExploredMapClient.acceptRelations(payload);
    }
}
