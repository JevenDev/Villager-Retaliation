package com.jvn.villagerretaliation.debug;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.interaction.HiredCombatMode;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderPaymentEscrowService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Builds a self-contained production workload for every hired villager role. */
public final class HiredStressGridService {
    public static final int MAX_COUNT = 100;
    public static final int CONTRACT_DAYS = 30;
    public static final int ROLE_COUNT = HiredVillagerRole.values().length;
    private static final String STRESS_WORKER_TAG = "VillagerRetaliationHiredStressWorker";
    private static final String STRESS_ROLE_TAG = "VillagerRetaliationHiredStressRole";
    private static final String STRESS_CELL_TAG = "VillagerRetaliationHiredStressCell";
    private static final String STRESS_TARGET_TAG = "VillagerRetaliationHiredStressTarget";
    private static final String NEXT_SUPPLY_REFILL_TAG = "VillagerRetaliationHiredStressNextSupplyRefill";
    private static final int GRID_SPACING = 16;
    private static final int CELL_RADIUS = 7;
    private static final int WORK_RADIUS = 5;
    private static final int CELL_HEIGHT = 10;
    private static final int MINING_RADIUS = 3;
    private static final int MINING_DEPTH = 24;
    private static final int SUPPLY_REFILL_INTERVAL_TICKS = 20;
    private static final String PLAINS_SMALL_HOUSE_PREFIX = "village/plains/houses/plains_small_house_";

    private static final List<RoleSpec> ROLE_SPECS = List.of(
            spec(HiredVillagerRole.COMBAT, VillagerProfession.WEAPONSMITH),
            spec(HiredVillagerRole.HUNTING, VillagerProfession.FLETCHER),
            spec(HiredVillagerRole.MINING, VillagerProfession.TOOLSMITH),
            spec(HiredVillagerRole.LOGGING, VillagerProfession.NONE),
            spec(HiredVillagerRole.FARMING, VillagerProfession.FARMER),
            spec(HiredVillagerRole.FISHING, VillagerProfession.FISHERMAN),
            spec(HiredVillagerRole.BREWING, VillagerProfession.CLERIC),
            spec(HiredVillagerRole.CRAFTSMAN, VillagerProfession.TOOLSMITH),
            spec(HiredVillagerRole.BUILDER, VillagerProfession.MASON),
            spec(HiredVillagerRole.ANIMAL_HANDLING, VillagerProfession.SHEPHERD),
            spec(HiredVillagerRole.NITWIT, VillagerProfession.NITWIT),
            spec(HiredVillagerRole.COOK, VillagerProfession.BUTCHER),
            spec(HiredVillagerRole.SMELTER, VillagerProfession.ARMORER),
            spec(HiredVillagerRole.COURIER, VillagerProfession.NONE));

    private HiredStressGridService() {
    }

    public static Result spawn(ServerPlayer player, int requestedCount) {
        ServerLevel level = player.serverLevel();
        int count = Math.clamp(requestedCount, 1, MAX_COUNT);
        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (count + columns - 1) / columns;
        Direction forward = player.getDirection();
        Direction right = forward.getClockWise();
        BlockPos gridCenter = player.blockPosition().relative(forward, 5);
        BuilderFixture builderFixture = findBuilderFixture(level);
        int spawned = 0;
        int blocked = 0;
        int builders = 0;
        long prepaidCurrency = 0L;
        EnumMap<HiredVillagerRole, Integer> roles = new EnumMap<>(HiredVillagerRole.class);

        for (int index = 0; index < count; index++) {
            int row = index / columns;
            int column = index % columns;
            int forwardOffset = row * GRID_SPACING - ((rows - 1) * GRID_SPACING) / 2;
            int rightOffset = column * GRID_SPACING - ((columns - 1) * GRID_SPACING) / 2;
            BlockPos cell = gridCenter.relative(forward, forwardOffset).relative(right, rightOffset);
            RoleSpec spec = ROLE_SPECS.get(index % ROLE_SPECS.size());

            prepareCell(level, cell);
            BuilderPlacement builderPlacement = null;
            if (spec.role() == HiredVillagerRole.BUILDER) {
                if (builderFixture == null) {
                    blocked++;
                    continue;
                }
                builderPlacement = prepareBuilderSite(level, cell, builderFixture);
                buildFence(level, cell);
            }

            Villager villager = spawnVillager(level, player, villagerSpawnPos(cell, spec.role()), spec.profession());
            if (villager == null) {
                blocked++;
                continue;
            }
            qualifyForRole(level, villager, spec.role());

            int payment;
            boolean hired;
            if (spec.role() == HiredVillagerRole.BUILDER) {
                payment = startBuilder(level, villager, player, builderPlacement);
                hired = payment >= 0;
                if (hired) {
                    builders++;
                }
            } else {
                payment = HiredVillagerContractService.getHireCost(
                        level, villager, player, CONTRACT_DAYS, spec.role());
                hired = HiredVillagerContractService.startHireContract(
                        level, villager, player, CONTRACT_DAYS, payment, spec.role());
                if (hired) {
                    configureRegularRole(level, player, villager, cell, spec.role());
                }
            }
            if (!hired) {
                villager.discard();
                blocked++;
                continue;
            }

            markStressWorker(villager, spec.role(), cell);
            villager.setCustomName(Component.literal("Stress " + spec.role().label() + " #" + (index + 1)));
            villager.setCustomNameVisible(true);
            villager.setPersistenceRequired();
            prepaidCurrency += payment;
            roles.merge(spec.role(), 1, Integer::sum);
            spawned++;
        }
        return new Result(spawned, blocked, columns, rows, prepaidCurrency, builders, roles);
    }

