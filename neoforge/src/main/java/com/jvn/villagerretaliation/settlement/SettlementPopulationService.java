package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class SettlementPopulationService {
    private static final List<VillagerProfession> PROFESSIONS = List.of(VillagerProfession.FARMER,
            VillagerProfession.FISHERMAN, VillagerProfession.BUTCHER, VillagerProfession.FLETCHER,
            VillagerProfession.TOOLSMITH, VillagerProfession.ARMORER, VillagerProfession.CLERIC,
            VillagerProfession.NITWIT, VillagerProfession.MASON);

    private SettlementPopulationService() {
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 200L != 0L) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (SettlementRecord town : SettlementSavedData.get(level).settlements()) {
                if (!town.dimension().equals(level.dimension().location())) {
                    continue;
                }
                if (level.hasChunkAt(town.center()) && town.applicants().values().stream().anyMatch(applicant -> applicant.approvedBy().isPresent())) {
                    town.areas().values().forEach(area -> SettlementBuildingService.refresh(level, town, area));
                }
                for (SettlementApplicant applicant : List.copyOf(town.applicants().values())) {
                    if (applicant.approvedBy().isPresent()) {
                        arrive(level, town, applicant);
                    } else if (now - applicant.createdAt() >= 7L * 24_000L) {
                        town.removeApplicant(applicant.id());
                    }
                }
                for (var member : VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), now)) {
                    if (level.getEntity(member.id()) instanceof Villager resident) {
                        provideFamilyFood(level, town, resident);
                    }
                }
                if (now >= town.nextApplicantAt() && canGrow(level, town)
                        && town.applicants().size() < SettlementRecord.MAX_APPLICANTS) {
                    propose(level, town);
                    town.scheduleApplicant(now + 24_000L);
                }
            }
        }
    }

    public static boolean canGrow(ServerLevel level, SettlementRecord town) {
        int population = VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), level.getGameTime()).size();
        return population < Math.clamp(VillagerRetaliationConfig.SETTLEMENT_POPULATION_TARGET.get(), 1, 1000)
                && town.housingCapacity() > population
                && SettlementFoodService.nutrition(town) >= (long) (population + 1) * SettlementFoodService.dailyNutrition()
                        * Math.clamp(VillagerRetaliationConfig.SETTLEMENT_FOOD_RESERVE_DAYS.get(), 1, 30);
    }

    static Optional<SettlementApplicant> propose(ServerLevel level, SettlementRecord town) {
        int population = VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), level.getGameTime()).size();
        int vacancies = Math.min(town.housingCapacity(),
                Math.clamp(VillagerRetaliationConfig.SETTLEMENT_POPULATION_TARGET.get(), 1, 1000)) - population;
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
        if (level.getEntity(applicant.id()) instanceof Villager existing) {
            if (VillageAllegianceApi.canonicalPrimary(level, existing).filter(town.id()::equals).isPresent()) {
                town.removeApplicant(applicant.id());
                return true;
            }
            return false;
        }
        if (!canGrow(level, town)) {
            return false;
        }
        Villager resident = EntityType.VILLAGER.create(level);
        resident.load(applicant.entity());
        Optional<BlockPos> arrival = arrivalPosition(level, town, resident);
        if (arrival.isEmpty()) {
            return false;
        }
        resident.setPos(Vec3.atBottomCenterOf(arrival.get()));
        VillageAllegianceApi.assignKnown(level, resident, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        if (!level.addFreshEntity(resident)) {
            VillageAllegianceRegistrySavedData.get(level).removeResident(town.id(), resident.getUUID());
            return false;
        }
        town.removeApplicant(applicant.id());
        return true;
    }

    static void provideFamilyFood(ServerLevel level, SettlementRecord town, Villager resident) {
        if (resident.getAge() != 0 || resident.canBreed() || !canGrow(level, town)
                || !com.jvn.villagerretaliation.social.VillagerBreedingPolicy.evaluateParent(level, resident).allowed()) {
            return;
        }
        var inventory = resident.getInventory();
        int points = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            points += familyFoodPoints(inventory.getItem(slot)) * inventory.getItem(slot).getCount();
        }
        for (SettlementDepot depot : SettlementStock.supplies(town)) {
            for (int slot = 0; slot < depot.getContainerSize() && points < 12; slot++) {
                ItemStack stock = depot.getItem(slot);
                int value = familyFoodPoints(stock);
                while (value > 0 && !stock.isEmpty() && points < 12 && inventory.canAddItem(stock.copyWithCount(1))) {
                    ItemStack remainder = inventory.addItem(depot.removeItem(slot, 1));
                    if (!remainder.isEmpty()) {
                        depot.deposit(remainder);
                        return;
                    }
                    points += value;
                    stock = depot.getItem(slot);
                }
            }
        }
    }

    private static int familyFoodPoints(ItemStack stack) {
        return stack.is(Items.BREAD) ? 4
                : stack.is(Items.CARROT) || stack.is(Items.POTATO) || stack.is(Items.BEETROOT) ? 1 : 0;
    }

    private static Optional<BlockPos> arrivalPosition(ServerLevel level, SettlementRecord town, Villager resident) {
        for (BlockPos offset : BlockPos.betweenClosed(-4, -2, -4, 4, 3, 4)) {
            BlockPos pos = town.center().offset(offset);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                    || !level.getFluidState(pos).isEmpty()) {
                continue;
            }
            var box = resident.getDimensions(resident.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(pos));
            if (level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                    && level.hasChunkAt(BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                    && level.getFluidState(pos.above()).isEmpty() && level.noCollision(resident, box)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }
}
