package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.SettlementDepotBlockEntity;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.villager.VillagerGender;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

public final class SettlementManyVillageProfileScenario {
    public static final int TOWNS = 100;
    public static final int RESIDENTS_PER_TOWN = 10;
    public static final int LOADED_TOWNS = 5;
    private static final int IRON_PER_TOWN = 2_048;
    private final ServerLevel level;
    private final List<Village> villages = new ArrayList<>();
    private boolean observationsComplete;

    private SettlementManyVillageProfileScenario(ServerLevel level) {
        this.level = level;
    }

    public static SettlementManyVillageProfileScenario prepare(ServerPlayer player) {
        var level = player.serverLevel();
        VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.set(0);
        player.setGameMode(GameType.CREATIVE);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.setDayTime(6_000L);
        level.setWeatherParameters(100_000, 0, false, false);
        var scenario = new SettlementManyVillageProfileScenario(level);
        for (int index = 0; index < TOWNS; index++) {
            var origin = index < LOADED_TOWNS
                    ? new BlockPos(1_024 + index % 3 * 32, -60, 1_024 + index / 3 * 32)
                    : new BlockPos(16_384 + index % 10 * 64, -60, 16_384 + index / 10 * 64);
            level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
            scenario.villages.add(scenario.prepareVillage(player, origin, index));
        }
        player.connection.teleport(1_064.5D, -59.0D, 1_048.5D, 0.0F, 0.0F);
        return scenario;
    }