    private static Villager spawnVillager(
            ServerLevel level, ServerPlayer player, BlockPos pos, VillagerProfession profession) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return null;
        }
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0.0F);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
        villager.setVillagerData(villager.getVillagerData().setProfession(profession));
        if (!level.noCollision(villager) || !level.addFreshEntity(villager)) {
            villager.discard();
            return null;
        }
        return villager;
    }

    private static void qualifyForRole(ServerLevel level, Villager villager, HiredVillagerRole role) {
        for (VillagerSkill skill : HiredVillagerRoles.roleSkills(role)) {
            VillagerProfileManager.setSkill(level, villager, skill, 100);
        }
    }

    private static void configureRegularRole(
            ServerLevel level, ServerPlayer player, Villager villager, BlockPos cell, HiredVillagerRole role) {
        BlockPos workMin = role == HiredVillagerRole.MINING
                ? new BlockPos(cell.getX() - MINING_RADIUS, miningBottomY(level, cell), cell.getZ() - MINING_RADIUS)
                : cell.offset(-WORK_RADIUS, -1, -WORK_RADIUS);
        BlockPos workMax = role == HiredVillagerRole.MINING
                ? cell.offset(MINING_RADIUS, 0, MINING_RADIUS)
                : cell.offset(WORK_RADIUS, CELL_HEIGHT, WORK_RADIUS);
        HiredVillagerWorkService.setWorkArea(player, level, villager, workMin, workMax);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredJobInventory inventory = session.inventory();
        switch (role) {
            case COMBAT -> configureCombat(level, villager, cell, session, inventory);
            case HUNTING -> configureHunting(level, player, villager, cell, session, inventory);
            case MINING -> configureMining(level, player, villager, cell, session, inventory);
            case LOGGING -> configureLogging(level, player, villager, cell, session, inventory);
            case FARMING -> configureFarming(level, cell, inventory);
            case FISHING -> configureFishing(level, cell, inventory);
            case BREWING -> configureBrewing(level, player, villager, cell, session, inventory);
            case CRAFTSMAN -> configureCraftsman(level, player, villager, cell, inventory);
            case ANIMAL_HANDLING -> configureAnimalHandling(level, cell, inventory);
            case NITWIT -> session.state().remove("NitwitNoticeTick");
            case COOK -> configureCooking(level, cell, inventory);
            case SMELTER -> configureSmelting(level, cell, inventory);
            case COURIER -> configureCourier(level, player, villager, cell, session);
            case BUILDER -> throw new IllegalStateException("Builder uses one-off setup");
        }
        session.state().remove("NextWorkGameTime");
        HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.SELECTING_TARGET, cell);
        primeWorker(level, villager, player, session);
    }

    private static void configureCombat(
            ServerLevel level, Villager villager, BlockPos cell, HiredWorkSession session, HiredJobInventory inventory) {
        equip(level, inventory, Items.NETHERITE_SWORD);
        session.state().putString(HiredCombatMode.STATE_TAG, HiredCombatMode.ATTACK_ALL.serializedName());
        spawnStressTarget(level, villager, cell, HiredVillagerRole.COMBAT);
    }

    private static void configureHunting(
            ServerLevel level, ServerPlayer player, Villager villager, BlockPos cell,
            HiredWorkSession session, HiredJobInventory inventory) {
        equip(level, inventory, Items.NETHERITE_SWORD);
        session.state().putBoolean(HiredHuntingTargets.HUNT_ANIMALS_TAG, true);
        session.state().putBoolean(HiredHuntingTargets.HUNT_HOSTILES_TAG, false);
        session.state().putBoolean(HiredHuntingTargets.HUNT_PLAYERS_TAG, false);
        DoubleChest output = placeDoubleChest(level, cell.offset(-1, 0, 5), "Stress Hunting OUTPUT");
        assignStorage(player, villager, AssignedStorageService.OUTPUT_PURPOSE, output.left());
        spawnStressTarget(level, villager, cell, HiredVillagerRole.HUNTING);
    }

    private static void configureMining(
            ServerLevel level, ServerPlayer player, Villager villager, BlockPos cell,
            HiredWorkSession session, HiredJobInventory inventory) {
        equip(level, inventory, Items.NETHERITE_PICKAXE);
        session.state().putString(HiredMiningMode.STATE_TAG, HiredMiningMode.EXCAVATE_AREA.serializedName());
        Block[] ores = {
                Blocks.COAL_ORE, Blocks.COPPER_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE,
                Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE
        };
        int bottomY = miningBottomY(level, cell);
        for (int y = cell.getY() - 1; y >= bottomY; y--) {
            for (int x = -MINING_RADIUS; x <= MINING_RADIUS; x++) {
                for (int z = -MINING_RADIUS; z <= MINING_RADIUS; z++) {
                    int oreIndex = Math.floorMod(x * 31 + z * 17 + y * 13, ores.length);
                    level.setBlock(new BlockPos(cell.getX() + x, y, cell.getZ() + z),
                            ores[oreIndex].defaultBlockState(), 3);
                }
            }
        }
        DoubleChest input = placeDoubleChest(level, cell.offset(-5, 0, -5), "Stress Mining INPUT");
        DoubleChest output = placeDoubleChest(level, cell.offset(3, 0, -5), "Stress Mining OUTPUT");
        fillDoubleChest(level, input, List.of(
                stressTool(level, Items.NETHERITE_PICKAXE),
                new ItemStack(Items.LADDER, 64),
                new ItemStack(Items.TORCH, 64),
                new ItemStack(Items.COBBLESTONE, 64)), false);
        assignStorage(player, villager, AssignedStorageService.SUPPLY_PURPOSE, input.left());
        assignStorage(player, villager, AssignedStorageService.OUTPUT_PURPOSE, output.left());
        inventory.insertSupply(new ItemStack(Items.LADDER, 64));
        inventory.insertSupply(new ItemStack(Items.TORCH, 64));
        inventory.insertSupply(new ItemStack(Items.COBBLESTONE, 64));
    }

    private static void configureLogging(
            ServerLevel level, ServerPlayer player, Villager villager, BlockPos cell,
            HiredWorkSession session, HiredJobInventory inventory) {
        equip(level, inventory, Items.NETHERITE_AXE);
        HiredLoggingOptions.initializeDefaults(session.state());
        session.state().putBoolean(HiredLoggingOptions.BONEMEAL_SAPLINGS_TAG, true);
        session.state().putBoolean(HiredLoggingOptions.PLANT_SAPLINGS_TAG, true);
        session.state().putBoolean(HiredLoggingOptions.PICK_UP_DECAY_DROPS_TAG, true);
        inventory.insertSupply(new ItemStack(Items.OAK_SAPLING, 64));
        inventory.insertSupply(new ItemStack(Items.BONE_MEAL, 64));

        BlockPos trunk = cell.offset(2, 0, 0);
        level.setBlock(trunk.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        for (int y = 0; y <= 3; y++) {
            level.setBlock(trunk.above(y), Blocks.OAK_LOG.defaultBlockState(), 3);
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) + Math.abs(z) <= 3) {
                    level.setBlock(trunk.offset(x, 4, z),
                            Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false), 3);
                }
            }
        }
        level.setBlock(trunk.above(5),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false), 3);

        DoubleChest input = placeDoubleChest(level, cell.offset(-5, 0, -5), "Stress Logging INPUT");
        DoubleChest output = placeDoubleChest(level, cell.offset(-5, 0, 5), "Stress Logging OUTPUT");
        fillDoubleChest(level, input, loggingSupplies(level), false);
        assignStorage(player, villager, AssignedStorageService.SUPPLY_PURPOSE, input.left());
        assignStorage(player, villager, AssignedStorageService.OUTPUT_PURPOSE, output.left());
    }

    private static void configureFarming(ServerLevel level, BlockPos cell, HiredJobInventory inventory) {
        equip(level, inventory, Items.NETHERITE_HOE);
        inventory.insertSupply(new ItemStack(Items.WHEAT_SEEDS, 64));
        for (int x = 1; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos crop = cell.offset(x, 0, z);
                level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 3);
                level.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7), 3);
            }
        }
        placeWaterHole(level, cell.offset(0, -1, 0));
    }

    private static void configureFishing(ServerLevel level, BlockPos cell, HiredJobInventory inventory) {
        equip(level, inventory, Items.FISHING_ROD);
        for (int index = 0; index < 4; index++) {
            inventory.insertTool(stressTool(level, Items.FISHING_ROD));
        }
        placeWaterHole(level, cell.offset(-2, -1, 0));
    }

    private static void configureBrewing(
            ServerLevel level, ServerPlayer player, Villager villager, BlockPos cell,
            HiredWorkSession session, HiredJobInventory inventory) {
        level.setBlock(cell.offset(2, 0, 0), Blocks.BREWING_STAND.defaultBlockState(), 3);
        placeWaterHole(level, cell.offset(-2, -1, 0));
        HiredBrewingRecipeCatalog.BrewingRoute route = selectInvisibilityRoute(level);
        if (route == null) {
            return;
        }
        List<ItemStack> supplies = brewingSupplies(route);
        for (ItemStack supply : supplies) {
            inventory.insertSupply(supply.copy());
        }
        DoubleChest input = placeDoubleChest(level, cell.offset(-5, 0, -5), "Stress Brewing INPUT");
        DoubleChest outputOne = placeDoubleChest(level, cell.offset(-5, 0, 5), "Stress Brewing OUTPUT 1");
        DoubleChest outputTwo = placeDoubleChest(level, cell.offset(3, 0, 5), "Stress Brewing OUTPUT 2");
        fillDoubleChest(level, input, supplies, false);
        assignStorage(player, villager, AssignedStorageService.SUPPLY_PURPOSE, input.left());
        assignStorage(player, villager, AssignedStorageService.OUTPUT_PURPOSE, outputOne.left(), outputTwo.left());
        BrewingWorker.setOrder(
                session.state(), route.itemId(), route.potionId(), Integer.MAX_VALUE, true,
                HiredVillagerContractService.currentContractId(villager).orElse(null));
    }

    private static void configureCraftsman(
            ServerLevel level, ServerPlayer player, Villager villager, BlockPos cell, HiredJobInventory inventory) {
        level.setBlock(cell.offset(2, 0, 0), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        List<ItemStack> supplies = craftsmanSupplies();
        for (ItemStack supply : supplies) {
            inventory.insertSupply(supply.copy());
        }
        DoubleChest inputOne = placeDoubleChest(level, cell.offset(-5, 0, -5), "Stress Craftsman INPUT 1");
        DoubleChest inputTwo = placeDoubleChest(level, cell.offset(3, 0, -5), "Stress Craftsman INPUT 2");
        DoubleChest output = placeDoubleChest(level, cell.offset(-1, 0, 5), "Stress Craftsman OUTPUT");
        fillDoubleChest(level, inputOne, supplies, false);
        fillDoubleChest(level, inputTwo, supplies, false);
        assignStorage(player, villager, AssignedStorageService.SUPPLY_PURPOSE, inputOne.left(), inputTwo.left());
        assignStorage(player, villager, AssignedStorageService.OUTPUT_PURPOSE, output.left());
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.DISPENSER));
        VillagerItemFilterData.setMode(filter, VillagerItemFilterData.Mode.ALLOWLIST);
        VillagerItemFilterService.replaceFilter(villager, filter);
    }

    private static void configureAnimalHandling(ServerLevel level, BlockPos cell, HiredJobInventory inventory) {
        equip(level, inventory, Items.SHEARS);
        inventory.insertSupply(new ItemStack(Items.WHEAT, 64));
        for (int x = -WORK_RADIUS; x <= WORK_RADIUS; x++) {
            for (int z = -WORK_RADIUS; z <= WORK_RADIUS; z++) {
                level.setBlock(cell.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        }
        spawnMob(level, EntityType.SHEEP, cell.offset(2, 0, 0));
        spawnMob(level, EntityType.SHEEP, cell.offset(2, 0, 2));
        spawnMob(level, EntityType.SHEEP, cell.offset(0, 0, 2));
    }

    private static void configureCooking(ServerLevel level, BlockPos cell, HiredJobInventory inventory) {
        level.setBlock(cell.offset(2, 0, 0), Blocks.SMOKER.defaultBlockState(), 3);
        inventory.insertSupply(new ItemStack(Items.BEEF, 64));
        inventory.insertSupply(new ItemStack(Items.COAL, 64));
    }

    private static void configureSmelting(ServerLevel level, BlockPos cell, HiredJobInventory inventory) {
        level.setBlock(cell.offset(2, 0, 0), Blocks.BLAST_FURNACE.defaultBlockState(), 3);
        inventory.insertSupply(new ItemStack(Items.RAW_IRON, 64));
        inventory.insertSupply(new ItemStack(Items.COAL, 64));
    }

    private static void configureCourier(
            ServerLevel level, ServerPlayer player, Villager villager, BlockPos cell, HiredWorkSession session) {
        DoubleChest input = placeDoubleChest(level, cell.offset(-5, 0, 0), "Stress Courier INPUT");
        DoubleChest output = placeDoubleChest(level, cell.offset(4, 0, 0), "Stress Courier OUTPUT");
        fillDoubleChest(level, input, List.of(
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.GRANITE, 64),
                new ItemStack(Items.ANDESITE, 64)), false);
        assignStorage(player, villager, AssignedStorageService.SUPPLY_PURPOSE, input.left());
        assignStorage(player, villager, AssignedStorageService.OUTPUT_PURPOSE, output.left());
        new HiredRoute(List.of(cell.offset(-3, 0, 0), cell.offset(3, 0, 0)), true).save(session.state());
    }

    private static int startBuilder(
            ServerLevel level, Villager villager, ServerPlayer player, BuilderPlacement placement) {
        if (placement == null) {
            return -1;
        }
        HiredVillagerContractService.startOneOffBuilderJob(level, villager, player);
        if (!HiredVillagerContractService.isHired(level, villager)) {
            return -1;
        }
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        UUID jobId = UUID.randomUUID();
        int price = placement.fixture().plan().price();
        BuilderTaskState.start(
                session.state(), placement.fixture().entry(), placement.fixture().plan(), placement.origin(),
                Rotation.NONE, price, level.getGameTime(), jobId);
        BuilderPaymentEscrowService.escrow(villager, jobId, price);
        equip(level, session.inventory(), Items.NETHERITE_PICKAXE);
        session.inventory().insertTool(stressTool(level, Items.NETHERITE_AXE));
        session.inventory().insertTool(stressTool(level, Items.NETHERITE_SHOVEL));
        session.inventory().insertTool(stressTool(level, Items.NETHERITE_HOE));
        for (BuilderStructureScanner.MaterialRequirement material : placement.fixture().plan().materials()) {
            insertSupply(session.inventory(), material.item(), material.count());
        }
        session.state().remove("NextWorkGameTime");
        HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.SELECTING_TARGET, placement.origin());
        primeWorker(level, villager, player, session);
        return price;
    }

    private static BuilderFixture findBuilderFixture(ServerLevel level) {
        return BuilderStructureCatalog.entries(level.getServer()).stream()
                .filter(entry -> entry.id().getNamespace().equals("minecraft"))
                .filter(entry -> entry.id().getPath().startsWith(PLAINS_SMALL_HOUSE_PREFIX))
                .sorted(Comparator.comparing(entry -> entry.id().toString()))
                .map(entry -> BuilderStructureScanner.scan(level, entry, Rotation.NONE)
                        .map(plan -> new BuilderFixture(entry, plan))
                        .orElse(null))
                .filter(fixture -> fixture != null)
                .filter(HiredStressGridService::fitsInsideCell)
                .findFirst()
                .orElse(null);
    }

    private static BuilderPlacement prepareBuilderSite(
            ServerLevel level, BlockPos cell, BuilderFixture fixture) {
        BuilderStructureScanner.StructurePlan plan = fixture.plan();
        BlockPos center = plan.localCenter();
        BlockPos origin = new BlockPos(
                cell.getX() - center.getX(),
                cell.getY() - plan.localMin().getY(),
                cell.getZ() - center.getZ());
        BlockPos min = plan.worldMin(origin);
        BlockPos max = plan.worldMax(origin);
        for (int x = min.getX() - 1; x <= max.getX() + 1; x++) {
            for (int z = min.getZ() - 1; z <= max.getZ() + 1; z++) {
                level.setBlock(new BlockPos(x, cell.getY() - 1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                for (int y = cell.getY(); y <= max.getY() + 2; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        return new BuilderPlacement(fixture, origin);
    }

    private static void prepareCell(ServerLevel level, BlockPos cell) {
        for (int x = -CELL_RADIUS; x <= CELL_RADIUS; x++) {
            for (int z = -CELL_RADIUS; z <= CELL_RADIUS; z++) {
                level.setBlock(cell.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                for (int y = 0; y <= CELL_HEIGHT; y++) {
                    level.setBlock(cell.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        buildFence(level, cell);
    }

    private static void buildFence(ServerLevel level, BlockPos cell) {
        for (int offset = -CELL_RADIUS; offset <= CELL_RADIUS; offset++) {
            level.setBlock(cell.offset(offset, 0, -CELL_RADIUS), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(cell.offset(offset, 0, CELL_RADIUS), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(cell.offset(-CELL_RADIUS, 0, offset), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(cell.offset(CELL_RADIUS, 0, offset), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
    }

    private static void placeWaterHole(ServerLevel level, BlockPos water) {
        level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
    }

    private static boolean fitsInsideCell(BuilderFixture fixture) {
        BuilderStructureScanner.StructurePlan plan = fixture.plan();
        int width = plan.localMax().getX() - plan.localMin().getX() + 1;
        int depth = plan.localMax().getZ() - plan.localMin().getZ() + 1;
        int interior = CELL_RADIUS * 2 - 1;
        return width <= interior && depth <= interior;
    }

    public static boolean isStressWorker(Villager villager) {
        if (villager == null) {
            return false;
        }
        if (villager.getPersistentData().getBoolean(STRESS_WORKER_TAG)) {
            return true;
        }
        Component customName = villager.getCustomName();
        return customName != null && customName.getString().startsWith("Stress ");
    }

    public static void keepStressWorkerAwake(Villager villager) {
        if (!isStressWorker(villager)) {
            return;
        }
        if (villager.isSleeping()) {
            villager.stopSleeping();
        }
        villager.getBrain().setDefaultActivity(Activity.IDLE);
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
    }

    private static void markStressWorker(Villager villager, HiredVillagerRole role, BlockPos cell) {
        villager.getPersistentData().putBoolean(STRESS_WORKER_TAG, true);
        villager.getPersistentData().putString(STRESS_ROLE_TAG, role.serializedName());
        villager.getPersistentData().putLong(STRESS_CELL_TAG, cell.asLong());
        villager.getPersistentData().putLong(NEXT_SUPPLY_REFILL_TAG, 0L);
        keepStressWorkerAwake(villager);
    }

    private static BlockPos villagerSpawnPos(BlockPos cell, HiredVillagerRole role) {
        if (role == HiredVillagerRole.FISHING) {
            return cell.offset(3, 0, 0);
        }
        if (role == HiredVillagerRole.BUILDER) {
            return cell.offset(CELL_RADIUS - 1, 0, 0);
        }
        return cell;
    }

    private static <T extends Mob> T spawnMob(ServerLevel level, EntityType<T> type, BlockPos pos) {
        T mob = type.create(level);
        if (mob == null) {
            return null;
        }
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
        mob.setPersistenceRequired();
        if (!level.addFreshEntity(mob)) {
            mob.discard();
            return null;
        }
        return mob;
    }

    public static void maintainStressWorker(ServerLevel level, Villager villager) {
        if (level == null || villager == null || !villager.getPersistentData().getBoolean(STRESS_WORKER_TAG)) {
            return;
        }
        HiredVillagerRole role = HiredVillagerRole.bySerializedName(
                villager.getPersistentData().getString(STRESS_ROLE_TAG));
        if (role == null) {
            return;
        }
        BlockPos cell = BlockPos.of(villager.getPersistentData().getLong(STRESS_CELL_TAG));
        if (role == HiredVillagerRole.COMBAT || role == HiredVillagerRole.HUNTING) {
            maintainStressTarget(level, villager, cell, role);
        }
        long gameTime = level.getGameTime();
        if (villager.getPersistentData().getLong(NEXT_SUPPLY_REFILL_TAG) > gameTime) {
            return;
        }
        villager.getPersistentData().putLong(
                NEXT_SUPPLY_REFILL_TAG, gameTime + SUPPLY_REFILL_INTERVAL_TICKS);
        if (role == HiredVillagerRole.LOGGING) {
            fillDoubleChest(level, new DoubleChest(cell.offset(-5, 0, -5), cell.offset(-4, 0, -5)),
                    loggingSupplies(level), true);
        } else if (role == HiredVillagerRole.BREWING) {
            HiredBrewingRecipeCatalog.BrewingRoute route = selectInvisibilityRoute(level);
            if (route != null) {
                fillDoubleChest(level, new DoubleChest(cell.offset(-5, 0, -5), cell.offset(-4, 0, -5)),
                        brewingSupplies(route), true);
            }
        }
    }

    private static void maintainStressTarget(
            ServerLevel level, Villager villager, BlockPos cell, HiredVillagerRole role) {
        Entity current = villager.getPersistentData().hasUUID(STRESS_TARGET_TAG)
                ? level.getEntity(villager.getPersistentData().getUUID(STRESS_TARGET_TAG))
                : null;
        if (current != null && current.isAlive()) {
            return;
        }
        spawnStressTarget(level, villager, cell, role);
    }

    private static Cow spawnStressTarget(
            ServerLevel level, Villager villager, BlockPos cell, HiredVillagerRole role) {
        Cow target = spawnMob(level, EntityType.COW, cell.offset(2, 0, 0));
        if (target == null) {
            return null;
        }
        target.setCustomName(Component.literal("Renewable Stress Target"));
        target.setCustomNameVisible(true);
        villager.getPersistentData().putUUID(STRESS_TARGET_TAG, target.getUUID());
        if (role == HiredVillagerRole.COMBAT) {
            VillagerRetaliationHandler.engageCustomTarget(villager, target, false);
        }
        return target;
    }

    private static int miningBottomY(ServerLevel level, BlockPos cell) {
        return Math.max(level.getMinBuildHeight() + 2, cell.getY() - MINING_DEPTH);
    }

    private static HiredBrewingRecipeCatalog.BrewingRoute selectInvisibilityRoute(ServerLevel level) {
        List<HiredBrewingRecipeCatalog.BrewingRoute> routes = HiredBrewingRecipeCatalog.routes(level);
        HiredBrewingRecipeCatalog.BrewingRoute exact = routes.stream()
                .filter(route -> route.output().is(Items.POTION))
                .filter(route -> route.potionId().getPath().equals("long_invisibility"))
                .findFirst()
                .orElse(null);
        if (exact != null) {
            return exact;
        }
        return routes.stream()
                .filter(route -> route.output().is(Items.POTION))
                .filter(route -> route.potionId().getPath().contains("invisibility"))
                .max(Comparator.comparingInt(route -> route.ingredients().size()))
                .orElseGet(() -> routes.stream()
                        .max(Comparator.comparingInt(route -> route.ingredients().size()))
                        .orElse(null));
    }

    private static List<ItemStack> brewingSupplies(HiredBrewingRecipeCatalog.BrewingRoute route) {
        List<ItemStack> supplies = new ArrayList<>();
        supplies.add(new ItemStack(Items.GLASS_BOTTLE, 64));
        supplies.add(new ItemStack(Items.BLAZE_POWDER, 64));
        for (Item ingredient : route.ingredients()) {
            supplies.add(new ItemStack(ingredient, 64));
        }
        return List.copyOf(supplies);
    }

    private static List<ItemStack> loggingSupplies(ServerLevel level) {
        return List.of(
                stressTool(level, Items.NETHERITE_AXE),
                new ItemStack(Items.BONE_MEAL, 64),
                new ItemStack(Items.BONE_MEAL, 64),
                new ItemStack(Items.OAK_SAPLING, 64));
    }

    private static List<ItemStack> craftsmanSupplies() {
        return List.of(
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.REDSTONE, 64),
                new ItemStack(Items.STRING, 64),
                new ItemStack(Items.OAK_PLANKS, 64));
    }

    private static DoubleChest placeDoubleChest(ServerLevel level, BlockPos left, String name) {
        BlockPos right = left.east();
        level.setBlock(left, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT), Block.UPDATE_ALL);
        level.setBlock(right, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT), Block.UPDATE_ALL);
        setContainerName(level, left, name);
        setContainerName(level, right, name);
        return new DoubleChest(left.immutable(), right.immutable());
    }

    private static void setContainerName(ServerLevel level, BlockPos pos, String name) {
        if (level.getBlockEntity(pos) instanceof BaseContainerBlockEntity container) {
            DataComponentMap components = DataComponentMap.builder()
                    .addAll(container.components())
                    .set(DataComponents.CUSTOM_NAME, Component.literal(name))
                    .build();
            container.setComponents(components);
            container.setChanged();
        }
    }

    private static void fillDoubleChest(
            ServerLevel level, DoubleChest chest, List<ItemStack> pattern, boolean onlyEmpty) {
        if (pattern.isEmpty()) {
            return;
        }
        int patternIndex = 0;
        for (BlockPos pos : List.of(chest.left(), chest.right())) {
            if (!(level.getBlockEntity(pos) instanceof Container container)) {
                continue;
            }
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (onlyEmpty && !container.getItem(slot).isEmpty()) {
                    continue;
                }
                ItemStack template = pattern.get(patternIndex++ % pattern.size());
                container.setItem(slot, template.copyWithCount(template.getMaxStackSize()));
            }
            container.setChanged();
        }
    }

    private static void assignStorage(
            ServerPlayer player, Villager villager, String purpose, BlockPos... positions) {
        List<AssignedStorageService.StoragePosition> storage = new ArrayList<>();
        for (BlockPos pos : positions) {
            storage.add(new AssignedStorageService.StoragePosition(player.level().dimension(), pos));
        }
        AssignedStorageService.assign(player, villager, storage, purpose);
    }

    private static ItemStack stressTool(ServerLevel level, Item item) {
        ItemStack stack = new ItemStack(item);
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        stack.enchant(enchantments.getOrThrow(Enchantments.MENDING), 1);
        stack.enchant(enchantments.getOrThrow(Enchantments.UNBREAKING), 3);
        return stack;
    }

    private static void primeWorker(
            ServerLevel level, Villager villager, ServerPlayer player, HiredWorkSession session) {
        if (session.worker() == null) {
            return;
        }
        WorkResult result = session.worker().tick(level, villager, player, session.context());
        session.state().putString("Status", result.status());
    }

    private static void equip(ServerLevel level, HiredJobInventory inventory, Item item) {
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, stressTool(level, item));
    }

    private static void insertSupply(HiredJobInventory inventory, ItemStack template, int count) {
        int remaining = Math.max(0, count);
        while (remaining > 0) {
            ItemStack stack = template.copyWithCount(Math.min(remaining, template.getMaxStackSize()));
            ItemStack remainder = inventory.insertSupply(stack);
            int inserted = stack.getCount() - remainder.getCount();
            if (inserted <= 0) {
                return;
            }
            remaining -= inserted;
        }
    }

    private static RoleSpec spec(HiredVillagerRole role, VillagerProfession profession) {
        return new RoleSpec(role, profession);
    }

    private record DoubleChest(BlockPos left, BlockPos right) {
    }

    private record RoleSpec(HiredVillagerRole role, VillagerProfession profession) {
    }

    private record BuilderFixture(
            BuilderStructureCatalog.Entry entry, BuilderStructureScanner.StructurePlan plan) {
    }

    private record BuilderPlacement(BuilderFixture fixture, BlockPos origin) {
    }

    public record Result(
            int spawned,
            int blocked,
            int columns,
            int rows,
            long prepaidCurrency,
            int builders,
            Map<HiredVillagerRole, Integer> roles) {
        public Result {
            roles = Map.copyOf(roles);
        }

        public String roleSummary() {
            return roles.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey().serializedName() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));
        }
    }
}
