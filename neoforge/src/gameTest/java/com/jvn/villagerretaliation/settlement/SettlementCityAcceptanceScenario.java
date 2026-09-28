package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.SettlementDepotBlockEntity;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.villager.VillagerGender;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;

public final class SettlementCityAcceptanceScenario {
    private static final int INITIAL_BREAD = 128;
    private final ServerLevel level;
    private final SettlementRecord town;
    private final List<UUID> residents = new ArrayList<>();
    private final List<BlockPos> beds = new ArrayList<>();
    private final List<SettlementArea> farms = new ArrayList<>();
    private final Map<String, Snapshot> checkpoints = new LinkedHashMap<>();
    private SettlementArea kitchen;
    private SettlementArea workshop;
    private SettlementArea guard;
    private SettlementArea trading;
    private SettlementArea construction;
    private SettlementArea distribution;
    private SettlementDepot destination;
    private BuilderStructureScanner.StructurePlan plan;
    private BlockPos origin;
    private long phaseStarted;
    private long foundedAt;
    private long advancedAt;
    private final java.util.Set<UUID> preparedResidents = new java.util.HashSet<>();
    private int nativeProductionBread;
    private int transitions;
    private Phase phase = Phase.GROWING;

    private SettlementCityAcceptanceScenario(ServerLevel level, SettlementRecord town) {
        this.level = level;
        this.town = town;
        this.foundedAt = level.getGameTime();
        this.phaseStarted = this.foundedAt;
        this.advancedAt = this.foundedAt;
    }

