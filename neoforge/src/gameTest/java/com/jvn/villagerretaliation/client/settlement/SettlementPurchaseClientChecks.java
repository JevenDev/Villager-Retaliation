package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.settlement.SettlementDepotPurpose;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementPurchasePlan;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import com.jvn.villagerretaliation.settlement.SettlementSupplyPolicy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class SettlementPurchaseClientChecks {
    private final VillageAllegianceId town;
    private final Consumer<String> capture;
    private SettlementPoliciesScreen editor;
    private SettlementPolicyPurchasesScreen purchases;
    private BlockPos destination;
    private UUID depot;
    private SettlementDepotPurpose purpose;
    private int depotPriority;
    private UUID policy;
    private UUID approval;
    private CurrencyAmount balance;
    private final UUID temporaryLeader = UUID.randomUUID();
    private Map<UUID, Set<SettlementPermission>> delegates;
    private boolean paged;
    private int phase;
    private int ticks;

    SettlementPurchaseClientChecks(VillageAllegianceId town, Consumer<String> capture) {
        this.town = town;
        this.capture = capture;
    }

    boolean tick(Minecraft minecraft) {
        if (++this.ticks < 5) return false;
        require(this.ticks < 400, "Purchase client step " + this.phase + " did not complete: "
                + (this.purchases == null ? "" : this.purchases.feedback()));
        var screen = minecraft.screen;
        switch (this.phase) {
            case 0 -> {
                require(screen instanceof SettlementManagementScreen, "purchases remain connected to settlement management");
                server(minecraft, () -> {
                    var town = town(minecraft);
                    var depot = town.depots().values().stream().filter(value -> town.areas().get(value.area()).enabled()).findFirst().orElseThrow();
                    this.depot = depot.id();
                    this.destination = depot.position();
                    this.purpose = depot.purpose();
                    this.depotPriority = depot.priority();
                    this.balance = town.economy().balance();
                    require(town.configureDepot(town.leader(), depot.id(), SettlementDepotPurpose.GENERAL, depot.priority()),
                            "the purchase fixture has a receiving depot");
                });
                this.editor = new SettlementPoliciesScreen(screen, this.town.value(), new SettlementSupplyPolicy.Target(
                        SettlementSupplyPolicy.TargetKind.FOOD, this.town.value(), null, null, ItemStack.EMPTY, 0));
                minecraft.setScreen(this.editor);
                next();
            }
            case 1 -> {
                if (!button(screen, "purchases").active) return false;
                require(!this.editor.configuration().purchases().enabled(), "a new policy starts without spending permission");
                click(screen, "purchases");
                next();
            }
            case 2 -> {
                this.purchases = (SettlementPolicyPurchasesScreen) screen;
                if (!button(screen, "purchase_add").active) return false;
                require(!this.purchases.plan().enabled() && this.purchases.plan().dailyBudget().isZero(), "the purchase draft starts disabled with a zero cap");
                click(screen, "purchase_add");
                next();
            }
            case 3 -> { click(screen, "choose_requirement"); next(); }
            case 4 -> { rows(screen).children().getFirst().mouseClicked(0, 0, 0); next(); }
            case 5 -> {
                if (!button(screen, "purchase_commodity").active) return false;
                click(screen, "purchase_commodity");
                next();
            }
            case 6 -> {
                if (rows(screen).children().isEmpty() || !this.purchases.ready()) return false;
                require(rows(screen).children().size() > 16, "the market picker exposes more than the preview sample limit");
                SettlementPolicyClientChecks.bounds(screen);
                this.capture.accept("purchase-market");
                this.paged = this.purchases.snapshot().market().more();
                if (this.paged) click(screen, "purchase_next");
                next();
            }
            case 7 -> {
                if (this.paged) {
                    if (!this.purchases.ready() || this.purchases.snapshot().market().page() != 1) return false;
                    require(!rows(screen).children().isEmpty(), "the next market page contains selectable commodities");
                    click(screen, "purchase_previous");
                }
                next();
            }
            case 8 -> {
                if (!this.purchases.ready() || this.purchases.snapshot().market().page() != 0) return false;
                field(screen, "search").setValue("minecraft:carrot");
                field(screen, "search").setValue("minecraft:bread");
                next();
            }
            case 9 -> {
                if (!this.purchases.ready() || rows(screen).children().isEmpty()) return false;
                require(rows(screen).children().size() == 1 && rows(screen).children().getFirst().getNarration().getString().contains("minecraft:bread"),
                        "superseded searches cannot replace the latest exact commodity");
                rows(screen).children().getFirst().mouseClicked(0, 0, 0);
                next();
            }
            case 10 -> {
                if (!this.purchases.ready()) return false;
                var depotButton = button(screen, "purchase_depot", "purchase_depot_value");
                require(depotButton.active, "only linked receiving depots can be selected");
                depotButton.onPress();
                next();
            }
            case 11 -> { choice(screen, this.destination.toShortString()); next(); }
            case 12 -> {
                field(screen, "purchase_unit_limit").setValue("1/0");
                require(!button(screen, "select").active, "invalid fractional price limits cannot be selected");
                field(screen, "purchase_unit_limit").setValue("1/3");
                SettlementPolicyClientChecks.bounds(screen);
                this.capture.accept("purchase-approval");
                click(screen, "select");
                next();
            }
            case 13 -> {
                if (!this.purchases.ready()) return false;
                require(this.purchases.plan().approvals().size() == 1, "choosing a commodity creates one explicit approval");
                this.approval = this.purchases.plan().approvals().getFirst().id();
                field(screen, "purchase_budget").setValue("9/2");
                click(screen, "purchase_off");
                next();
            }
            case 14 -> {
                if (!button(screen, "purchase_apply").active) return false;
                require(this.purchases.snapshot().purchases().approvals().size() == 1
                                && !this.purchases.snapshot().purchases().approvals().getFirst().unitPrice().isZero(),
                        "the draft shows its current authoritative market quote");
                this.capture.accept("purchase-draft");
                minecraft.options.guiScale().set(4);
                minecraft.resizeDisplay();
                next();
            }
            case 15 -> {
                SettlementPolicyClientChecks.bounds(screen);
                require(field(screen, "purchase_budget").getValue().equals("9/2"), "resizing preserves exact unsaved currency");
                this.capture.accept("purchase-small");
                minecraft.options.guiScale().set(2);
                minecraft.resizeDisplay();
                click(screen, "purchase_apply");
                next();
            }
            case 16 -> {
                var plan = this.editor.configuration().purchases();
                require(plan.enabled() && plan.dailyBudget().equals(CurrencyAmount.of(9, 2))
                                && plan.approvals().getFirst().unitPriceLimit().equals(CurrencyAmount.of(1, 3)),
                        "applying the purchase draft preserves exact policy and unit limits");
                require(plan.approvals().getFirst().sample().is(Items.BREAD), "the approved commodity keeps its registry identity");
                field(screen, "name").setValue("Food imports");
                next();
            }
            case 17 -> {
                if (!button(screen, "save").active) return false;
                click(screen, "save");
                next();
            }
            case 18 -> {
                this.policy = this.editor.policyId();
                if (!saved(minecraft, CurrencyAmount.of(9, 2)) || !button(screen, "save").active) return false;
                require(this.editor.snapshot().selected() != null, "saving returns the confirmed policy configuration");
                this.editor.onClose();
                next();
            }
            case 19 -> {
                if (!button(screen, "edit").active) return false;
                choice(screen, "Food imports");
                click(screen, "edit");
                next();
            }
            case 20 -> {
                if (this.editor.configuration() == null || !button(screen, "purchases").active) return false;
                require(this.editor.configuration().purchases().approvals().getFirst().id().equals(this.approval),
                        "reopening the policy preserves the purchase plan and stable approval identity");
                click(screen, "purchases");
                next();
            }
            case 21 -> {
                this.purchases = (SettlementPolicyPurchasesScreen) screen;
                if (!this.purchases.ready()) return false;
                field(screen, "purchase_budget").setValue("7/2");
                screen.onClose();
                require(this.editor.configuration().purchases().dailyBudget().equals(CurrencyAmount.of(9, 2)), "Back discards an unapplied child draft");
                next();
            }
            case 22 -> {
                if (!button(screen, "purchases").active) return false;
                click(screen, "purchases");
                next();
            }
            case 23 -> {
                this.purchases = (SettlementPolicyPurchasesScreen) screen;
                if (!this.purchases.ready() || !button(screen, "refresh").active) return false;
                server(minecraft, () -> {
                    var town = town(minecraft);
                    this.delegates = Map.copyOf(town.delegates());
                    long now = minecraft.getSingleplayerServer().overworld().getGameTime();
                    var offer = town.offerLeadership(town.leader(), this.temporaryLeader, now);
                    require(town.acceptLeadership(this.temporaryLeader, offer.id(), now), "the permission fixture has a separate leader");
                    require(town.setPermissions(this.temporaryLeader, minecraft.player.getUUID(), Set.of(SettlementPermission.VIEW)),
                            "the inspecting player retains view permission only");
                });
                click(screen, "refresh");
                next();
            }
            case 24 -> {
                if (!this.purchases.ready() || this.purchases.snapshot().editable() || !button(screen, "refresh").active) return false;
                require(!this.purchases.snapshot().trade() && !button(screen, "purchase_apply").active
                                && !field(screen, "purchase_budget").active && !button(screen, "purchase_on").active,
                        "a refreshed view-only editor cannot change or approve purchases");
                this.capture.accept("purchase-permission");
                server(minecraft, () -> {
                    var town = town(minecraft);
                    long now = minecraft.getSingleplayerServer().overworld().getGameTime();
                    var player = minecraft.player.getUUID();
                    var offer = town.offerLeadership(this.temporaryLeader, player, now);
                    require(town.acceptLeadership(player, offer.id(), now), "leadership returns to the fixture player");
                    this.delegates.forEach((delegate, permissions) -> require(town.setPermissions(player, delegate, permissions), "fixture permissions restore"));
                });
                click(screen, "refresh");
                next();
            }
            case 25 -> {
                if (!this.purchases.ready() || !this.purchases.editable()) return false;
                server(minecraft, () -> {
                    var town = town(minecraft);
                    var policy = town.supplyPolicies().get(this.policy);
                    var current = policy.configuration();
                    var changed = new SettlementSupplyPolicy.Configuration(current.name(), current.target(), current.priority(), current.steps(), current.connections(),
                            new SettlementPurchasePlan(true, CurrencyAmount.of(13, 2), current.purchases().approvals()));
                    var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
                    com.jvn.villagerretaliation.settlement.SettlementPolicyManagementService.handle(player, new SettlementPolicyRequestPayload(
                            this.town.value(), UUID.randomUUID(), this.policy, policy.revision(), null, new SettlementPolicyRequestPayload.Save(changed, false)));
                });
                next();
            }
            case 26 -> {
                if (!saved(minecraft, CurrencyAmount.of(13, 2))) return false;
                field(screen, "purchase_budget").setValue("11/2");
                next();
            }
            case 27 -> {
                if (!button(screen, "purchase_apply").active) return false;
                click(screen, "purchase_apply");
                next();
            }
            case 28 -> {
                if (!button(screen, "save").active) return false;
                click(screen, "save");
                next();
            }
            case 29 -> {
                if (!this.editor.snapshot().feedback().equals("policy_revision_changed")) return false;
                require(saved(minecraft, CurrencyAmount.of(13, 2)), "a stale purchase save cannot replace another manager's budget");
                require(this.editor.configuration().purchases().dailyBudget().equals(CurrencyAmount.of(11, 2)),
                        "the stale draft remains available for inspection");
                this.capture.accept("purchase-stale");
                this.editor.onClose();
                next();
            }
            case 30 -> {
                if (!button(screen, "edit").active) return false;
                click(screen, "edit");
                next();
            }
            case 31 -> {
                if (this.editor.configuration() == null || !button(screen, "purchases").active) return false;
                require(this.editor.configuration().purchases().dailyBudget().equals(CurrencyAmount.of(13, 2)),
                        "explicit reopening loads the current saved allowance");
                this.editor.onClose();
                next();
            }
            case 32 -> {
                if (!button(screen, "remove").active) return false;
                click(screen, "remove");
                next();
            }
            case 33 -> {
                screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(value -> value.getMessage().getString().equals(Component.translatable("gui.yes").getString())).findFirst().orElseThrow().onPress();
                next();
            }
            case 34 -> {
                if (this.editor.snapshot().policies().stream().anyMatch(value -> value.id().equals(this.policy))) {
                    if (button(screen, "refresh").active) click(screen, "refresh");
                    return false;
                }
                server(minecraft, () -> {
                    var town = town(minecraft);
                    require(town.economy().balance().equals(this.balance) && town.economy().policySpending(this.policy).isZero(),
                            "editing and saving a paused policy never spend treasury funds");
                    require(town.configureDepot(town.leader(), this.depot, this.purpose, this.depotPriority), "fixture receiving rules restore");
                });
                require(SettlementViewClient.connectedPanel() && minecraft.getCameraEntity() != minecraft.player,
                        "purchase navigation preserves the connected world camera");
                this.editor.onClose();
                return true;
            }
            default -> throw new IllegalStateException("Unknown purchase client check " + this.phase);
        }
        return false;
    }

    private com.jvn.villagerretaliation.settlement.SettlementRecord town(Minecraft minecraft) {
        return SettlementSavedData.get(minecraft.getSingleplayerServer().overworld()).get(this.town).orElseThrow();
    }

    private boolean saved(Minecraft minecraft, CurrencyAmount budget) {
        return minecraft.getSingleplayerServer().submit(() -> {
            var policy = town(minecraft).supplyPolicies().get(this.policy);
            return policy != null && policy.state() == SettlementSupplyPolicy.State.PAUSED && policy.configuration().purchases().dailyBudget().equals(budget);
        }).join();
    }

    private static void server(Minecraft minecraft, Runnable action) { minecraft.getSingleplayerServer().submit(action).join(); }

    private void next() {
        this.phase++;
        this.ticks = 0;
        com.mojang.logging.LogUtils.getLogger().info("Purchase client check {}", this.phase);
    }

    private static Button button(Screen screen, String... keys) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(value -> value.visible)
                .filter(value -> value.getMessage().getContents() instanceof TranslatableContents content
                        && java.util.Arrays.stream(keys).anyMatch(key -> content.getKey().equals("villagerretaliation.gui.settlement.policy." + key)))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing purchase control " + java.util.Arrays.toString(keys)));
    }

    private static void click(Screen screen, String key) {
        var button = button(screen, key);
        require(button.active, "purchase action is available: " + key);
        button.onPress();
    }

    private static EditBox field(Screen screen, String key) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(value -> value.getMessage().getString().equals(SettlementPolicyClient.text(key).getString())).findFirst().orElseThrow();
    }

    private static ObjectSelectionList<?> rows(Screen screen) {
        return screen.children().stream().filter(ObjectSelectionList.class::isInstance).map(value -> (ObjectSelectionList<?>) value).findFirst().orElseThrow();
    }

    private static void choice(Screen screen, String text) {
        rows(screen).children().stream().filter(value -> value.getNarration().getString().contains(text)).findFirst().orElseThrow().mouseClicked(0, 0, 0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
