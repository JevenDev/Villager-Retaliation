package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

public final class SettlementPopulationService {
    private static final List<VillagerProfession> PROFESSIONS = List.of(VillagerProfession.FARMER,
            VillagerProfession.FISHERMAN, VillagerProfession.BUTCHER, VillagerProfession.FLETCHER,
            VillagerProfession.TOOLSMITH, VillagerProfession.ARMORER, VillagerProfession.CLERIC,
            VillagerProfession.NITWIT, VillagerProfession.MASON);

    private SettlementPopulationService() {
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        var jobs = new java.util.ArrayList<Runnable>();
        schedule(level, town, now, jobs);
        var work = new SettlementWork(jobs);
        while (!SettlementWork.complete(work)) work.run();
    }

    static void schedule(ServerLevel level, SettlementRecord town, long now, List<Runnable> jobs) {
        if (level.hasChunkAt(town.center()) && town.applicants().values().stream().anyMatch(applicant -> applicant.approvedBy().isPresent())) {
            for (var area : town.areas().values()) jobs.add(SettlementBuildingService.work(level, town, area));
        }
        jobs.add(SettlementWork.each(List.copyOf(town.arrivals().keySet()), id -> SettlementArrivalService.materialize(level, town, id)));
        jobs.add(SettlementWork.each(List.copyOf(town.applicants().keySet()), id -> {
            var applicant = town.applicants().get(id);
            if (applicant == null) return;
            if (!town.manualManagement() && applicant.approvedBy().isEmpty() && canGrow(level, town)) {
                applicant = applicant.approve(town.leader());
                town.putApplicant(applicant);
            }
            if (applicant.approvedBy().isPresent()) arrive(level, town, applicant);
            else if (now - applicant.createdAt() >= 7L * 24_000L) town.removeApplicant(applicant.id());
        }));
        jobs.add(SettlementWork.lazy(() -> SettlementWork.each(
                VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), now),
                member -> provideFamilyFood(level, town, member.id()))));
        jobs.add(SettlementFamilyService.cycle(level, town, now));
        jobs.add(() -> {
            if (now >= town.nextApplicantAt() && canGrow(level, town)
                    && town.applicants().size() < SettlementRecord.MAX_APPLICANTS) {
                var applicant = propose(level, town).orElse(null);
                if (!town.manualManagement() && applicant != null) {
                    applicant = applicant.approve(town.leader());
                    town.putApplicant(applicant);
                    arrive(level, town, applicant);
                }
                town.scheduleApplicant(now + 24_000L);
            }
        });
    }

    public static boolean canGrow(ServerLevel level, SettlementRecord town) {
        int population = VillageAllegianceRegistrySavedData.get(level).residentCount(town.id(), level.getGameTime());
        return population < growthTarget(level, town)
                && town.housingCapacity() > population
                && SettlementFoodService.nutrition(town) >= (long) (population + 1) * SettlementFoodService.dailyNutrition()
                        * Math.clamp(VillagerRetaliationConfig.SETTLEMENT_FOOD_RESERVE_DAYS.get(), 1, 30);
    }

    static int growthTarget(ServerLevel level, SettlementRecord town) {
        int target = SettlementPopulationRequirements.growthTarget(town.stage());
        return town.autonomous() == null ? target : Math.min(target, AutonomousVillageRules.get(level.getServer()).population());
    }

    static Optional<SettlementApplicant> propose(ServerLevel level, SettlementRecord town) {
        int population = VillageAllegianceRegistrySavedData.get(level).residentCount(town.id(), level.getGameTime());
        int vacancies = Math.min(town.housingCapacity(),
                growthTarget(level, town)) - population;
        if (!canGrow(level, town) || town.applicants().size() >= vacancies
                || town.applicants().size() >= SettlementRecord.MAX_APPLICANTS) {
            return Optional.empty();
        }
        Villager candidate = EntityType.VILLAGER.create(level);
        candidate.setPos(town.center().getCenter());
        candidate.setVillagerData(candidate.getVillagerData().setProfession(
                PROFESSIONS.get(Math.floorMod(population + town.applicants().size(), PROFESSIONS.size()))));
        VillagerPresetNameRegistry.ensurePresetNameAssigned(candidate);
        CompoundTag entity = new CompoundTag();
        candidate.saveWithoutId(entity);
        SettlementApplicant applicant = new SettlementApplicant(candidate.getUUID(),
                VillagerPresetNameRegistry.resolveDisplayName(candidate).getString(), level.getGameTime(), entity, null);
        return town.putApplicant(applicant) ? Optional.of(applicant) : Optional.empty();
    }

    public static boolean approve(ServerPlayer player, VillageAllegianceId id, UUID applicantId) {
        SettlementRecord town = SettlementSavedData.get(player.serverLevel()).get(id).orElse(null);
        if (!authorized(player, town)) {
            return false;
        }
        town.areas().values().forEach(area -> SettlementBuildingService.refresh(player.serverLevel(), town, area));
        if (!canGrow(player.serverLevel(), town)) {
            return false;
        }
        SettlementApplicant applicant = town.applicants().get(applicantId);
        if (applicant == null) {
            return false;
        }
        var approved = applicant.approve(player.getUUID());
        town.putApplicant(approved);
        arrive(player.serverLevel(), town, approved);
        return true;
    }

    public static boolean decline(ServerPlayer player, VillageAllegianceId id, UUID applicantId) {
        SettlementRecord town = SettlementSavedData.get(player.serverLevel()).get(id).orElse(null);
        return authorized(player, town) && town.removeApplicant(applicantId);
    }

    private static boolean authorized(ServerPlayer player, SettlementRecord town) {
        return town != null && player.isAlive() && !player.isSpectator()
                && town.dimension().equals(player.level().dimension().location())
                && town.permits(player.getUUID(), SettlementPermission.STAFF);
    }

    static boolean arrive(ServerLevel level, SettlementRecord town, SettlementApplicant applicant) {
        applicant = town.applicants().get(applicant.id());
        if (applicant == null) {
            return false;
        }
        if (VillageAllegianceRegistrySavedData.get(level).record(town.id()).orElseThrow()
                .residents().containsKey(applicant.id())) {
            town.removeApplicant(applicant.id());
            return true;
        }
        if (applicant.approvedBy().isEmpty()
                || !town.permits(applicant.approvedBy().get(), SettlementPermission.STAFF)) {
            if (applicant.approvedBy().isPresent()) {
                town.putApplicant(applicant.approve(null));
            }
            return false;
        }
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            var existing = dimension.getEntity(applicant.id());
            if (existing != null) {
                if (existing instanceof Villager
                        && VillageAllegianceApi.canonicalPrimary(level, existing).filter(town.id()::equals).isPresent()) {
                    town.removeApplicant(applicant.id());
                    return true;
                }
                return false;
            }
        }
        if (!canGrow(level, town)) {
            return false;
        }
        Villager resident = EntityType.VILLAGER.create(level);
        resident.load(applicant.entity());
        resident.setPos(town.center().getCenter());
        SettlementArrivalService.admit(level, town, resident);
        town.removeApplicant(applicant.id());
        SettlementArrivalService.materialize(level, town, resident.getUUID());
        return true;
    }

    static void provideFamilyFood(ServerLevel level, SettlementRecord town) {
        for (var member : VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), level.getGameTime())) {
            provideFamilyFood(level, town, member.id());
        }
    }

    private static void provideFamilyFood(ServerLevel level, SettlementRecord town, UUID id) {
        if (!VillageAllegianceRegistrySavedData.get(level).canonicalRecord(town.id())
                .map(record -> record.residents().containsKey(id)).orElse(false)) return;
        net.minecraft.world.entity.Entity existing = null;
        for (var dimension : level.getServer().getAllLevels()) {
            existing = dimension.getEntity(id);
            if (existing != null) break;
        }
        if (existing != null && (!(existing instanceof Villager) || !existing.isAlive() || existing.level() != level
                || VillageAllegianceApi.canonicalPrimary(level, existing).filter(town.id()::equals).isEmpty())) return;
        if (existing instanceof Villager resident && level.isPositionEntityTicking(resident.blockPosition())) {
            provideFamilyFood(level, town, resident);
            return;
        }
        var care = town.residents().get(id);
        var resident = familyContext(level, town, care);
        if (resident != null) provideFamilyFood(level, town, resident);
    }

    static Villager familyContext(ServerLevel level, SettlementRecord town, SettlementResidentState care) {
        if (care == null || !care.safeForFamily() || care.age() != 0 || town.departures().containsKey(care.id())) return null;
        var resident = care.context(level);
        if (resident == null) return null;
        var area = town.workplace(town.duties().get(care.id()));
        var worker = town.workers().get(care.id());
        if (area != null) {
            var settings = town.effectiveWorkerSettings(care.id());
            boolean enabled = settings == null || settings.enabled();
            if (settings == null && worker != null && worker.settings().contains("Enabled")) enabled = worker.settings().getBoolean("Enabled");
            if (worker == null || worker.unsafe() || area.enabled() && enabled
                    && SettlementWorkforceService.workingHours(level.getDayTime(), care.id(), area.type().role())) return null;
            com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(resident).putString("WorkerTaskState",
                    com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState.IDLE.id());
        }
        return resident;
    }

    static void provideFamilyFood(ServerLevel level, SettlementRecord town, Villager resident) {
        var care = town.residents().get(resident.getUUID());
        if (care != null) care.restore(resident);
        if (resident.getAge() != 0 || familyFood(resident) >= 12 || !canGrow(level, town)
                || SettlementWorkforceService.hasPersonalCommitment(resident)
                || !com.jvn.villagerretaliation.social.VillagerBreedingPolicy.evaluateProvision(level, resident).allowed()) {
            return;
        }
        var inventory = resident.getInventory();
        int points = familyFood(resident);
        boolean changed = false;
        for (SettlementDepot depot : SettlementStock.supplies(town)) {
            for (int slot = 0; slot < depot.getContainerSize() && points < 12; slot++) {
                ItemStack stock = depot.getItem(slot);
                int value = familyFoodPoints(stock);
                while (value > 0 && !stock.isEmpty() && points < 12 && inventory.canAddItem(stock.copyWithCount(1))) {
                    ItemStack remainder = inventory.addItem(depot.removeItem(slot, 1));
                    if (!remainder.isEmpty()) {
                        depot.deposit(remainder);
                        break;
                    }
                    points += value;
                    changed = true;
                    stock = depot.getItem(slot);
                }
            }
        }
        if (changed) {
            if (care == null) {
                town.recordResident(SettlementResidentState.capture(resident, town, level.getGameTime()));
            } else {
                care.rememberFamily(resident);
                town.changed();
            }
        }
    }

    static int familyFood(Villager resident) {
        var access = (com.jvn.villagerretaliation.mixin.VillagerFamilyAccessor) resident;
        return access.villagerretaliation$getFamilyFood() + access.villagerretaliation$countFamilyFood();
    }

    private static int familyFoodPoints(ItemStack stack) {
        return stack.is(Items.BREAD) ? 4
                : stack.is(Items.CARROT) || stack.is(Items.POTATO) || stack.is(Items.BEETROOT) ? 1 : 0;
    }
}