    public static SettlementCityAcceptanceScenario prepare(ServerPlayer player) {
        var level = player.serverLevel();
        player.setGameMode(GameType.SURVIVAL);
        com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ENABLE_FAMILY_BREEDING_RULES.set(true);
        com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ENABLE_OPPOSITE_GENDER_BREEDING_RULES.set(true);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(3, level.getServer());
        level.setDayTime(6_000L);
        level.setWeatherParameters(1_000_000, 0, false, false);
        var center = new BlockPos(1_024, -60, 1_024);
        for (var pos : BlockPos.betweenClosed(center.offset(-45, -1, -45), center.offset(45, 12, 45))) {
            var state = pos.getY() == center.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
            if (!level.getBlockState(pos).equals(state)) level.setBlock(pos, state, 2);
        }
        level.setBlockAndUpdate(center, VillagerRetaliationBlocks.SETTLEMENT_MANAGEMENT.get().defaultBlockState());
        player.connection.teleport(center.getX() + 0.5D, center.getY(), center.getZ() + 3.5D, 0, 0);
        var result = SettlementFoundingService.found(player, center);
        require(result.status() == SettlementFoundingService.Status.FOUNDED, "an empty town is founded through its management block");
        var fixture = new SettlementCityAcceptanceScenario(level, result.town().orElseThrow());
        require(fixture.town.setManualManagement(player.getUUID(), true), "the fixture uses explicit resident approval and staffing");
        require(fixture.town.stage() == SettlementStage.CAMP && fixture.population() == 0, "founding begins at Camp without residents");
        for (int index = 0; index < 75; index++) {
            var head = center.offset(-40 + index % 10 * 3, 0, -40 + index / 10 * 4);
            var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
            level.setBlock(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
            level.getPoiManager().add(head, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME));
            fixture.beds.add(head);
        }
        fixture.area(player, SettlementAreaType.HOME, "Homes", center.offset(-42, -1, -42), center.offset(-10, 3, -8));
        for (int index = 0; index < 4; index++) {
            var min = center.offset(8 + index % 2 * 18, 0, -40 + index / 2 * 20);
            int size = index == 0 ? 9 : 15;
            for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
                var crop = min.offset(x, 0, z);
                boolean water = x % 8 == 3 && z % 8 == 3;
                level.setBlock(crop.below(), water ? Blocks.WATER.defaultBlockState()
                        : Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 2);
                if (!water) level.setBlock(crop, ((CropBlock) (index == 0 ? Blocks.WHEAT : Blocks.CARROTS)).getStateForAge(7), 2);
            }
            fixture.farms.add(fixture.area(player, SettlementAreaType.FARM, (index == 0 ? "Wheat " : "Carrots ") + index, min.below(), min.offset(size - 1, 3, size - 1)));
        }
        fixture.distribution = fixture.area(player, SettlementAreaType.DEPOT, "Supplies", center.offset(-4, -1, -16), center.offset(4, 3, 20));
        var general = fixture.depot(player, center.offset(0, 0, -12), SettlementDepotPurpose.GENERAL, 5);
        var food = fixture.depot(player, center.offset(0, 0, -6), SettlementDepotPurpose.FOOD, 9);
        var materials = fixture.depot(player, center.offset(0, 0, 6), SettlementDepotPurpose.MATERIALS, 7);
        var source = fixture.depot(player, center.offset(0, 0, 12), SettlementDepotPurpose.OUTPUT, 3);
        fixture.destination = fixture.depot(player, center.offset(0, 0, 18), SettlementDepotPurpose.MATERIALS, 8);
        put(food, Items.BREAD, INITIAL_BREAD);
        put(materials, Items.WHEAT_SEEDS, 512);
        put(materials, Items.OAK_LOG, 64);
        put(source, Items.IRON_INGOT, 256);
        require(fixture.town.setDepotStockTarget(player.getUUID(), fixture.destination.id(), new ItemStack(Items.IRON_INGOT), 256), "real hauling demand is configured");
        level.setBlockAndUpdate(center.offset(-8, 0, 8), Blocks.CRAFTING_TABLE.defaultBlockState());
        for (int x : new int[]{-10, -7}) for (int z : new int[]{6, 10}) level.setBlockAndUpdate(center.offset(x, 0, z), Blocks.CRAFTING_TABLE.defaultBlockState());
        fixture.kitchen = fixture.area(player, SettlementAreaType.KITCHEN, "Bakery", center.offset(-12, -1, 4), center.offset(-5, 3, 12));
        level.setBlockAndUpdate(center.offset(-20, 0, 8), Blocks.CRAFTING_TABLE.defaultBlockState());
        fixture.workshop = fixture.area(player, SettlementAreaType.WORKSHOP, "Workshop", center.offset(-24, -1, 4), center.offset(-16, 3, 12));
        fixture.guard = fixture.area(player, SettlementAreaType.GUARD_POST, "Guard post", center.offset(-40, -1, 4), center.offset(-28, 3, 16));
        level.setBlockAndUpdate(center.offset(-32, 0, 26), Blocks.LECTERN.defaultBlockState());
        fixture.trading = fixture.area(player, SettlementAreaType.TRADING_POST, "Market", center.offset(-40, -1, 22), center.offset(-26, 3, 32));
        fixture.origin = center.offset(16, 0, 12);
        fixture.construction = fixture.area(player, SettlementAreaType.CONSTRUCTION, "House", fixture.origin.offset(-4, -1, -4), fixture.origin.offset(16, 12, 16));
        var entry = BuilderStructureCatalog.byId(level.getServer(), ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1")).orElseThrow();
        fixture.plan = BuilderStructureScanner.scan(level, entry, Rotation.NONE).orElseThrow();
        fixture.town.economy().credit(com.jvn.villagerretaliation.sell.CurrencyAmount.of(fixture.plan.price(), 1));
        for (var block : fixture.plan.blocks()) if (block.requiresMaterial()) require(materials.addItem(block.requiredItem().copy()).isEmpty(), "the finite depot holds one house plan");
        require(general.isEmpty(), "production output begins empty");
        level.setBlockAndUpdate(new BlockPos(0, -61, 0), Blocks.STONE.defaultBlockState());
        player.connection.teleport(center.getX() + 0.5D, center.getY(), center.getZ() + 3.5D, 0, 0);
        fixture.checkpoints.put("founded", fixture.snapshot());
        return fixture;
    }

