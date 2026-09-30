package com.jvn.villagerretaliation.interaction.work.production;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public final class WorkSupplyEvaluation {
    private WorkSupplyEvaluation() { }

    public static long consume(List<ItemStack> supplies, WorkSupplyRequirement requirement, long remaining) {
        for (var sample : supplies) {
            long value = requirement.value(sample);
            if (value <= 0 || remaining <= 0) continue;
            int used = (int) Math.min(sample.getCount(), (remaining - 1) / value + 1);
            sample.shrink(used);
            remaining = Math.max(0, remaining - used * value);
        }
        return remaining;
    }

    public static long progress(WorkSupplyRequirement requirement, long burn, long uses, boolean water) {
        return Math.min(requirement.quantity(), switch (requirement.unit()) {
            case BURN_TICKS -> burn;
            case BREW_USES -> uses;
            case WATER_FILLS -> water ? requirement.quantity() : 0;
            default -> 0;
        });
    }

    public static String name(WorkSupplyRequirement requirement) {
        if (requirement.unit() == WorkSupplyRequirement.Unit.NUTRITION)
            return net.minecraft.network.chat.Component.translatable("villagerretaliation.gui.settlement.eligible_meals").getString();
        return requirement.alternatives().isEmpty() ? requirement.key()
                : requirement.alternatives().getFirst().getHoverName().getString()
                + (requirement.alternatives().size() > 1 ? " or compatible alternative" : "");
    }
}
