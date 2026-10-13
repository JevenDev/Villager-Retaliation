package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.SettlementDepotBlockEntity;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.villager.VillagerGender;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
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
import net.minecraft.world.phys.Vec3;

public final class SettlementTownProfileScenario {
    public static final int RESIDENTS = 100;
    public static final int IRON_SUPPLY = 8_192;
    private final ServerLevel level;
    private final SettlementRecord town;
    private final List<UUID> residents = new ArrayList<>();
    private final List<UUID> builders = new ArrayList<>();
    private final List<SettlementDepot> sources = new ArrayList<>();
    private final List<SettlementDepot> destinations = new ArrayList<>();
    private final List<SettlementArea> sites = new ArrayList<>();
    private final List<BlockPos> origins = new ArrayList<>();
    private final BuilderStructureScanner.StructurePlan plan;
    private final Vec3 body;

    private SettlementTownProfileScenario(ServerLevel level, SettlementRecord town,
                                          BuilderStructureScanner.StructurePlan plan, Vec3 body) {
        this.level = level;
        this.town = town;
        this.plan = plan;
        this.body = body;
    }

    public static SettlementTownProfileScenario prepare(ServerPlayer player) {
        return prepare(player, new BlockPos(1_024, -60, 1_024), true);
    }

    static SettlementTownProfileScenario prepare(ServerPlayer player, BlockPos center, boolean openView) {
        var level = player.serverLevel();
        player.setGameMode(GameType.SURVIVAL);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.setDayTime(6_000L);
        level.setWeatherParameters(100_000, 0, false, false);
        for (var position : BlockPos.betweenClosed(center.offset(-46, -1, -46), center.offset(62, 12, 46))) {
            var state = position.getY() == center.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
            if (!level.getBlockState(position).equals(state)) level.setBlock(position, state, 2);
        }
        for (int x = -46; x <= 62; x++) {
            level.setBlockAndUpdate(center.offset(x, 0, -46), Blocks.OAK_FENCE.defaultBlockState());
            level.setBlockAndUpdate(center.offset(x, 0, 46), Blocks.OAK_FENCE.defaultBlockState());
        }
        for (int z = -45; z <= 45; z++) {
            level.setBlockAndUpdate(center.offset(-46, 0, z), Blocks.OAK_FENCE.defaultBlockState());
            level.setBlockAndUpdate(center.offset(62, 0, z), Blocks.OAK_FENCE.defaultBlockState());
        }
        var body = openView ? new Vec3(0.5D, -60.0D, 0.5D) : center.above(6).getCenter();
        level.setBlockAndUpdate(BlockPos.containing(body).below(), Blocks.STONE.defaultBlockState());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Civic performance town");
        var town = SettlementSavedData.get(level).establish(registry, id, player.getUUID(), center, level.getGameTime()).orElseThrow();
        while (town.stage() != SettlementStage.CITY) town.advance();
        var entry = BuilderStructureCatalog.byId(level.getServer(),
                ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1")).orElseThrow();
        var plan = BuilderStructureScanner.scan(level, entry, Rotation.NONE).orElseThrow();
        town.economy().credit(com.jvn.villagerretaliation.sell.CurrencyAmount.of(plan.price() * 8L, 1));
        var fixture = new SettlementTownProfileScenario(level, town, plan, body);

        var beds = new ArrayList<BlockPos>();
        for (int index = 0; index < RESIDENTS; index++) {
            var head = center.offset(-42 + index % 10 * 3, 0, -42 + index / 10 * 4);
            var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
            level.setBlock(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
            level.getPoiManager().add(head, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                    .getHolderOrThrow(PoiTypes.HOME));
            beds.add(head);
        }
        fixture.area(player, SettlementAreaType.HOME, "Housing", center.offset(-44, -1, -44), center.offset(-12, 3, -3));
        var farms = new ArrayList<SettlementArea>();
        for (int index = 0; index < 4; index++) {
            var origin = center.offset(-44 + index % 2 * 18, 0, 8 + index / 2 * 18);
            for (var crop : BlockPos.betweenClosed(origin, origin.offset(14, 0, 14))) {
                boolean water = Math.floorMod(crop.getX() - origin.getX(), 8) == 4
                        && Math.floorMod(crop.getZ() - origin.getZ(), 8) == 4;
                level.setBlock(crop.below(), water ? Blocks.WATER.defaultBlockState()
                        : Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 2);
                if (!water) level.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7), 2);
            }
            farms.add(fixture.area(player, SettlementAreaType.FARM, "Farm " + index, origin.below(), origin.offset(14, 3, 14)));
        }
        var distribution = fixture.area(player, SettlementAreaType.DEPOT, "Distribution",
                center.offset(-9, -1, -44), center.offset(11, 3, 44));
        for (int index = 0; index < 8; index++) {
            var source = fixture.depot(player, center.offset(-6, 0, -38 + index * 10), SettlementDepotPurpose.OUTPUT, 5);
            var destination = fixture.depot(player, center.offset(6, 0, -38 + index * 10), SettlementDepotPurpose.MATERIALS, 8);
            for (int slot = 0; slot < 16; slot++) source.setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
            fixture.sources.add(source);
            fixture.destinations.add(destination);
        }
        var food = fixture.depot(player, center.offset(0, 0, -42), SettlementDepotPurpose.FOOD, 9);
        for (int slot = 0; slot < 8; slot++) food.setItem(slot, new ItemStack(Items.BREAD, 64));
        var seeds = fixture.depot(player, center.offset(0, 0, 42), SettlementDepotPurpose.MATERIALS, 7);
        for (int slot = 0; slot < 8; slot++) seeds.setItem(slot, new ItemStack(Items.WHEAT_SEEDS, 64));
        var posts = new ArrayList<SettlementArea>();
        for (int index = 0; index < 4; index++) {
            var min = center.offset(-44 + index % 2 * 18, 0, -42 + index / 2 * 20);
            posts.add(fixture.area(player, SettlementAreaType.GUARD_POST, "Guard post " + index, min, min.offset(14, 3, 16)));
        }
        for (int index = 0; index < 8; index++) {
            var origin = center.offset(index < 4 ? 18 : 42, 0, -40 + index % 4 * 22);
            fixture.origins.add(origin);
            fixture.sites.add(fixture.area(player, SettlementAreaType.CONSTRUCTION, "House " + index,
                    origin.offset(-4, -1, -4), origin.offset(16, 12, 16)));
        }
        for (int index = 0; index < 4; index++) {
            var supplies = fixture.depot(player, center.offset(10, 0, -36 + index * 22), SettlementDepotPurpose.MATERIALS, 9);
            for (var block : plan.blocks()) {
                if (!block.requiresMaterial()) continue;
                var remaining = supplies.addItem(block.requiredItem().copyWithCount(block.requiredItem().getCount() * 2));
                require(remaining.isEmpty(), "the finite construction depot holds both house plans");
            }
        }

        for (int index = 0; index < RESIDENTS; index++) {
            SettlementArea workplace;
            VillagerProfession profession;
            BlockPos position;
            if (index < 32) {
                workplace = farms.get(index / 8);
                profession = VillagerProfession.FARMER;
                position = workplace.min().above().offset(1 + index % 4 * 3, 0, 1 + index % 8 / 4 * 6);
            } else if (index < 64) {
                workplace = distribution;
                profession = VillagerProfession.FARMER;
                position = center.offset(-3 + index % 4 * 2, 0, -35 + (index - 32) / 4 * 9);
            } else if (index < 96) {
                workplace = posts.get((index - 64) / 8);
                profession = VillagerProfession.WEAPONSMITH;
                position = workplace.min().offset(1 + index % 4 * 3, 0, 2 + index % 8 / 4 * 8);
            } else {
                workplace = fixture.sites.get(index - 96);
                profession = VillagerProfession.MASON;
                position = fixture.origins.get(index - 96).offset(-2, 0, 2);
            }
            var villager = EntityType.VILLAGER.create(level);
            var uuid = UUID.nameUUIDFromBytes(("town-profile:" + center + ":" + index)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            villager.setUUID(new UUID(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits() & ~1L));
            villager.setPos(position.getBottomCenter());
            villager.setOnGround(true);
            villager.getRandom().setSeed(center.asLong() + index);
            villager.setVillagerData(villager.getVillagerData().setProfession(profession));
            villager.setVillagerXp(1);
            VillagerPresetNameRegistry.setStoredGender(villager, VillagerGender.MALE);
            for (var skill : HiredVillagerRoles.roleSkills(workplace.type().role())) VillagerProfileManager.setSkill(level, villager, skill, 60);
            VillageAllegianceApi.assignKnown(level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
            require(level.addFreshEntity(villager), "the resident spawns");
            require(SettlementWorkforceService.assign(level, player, id, villager, workplace.id()), "the resident receives civic work");
            require(SettlementHousingService.assign(player, town, villager.getUUID(), beds.get(index)), "the resident receives a usable bed");
            var inventory = HiredJobInventory.getJobInventory(villager);
            if (index < 32) inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
            if (index >= 96) {
                inventory.insertTool(new ItemStack(Items.IRON_PICKAXE));
                inventory.insertTool(new ItemStack(Items.IRON_AXE));
                inventory.insertTool(new ItemStack(Items.IRON_SHOVEL));
                fixture.builders.add(villager.getUUID());
            }
            if (index % 5 == 0) VillagerRecoveryService.restoreRecoveryState(villager, new VillagerRecoveryService.RecoverySnapshot(0, 0, 0, 0));
            fixture.residents.add(villager.getUUID());
        }
        for (int index = 0; index < 4; index++) fixture.commission(player, index, index);
        fixture.demand(player, 256, 512);
        var offlineLeader = UUID.randomUUID();
        var offer = town.offerLeadership(player.getUUID(), offlineLeader, 0L);
        require(offer != null && town.acceptLeadership(offlineLeader, offer.id(), 0L), "the fixture transfers to an offline leader");
        require(town.setPermissions(offlineLeader, player.getUUID(), EnumSet.allOf(SettlementPermission.class)), "the observer receives delegated access");
        var permitted = town.areas().values().stream().filter(area -> area.type().role() != null)
                .map(area -> new SettlementWorkerPool.Staffing(area.id(), 0, SettlementWorksitePlan.MAX_STAFF)).toList();
        for (int index = 0; index < 4; index++) {
            var members = List.copyOf(fixture.residents.subList(index * 25, (index + 1) * 25));
            require(town.configureWorkerPool(offlineLeader, UUID.randomUUID(), -1,
                    new SettlementWorkerPool.Configuration("Work crew " + index, members, permitted), true), "the performance fixture enrolls four active pools");
            for (var resident : members) if (!fixture.builders.contains(resident))
                require(town.returnToAutomaticStaffing(offlineLeader, resident), "only the builders remain manually pinned");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (openView) {
            player.connection.teleport(body.x, body.y, body.z, 0.0F, 0.0F);
            SettlementManagementService.open(player, id);
        } else {
            player.setPos(body);
        }
        return fixture;
    }

    public Set<UUID> residents() {
        return Set.copyOf(this.residents);
    }

    public String viewState(ServerPlayer player) {
        var session = SettlementViewService.session(player.getUUID());
        return session == null ? "closed" : session.window + ", held=" + session.held.size();
    }

    public int tickingResidents() {
        int count = 0;
        for (var id : this.residents) {
            if (this.level.getEntity(id) instanceof Villager villager && villager.isAlive() && !villager.isNoAi()
                    && this.level.isPositionEntityTicking(villager.blockPosition())) count++;
        }
        return count;
    }

    public List<String> builderDiagnostics() {
        var diagnostics = new ArrayList<String>();
        for (var id : this.builders) {
            if (this.level.getEntity(id) instanceof Villager villager) {
                diagnostics.add(id + " " + com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.snapshot(
                        com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager), this.level.getGameTime())
                        + " " + com.jvn.villagerretaliation.interaction.HiredWorkDecisionScheduler.debugSnapshot(this.level, villager));
            }
        }
        return diagnostics;
    }

    public void beginActive(ServerPlayer player) {
        demand(player, 512, 0);
    }

    public void beginBackground(ServerPlayer player) {
        demand(player, 1_024, 0);
        for (int index = 0; index < this.builders.size(); index++) {
            UUID builder = this.builders.get(index);
            if (this.town.projects().values().stream().noneMatch(project -> project.builder().equals(builder) && project.active())) {
                require(SettlementWorkforceService.assignResident(this.level, player, this.town.id(), builder, this.sites.get(index + 4).id()),
                        "the unloaded builder can take the next construction site");
                commission(player, index, index + 4);
            }
        }
    }

    public void open(ServerPlayer player) {
        SettlementManagementService.open(player, this.town.id());
    }

    public boolean unloaded() {
        if (this.residents.stream().anyMatch(id -> this.level.getEntity(id) != null)) return false;
        var center = this.town.center();
        for (int x = (center.getX() - 46) >> 4; x <= (center.getX() + 62) >> 4; x++) {
            for (int z = (center.getZ() - 46) >> 4; z <= (center.getZ() + 46) >> 4; z++) {
                if (this.level.getChunkSource().getChunkNow(x, z) != null) return false;
            }
        }
        return true;
    }

    public com.jvn.villagerretaliation.network.SettlementManagementPayload demand(ServerPlayer player) {
        return SettlementManagementService.snapshot(player, new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(
                town.id().value(), com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.DEMAND, 0, "", 0, false));
    }

    public Snapshot snapshot(ServerPlayer player) {
        require(this.town.workerPools().size() == 4 && this.town.workerPools().values().stream().allMatch(pool -> pool.authorized(this.town)),
                "the four configured worker pools stay authorized throughout the profile");
        int loaded = 0;
        int ticking = 0;
        int aiEnabled = 0;
        int iron = this.town.depots().values().stream().mapToInt(depot -> depot.count(stack -> stack.is(Items.IRON_INGOT))).sum();
        int wheat = this.town.depots().values().stream().mapToInt(depot -> depot.count(stack -> stack.is(Items.WHEAT))).sum();
        for (var id : this.residents) {
            if (this.level.getEntity(id) instanceof Villager villager) {
                loaded++;
                if (this.level.isPositionEntityTicking(villager.blockPosition())) ticking++;
                if (!villager.isNoAi()) aiEnabled++;
                var inventory = HiredJobInventory.getJobInventory(villager);
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    iron += count(inventory.getItem(slot), Items.IRON_INGOT);
                    wheat += count(inventory.getItem(slot), Items.WHEAT);
                }
                wheat += villager.getInventory().countItem(Items.WHEAT);
            } else {
                var worker = this.town.workers().get(id);
                if (worker != null) {
                    for (var stack : worker.items(this.level.registryAccess())) {
                        iron += count(stack, Items.IRON_INGOT);
                        wheat += count(stack, Items.WHEAT);
                    }
                }
            }
        }
        var blockers = new LinkedHashMap<String, Integer>();
        this.town.projects().values().forEach(project -> blockers.merge(project.blocker().isBlank() ? project.status().name() : project.blocker(), 1, Integer::sum));
        return new Snapshot(loaded, ticking, aiEnabled,
                VillageAllegianceRegistrySavedData.get(this.level).residentRoster(this.town.id(), this.level.getGameTime()).size(),
                (int) this.town.homes().values().stream().filter(home -> home.bed() != null).count(),
                this.town.workers().size(), iron,
                this.destinations.stream().mapToInt(depot -> depot.count(stack -> stack.is(Items.IRON_INGOT))).sum(),
                wheat, this.town.depots().values().stream().mapToInt(depot -> depot.count(stack -> stack.is(Items.BREAD))).sum(),
                this.town.projects().values().stream().mapToInt(SettlementProject::placed).sum(),
                this.level.getServer().getPlayerList().getPlayer(this.town.leader()) == null,
                player.position().distanceToSqr(this.body), Map.copyOf(blockers), this.town.workerPools().size(),
                (int) this.residents.stream().filter(resident -> this.town.workerPoolFor(resident) != null).count());
    }

    public int verifyReturnedWorld() {
        int checked = 0;
        for (var project : this.town.projects().values()) {
            for (int index = 0; index < project.placed(); index++) {
                var block = this.plan.blocks().get(index);
                if (!block.requiresMaterial() || block.requiresToolAction()) continue;
                var position = this.plan.worldPos(project.origin(), block);
                require(this.level.getBlockState(position).is(block.state().getBlock()), "the returned world contains the recorded construction at " + position);
                checked++;
            }
        }
        require(checked > 0, "return verifies real constructed blocks");
        return checked;
    }

    private void demand(ServerPlayer player, int target, int reserve) {
        for (var source : this.sources) require(this.town.setDepotStockTarget(player.getUUID(), source.id(), new ItemStack(Items.IRON_INGOT), reserve), "source reserve is configured");
        for (var destination : this.destinations) require(this.town.setDepotStockTarget(player.getUUID(), destination.id(), new ItemStack(Items.IRON_INGOT), target), "destination demand is configured");
    }

    private void commission(ServerPlayer player, int builder, int site) {
        UUID id = UUID.randomUUID();
        player.setItemInHand(InteractionHand.MAIN_HAND, ConstructionBlueprintItem.create(this.level, null, this.plan,
                this.origins.get(site), id, 0, this.plan.price(), "", 0L));
        require(SettlementConstructionService.start(player, this.town, this.builders.get(builder), id), "the real blueprint is commissioned at site " + site);
    }

    private SettlementArea area(ServerPlayer player, SettlementAreaType type, String name, BlockPos min, BlockPos max) {
        SettlementTestPlots.claim(this.level, this.town, min, max);
        var area = new SettlementArea(UUID.randomUUID(), type, name, min, max, 5, true);
        require(SettlementBuildingService.register(player, this.town.id(), area), "the functional area registers: " + name);
        return area;
    }

    private SettlementDepot depot(ServerPlayer player, BlockPos position, SettlementDepotPurpose purpose, int priority) {
        this.level.setBlockAndUpdate(position, VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
        player.setPos(position.north().getBottomCenter());
        require(SettlementDepotService.register(player, position), "the physical depot registers");
        var depot = ((SettlementDepotBlockEntity) this.level.getBlockEntity(position)).inventory();
        require(this.town.configureDepot(player.getUUID(), depot.id(), purpose, priority), "the depot purpose is configured");
        return depot;
    }

    private static int count(ItemStack stack, Item item) {
        return stack.is(item) ? stack.getCount() : 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public record Snapshot(int loaded, int ticking, int aiEnabled, int census, int homes, int workers, int iron,
                           int deliveredIron, int wheat, int bread, int placed, boolean leaderOffline,
                           double bodyDisplacementSquared, Map<String, Integer> projectBlockers, int activePools, int poolMembers) {
    }
}