    public void tick(ServerPlayer player) {
        long now = this.level.getGameTime();
        require(now - this.foundedAt < 600_000L, "founding-to-City completes within 25 actual game days: " + snapshot());
        if (now % 20 != 0) return;
        if (this.phase == Phase.GROWING) {
            int target = this.town.stage().next().orElseThrow().population();
            if (population() < target && SettlementPopulationService.canGrow(this.level, this.town)) {
                var applicant = this.town.applicants().values().stream().filter(value -> value.approvedBy().isEmpty()).findFirst()
                        .or(() -> SettlementPopulationService.propose(this.level, this.town)).orElseThrow();
                require(SettlementPopulationService.approve(player, this.town.id(), applicant.id()), "an invited applicant passes the normal approval checks");
                this.residents.add(applicant.id());
            }
            prepareResidents(player);
            if (population() == target && this.preparedResidents.size() == target) {
                this.phase = Phase.ACTIVE;
                this.phaseStarted = now;
                this.checkpoints.put(this.town.stage() + "-arrivals", snapshot());
            }
        } else if (this.phase == Phase.ACTIVE) {
            prepareResidents(player);
            if (now - this.phaseStarted < 2_000L || (this.nativeProductionBread <= INITIAL_BREAD && count(Items.BREAD) <= INITIAL_BREAD)
                    || count(Items.WHEAT) == 0) return;
            this.nativeProductionBread = Math.max(this.nativeProductionBread, count(Items.BREAD));
            this.checkpoints.put(this.town.stage() + "-active", snapshot());
            require(this.destination.count(stack -> stack.is(Items.IRON_INGOT)) > 0, "native hauling delivers finite cargo");
            player.connection.teleport(0.5D, -60.0D, 0.5D, 0, 0);
            this.phase = Phase.UNLOADING;
        } else if (this.phase == Phase.UNLOADING) {
            if (!unloaded()) return;
            this.checkpoints.put(this.town.stage() + "-unloaded", snapshot());
            this.phase = Phase.SUPPORT;
            this.phaseStarted = now;
        } else if (this.phase == Phase.SUPPORT) {
            require(unloaded(), "the advancement interval does not force the town to load");
            var status = SettlementProgressionService.assess(this.level, this.town);
            if (!status.ready() || now - this.phaseStarted < 2_000L) return;
            require(status.requiredTicks() == 48_000L && now - this.advancedAt >= status.requiredTicks(), "advancement earns both default days through native server ticks");
            var before = this.town.stage();
            require(SettlementProgressionService.advance(player, this.town.id()), "the leader explicitly approves advancement from " + before);
            require(this.town.stage() == before.next().orElseThrow(), "one explicit action advances exactly one stage");
            this.advancedAt = now;
            this.transitions++;
            this.checkpoints.put(this.town.stage() + "-earned", snapshot());
            player.connection.teleport(this.town.center().getX() + 0.5D, this.town.center().getY(), this.town.center().getZ() + 3.5D, 0, 0);
            this.phase = this.town.stage() == SettlementStage.CITY ? Phase.RETURNING : Phase.GROWING;
            this.phaseStarted = now;
        } else if (this.phase == Phase.RETURNING) {
            if (now - this.phaseStarted < 400 || this.residents.stream().anyMatch(id -> !(this.level.getEntity(id) instanceof Villager))) return;
            require(this.transitions == 4 && population() == 75 && this.town.homes().size() == 75, "City returns with all 75 approved residents and homes");
            require(this.nativeProductionBread > INITIAL_BREAD && count(Items.IRON_INGOT) == 256, "production and hauling preserve the real resource balances");
            require(this.town.projects().values().stream().anyMatch(project -> project.placed() > 0), "the civic builder made real progress");
            int verified = 0;
            for (var project : this.town.projects().values()) for (int index = 0; index < project.placed(); index++) {
                var block = this.plan.blocks().get(index);
                if (!block.requiresMaterial() || block.requiresToolAction()) continue;
                require(this.level.getBlockState(this.plan.worldPos(project.origin(), block)).is(block.state().getBlock()), "return reconciles the constructed house");
                verified++;
            }
            require(verified > 0, "returned blocks match the construction ledger");
            require(this.town.history().stream().filter(event -> event.type() == SettlementEvent.Type.ESTABLISHED).count() == 1,
                    "the original founding remains in history");
            require(this.town.history().stream().filter(event -> event.type() == SettlementEvent.Type.RESIDENT_JOINED).count() == 75,
                    "history retains every approved arrival without replaying materialization");
            require(this.town.history().stream().filter(event -> event.type() == SettlementEvent.Type.STAGE_ADVANCED)
                    .map(SettlementEvent::subject).toList().equals(List.of("HAMLET", "VILLAGE", "TOWN", "CITY")),
                    "history records all four earned stages in order");
            require(SettlementRecord.load(this.town.save(this.level.registryAccess()), this.level.registryAccess(), () -> {}).history().equals(this.town.history()),
                    "returned city history survives save loading unchanged");
            this.checkpoints.put("returned", snapshot());
            this.phase = Phase.VIEW;
            this.level.getServer().tickRateManager().setTickRate(20.0F);
            SettlementManagementService.open(player, this.town.id());
        }
    }