    private Village prepareVillage(ServerPlayer player, BlockPos origin, int index) {
        for (var position : BlockPos.betweenClosed(origin.below(), origin.offset(15, 3, 15))) {
            int x = position.getX() - origin.getX();
            int z = position.getZ() - origin.getZ();
            var block = position.getY() < origin.getY() ? Blocks.GRASS_BLOCK
                    : x == 0 || x == 15 || z == 0 || z == 15 ? Blocks.GLASS : Blocks.AIR;
            this.level.setBlock(position, block.defaultBlockState(), 2);
        }
        var center = origin.offset(8, 0, 8);
        var registry = VillageAllegianceRegistrySavedData.get(this.level);
        var id = registry.create(this.level.getGameTime(), this.level.dimension().location(), center, "Profile village " + index);
        var town = SettlementSavedData.get(this.level).establish(registry, id, player.getUUID(), center, this.level.getGameTime()).orElseThrow();
        require(!town.manualManagement(), "the benchmark uses autonomous management by default");
        var beds = new ArrayList<BlockPos>();
        for (int resident = 0; resident < RESIDENTS_PER_TOWN; resident++) {
            var head = origin.offset(2 + resident % 5 * 2, 0, 2 + resident / 5 * 3);
            var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            this.level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
            this.level.setBlock(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
            this.level.getPoiManager().add(head, this.level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME));
            beds.add(head);
        }
        area(player, town, SettlementAreaType.HOME, origin.offset(1, -1, 1), origin.offset(13, 3, 8));
        var workplace = area(player, town, SettlementAreaType.DEPOT, origin.offset(1, -1, 9), origin.offset(14, 3, 14));
        var source = depot(player, town, origin.offset(3, 0, 12), SettlementDepotPurpose.OUTPUT);
        var destination = depot(player, town, origin.offset(9, 0, 12), SettlementDepotPurpose.MATERIALS);
        var food = depot(player, town, origin.offset(13, 0, 12), SettlementDepotPurpose.FOOD);
        for (int slot = 0; slot < IRON_PER_TOWN / 64; slot++) source.setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
        for (int slot = 0; slot < 4; slot++) food.setItem(slot, new ItemStack(Items.BREAD, 64));
        require(town.setDepotStockTarget(player.getUUID(), source.id(), new ItemStack(Items.IRON_INGOT), 0), "source reserve");
        require(town.setDepotStockTarget(player.getUUID(), destination.id(), new ItemStack(Items.IRON_INGOT), IRON_PER_TOWN), "destination demand");
        var residents = new ArrayList<UUID>();
        for (int resident = 0; resident < RESIDENTS_PER_TOWN; resident++) {
            var villager = EntityType.VILLAGER.create(this.level);
            villager.setUUID(new UUID(0xB014L + index, 2L * (resident + 1)));
            villager.setPos(origin.offset(2 + resident % 5 * 2, 0, 9 + resident / 5).getBottomCenter());
            villager.setOnGround(true);
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
            villager.setVillagerXp(1);
            VillagerPresetNameRegistry.setStoredGender(villager, VillagerGender.MALE);
            VillageAllegianceApi.assignKnown(this.level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
            require(this.level.addFreshEntity(villager), "resident spawns");
            if (resident < 2) {
                for (var skill : HiredVillagerRoles.roleSkills(HiredVillagerRole.COURIER)) VillagerProfileManager.setSkill(this.level, villager, skill, 60);
                require(SettlementWorkforceService.assign(this.level, player, id, villager, workplace.id()), "courier receives civic work");
            }
            require(SettlementHousingService.assign(player, town, villager.getUUID(), beds.get(resident)), "resident receives housing");
            town.recordResident(SettlementResidentState.capture(villager, town, this.level.getGameTime()));
            residents.add(villager.getUUID());
        }
        return new Village(town, origin, List.copyOf(residents), destination);
    }

    public boolean observeAndRelease() {
        if (this.observationsComplete) return true;
        boolean complete = true;
        for (var village : this.villages) {
            for (var depot : village.town.depots().values()) {
                SettlementDepotService.observeRules(this.level, depot);
                complete &= depot.rulesObserved();
            }
        }
        if (!complete) return false;
        for (var village : this.villages) {
            village.town.areas().values().forEach(area -> SettlementBuildingService.refresh(this.level, village.town, area));
            this.level.setChunkForced(village.origin.getX() >> 4, village.origin.getZ() >> 4, false);
        }
        this.observationsComplete = true;
        return true;
    }

    public boolean ready() {
        return this.observationsComplete && this.level.getForcedChunks().isEmpty()
                && tickingResidents() == LOADED_TOWNS * RESIDENTS_PER_TOWN && unloadedVillages() == TOWNS - LOADED_TOWNS;
    }

    public int tickingResidents() {
        int count = 0;
        for (var village : this.villages) for (var id : village.residents) {
            if (this.level.getEntity(id) instanceof Villager villager && villager.isAlive() && !villager.isNoAi()
                    && this.level.isPositionEntityTicking(villager.blockPosition())) count++;
        }
        return count;
    }

    public int unloadedVillages() {
        int count = 0;
        for (var village : this.villages.subList(LOADED_TOWNS, TOWNS)) {
            if (this.level.getChunkSource().getChunkNow(village.origin.getX() >> 4, village.origin.getZ() >> 4) == null
                    && village.residents.stream().allMatch(id -> this.level.getEntity(id) == null)) count++;
        }
        return count;
    }

    public Snapshot snapshot() {
        int iron = 0;
        int roster = 0;
        var deliveries = new ArrayList<Integer>();
        var careTimes = new ArrayList<Long>();
        for (int index = 0; index < this.villages.size(); index++) {
            var village = this.villages.get(index);
            var town = village.town;
            roster += VillageAllegianceRegistrySavedData.get(this.level).residentRoster(town.id(), this.level.getGameTime()).size();
            iron += town.depots().values().stream().mapToInt(depot -> depot.count(stack -> stack.is(Items.IRON_INGOT))).sum();
            for (var id : village.residents) {
                if (this.level.getEntity(id) instanceof Villager villager) {
                    var inventory = HiredJobInventory.getJobInventory(villager);
                    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                        var stack = inventory.getItem(slot);
                        if (stack.is(Items.IRON_INGOT)) iron += stack.getCount();
                    }
                } else if (town.workers().containsKey(id)) {
                    for (var stack : town.workers().get(id).items(this.level.registryAccess())) if (stack.is(Items.IRON_INGOT)) iron += stack.getCount();
                }
                if (index >= LOADED_TOWNS) careTimes.add(town.residents().get(id).checkedAt());
            }
            if (index >= LOADED_TOWNS) deliveries.add(village.destination.count(stack -> stack.is(Items.IRON_INGOT)));
        }
        require(iron == TOWNS * IRON_PER_TOWN, "all depot and carried iron is conserved: " + iron);
        require(roster == TOWNS * RESIDENTS_PER_TOWN, "the census remains 1,000: " + roster);
        return new Snapshot(tickingResidents(), unloadedVillages(), roster, iron, SettlementSimulationService.completedCycles(this.level),
                List.copyOf(deliveries), List.copyOf(careTimes));
    }

    private SettlementArea area(ServerPlayer player, SettlementRecord town, SettlementAreaType type, BlockPos min, BlockPos max) {
        var area = new SettlementArea(UUID.randomUUID(), type, type.name(), min, max, 5, true);
        require(SettlementBuildingService.register(player, town.id(), area), "observed work area registers");
        return area;
    }

    private SettlementDepot depot(ServerPlayer player, SettlementRecord town, BlockPos position, SettlementDepotPurpose purpose) {
        this.level.setBlockAndUpdate(position, VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
        player.setPos(position.north().getBottomCenter());
        require(SettlementDepotService.register(player, position), "physical depot registers");
        var depot = ((SettlementDepotBlockEntity) this.level.getBlockEntity(position)).inventory();
        require(town.configureDepot(player.getUUID(), depot.id(), purpose, 5), "depot purpose");
        return depot;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Village(SettlementRecord town, BlockPos origin, List<UUID> residents, SettlementDepot destination) {
    }

    public record Snapshot(int ticking, int unloadedVillages, int census, int iron, long completedCycles,
                           List<Integer> deliveries, List<Long> careTimes) {
    }
}
