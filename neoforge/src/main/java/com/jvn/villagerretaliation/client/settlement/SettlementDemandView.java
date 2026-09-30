package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.inventory.ProductionOrdersScreen;
import com.jvn.villagerretaliation.client.inventory.TransferOrdersScreen;
import com.jvn.villagerretaliation.network.ProductionTarget;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Demand;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.TransferRequestPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class SettlementDemandView {
    private SettlementDemandView() { }

    static Component detail(Demand demand) {
        var unit = Component.translatable("villagerretaliation.gui.settlement.supply_unit." + demand.unit().name().toLowerCase(java.util.Locale.ROOT));
        return Component.translatable("villagerretaliation.gui.settlement.demand_quantities",
                demand.requirement(), demand.required(), unit, demand.availableNow(), demand.committed(), demand.incoming(),
                demand.missing() < 0 ? Component.translatable("villagerretaliation.gui.settlement.supply_unknown") : demand.missing());
    }

    static List<Component> tooltip(Demand demand) {
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(demand.advice()));
        if (demand.alternatives().size() > 1) lines.add(Component.literal("Accepts: " + String.join(", ",
                demand.alternatives().stream().map(stack -> stack.getHoverName().getString()).toList())));
        for (var source : demand.sources()) lines.add(Component.literal("Input storage: " + source.toShortString()));
        lines.add(Component.literal("Immediate batch shortage: " + (demand.immediateMissing() < 0 ? "unknown" : demand.immediateMissing())));
        lines.add(Component.translatable("villagerretaliation.gui.settlement.demand_allocation",
                demand.protectedMinimum(), demand.priority(), demand.targetReserve()));
        if (demand.proposed() > 0) lines.add(Component.translatable("villagerretaliation.gui.settlement.demand_proposed", demand.proposed()));
        lines.add(Component.literal("Required quantities cover remaining work."));
        return lines;
    }

    static void open(SettlementManagementScreen parent, Demand demand, int action) {
        var client = Minecraft.getInstance();
        if (action == 2) { parent.focusSupply(demand.area(), demand.focus()); return; }
        SettlementViewClient.focusSupply(parent, demand.area(), demand.focus(), false);
        if ((demand.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD
                || demand.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE) && action != 4) {
            parent.navigateTo(Page.DEPOTS, "");
            return;
        }
        if (action == 1) {
            if (demand.cause() == com.jvn.villagerretaliation.settlement.SettlementSupplyRecovery.Cause.STATION)
                parent.navigateTo(Page.STATIONS, demand.area().toString());
            else parent.openSupplyStorage(demand.area());
        } else if (action == 3) parent.navigateTo(Page.AREAS, demand.area().toString());
        else if (action == 4 && demand.courier() != null) {
            var orders = new TransferOrdersScreen(parent, new ProductionTarget(parent.snapshot().header().id(), demand.courier()));
            long value = switch (demand.unit()) {
                case ITEMS, WATER_FILLS -> 1;
                case BURN_TICKS -> Math.max(1, demand.sample().getBurnTime(null));
                case BREW_USES -> 20;
                case NUTRITION -> demand.sample().has(net.minecraft.core.component.DataComponents.FOOD)
                        ? Math.max(1, demand.sample().get(net.minecraft.core.component.DataComponents.FOOD).nutrition()) : 1;
            };
            int amount = (int) Math.clamp((Math.max(1, demand.missing()) + value - 1) / value, 1, 4096);
            orders.draft(new TransferRequestPayload.Append(demand.source(), demand.destination(), demand.sample(), amount));
            client.setScreen(orders);
        } else if (action == 0) {
            if (demand.project()) parent.navigateTo(Page.PROJECTS, demand.consumer().toString());
            else client.setScreen(new ProductionOrdersScreen(parent, new ProductionTarget(parent.snapshot().header().id(), demand.area()),
                    () -> client.setScreen(parent), demand.consumer()));
        }
    }
}