    private void prepareResidents(ServerPlayer player) {
        for (int index = 0; index < this.residents.size(); index++) {
            if (!(this.level.getEntity(this.residents.get(index)) instanceof Villager villager)) continue;
            if (this.preparedResidents.add(villager.getUUID())) {
                VillagerPresetNameRegistry.setStoredGender(villager, VillagerGender.MALE);
                require(SettlementHousingService.assign(player, this.town, villager.getUUID(), this.beds.get(index)), "an arriving resident receives an individual usable bed");
            }
            if (this.town.duties().containsKey(villager.getUUID())) continue;
            SettlementArea workplace = switch (index) {
                case 0 -> this.farms.get(0);
                case 1 -> this.distribution;
                case 2 -> this.kitchen;
                case 3 -> this.guard;
                case 4 -> this.workshop;
                case 7 -> this.town.stage().ordinal() >= SettlementStage.VILLAGE.ordinal() ? this.trading : null;
                case 8 -> this.town.stage().ordinal() >= SettlementStage.HAMLET.ordinal() ? this.construction : null;
                case 9 -> this.farms.get(1);
                case 18 -> this.farms.get(2);
                case 27 -> this.farms.get(3);
                default -> index < 18 ? this.kitchen : index % 9 == 0 ? this.farms.get(1 + index / 9 % 3) : null;
            };
            if (workplace == null) continue;
            for (var skill : HiredVillagerRoles.roleSkills(workplace.type().role())) VillagerProfileManager.setSkill(this.level, villager, skill, 60);
            require(SettlementWorkforceService.assign(this.level, player, this.town.id(), villager, workplace.id()), "an eligible approved resident takes " + workplace.type());
            var inventory = HiredJobInventory.getJobInventory(villager);
            if (workplace.type() == SettlementAreaType.FARM) inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
            if (workplace == this.guard) inventory.insertPlainSupply(new ItemStack(Items.ARROW, 64));
            if (workplace == this.kitchen || workplace == this.workshop) {
                var filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
                VillagerItemFilterData.setEntry(filter, 0, new ItemStack(workplace == this.kitchen ? Items.BREAD : Items.OAK_PLANKS));
                inventory.setItem(HiredJobInventory.FILTER_SLOT, filter);
            }
            if (workplace == this.construction) {
                inventory.insertTool(new ItemStack(Items.IRON_PICKAXE));
                inventory.insertTool(new ItemStack(Items.IRON_AXE));
                inventory.insertTool(new ItemStack(Items.IRON_SHOVEL));
                var blueprint = UUID.randomUUID();
                player.setItemInHand(InteractionHand.MAIN_HAND, ConstructionBlueprintItem.create(this.level, null, this.plan, this.origin, blueprint, 0, this.plan.price(), "", 0L));
                require(SettlementConstructionService.start(player, this.town, villager.getUUID(), blueprint), "Hamlet unlocks a real supplied blueprint project");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
    }

    public boolean readyForView() {
        return this.phase == Phase.VIEW;
    }

    public Map<String, Snapshot> checkpoints() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(this.checkpoints));
    }

