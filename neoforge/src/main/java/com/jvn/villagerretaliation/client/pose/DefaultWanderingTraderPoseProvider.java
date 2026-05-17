package com.jvn.villagerretaliation.client.pose;

import net.minecraft.world.entity.npc.WanderingTrader;

public final class DefaultWanderingTraderPoseProvider extends AbstractCombatVillagerPoseProvider<WanderingTrader> {
    public static final DefaultWanderingTraderPoseProvider INSTANCE = new DefaultWanderingTraderPoseProvider();

    private DefaultWanderingTraderPoseProvider() {
    }

    @Override
    protected boolean isAggressivelyPostured(WanderingTrader trader) {
        return trader.isAggressive() || trader.getTarget() != null;
    }
}
