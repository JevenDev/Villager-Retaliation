package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentStore;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.projectile.Projectile;

public final class SettlementLifecycleService {
    private static final Map<MinecraftServer, Map<UUID, Confirmation>> CONFIRMATIONS = new WeakHashMap<>();

    private SettlementLifecycleService() {
    }

    public enum Operation { DISSOLVE, RELEASE, RELOCATE, MERGE }

    public record Preview(UUID token, String summary, List<String> blockers) {
        public Preview { blockers = List.copyOf(blockers); }
    }

    public static Preview preview(CommandSourceStack actor, VillageAllegianceId id, Operation operation,
                                  VillageAllegianceId targetId, BlockPos position) {
        var plan = plan(actor, id, operation, targetId, position);
        if (!plan.blockers().isEmpty()) return new Preview(null, plan.summary(), plan.blockers());
        long now = actor.getServer().overworld().getGameTime();
        var pending = CONFIRMATIONS.computeIfAbsent(actor.getServer(), ignored -> new HashMap<>());
        pending.values().removeIf(confirmation -> confirmation.expiresAt() <= now);
        if (pending.size() >= 256) return new Preview(null, "", List.of("Too many pending confirmations. Wait one minute."));
        UUID token = UUID.randomUUID();
        pending.put(token, new Confirmation(identity(actor), id, operation, targetId, position, plan.summary(), now + 1200L));
        return new Preview(token, plan.summary(), List.of());
    }

    public static String confirm(CommandSourceStack actor, UUID token) {
        var pending = CONFIRMATIONS.getOrDefault(actor.getServer(), Map.of());
        var confirmation = pending.get(token);
        if (confirmation == null || !confirmation.actor().equals(identity(actor))) return "No matching confirmation.";
        pending.remove(token);
        if (actor.getServer().overworld().getGameTime() >= confirmation.expiresAt()) return "Confirmation expired. Request a new preview.";
        var plan = plan(actor, confirmation.town(), confirmation.operation(), confirmation.target(), confirmation.position());
        if (!plan.blockers().isEmpty()) return String.join(" ", plan.blockers());
        if (!plan.summary().equals(confirmation.summary())) return "The town changed since the preview. Request a new preview.";
        try {
            if (confirmation.operation() == Operation.DISSOLVE) {
                settleResidents(plan.level(), plan.town());
                var unsettled = new ArrayList<String>();
                dissolutionBlockers(plan.level(), plan.town(), unsettled);
                if (!unsettled.isEmpty()) return String.join(" ", unsettled);
            }
            SettlementSavedData.get(plan.level()).applyLifecycle(plan.level(), plan.town(), plan.target(), plan.replacement());
            if (plan.replacement() != null) {
                var updated = SettlementSavedData.get(plan.level()).get(plan.town().id()).orElseThrow();
                rebindWorkers(plan.level(), updated);
            }
        } catch (java.io.IOException exception) {
            com.mojang.logging.LogUtils.getLogger().error("Could not commit settlement lifecycle", exception);
            return "Could not save the lifecycle change. The settlement record and claims were retained. Check server disk errors.";
        }
        return "";
    }

