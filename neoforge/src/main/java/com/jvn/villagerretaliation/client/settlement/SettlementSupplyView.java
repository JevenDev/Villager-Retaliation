package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.inventory.ProductionOrdersScreen;
import com.jvn.villagerretaliation.client.inventory.TransferOrdersScreen;
import com.jvn.villagerretaliation.network.ProductionTarget;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Supply;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.SupplyLink;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class SettlementSupplyView {
    private SettlementSupplyView() { }

    static Component detail(Supply supply) {
        return SettlementManagementScreen.text("supply_counts", quantity(supply.stored()), quantity(supply.outgoing()),
                quantity(supply.incoming()), supply.target(), quantity(supply.shortage()));
    }

    static Component detail(SupplyLink link) {
        var detail = SettlementManagementScreen.text("supply_link_counts", status(link.status()), link.incoming(), link.target());
        return link.detail().isBlank() ? detail : detail.append(" | ").append(link.detail().replace('_', ' '));
    }

    static List<Component> tooltip(Supply supply) {
        var lines = new ArrayList<Component>();
        if (!supply.item().isEmpty()) lines.addAll(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(), supply.item()));
        lines.add(status(supply.status()));
        lines.add(SettlementManagementScreen.text("supply_accounting"));
        lines.add(SettlementManagementScreen.text("supply_target_hint"));
        return lines;
    }

    static List<Component> tooltip(SupplyLink link) {
        var lines = new ArrayList<Component>();
        if (!link.detail().isBlank()) lines.add(Component.literal(link.detail().replace('_', ' ')));
        lines.add(SettlementManagementScreen.text("supply_relation." + link.kind().name().toLowerCase(java.util.Locale.ROOT)));
        lines.add(Component.literal(link.position().toShortString()));
        return lines;
    }

    static void open(SettlementManagementScreen parent, SupplyLink link, int action) {
        if (action == 2) {
            parent.focusSupply(link.area(), link.position());
            return;
        }
        SettlementViewClient.focusSupply(parent, link.area(), link.position(), false);
        if (action == 1) {
            parent.navigateTo(Page.AREAS, link.area().toString());
            return;
        }
        if (action == 3) {
            parent.openSupplyStorage(link.area());
            return;
        }
        var client = Minecraft.getInstance();
        var target = new ProductionTarget(parent.snapshot().header().id(), link.area());
        switch (link.kind()) {
            case PRODUCTION -> client.setScreen(new ProductionOrdersScreen(parent, target, () -> client.setScreen(parent), link.targetId()));
            case TRANSFER -> client.setScreen(new TransferOrdersScreen(parent, target, () -> client.setScreen(parent), link.targetId()));
            case STORAGE_TARGET -> parent.navigateTo(Page.DEPOTS, link.targetId().toString());
            case INPUT_STORAGE -> parent.navigateTo(Page.SUPPLIES, link.position().toShortString());
            case PROJECT -> parent.navigateTo(Page.PROJECTS, link.targetId().toString());
            case CONSUMER -> parent.navigateTo(Page.DEMAND, link.targetId().toString());
            case WORKSITE -> parent.navigateTo(Page.AREAS, link.area().toString());
        }
    }

    private static Component quantity(long value) {
        return value < 0 ? SettlementManagementScreen.text("supply_unknown") : Component.literal(Long.toString(value));
    }

    private static Component status(String status) {
        return Component.translatableWithFallback("villagerretaliation.gui.settlement.supply_status." + status, status.replace('_', ' '));
    }
}
