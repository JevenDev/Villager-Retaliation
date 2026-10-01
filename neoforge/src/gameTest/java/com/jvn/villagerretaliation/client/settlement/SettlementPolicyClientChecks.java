package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.client.inventory.ProductionRecipeScreen;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload;
import com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import com.jvn.villagerretaliation.settlement.SettlementSupplyPolicy;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

final class SettlementPolicyClientChecks {
    private final VillageAllegianceId town;
    private final UUID area;
    private final BlockPos destination;
    private final Consumer<String> capture;
    private SettlementPoliciesScreen editor;
    private UUID policy;
    private UUID binding;
    private com.jvn.villagerretaliation.client.inventory.ProductionOrdersScreen managedOrders;
    private int phase;
    private int ticks;

    SettlementPolicyClientChecks(VillageAllegianceId town, UUID area, BlockPos destination, Consumer<String> capture) {
        this.town = town;
        this.area = area;
        this.destination = destination;
        this.capture = capture;
    }

    boolean tick(Minecraft minecraft) {
        if (++this.ticks < 5) return false;
        Screen screen = minecraft.screen;
        require(this.ticks < 400, "Policy client step " + this.phase + " did not complete: "
                + (this.editor == null || this.editor.snapshot() == null ? "no snapshot" : this.editor.snapshot().feedback()
                + " / " + this.editor.snapshot().blocker() + " / " + this.editor.snapshot().policies()));
        switch (this.phase) {
            case 0 -> {
                if (!(screen instanceof SettlementPoliciesScreen policies) || policies.snapshot() == null) return false;
                this.editor = policies;
                require(server(minecraft, () -> {
                    var level = minecraft.getSingleplayerServer().overworld();
                    for (var frame : level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
                            new net.minecraft.world.phys.AABB(this.destination).inflate(2),
                            entity -> entity.getPos().relative(entity.getDirection().getOpposite()).equals(this.destination)))
                        frame.setItem(net.minecraft.world.item.ItemStack.EMPTY);
                    return com.jvn.villagerretaliation.inventory.AssignedStorageService.unreservedOutputCapacity(level, this.destination,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD)) >= 12;
                }), "the policy fixture permits bread in the selected output container");
                verifyRequests(policies.snapshot());
                bounds(screen);
                click(screen, "new");
                next();
            }
            case 1 -> { choice(screen, SettlementPolicyClient.text("target_stock").getString()); next(); }
            case 2 -> { choice(screen, workshop()); next(); }
            case 3 -> {
                if (!(screen instanceof SettlementPolicyChoiceScreen)) return false;
                choice(screen, this.destination.toShortString());
                next();
            }
            case 4 -> {
                search(screen, "minecraft:bread");
                choice(screen, "minecraft:bread");
                next();
            }
            case 5 -> {
                require(screen == this.editor && this.editor.configuration().target().sample().is(net.minecraft.world.item.Items.BREAD),
                        "the storage and exact item selections form the stock target");
                field(screen, "name").setValue("Bakery reserve");
                field(screen, "amount").setValue("8");
                click(screen, "add_step");
                next();
            }
            case 6 -> { bounds(screen); click(screen, "choose_worksite"); next(); }
            case 7 -> { choice(screen, workshop()); next(); }
            case 8 -> {
                if (!button(screen, "choose_destination").active) return false;
                click(screen, "choose_recipe");
                next();
            }
            case 9 -> {
                require(screen instanceof ProductionRecipeScreen, "policy production uses the native recipe picker");
                search(screen, "minecraft:bread");
                rows(screen).children().getFirst().mouseClicked(0, 0, 0);
                bounds(screen);
                this.capture.accept("policy-exact-recipe");
                click(screen, "select");
                next();
            }
            case 10 -> { click(screen, "choose_destination"); next(); }
            case 11 -> { choice(screen, this.destination.toShortString()); next(); }
            case 12 -> { this.capture.accept("policy-step"); click(screen, "select"); next(); }
            case 13 -> {
                if (screen != this.editor || this.editor.snapshot().needs().isEmpty() || !button(screen, "connect").active) return false;
                require(this.editor.configuration().steps().size() == 1, "saving the step retains the exact configured recipe");
                click(screen, "connect");
                next();
            }
            case 14 -> { choice(screen, SettlementPolicyClient.text("target").getString()); next(); }
            case 15 -> {
                if (!button(screen, "activate").active) return false;
                bounds(screen);
                require(this.editor.configuration().connections().size() == 1, "the requirement picker connects the producer to the target");
                this.capture.accept("policy-editor");
                click(screen, "preview");
                next();
            }
            case 16 -> {
                if (!(screen instanceof SettlementPolicyPreviewScreen)) return false;
                bounds(screen);
                require(this.editor.snapshot().needs().stream().anyMatch(need -> need.step() != null && need.required() == 24),
                        "eight bread uses the native requirement of twenty-four wheat");
                require(this.editor.snapshot().production().getFirst().proposed() == 8, "preview keeps proposed production separate");
                this.capture.accept("policy-preview");
                screen.onClose();
                minecraft.options.guiScale().set(4);
                minecraft.resizeDisplay();
                next();
            }
            case 17 -> {
                if (!button(screen, "save").active) return false;
                bounds(screen);
                this.capture.accept("policy-editor-small");
                require(field(screen, "name").getValue().equals("Bakery reserve") && field(screen, "amount").getValue().equals("8"),
                        "resizing preserves the unsaved policy");
                minecraft.options.guiScale().set(2);
                minecraft.resizeDisplay();
                click(screen, "save");
                next();
            }
            case 18 -> {
                if (!saved(SettlementSupplyPolicy.State.PAUSED, "Bakery reserve")) return false;
                this.policy = this.editor.snapshot().policies().getFirst().id();
                require(server(minecraft, () -> {
                    var town = SettlementSavedData.get(minecraft.getSingleplayerServer().overworld()).get(this.town).orElseThrow();
                    return town.supplyPolicies().get(this.policy).bindings().isEmpty();
                }), "saving a paused policy does not create execution orders");
                if (!button(screen, "activate").active) return false;
                click(screen, "activate");
                next();
            }
            case 19 -> {
                if (!saved(SettlementSupplyPolicy.State.ACTIVE, "Bakery reserve")) return false;
                if (!server(minecraft, () -> {
                    var town = SettlementSavedData.get(minecraft.getSingleplayerServer().overworld()).get(this.town).orElseThrow();
                    var policy = town.supplyPolicies().get(this.policy);
                    if (policy.bindings().size() != 1) return false;
                    this.binding = policy.bindings().getFirst().order();
                    return town.productionQueues().get(this.area).order(this.binding) != null;
                })) return false;
                this.managedOrders = new com.jvn.villagerretaliation.client.inventory.ProductionOrdersScreen(this.editor,
                        new com.jvn.villagerretaliation.network.ProductionTarget(this.town.value(), this.area),
                        () -> minecraft.setScreen(this.editor), this.binding);
                minecraft.setScreen(this.managedOrders);
                this.phase = 100;
                this.ticks = 0;
            }
            case 100 -> {
                if (screen != this.managedOrders || this.managedOrders.state().snapshot() == null
                        || !button(screen, "open").active) return false;
                var links = this.managedOrders.state().snapshot().managed();
                require(links.size() == 1 && links.getFirst().policy().equals(this.policy)
                        && links.getFirst().order().equals(this.binding), "native production exposes the exact managed policy link");
                require(rows(screen).children().stream().anyMatch(row -> row.getNarration().getString().contains(
                        SettlementPolicyClient.text("managed_label", net.minecraft.world.item.Items.BREAD.getDescription()).getString())),
                        "managed orders are identifiable in the native queue");
                bounds(screen);
                require(minecraft.getCameraEntity() != minecraft.player, "managed orders remain connected to the world view");
                this.capture.accept("policy-managed-order");
                click(screen, "open");
                next();
            }
            case 101 -> {
                if (!(screen instanceof SettlementPoliciesScreen policies) || policies == this.editor || policies.configuration() == null) return false;
                require(policies.policyId().equals(this.policy) && policies.previousPolicy() == this.managedOrders,
                        "the order link opens its existing policy and retains the return path");
                require(policies.configuration().name().equals("Bakery reserve"), "the linked policy loads its current configuration");
                bounds(screen);
                this.capture.accept("policy-from-order");
                screen.onClose();
                next();
            }
            case 102 -> {
                require(screen instanceof SettlementPoliciesScreen && screen != this.editor, "back returns to the linked policy list");
                screen.onClose();
                next();
            }
            case 103 -> {
                require(screen == this.managedOrders, "closing the linked policy restores the same order screen");
                var pause = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(value -> value.getMessage().getString().equals("Pause")).findFirst().orElseThrow();
                require(pause.active, "the managed native order can be paused");
                pause.onPress();
                next();
            }
            case 104 -> {
                if (this.managedOrders.state().snapshot().managed().getFirst().state() != SettlementSupplyPolicy.State.SUSPENDED) return false;
                screen.onClose();
                require(minecraft.screen == this.editor, "closing orders restores the original editor");
                var management = (SettlementManagementScreen) this.editor.previousPolicy();
                minecraft.setScreen(management);
                management.navigateTo(com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.ISSUES, "Bakery reserve");
                next();
            }
            case 105 -> {
                if (!(screen instanceof SettlementManagementScreen management)
                        || management.snapshot().page() != com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.ISSUES) return false;
                var issue = management.snapshot().entries().stream()
                        .filter(com.jvn.villagerretaliation.network.SettlementManagementPayload.Issue.class::isInstance)
                        .map(com.jvn.villagerretaliation.network.SettlementManagementPayload.Issue.class::cast)
                        .filter(value -> this.policy.equals(value.targetId())).findFirst().orElse(null);
                if (issue == null) return false;
                require(issue.cause().equals("policy_order_suspended") && !issue.needsAttention(),
                        "a deliberate pause remains recoverable without a warning notification");
                management.restoreSelection(issue.id());
                var open = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(value -> value.getMessage().getString().equals(SettlementManagementScreen.text("issue_open").getString()))
                        .findFirst().orElseThrow();
                require(open.active, "the policy issue exposes its recovery control");
                this.capture.accept("policy-paused-issue");
                open.onPress();
                next();
            }
            case 106 -> {
                if (!(screen instanceof SettlementPoliciesScreen policies) || policies.configuration() == null
                        || !button(screen, "activate").active) return false;
                require(policies.policyId().equals(this.policy) && policies.previousPolicy() == this.editor.previousPolicy(),
                        "the issue opens the affected policy in the existing panel navigation");
                require(minecraft.getCameraEntity() != minecraft.player, "issue recovery retains the connected world camera");
                bounds(screen);
                next();
            }
            case 107 -> {
                if (!button(screen, "activate").active) return false;
                this.capture.accept("policy-from-issue");
                click(screen, "activate");
                this.phase = 109;
                this.ticks = 0;
            }
            case 109 -> {
                var policies = (SettlementPoliciesScreen) screen;
                if (policies.snapshot().policies().stream().noneMatch(value ->
                        value.id().equals(this.policy) && value.state() == SettlementSupplyPolicy.State.ACTIVE)) return false;
                if (!server(minecraft, () -> {
                    var town = SettlementSavedData.get(minecraft.getSingleplayerServer().overworld()).get(this.town).orElseThrow();
                    var policy = town.supplyPolicies().get(this.policy);
                    if (policy.bindings().size() != 1 || policy.bindings().getFirst().order().equals(this.binding)) return false;
                    var queue = town.productionQueues().get(this.area);
                    var replacement = policy.bindings().getFirst().order();
                    if (queue.order(replacement) == null) return false;
                    require(queue.order(this.binding) == null && queue.orders().size() == 2,
                            "explicit reactivation retires the stopped binding without duplicating managed or manual work");
                    this.binding = replacement;
                    return true;
                })) return false;
                screen.onClose();
                this.phase = 108;
                this.ticks = 0;
            }
            case 108 -> {
                screen.onClose();
                require(minecraft.screen == this.editor.previousPolicy(), "closing issue recovery returns to management");
                minecraft.setScreen(this.editor);
                click(this.editor, "back");
                this.phase = 20;
                this.ticks = 0;
            }
            case 20 -> {
                if (this.editor.snapshot().policies().isEmpty()) return false;
                rows(screen).children().getFirst().mouseClicked(0, 0, 0);
                if (!button(screen, "pause").active) return false;
                click(screen, "pause");
                next();
            }
            case 21 -> {
                if (!saved(SettlementSupplyPolicy.State.PAUSED, "Bakery reserve") || !button(screen, "edit").active) return false;
                this.capture.accept("policy-paused");
                click(screen, "history");
                this.phase = 110;
                this.ticks = 0;
            }
            case 110 -> {
                if (!(screen instanceof SettlementPolicyHistoryScreen)) return false;
                for (var kind : List.of("history_activated", "history_paused")) require(rows(screen).children().stream()
                        .anyMatch(row -> row.getNarration().getString().contains(SettlementPolicyClient.text(kind).getString())),
                        "policy history exposes activation and pause events");
                bounds(screen);
                this.capture.accept("policy-history");
                screen.onClose();
                next();
            }
            case 111 -> {
                if (screen != this.editor || !button(screen, "edit").active) return false;
                click(screen, "edit");
                this.phase = 22;
                this.ticks = 0;
            }
            case 22 -> {
                if (this.editor.configuration() == null) return false;
                field(screen, "name").setValue("Bakery reserve revised");
                field(screen, "amount").setValue("12");
                next();
            }
            case 23 -> {
                if (!button(screen, "activate").active) return false;
                click(screen, "activate");
                next();
            }
            case 24 -> {
                if (!saved(SettlementSupplyPolicy.State.ACTIVE, "Bakery reserve revised")) return false;
                if (!server(minecraft, () -> {
                    var town = SettlementSavedData.get(minecraft.getSingleplayerServer().overworld()).get(this.town).orElseThrow();
                    var policy = town.supplyPolicies().get(this.policy);
                    return policy.bindings().size() == 1 && policy.bindings().getFirst().order().equals(this.binding)
                            && town.productionQueues().get(this.area).order(this.binding).amount() == 12;
                })) return false;
                click(screen, "back");
                next();
            }
            case 25 -> {
                if (this.editor.snapshot().policies().isEmpty()) return false;
                rows(screen).children().getFirst().mouseClicked(0, 0, 0);
                if (!button(screen, "remove").active) return false;
                click(screen, "remove");
                next();
            }
            case 26 -> {
                require(screen instanceof SettlementConfirmScreen, "removing a policy requires the displayed confirmation");
                screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(value -> value.getMessage().getString().equals(Component.translatable("gui.yes").getString()))
                        .findFirst().orElseThrow().onPress();
                next();
            }
            case 27 -> {
                if (!this.editor.snapshot().policies().isEmpty()) {
                    if (this.ticks % 10 == 0 && button(screen, "refresh").active) click(screen, "refresh");
                    return false;
                }
                require(server(minecraft, () -> {
                    var town = SettlementSavedData.get(minecraft.getSingleplayerServer().overworld()).get(this.town).orElseThrow();
                    return !town.supplyPolicies().containsKey(this.policy) && town.productionQueues().get(this.area).order(this.binding) == null
                            && !town.productionQueues().get(this.area).orders().isEmpty();
                }), "removal retires the managed order while preserving manual production");
                require(minecraft.getCameraEntity() != minecraft.player, "the complete policy flow preserves the connected world camera");
                this.editor.onClose();
                return true;
            }
            default -> throw new IllegalStateException("Unknown policy client check " + this.phase);
        }
        return false;
    }

    private boolean saved(SettlementSupplyPolicy.State state, String name) {
        return this.editor.snapshot().policies().stream().anyMatch(value -> value.state() == state && value.name().equals(name));
    }

    private String workshop() {
        return this.editor.snapshot().worksites().stream().filter(value -> value.id().equals(this.area)).findFirst().orElseThrow().name();
    }

    private void next() {
        this.phase++;
        this.ticks = 0;
        com.mojang.logging.LogUtils.getLogger().info("Policy client check {}", this.phase);
    }

    private static boolean server(Minecraft minecraft, java.util.function.Supplier<Boolean> check) {
        return minecraft.getSingleplayerServer().submit(check).join();
    }

    private static void click(Screen screen, String key) {
        var button = button(screen, key);
        require(button.active, "policy action " + key + " is available on " + screen.getClass().getSimpleName());
        button.onPress();
    }

    private static Button button(Screen screen, String key) {
        String label = SettlementPolicyClient.text(key).getString();
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.visible && button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static EditBox field(Screen screen, String key) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(field -> field.visible && field.getMessage().getString().equals(SettlementPolicyClient.text(key).getString()))
                .findFirst().orElseThrow();
    }

    private static void search(Screen screen, String query) {
        screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(field -> field.visible).findFirst().orElseThrow().setValue(query);
    }

    private static ObjectSelectionList<?> rows(Screen screen) {
        return screen.children().stream().filter(ObjectSelectionList.class::isInstance)
                .map(child -> (ObjectSelectionList<?>) child).findFirst().orElseThrow();
    }

    private static void choice(Screen screen, String text) {
        rows(screen).children().stream().filter(row -> row.getNarration().getString().contains(text))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing policy choice " + text)).mouseClicked(0, 0, 0);
    }

    static void bounds(Screen screen) {
        var minecraft = Minecraft.getInstance();
        int width = screen.width, height = screen.height;
        for (var size : List.of(new int[]{320, 240}, new int[]{426, 240}, new int[]{width, height})) {
            screen.resize(minecraft, size[0], size[1]);
            var controls = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .filter(widget -> widget.visible).toList();
            for (var control : controls) {
                require(control.getX() >= 0 && control.getY() >= 0 && control.getRight() <= screen.width && control.getBottom() <= screen.height,
                        "policy controls fit at " + size[0] + " by " + size[1]);
                for (var other : controls) if (other != control) require(control.getRight() <= other.getX() || other.getRight() <= control.getX()
                        || control.getBottom() <= other.getY() || other.getBottom() <= control.getY(), "policy controls do not overlap: "
                        + control.getMessage().getString() + " / " + other.getMessage().getString() + " at " + size[0] + " by " + size[1]);
            }
            if (screen instanceof VillagerWindowScreen window) SettlementViewPanelClientChecks.verifyWindow(window);
        }
    }

    private static void verifyRequests(SettlementPolicySnapshotPayload snapshot) {
        var state = new SettlementPolicyClientState(snapshot.town());
        var old = state.request(null, 0, null, new SettlementPolicyRequestPayload.View(), 0);
        var current = state.request(null, 0, null, new SettlementPolicyRequestPayload.View(), 1);
        require(!state.accept(reply(snapshot, old.request())) && state.snapshot() == null, "superseded policy responses cannot replace current state");
        require(state.accept(reply(snapshot, current.request())) && state.editable(), "the matching response publishes its permissions");
        var save = state.request(UUID.randomUUID(), 1, null, new SettlementPolicyRequestPayload.Change(SettlementSupplyPolicy.State.PAUSED), 10);
        require(save != null && state.busy() && state.request(null, 0, null, new SettlementPolicyRequestPayload.View(), 11) == null,
                "a mutation cannot be superseded by a view refresh");
        require(!state.expire(10_009) && state.expire(10_010) && !state.accept(reply(snapshot, save.request())),
                "timed-out mutations retain the last confirmed snapshot and ignore late replies");
    }

    private static SettlementPolicySnapshotPayload reply(SettlementPolicySnapshotPayload source, UUID request) {
        return new SettlementPolicySnapshotPayload(source.dimension(), source.town(), request, false, source.editable(), "", "",
                source.policies(), source.selected(), source.worksites(), source.storages(), source.consumers(), source.needs(), source.production(),
                source.trade(), source.gameTime(), source.purchases(), source.market());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
