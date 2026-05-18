package com.jvn.villagerretaliation.client.reputation;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record VillagerReputationTooltipComponent(String text, com.jvn.villagerretaliation.reputation.VillagerReputationLevel level)
        implements TooltipComponent {
}
