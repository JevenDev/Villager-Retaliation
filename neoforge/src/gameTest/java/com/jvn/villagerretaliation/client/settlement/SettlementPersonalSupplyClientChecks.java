package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.client.inventory.ProductionOrdersScreen;
import com.jvn.villagerretaliation.client.inventory.ProductionSupplyScreen;
import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import com.jvn.villagerretaliation.interaction.work.production.ProductionManagementService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.network.ProductionRequestPayload;
import com.jvn.villagerretaliation.network.ProductionTarget;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.settlement.SettlementArea;
import com.jvn.villagerretaliation.settlement.SettlementManagementService;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class SettlementPersonalSupplyClientChecks {
    private final VillageAllegianceId town;
    private final UUID area;
    private final BlockPos source;
    private final Consumer<String> capture;
    private SettlementManagementScreen management;
    private ProductionOrdersScreen orders;
    private SettlementArea originalArea;
    private List<ItemStack> originalStock;
    private UUID worker;
    private UUID order;
    private UUID competitor;
    private int phase;
    private int ticks;
    private long nextServerTick;

    SettlementPersonalSupplyClientChecks(VillageAllegianceId town, UUID area, BlockPos source, Consumer<String> capture) {
        this.town = town;
        this.area = area;
        this.source = source;
        this.capture = capture;
    }

    boolean tick(Minecraft minecraft) {
        if (++this.ticks < 5) return false;
        require(this.ticks < 400, "Personal supply client step " + this.phase + " did not complete");
        if (server(minecraft, () -> minecraft.getSingleplayerServer().overworld().getGameTime()) < this.nextServerTick) return false;
        var screen = minecraft.screen;
        switch (this.phase) {
            case 0 -> {
                require(screen instanceof SettlementManagementScreen, "personal supply inspection starts from settlement management");
                this.management = (SettlementManagementScreen) screen;
                prepare(minecraft);
                next();
            }
            case 1 -> {
                var snapshot = server(minecraft, () -> {
                    var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
                    return ProductionManagementService.snapshot(player, new ProductionRequestPayload(new ProductionTarget(null, this.worker),
                            UUID.randomUUID(), 0, new ProductionRequestPayload.View()), false, "");
                });
                var need = snapshot.requirements().stream().filter(value -> value.order().equals(this.order)).findFirst().orElse(null);
                if (need == null || need.allocation().competitor() == null) return false;
                require(need.required() == 3 && need.availableNow() == 0 && need.committed() == 0 && need.incoming() == 0 && need.missing() == 3,
                        "the personal snapshot distinguishes real unavailable inputs from commitments and incoming cargo");
                require(need.allocation().blocker().equals("allocated_elsewhere") && need.allocation().competitor().priority() == 9,
                        "shared allocation identifies the higher priority civic consumer");
                this.competitor = need.allocation().competitor().consumer();
                this.management.navigateTo(Page.DEMAND, this.order.toString());
                next();
            }
            case 2 -> {
                if (this.management.snapshot().page() != Page.DEMAND) return false;
                var demand = this.management.snapshot().entries().stream().filter(SettlementManagementPayload.Demand.class::isInstance)
                        .map(SettlementManagementPayload.Demand.class::cast).filter(value -> value.consumer().equals(this.order)).findFirst().orElse(null);
                if (demand == null) return false;
                require(demand.control() == SettlementManagementPayload.DemandControl.PERSONAL_PRODUCTION,
                        "settlement demand retains the personal worker's native control target");
                this.management.restoreSelection(demand.id());
                require(!button(screen, "villagerretaliation.gui.settlement.policy.create").active
                                && !button(screen, "villagerretaliation.gui.settlement.supply_worksite").active,
                        "personal shortages cannot create civic policies or change civic staffing");
                click(screen, "villagerretaliation.gui.settlement.demand_open");
                next();
            }
            case 3 -> {
                if (!(screen instanceof ProductionOrdersScreen production) || !button(screen, "villagerretaliation.gui.production.supply.title").active) return false;
                this.orders = production;
                require(production.state().target().town() == null && production.state().target().subject().equals(this.worker),
                        "Open consumer selects the personally hired worker");
                click(screen, "villagerretaliation.gui.production.supply.title");
                next();
            }
            case 4 -> {
                if (!(screen instanceof ProductionSupplyScreen)) return false;
                verify(minecraft, screen);
                this.capture.accept("personal-supplies");
                minecraft.options.guiScale().set(4);
                minecraft.resizeDisplay();
                next();
            }
            case 5 -> {
                verify(minecraft, screen);
                this.capture.accept("personal-supplies-small");
                minecraft.options.guiScale().set(2);
                minecraft.resizeDisplay();
                screen.onClose();
                next();
            }
            case 6 -> {
                require(screen == this.orders, "Back returns to the same personal queue");
                if (!button(screen, "villagerretaliation.gui.production.supply.title").active) return false;
                click(screen, "villagerretaliation.gui.production.supply.title");
                next();
            }
            case 7 -> {
                if (!(screen instanceof ProductionSupplyScreen) || !button(screen, "villagerretaliation.gui.settlement.demand_competitor").active) return false;
                click(screen, "villagerretaliation.gui.settlement.demand_competitor");
                next();
            }
            case 8 -> {
                require(screen == this.management, "the competing consumer reuses the connected settlement controls");
                if (this.management.snapshot().page() != Page.DEMAND || this.management.snapshot().entries().isEmpty()) return false;
                var demands = this.management.snapshot().entries().stream().filter(SettlementManagementPayload.Demand.class::isInstance)
                        .map(SettlementManagementPayload.Demand.class::cast).toList();
                if (demands.stream().noneMatch(value -> value.consumer().equals(this.competitor))) return false;
                require(demands.stream().allMatch(value -> value.consumer().equals(this.competitor)),
                        "the competing link filters to the exact authoritative consumer");
                require(SettlementViewClient.connectedPanel() && minecraft.getCameraEntity() != minecraft.player,
                        "personal shortage recovery retains the world camera");
                this.capture.accept("personal-competing-demand");
                restore(minecraft);
                return true;
            }
            default -> throw new IllegalStateException("Unknown personal supply client check " + this.phase);
        }
        return false;
    }

    private void prepare(Minecraft minecraft) {
        server(minecraft, () -> {
            var level = minecraft.getSingleplayerServer().overworld();
            var town = SettlementSavedData.get(level).get(this.town).orElseThrow();
            var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
            this.originalArea = town.areas().get(this.area);
            var area = this.originalArea;
            SettlementManagementService.action(player, new SettlementManagementActionPayload(this.town.value(),
                    new SettlementManagementActionPayload.SaveArea(new SettlementArea(area.id(), area.type(), area.name(),
                            area.min(), area.max(), 9, area.enabled()))));
            require(town.areas().get(this.area).priority() == 9, "the civic worksite has explicit priority over personal input demand");
            var container = (Container) level.getBlockEntity(this.source);
            this.originalStock = java.util.stream.IntStream.range(0, container.getContainerSize()).mapToObj(slot -> container.getItem(slot).copy()).toList();
            container.clearContent();
            container.setItem(0, new ItemStack(Items.WHEAT, 3));
            var worker = EntityType.VILLAGER.create(level);
            worker.setPos(this.source.above().getCenter());
            worker.setNoAi(true);
            worker.setCustomName(Component.literal("Personal cook"));
            require(level.addFreshEntity(worker), "the personal cook is loaded");
            this.worker = worker.getUUID();
            HireContractStore.save(worker, HireContract.regular(player.getUUID(), level.getGameTime(), 1, 1, 1, HiredVillagerRole.COOK));
            AssignedStorageService.assign(player, worker, List.of(new AssignedStorageService.StoragePosition(level.dimension(), this.source)), "supply");
            var order = HiredProductionOrder.create(level, HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("bread"), Map.of()),
                    HiredProductionOrder.Mode.BATCH, 1, null);
            require(HiredProductionService.appendPersonal(player, worker, 0, order).isEmpty(), "the personal cook has a native bread order");
            this.order = order.id();
            return true;
        });
    }

    private void restore(Minecraft minecraft) {
        server(minecraft, () -> {
            var level = minecraft.getSingleplayerServer().overworld();
            var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
            ((Villager) level.getEntity(this.worker)).discard();
            SettlementManagementService.action(player, new SettlementManagementActionPayload(this.town.value(),
                    new SettlementManagementActionPayload.SaveArea(this.originalArea)));
            var container = (Container) level.getBlockEntity(this.source);
            container.clearContent();
            for (int slot = 0; slot < this.originalStock.size(); slot++) container.setItem(slot, this.originalStock.get(slot));
            return true;
        });
    }

    private static void verify(Minecraft minecraft, Screen screen) {
        SettlementPolicyClientChecks.bounds(screen);
        var rows = screen.children().stream().filter(ObjectSelectionList.class::isInstance)
                .map(value -> (ObjectSelectionList<?>) value).findFirst().orElseThrow();
        require(rows.children().size() == 1, "the selected order has one stable input requirement");
        var narration = rows.children().getFirst().getNarration().getString();
        require(narration.contains("Need 3 items / Available 0 / Committed 0 / Incoming 0 / Missing 3")
                        && narration.contains("Protected 0 / Priority 5 / Reserve target 0") && narration.contains("priority 9"),
                "the supplies row exposes quantities and the competing consumer's allocation");
        for (var child : screen.children()) if (child instanceof Button button && button.visible)
            require(minecraft.font.width(button.getMessage()) <= button.getWidth() - 4,
                    "the personal supply label fits: " + button.getMessage().getString());
        require(SettlementViewClient.connectedPanel() && minecraft.getCameraEntity() != minecraft.player,
                "the personal supplies panel remains connected to the world view");
    }

    private void next() {
        this.phase++;
        this.ticks = 0;
        var minecraft = Minecraft.getInstance();
        this.nextServerTick = server(minecraft, () -> minecraft.getSingleplayerServer().overworld().getGameTime() + 5L);
        com.mojang.logging.LogUtils.getLogger().info("Personal supply client check {}", this.phase);
    }

    private static <T> T server(Minecraft minecraft, Supplier<T> action) { return minecraft.getSingleplayerServer().submit(action).join(); }

    private static Button button(Screen screen, String key) {
        var title = Component.translatable(key).getString();
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(value -> value.visible && value.getMessage().getString().equals(title)).findFirst().orElseThrow();
    }

    private static void click(Screen screen, String key) {
        var button = button(screen, key);
        require(button.active, "personal supply control is available: " + key);
        button.onPress();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