    public Snapshot snapshot() {
        var status = SettlementProgressionService.assess(this.level, this.town);
        var blockers = new LinkedHashMap<String, Integer>();
        this.town.workers().values().forEach(worker -> {
            var state = worker.settings();
            blockers.merge(worker.role() + ":" + worker.status() + ":" + state.getString("WorkerTaskState")
                    + ":" + state.getString("WorkerFailureReason") + ":" + state.getString("Status"), 1, Integer::sum);
        });
        return new Snapshot(this.phase.name(), this.town.stage().name(), this.level.getGameTime() - this.foundedAt, population(),
                this.town.housingCapacity(), this.town.homes().size(), this.town.duties().size(),
                count(Items.WHEAT), SettlementStock.count(this.town, stack -> stack.is(Items.WHEAT)), count(Items.CARROT),
                count(Items.BREAD), count(Items.IRON_INGOT), count(Items.OAK_PLANKS),
                this.town.projects().values().stream().mapToInt(SettlementProject::placed).sum(),
                status.sustainedTicks(), status.foodDays(), this.town.history().size(), status.missing(), Map.copyOf(blockers));
    }

    private int population() {
        return VillageAllegianceRegistrySavedData.get(this.level).residentRoster(this.town.id(), this.level.getGameTime()).size();
    }

    private int count(Item item) {
        int count = this.town.depots().values().stream().mapToInt(depot -> depot.count(stack -> stack.is(item))).sum();
        for (var id : this.residents) {
            if (this.level.getEntity(id) instanceof Villager villager) {
                var inventory = HiredJobInventory.getJobInventory(villager);
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).is(item)) count += inventory.getItem(slot).getCount();
            } else if (this.town.workers().containsKey(id)) {
                for (var stack : this.town.workers().get(id).items(this.level.registryAccess())) if (stack.is(item)) count += stack.getCount();
            }
        }
        return count;
    }

    private boolean unloaded() {
        if (this.residents.stream().anyMatch(id -> this.level.getEntity(id) != null)) return false;
        for (int x = 61; x <= 66; x++) for (int z = 61; z <= 66; z++) if (this.level.getChunkSource().getChunkNow(x, z) != null) return false;
        return true;
    }

    private SettlementArea area(ServerPlayer player, SettlementAreaType type, String name, BlockPos min, BlockPos max) {
        SettlementTestPlots.claim(this.level, this.town, min, max);
        var area = new SettlementArea(UUID.randomUUID(), type, name, min, max, 5, true);
        require(SettlementBuildingService.register(player, this.town.id(), area), "the designated area registers: " + name);
        return area;
    }

    private SettlementDepot depot(ServerPlayer player, BlockPos pos, SettlementDepotPurpose purpose, int priority) {
        this.level.setBlockAndUpdate(pos, VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
        player.setPos(pos.north().getBottomCenter());
        require(SettlementDepotService.register(player, pos), "the dedicated depot registers");
        var depot = ((SettlementDepotBlockEntity) this.level.getBlockEntity(pos)).inventory();
        require(this.town.configureDepot(player.getUUID(), depot.id(), purpose, priority), "the depot purpose is configured");
        return depot;
    }

    private static void put(SettlementDepot depot, Item item, int count) {
        for (int remaining = count; remaining > 0; remaining -= Math.min(64, remaining)) {
            require(depot.addItem(new ItemStack(item, Math.min(64, remaining))).isEmpty(), "starting supplies fit finite storage");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Phase { GROWING, ACTIVE, UNLOADING, SUPPORT, RETURNING, VIEW }

    public record Snapshot(String phase, String stage, long ticks, int population, int capacity, int homes, int workers,
                           int wheat, int depotWheat, int carrots, int bread, int iron, int planks, int placed, long sustainedTicks, double foodDays,
                           int historyEvents, List<SettlementProgressionService.Requirement> missing, Map<String, Integer> blockers) {
    }
}
