package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.settlement.SettlementEquipmentPlan;
import com.jvn.villagerretaliation.settlement.SettlementManagementService;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SettlementEquipmentClientChecks {
    private final VillageAllegianceId town;
    private final Consumer<String> capture;
    private SettlementEquipmentScreen editor;
    private UUID area;
    private int phase;
    private int ticks;
    private long nextServerTick;

    SettlementEquipmentClientChecks(VillageAllegianceId town, Consumer<String> capture) {
        this.town = town;
        this.capture = capture;
    }

    boolean tick(Minecraft minecraft) {
        if (++this.ticks < 5) return false;
        require(this.ticks < 300, "Equipment client step " + this.phase + " did not complete");
        if (minecraft.getSingleplayerServer().submit(() -> minecraft.getSingleplayerServer().overworld().getGameTime()).join()
                < this.nextServerTick) return false;
        var screen = minecraft.screen;
        switch (this.phase) {
            case 0 -> {
                require(screen instanceof SettlementManagementScreen, "the equipment flow starts from settlement management");
                ((SettlementManagementScreen) screen).navigateTo(Page.AREAS, "Guard post");
                next();
            }
            case 1 -> {
                var management = (SettlementManagementScreen) screen;
                if (management.snapshot().page() != Page.AREAS || management.snapshot().entries().size() != 1) return false;
                this.area = management.snapshot().entries().getFirst().id();
                screen.children().stream().filter(ObjectSelectionList.class::isInstance).map(child -> (ObjectSelectionList<?>) child)
                        .findFirst().orElseThrow().children().getFirst().mouseClicked(0, 0, 0);
                click(screen, SettlementManagementScreen.text("worksite_settings"));
                next();
            }
            case 2 -> {
                require(screen instanceof SettlementWorkerScreen, "equipment remains connected to worksite settings");
                click(screen, SettlementEquipmentScreen.text("button"));
                next();
            }
            case 3 -> {
                if (!(screen instanceof SettlementEquipmentScreen equipment) || !button(screen, SettlementEquipmentScreen.text("add")).active) return false;
                this.editor = equipment;
                click(screen, SettlementEquipmentScreen.text("kind.crossbow"));
                click(screen, SettlementEquipmentScreen.text("add"));
                next();
            }
            case 4 -> {
                require(amount(screen).getValue().equals("1"), "new durable reserve drafts default to one spare");
                SettlementPolicyClientChecks.bounds(screen);
                this.capture.accept("equipment-draft");
                click(screen, SettlementManagementScreen.text("save"));
                next();
            }
            case 5 -> {
                var saved = saved(minecraft);
                if (saved == null || !button(screen, SettlementManagementScreen.text("save")).active) return false;
                require(!saved.active(), "saving a new draft leaves replenishment paused");
                require(saved.configuration().selection(HiredEquipmentNeed.Kind.AMMUNITION).spares() == 32,
                        "a selected ranged loadout defaults to 32 compatible projectiles per worker");
                amount(screen).setValue("2");
                minecraft.options.guiScale().set(4);
                minecraft.resizeDisplay();
                next();
            }
            case 6 -> {
                SettlementPolicyClientChecks.bounds(screen);
                require(amount(screen).getValue().equals("2"), "resizing retains the unsaved spare quantity");
                this.capture.accept("equipment-small");
                minecraft.options.guiScale().set(2);
                minecraft.resizeDisplay();
                click(screen, SettlementEquipmentScreen.text("paused"));
                click(screen, SettlementManagementScreen.text("save"));
                next();
            }
            case 7 -> {
                var saved = saved(minecraft);
                if (saved == null || !saved.active() || !button(screen, SettlementManagementScreen.text("save")).active) return false;
                require(saved.configuration().selection(HiredEquipmentNeed.Kind.CROSSBOW).spares() == 2, "activation saves the chosen reserve quantity");
                this.capture.accept("equipment-active");
                next();
            }
            case 8 -> {
                var saved = saved(minecraft);
                var playerId = minecraft.player.getUUID();
                minecraft.getSingleplayerServer().submit(() -> {
                    var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                    var configuration = new SettlementEquipmentPlan.Configuration(saved.configuration().selections(), 8, true);
                    SettlementManagementService.action(player, new SettlementManagementActionPayload(this.town.value(),
                            new SettlementManagementActionPayload.SaveEquipment(this.area, saved.revision(), configuration)));
                }).join();
                require(saved(minecraft).revision() != saved.revision(), "another authorized edit advances the revision");
                next();
            }
            case 9 -> {
                amount(screen).setValue("3");
                click(screen, SettlementManagementScreen.text("save"));
                next();
            }
            case 10 -> {
                if (this.editor.parent().submitting() || button(screen, SettlementManagementScreen.text("save")).active) return false;
                var saved = saved(minecraft);
                require(saved.configuration().priority() == 8 && saved.configuration().selection(HiredEquipmentNeed.Kind.CROSSBOW).spares() == 2,
                        "the stale editor cannot overwrite another player's saved settings");
                require(amount(screen).getValue().equals("3"), "a rejected save retains its draft for inspection");
                this.capture.accept("equipment-stale");
                click(screen, SettlementEquipmentScreen.text("reload"));
                next();
            }
            case 11 -> {
                if (!button(screen, SettlementManagementScreen.text("save")).active) return false;
                require(amount(screen).getValue().equals("2"), "explicit reload replaces the draft with confirmed settings");
                amount(screen).setValue("3");
                click(screen, SettlementManagementScreen.text("save"));
                next();
            }
            case 12 -> {
                var saved = saved(minecraft);
                if (saved == null || saved.configuration().selection(HiredEquipmentNeed.Kind.CROSSBOW).spares() != 3
                        || !button(screen, SettlementPolicyClient.text("title")).active) return false;
                click(screen, SettlementPolicyClient.text("title"));
                next();
            }
            case 13 -> {
                if (!(screen instanceof SettlementPoliciesScreen policies) || policies.snapshot() == null) return false;
                require(policies.configuration().target().kind() == com.jvn.villagerretaliation.settlement.SettlementSupplyPolicy.TargetKind.EQUIPMENT
                                && policies.configuration().target().consumer().equals(this.area),
                        "the equipment editor opens a policy bound to its saved worksite reserve");
                require(SettlementViewClient.connectedPanel(), "equipment policy navigation preserves the world view");
                policies.onClose();
                policies.onClose();
                next();
            }
            case 14 -> {
                if (screen != this.editor || !button(screen, SettlementEquipmentScreen.text("remove")).active) return false;
                click(screen, SettlementEquipmentScreen.text("remove"));
                next();
            }
            case 15 -> {
                if (saved(minecraft) != null || button(screen, SettlementEquipmentScreen.text("remove")).active) return false;
                require(SettlementViewClient.connectedPanel() && minecraft.getCameraEntity() != minecraft.player,
                        "the complete equipment flow keeps the connected world camera");
                this.editor.onClose();
                require(minecraft.screen instanceof SettlementWorkerScreen, "closing equipment returns to its worksite settings");
                minecraft.screen.onClose();
                return true;
            }
            default -> throw new IllegalStateException("Unknown equipment client check " + this.phase);
        }
        return false;
    }

    private SettlementEquipmentPlan saved(Minecraft minecraft) {
        return minecraft.getSingleplayerServer().submit(() -> SettlementSavedData.get(minecraft.getSingleplayerServer().overworld())
                .get(this.town).orElseThrow().equipmentPlans().get(this.area)).join();
    }

    private void next() {
        this.phase++;
        this.ticks = 0;
        var server = Minecraft.getInstance().getSingleplayerServer();
        this.nextServerTick = server.submit(() -> server.overworld().getGameTime() + 5L).join();
        com.mojang.logging.LogUtils.getLogger().info("Equipment client check {}", this.phase);
    }

    private static Button button(Screen screen, Component label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.visible && button.getMessage().getString().equals(label.getString())).findFirst().orElseThrow();
    }

    private static void click(Screen screen, Component label) {
        var button = button(screen, label);
        require(button.active, "equipment control is available: " + label.getString());
        button.onPress();
    }

    private static EditBox amount(Screen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