    private static Plan plan(CommandSourceStack actor, VillageAllegianceId id, Operation operation,
                             VillageAllegianceId targetId, BlockPos position) {
        var data = SettlementSavedData.get(actor.getLevel());
        var town = data.get(id).orElse(null);
        var target = targetId == null ? null : data.get(targetId).orElse(null);
        var blockers = new ArrayList<String>();
        if (town == null || !town.id().equals(id) || !authorized(actor, town)) {
            return new Plan(null, null, null, null, "", List.of("Only the current leader or a permission-level 2 administrator can change this settlement."));
        }
        var level = actor.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, town.dimension()));
        if (level == null || data.readOnly()) return new Plan(null, town, null, null, "", List.of("The settlement dimension or saved data is unavailable."));
        CompoundTag replacement = null;
        String effect = "";
        try {
            switch (operation) {
                case DISSOLVE -> {
                    dissolutionBlockers(level, town, blockers);
                    effect = "Dissolve management and release all " + town.territory().plots().size()
                            + " plots. Residents and their beds remain in the independent village. Civic duties, applications, "
                            + "delegates and remote views end. Empty depot blocks remain. Chunk receipts remain valid. No automatic deletion of residents.";
                }
                case RELEASE -> {
                    if (position == null) throw new IllegalArgumentException("A plot position is required.");
                    replacement = SettlementLifecyclePlan.release(level, town, position);
                    effect = "Release plot " + new net.minecraft.world.level.ChunkPos(position) + ". No currency or free-claim credit is refunded.";
                }
                case RELOCATE -> {
                    if (position == null) throw new IllegalArgumentException("A new center is required.");
                    replacement = SettlementLifecyclePlan.relocate(level, town, position);
                    effect = town.contains(position) ? "Relocate the management center to " + position.toShortString()
                            + ". Existing plots, residents, buildings and inventories stay in place. Pending leadership offers are cancelled."
                            : "Relocate the empty town's claims to center " + position.toShortString()
                            + ". No physical blocks or entities move. Plot count, treasury and claim credits are preserved. Pending leadership offers are cancelled.";
                }
                case MERGE -> {
                    if (target == null || !target.id().equals(targetId) || !authorized(actor, target)) {
                        throw new IllegalArgumentException("You must lead both towns, or have administrator permission, to merge them.");
                    }
                    var targetVillage = VillageAllegianceRegistrySavedData.get(level).canonicalRecord(target.id()).orElse(null);
                    if (targetVillage == null || targetVillage.archived() || !targetVillage.id().equals(target.id())) {
                        throw new IllegalArgumentException("The target allegiance identity must be active and canonical.");
                    }
                    replacement = SettlementLifecyclePlan.merge(level, town, target);
                    effect = "Merge into " + target.id().value() + " owned by " + target.leader()
                            + ". Preserve all residents, plots, treasury, pending payments, projects and background work. "
                            + "Keep the target's delegates and revoke source delegates and both leadership offers. Source identity becomes an alias. "
                            + "Target plots " + target.territory().plots().size() + ", residents " + target.residents().size()
                            + ", treasury " + target.economy().balance() + ", territory revision " + target.territory().revision() + ".";
                }
            }
        } catch (IllegalArgumentException | ArithmeticException exception) {
            blockers.add(exception.getMessage());
        }
        var village = VillageAllegianceRegistrySavedData.get(level).canonicalRecord(town.id()).orElse(null);
        if (village == null || village.archived() || !village.id().equals(town.id())) blockers.add("The allegiance identity must be active and canonical.");
        String summary = "Town " + town.id().value() + ", owner " + town.leader() + ", residents " + town.residents().size()
                + ", projects " + town.projects().size() + ", depots " + town.depots().size() + ", treasury " + town.economy().balance()
                + ", territory revision " + town.territory().revision() + ". " + effect;
        return new Plan(level, town, operation == Operation.MERGE ? target : null, replacement, summary, blockers);
    }

    private static boolean authorized(CommandSourceStack actor, SettlementRecord town) {
        if (actor.hasPermission(2)) return true;
        return actor.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && player.isAlive() && !player.isSpectator() && town.leader().equals(player.getUUID());
    }

    private static void dissolutionBlockers(ServerLevel level, SettlementRecord town, List<String> blockers) {
        if (!town.economy().balance().equals(CurrencyAmount.ZERO)) blockers.add("Withdraw the treasury before dissolution.");
        if (town.depots().values().stream().anyMatch(depot -> !depot.isEmpty())) blockers.add("Empty every depot before dissolution. Its inventory belongs to the town.");
        if (town.projects().values().stream().anyMatch(SettlementProject::active)) blockers.add("Finish or cancel active construction in management first, then withdraw any refunded fees.");
        if (town.projects().values().stream().anyMatch(project -> project.site() != null && project.site().pending())
                || town.fields().values().stream().anyMatch(SettlementFieldPlot::pending)
                || town.furnaces().values().stream().anyMatch(SettlementFurnaceState::pending)
                || town.brewingStands().values().stream().anyMatch(SettlementBrewingStandState::pending)
                || town.forests().values().stream().anyMatch(SettlementForest::pending)
                || town.mines().values().stream().anyMatch(SettlementMine::pending)) {
            blockers.add("Load the town's work sites and reconcile pending terrain, construction or machine changes before dissolution.");
        }
        if (!town.arrivals().isEmpty() || !town.departures().isEmpty() || !town.animalBirths().isEmpty()
                || !town.itemDrops().isEmpty() || !town.entityDeaths().isEmpty() || !town.homeReleases().isEmpty()) {
            blockers.add("Load pending arrivals, departures, births, drops, deaths and home-release locations to complete their handoffs.");
        }
        var residents = new HashSet<>(town.residents().keySet());
        residents.addAll(town.workers().keySet());
        residents.addAll(town.duties().keySet());
        VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), level.getGameTime()).forEach(resident -> residents.add(resident.id()));
        town.projects().values().stream().filter(project -> project.paymentEarned() && !project.paymentDelivered() && project.fee() > 0).forEach(project -> residents.add(project.builder()));
        if (residents.stream().anyMatch(id -> !(existing(level, id) instanceof Villager))) blockers.add("Load every resident and builder owed payment before dissolution. Their saved inventories and wallets must be returned first.");
        if (town.livestock().values().stream().anyMatch(state -> !(existing(level, state.id()) instanceof Animal))
                || town.huntingTargets().values().stream().anyMatch(state -> !(existing(level, state.id()) instanceof LivingEntity))
                || town.groundItems().values().stream().anyMatch(state -> state.removed() || !(existing(level, state.drop().id()) instanceof ItemEntity))
                || town.groundExperience().values().stream().anyMatch(state -> state.removed() || state.pendingSpawn() || !(existing(level, state.id()) instanceof ExperienceOrb))
                || town.projectiles().values().stream().anyMatch(state -> state.removed() || state.pendingSpawn() || !(existing(level, state.id()) instanceof Projectile))) {
            blockers.add("Load tracked animals, items, experience and projectiles so their world receipts can finish reconciling.");
        }
    }

    private static void settleResidents(ServerLevel level, SettlementRecord town) {
        var ids = new HashSet<>(town.residents().keySet());
        ids.addAll(town.workers().keySet());
        ids.addAll(town.duties().keySet());
        town.projects().values().stream().filter(project -> project.paymentEarned() && !project.paymentDelivered() && project.fee() > 0).forEach(project -> ids.add(project.builder()));
        for (UUID id : ids) {
            var villager = (Villager) existing(level, id);
            SettlementCareService.onActive(villager);
            SettlementBackgroundService.onActive(villager);
            SettlementConstructionService.synchronizePayments(town, villager);
            town.removeDuty(id);
            SettlementWorkforceService.reconcile((ServerLevel) villager.level(), villager);
        }
        town.livestock().values().forEach(state -> state.restore((Animal) existing(level, state.id())));
        town.huntingTargets().values().forEach(state -> state.restore((LivingEntity) existing(level, state.id()), level.getGameTime()));
        List.copyOf(town.groundItems().values()).forEach(state -> SettlementGroundItemService.onActive((ItemEntity) existing(level, state.drop().id())));
        List.copyOf(town.groundExperience().values()).forEach(state -> SettlementGroundExperienceService.onActive((ExperienceOrb) existing(level, state.id())));
        List.copyOf(town.projectiles().values()).forEach(state -> SettlementProjectileService.onActive((Projectile) existing(level, state.id())));
        level.getServer().saveEverything(false, true, true);
        net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
    }

    private static void rebindWorkers(ServerLevel level, SettlementRecord town) {
        for (UUID id : List.copyOf(town.duties().keySet())) {
            if (!(existing(level, id) instanceof Villager villager)) continue;
            var area = town.workplace(town.duties().get(id));
            if (area == null || SettlementWorkforceService.hasPersonalCommitment(villager)) continue;
            VillagerAssignmentStore.assignCivic(villager, town.id().value(), area.id(), area.type().role(), level.getGameTime());
            SettlementBackgroundService.onActive(villager);
            SettlementWorkforceService.reconcile((ServerLevel) villager.level(), villager);
        }
    }

    private static Entity existing(ServerLevel level, UUID id) {
        return SettlementAnimalBirthService.existing(level, id);
    }

    private static String identity(CommandSourceStack actor) {
        return actor.getEntity() == null ? actor.getTextName() : actor.getEntity().getUUID().toString();
    }

    private record Plan(ServerLevel level, SettlementRecord town, SettlementRecord target, CompoundTag replacement,
                        String summary, List<String> blockers) {
    }

    private record Confirmation(String actor, VillageAllegianceId town, Operation operation, VillageAllegianceId target,
                                BlockPos position, String summary, long expiresAt) {
    }
}
