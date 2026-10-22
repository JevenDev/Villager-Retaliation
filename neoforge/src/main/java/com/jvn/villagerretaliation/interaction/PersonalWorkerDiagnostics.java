package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderBuildPhase;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingWorker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class PersonalWorkerDiagnostics {
    private PersonalWorkerDiagnostics() {
    }

    public static String preview(HiredWorkSession session, HiredWorkerBrain.Snapshot brain, HiredWorkerFacts facts) {
        return facts.canBeBlocked() ? describe(session, brain, facts) : "";
    }

    public static String describe(HiredWorkSession session, HiredWorkerBrain.Snapshot brain, HiredWorkerFacts facts) {
        String detail = workerDiagnostic(session.role(), brain, session.context(), session.state(),
                facts.inventoryFull(), facts.noStorage(), facts.missingMaterials(), facts.materialStorageUnreachable(),
                facts.materialInventoryFull(), facts.buildSiteUnreachable());
        if (!detail.isBlank()) return limitDiagnostic(detail);
        String common = commonDiagnostic(brain, facts);
        if (!common.isBlank()) return common;
        if (detail.isBlank() && facts.missingMaterials())
            detail = "Required supplies are missing. Add the recipe ingredients and fuel to Job Inventory or reachable assigned Supplies; check that the installed filter permits them.";
        return limitDiagnostic(detail);
    }

    /** Reports observed blockers; a scan with no targets does not prove that a filter caused it. */
    public static String commonDiagnostic(HiredWorkerBrain.Snapshot brain, HiredWorkerFacts facts) {
        if (!facts.canBeBlocked()) return "";
        String reason = lower(brain.failureReason());
        if (reason.contains("unreachable") || reason.contains("path_failed")) {
            BlockPos target = diagnosticStoragePos(brain);
            if (target == null) target = brain.targetPos();
            return "Cannot reach " + (target == null ? "the required work or storage target" : HiredWorkerBrain.formatPos(target))
                    + ". Clear a safe walking route and check that the target is inside the assigned area or storage links.";
        }
        if (brain.taskState() == HiredWorkerTaskState.PAUSED_STORAGE_FULL)
            return "Assigned output storage cannot accept more output. Make room, check its filter and access, or assign another Output chest with the Clipboard.";
        if (facts.noStorage())
            return "No usable assigned storage. Select the needed Supplies or Output mode on the Clipboard, use it on a chest, then on this worker and choose Assign Clipboard Storage.";
        if (facts.inventoryFull())
            return "Job output has no room. Empty its output slots or make room in assigned Output storage, then enable Auto Deposit Outputs in Job > Work Settings.";
        if (reason.equals("craftsman_filter_empty"))
            return "No craft output matches the installed filter. Configure a supported recipe or output, check Allow/Deny, and assign the corrected filter to this worker.";
        if (reason.equals("no_craftsman_table"))
            return "No crafting table found in the saved job area. Place a table there or apply a corrected Clipboard job site, leaving a clear path to it.";
        if (facts.noTargets())
            return "No eligible target was found in the latest scan. Check the saved job area, target readiness and job settings, then inspect the filter; the scan alone does not identify the cause.";
        return "";
    }

    private static String workerDiagnostic(
            HiredVillagerRole role,
            HiredWorkerBrain.Snapshot brain,
            HiredWorkContext context,
            CompoundTag state,
            boolean inventoryFull,
            boolean noStorage,
            boolean missingMaterials,
            boolean materialStorageUnreachable,
            boolean materialInventoryFull,
            boolean buildSiteUnreachable) {
        if (role == HiredVillagerRole.ANIMAL_HANDLING && missingMaterials) {
            return switch (brain.failureReason()) {
                case "missing_breeding_food" -> "Breeding needs two suitable food items. Add them to supply slots or enable assigned Supplies storage containing them.";
                case "missing_bucket" -> "Milking needs an empty bucket. Add one to supply slots or enable assigned Supplies storage containing buckets.";
                default -> "Animal handling is waiting for supplies. Check supply slots and assigned Supplies storage.";
            };
        }
        if (role == HiredVillagerRole.BREWING) {
            return brewingDiagnostic(
                    brain,
                    state,
                    inventoryFull,
                    noStorage,
                    missingMaterials,
                    materialStorageUnreachable,
                    materialInventoryFull);
        }
        if (role == HiredVillagerRole.COOK) {
            return cookingDiagnostic(
                    brain,
                    inventoryFull,
                    noStorage,
                    missingMaterials,
                    materialStorageUnreachable,
                    materialInventoryFull);
        }
        if (role == HiredVillagerRole.SMELTER) {
            return smeltingDiagnostic(
                    brain,
                    inventoryFull,
                    noStorage,
                    missingMaterials,
                    materialStorageUnreachable,
                    materialInventoryFull);
        }
        if (role == HiredVillagerRole.COURIER) {
            return courierDiagnostic(brain, inventoryFull, noStorage);
        }
        if (role == HiredVillagerRole.LOGGING) {
            return loggingDiagnostic(brain, context, inventoryFull, noStorage);
        }
        if (role == HiredVillagerRole.MINING) {
            return miningDiagnostic(brain, inventoryFull, noStorage, missingMaterials, materialStorageUnreachable);
        }
        if (role == HiredVillagerRole.FARMING) {
            if (brain.status().startsWith("interaction.work.farming.planting_"))
                return com.jvn.villagerretaliation.settlement.SettlementPlanting.description(state);
            return farmingDiagnostic(brain);
        }
        if (role == HiredVillagerRole.FISHING) {
            return fishingDiagnostic(brain);
        }
        String rangedAmmo = rangedAmmoDiagnostic(brain, role);
        if (!rangedAmmo.isBlank()) {
            return rangedAmmo;
        }
        if (role == HiredVillagerRole.HUNTING) {
            return huntingDiagnostic(brain);
        }
        if (role != HiredVillagerRole.BUILDER) {
            return "";
        }

        String missing = BuilderTaskState.missingMaterials(state);
        String reason = lower(brain.failureReason() + " " + BuilderTaskState.blockedReason(state));
        String tool = toolLabelFromFailure(reason, "tool");
        if (materialInventoryFull) {
            if (lower(brain.failureReason()).contains("builder_material_output_storage_unreachable")) {
                BlockPos storagePos = diagnosticStoragePos(brain);
                return limitDiagnostic(storagePos == null
                        ? "Builder needs to dump excavation output before restocking, but cannot reach assigned output storage."
                        : "Builder needs to dump excavation output before restocking, but cannot path to output storage at "
                                + HiredWorkerBrain.formatPos(storagePos) + ".");
            }
            if (lower(brain.failureReason()).contains("builder_material_output_slot_full")) {
                return limitDiagnostic(missing.isBlank()
                        ? "Required construction blocks are in output slots, but the builder cannot free a supply slot for them."
                        : "Required construction blocks are in output slots, but the builder cannot free a supply slot for: "
                                + missing + ".");
            }
            return limitDiagnostic(missing.isBlank()
                    ? "Builder cannot fit the next construction batch after dumping removable excavation output."
                    : "Builder cannot fit the next construction batch after dumping output. Still needs room for: "
                            + missing + ".");
        }
        if (materialStorageUnreachable) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Assigned storage contains the needed construction blocks, but the builder cannot path to that container."
                    : "Assigned storage at " + HiredWorkerBrain.formatPos(storagePos)
                            + " contains the needed construction blocks, but the builder cannot path to it.");
        }
        if (missingMaterials) {
            if (lower(brain.failureReason()).contains("missing_builder_materials_storage_too_far")) {
                int radius = Math.max(1, VillagerRetaliationConfig.HIRED_BUILDER_MATERIAL_STORAGE_RADIUS.get());
                return limitDiagnostic(missing.isBlank()
                        ? "Builder needs assigned construction-block storage within " + radius + " blocks of the build site."
                        : "Builder needs assigned construction-block storage within " + radius + " blocks of the build site for: "
                                + missing + ".");
            }
            if (lower(brain.failureReason()).contains("missing_builder_materials_storage_unreachable")) {
                return limitDiagnostic(missing.isBlank()
                        ? missingMaterialStorageWaitDiagnostic(brain)
                        : "No reachable assigned storage contains the next construction batch: " + missing
                                + ". " + missingMaterialStorageWaitDiagnostic(brain));
            }
            return limitDiagnostic(missing.isBlank()
                    ? "No assigned storage currently contains the next construction batch."
                    : "No assigned storage currently contains the next construction batch: " + missing + ".");
        }
        if (buildSiteUnreachable) {
            return limitDiagnostic(brain.targetPos() == null
                    ? "Builder has construction work queued but cannot path back to the build site."
                    : "Builder cannot path back to the next construction spot at "
                            + HiredWorkerBrain.formatPos(brain.targetPos()) + ".");
        }
        if (reason.contains("tool_storage_unreachable")) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Builder found the " + tool + " in assigned tool storage, but cannot path to that container."
                    : "Builder found the " + tool + " in assigned tool storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.");
        }
        if (reason.contains("tool_inventory_full")) {
            return "Builder found the " + tool + ", but cannot fit it in job gear.";
        }
        if (reason.contains("missing_clear_tool")) {
            return limitDiagnostic(brain.targetPos() == null
                    ? "Builder needs a suitable " + tool + " to clear an obstructing block."
                    : "Builder needs a suitable " + tool + " to clear the block at "
                            + HiredWorkerBrain.formatPos(brain.targetPos()) + ".");
        }
        if (reason.contains("missing_placement_tool")) {
            return limitDiagnostic(brain.targetPos() == null
                    ? "Builder needs a suitable " + tool + " to prepare the next construction block."
                    : "Builder needs a suitable " + tool + " to prepare the construction block at "
                            + HiredWorkerBrain.formatPos(brain.targetPos()) + ".");
        }
        if (isBuilderRestockingBatch(brain, state) && !missing.isBlank()) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Builder is restocking the current construction batch. Still needs: " + missing + "."
                    : "Builder is restocking the current construction batch at "
                            + HiredWorkerBrain.formatPos(storagePos) + ". Still needs: " + missing + ".");
        }
        if (noStorage) {
            return limitDiagnostic(missing.isBlank()
                    ? "Builder needs assigned storage before it can restock missing construction blocks."
                    : "Builder needs assigned storage before it can restock: " + missing + ".");
        }
        if (inventoryFull) {
            return "Inventory is full and there is no output room for builder leftovers.";
        }

        if (reason.contains("blocked_entity")) {
            return "A mob, player, or villager is standing inside the next planned block space.";
        }
        if (reason.contains("blocked_existing")) {
            return "The next planned block space contains something the builder will not replace.";
        }
        if (reason.contains("blocked_support")) {
            return "The next planned block needs support before it can be placed.";
        }
        if (reason.contains("placement_failed")) {
            return "The next construction block failed to place. Check collision, support, or protection at the target.";
        }
        return "";
    }

    private static String miningDiagnostic(
            HiredWorkerBrain.Snapshot brain,
            boolean inventoryFull,
            boolean noStorage,
            boolean missingMaterials,
            boolean materialStorageUnreachable) {
        String reason = lower(brain.failureReason());
        if (reason.contains("hazard_fill_storage_unreachable")) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return storagePos == null
                    ? "Assigned storage contains sturdy hazard-control blocks, but the miner cannot path to it."
                    : "Assigned storage at " + HiredWorkerBrain.formatPos(storagePos)
                            + " contains hazard-control blocks, but the miner cannot path to it.";
        }
        if (materialStorageUnreachable) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Assigned storage contains excavation supports, but the miner cannot path to that container."
                    : "Assigned storage at " + HiredWorkerBrain.formatPos(storagePos)
                            + " contains excavation supports, but the miner cannot path to it.");
        }
        if (reason.contains("missing_hazard_fill_blocks")) {
            return "Miner needs sturdy non-falling blocks in job supplies or reachable assigned storage to contain lava, water, or a drop.";
        }
        if (reason.contains("hazard_fill_inventory_full")) {
            return "Miner has no inventory room for the sturdy blocks needed to contain a hazard.";
        }
        if (reason.contains("hazard_placement_unreachable") || reason.contains("hazard_chunk_unloaded")) {
            return "Miner found a dangerous excavation face but cannot reach a safe position to contain it.";
        }
        if (missingMaterials || reason.contains("missing_ladders")) {
            return "Miner needs ladders in job supplies or reachable assigned storage before digging lower excavation layers.";
        }
        if (reason.contains("tool_storage_unreachable")) {
            return "Miner cannot path to assigned tool storage for a suitable " + toolLabelFromFailure(reason, "pickaxe") + ".";
        }
        if (reason.contains("tool_inventory_full")) {
            return "Miner found the " + toolLabelFromFailure(reason, "pickaxe") + ", but cannot fit it in job gear.";
        }
        if (reason.contains("missing_")) {
            return "Miner needs a suitable " + toolLabelFromFailure(reason, "pickaxe") + " before mining can continue.";
        }
        if (inventoryFull) {
            return noStorage
                    ? "Miner inventory is full and no assigned output storage is available."
                    : "Miner inventory is full and it is trying to deposit mined output.";
        }
        return "";
    }

    private static String loggingDiagnostic(
            HiredWorkerBrain.Snapshot brain,
            HiredWorkContext context,
            boolean inventoryFull,
            boolean noStorage) {
        String reason = lower(brain.failureReason());
        String scan = lower(brain.lastTargetScanResult());
        String summary = LoggingWorker.debugSummary(context);
        if (reason.contains("missing_axe")) {
            return "Logger needs an axe before it can cut trees.";
        }
        if (reason.contains("tool_storage_unreachable")) {
            return "Logger cannot path to assigned tool storage for an axe.";
        }
        if (reason.contains("tool_inventory_full")) {
            return "Logger found an axe in storage, but its job inventory has no tool slot free.";
        }
        if (reason.contains("pending_tree_unreachable")) {
            return limitDiagnostic("Logger has a tree harvest queued but cannot path back to the remaining blocks. " + summary);
        }
        if (reason.contains("access_leaf_unreachable") || reason.contains("access_leaf_blocked") || reason.contains("leaf_blocked_target")) {
            return limitDiagnostic("Logger is clearing leaves toward a tree but cannot reach the next blocking leaf. " + summary);
        }
        if (reason.contains("target_changed")) {
            return "Logger's selected tree changed before it could finish; it will choose another target shortly.";
        }
        if (reason.contains("decay_drop_unreachable")) {
            return "Logger found tree drops on the ground but cannot path to them.";
        }
        if (inventoryFull) {
            return noStorage
                    ? "Logger inventory is full and no assigned output storage is available."
                    : "Logger inventory is full and it is trying to deposit timber.";
        }
        if (scan.contains("tree_access_leaf")) {
            return limitDiagnostic("Logger is looking for reachable leaves to clear a path to a tree. " + summary);
        }
        if (scan.contains("tree_scan_full_no_reachable_targets") || scan.contains("tree_scan_cooldown")) {
            return limitDiagnostic("Logger did not find a reachable valid tree in the assigned area. " + summary);
        }
        if (summary.contains("pending logs=")) {
            return limitDiagnostic(summary);
        }
        return "";
    }

    private static String farmingDiagnostic(HiredWorkerBrain.Snapshot brain) {
        String reason = lower(brain.failureReason());
        if (reason.contains("farming_hoe_storage_unreachable")) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return storagePos == null
                    ? "Farmer found a hoe in assigned tool storage, but cannot path to that container."
                    : "Farmer found a hoe in assigned tool storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.";
        }
        if (reason.contains("farming_hoe_inventory_full")) {
            return "Farmer found a hoe, but cannot fit it in job gear.";
        }
        if (reason.contains("missing_hoe")) {
            return "Farmer needs a hoe before field work can continue.";
        }
        return "";
    }

    private static String fishingDiagnostic(HiredWorkerBrain.Snapshot brain) {
        String reason = lower(brain.failureReason());
        if (reason.equals("fishing_preferences_invalid")) return "Replace the damaged fishing spot list or reset it to automatic discovery.";
        if (reason.equals("fishing_selected_unavailable")) return "Check the selected water for an open surface and a reachable dry shore inside the assigned work area.";
        if (reason.equals("fishing_spot_claimed")) return "Another fisher is using the selected water. Add another spot or wait for the current cast.";
        if (reason.contains("fishing_rod_storage_unreachable")) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return storagePos == null
                    ? "Fisher found a fishing rod in assigned tool storage, but cannot path to that container."
                    : "Fisher found a fishing rod in assigned tool storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.";
        }
        if (reason.contains("fishing_rod_inventory_full")) {
            return "Fisher found a fishing rod, but cannot fit it in job gear.";
        }
        if (reason.contains("missing_fishing_rod")) {
            return "Fisher needs a fishing rod before fishing can continue.";
        }
        return "";
    }

    private static String rangedAmmoDiagnostic(HiredWorkerBrain.Snapshot brain, HiredVillagerRole role) {
        if (role != HiredVillagerRole.COMBAT && role != HiredVillagerRole.HUNTING) {
            return "";
        }
        String reason = lower(brain.failureReason());
        String worker = role == HiredVillagerRole.HUNTING ? "Hunter" : "Guard";
        if (reason.contains(HiredRangedAmmo.FAILURE_STORAGE_PATH)) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return storagePos == null
                    ? worker + " found arrows in assigned storage, but cannot path to that container."
                    : worker + " found arrows in assigned storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.";
        }
        if (reason.contains(HiredRangedAmmo.FAILURE_INVENTORY_FULL)) {
            return worker + " found arrows, but cannot fit them in job supplies.";
        }
        if (reason.contains(HiredRangedAmmo.FAILURE_MISSING)) {
            return worker + " needs arrows before using a bow or crossbow for hired work.";
        }
        return "";
    }

    private static String huntingDiagnostic(HiredWorkerBrain.Snapshot brain) {
        String reason = lower(brain.failureReason());
        if (reason.contains("hunting_loot_unreachable")) {
            return "Hunter found loot from the hunt but cannot path to it.";
        }
        if (reason.contains("output_inventory_full")) {
            return "Hunter found hunting loot to collect, but its job output is full.";
        }
        if (reason.contains("tool_storage_unreachable_hunting_weapon")) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return storagePos == null
                    ? "Hunter found a bow, crossbow, axe, or sword in assigned tool storage, but cannot path to that container."
                    : "Hunter found a bow, crossbow, axe, or sword in assigned tool storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.";
        }
        if (reason.contains("tool_inventory_full_hunting_weapon")) {
            return "Hunter found a bow, crossbow, axe, or sword, but cannot fit it in job gear.";
        }
        if (reason.contains("hunting_arrow_storage_path_failed")) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return storagePos == null
                    ? "Hunter found arrows in assigned storage, but cannot path to that container."
                    : "Hunter found arrows in assigned storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.";
        }
        if (reason.contains("hunting_arrow_inventory_full")) {
            return "Hunter found arrows, but cannot fit them in job supplies.";
        }
        if (reason.contains("missing_hunting_arrows")) {
            return "Hunter needs arrows before hunting with a bow or crossbow.";
        }
        if (reason.contains("missing_hunting_weapon")) {
            return "Hunter needs a bow, crossbow, axe, or sword before hunting can continue.";
        }
        return "";
    }

    private static String brewingDiagnostic(
            HiredWorkerBrain.Snapshot brain,
            CompoundTag state,
            boolean inventoryFull,
            boolean noStorage,
            boolean missingMaterials,
            boolean materialStorageUnreachable,
            boolean materialInventoryFull) {
        String reason = lower(brain.failureReason() + " " + BrewingWorker.blockedReason(state));
        String missing = BrewingWorker.missingMaterials(state);
        if (materialInventoryFull) {
            return limitDiagnostic(missing == null || missing.isBlank()
                    ? "Brewer needs room in the job inventory before it can carry brewing materials."
                    : "Brewer needs room in the job inventory for: " + missing + ".");
        }
        if (materialStorageUnreachable) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Brewer found required materials in assigned input storage, but cannot path to that container."
                    : "Brewer found required materials in assigned input storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.");
        }
        if (missingMaterials) {
            return limitDiagnostic(missing == null || missing.isBlank()
                    ? "Brewer is missing materials for the current potion order."
                    : "Brewer is missing brewing materials: " + missing + ".");
        }
        if (noStorage && reason.contains("missing_brewing")) {
            return "Brewer needs assigned input storage or carried supplies for the current potion order.";
        }
        if (reason.contains("no_brewing_stand")) {
            return "Brewer needs a brewing stand inside the assigned work area.";
        }
        if (reason.contains("brewing_stand_path_failed") || reason.contains("brewing_stand_unreachable")) {
            return "Brewer cannot path to the brewing stand inside the assigned work area.";
        }
        if (reason.contains("brewing_water_source_path_failed") || reason.contains("brewing_water_source_unreachable")) {
            return "Brewer cannot path to the water source for filling bottles.";
        }
        if (reason.contains("brewing_stand_wrong_ingredient")) {
            return "Brewing stand has an ingredient that does not match the active order.";
        }
        if (reason.contains("brewing_stand_wrong_fuel")) {
            return "Brewing stand fuel slot has something other than blaze powder.";
        }
        if (reason.contains("brewing_stand_blocked")) {
            return "Brewing stand bottle slots contain potions from another recipe.";
        }
        if (inventoryFull) {
            return "Brewer inventory is full and output storage cannot take more items right now.";
        }
        return "";
    }

    private static String cookingDiagnostic(
            HiredWorkerBrain.Snapshot brain,
            boolean inventoryFull,
            boolean noStorage,
            boolean missingMaterials,
            boolean materialStorageUnreachable,
            boolean materialInventoryFull) {
        String reason = lower(brain.failureReason());
        if (materialInventoryFull) {
            return "Cook found food or fuel in assigned storage, but the job inventory has no room for it.";
        }
        if (materialStorageUnreachable) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Cook found food or fuel in assigned input storage, but cannot path to that container."
                    : "Cook found food or fuel in assigned input storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.");
        }
        if (missingMaterials) {
            if (reason.contains("missing_cooking_fuel")) {
                return "Cook needs furnace fuel in job supplies or assigned input storage.";
            }
            if (reason.contains("missing_cooking_crafting_materials")) {
                return "Cook needs recipe ingredients for the filtered food in job supplies or assigned input storage.";
            }
            return "Cook needs raw food in job supplies or assigned input storage.";
        }
        if (noStorage && (reason.contains("missing_cooking_raw_food")
                || reason.contains("missing_cooking_fuel")
                || reason.contains("missing_cooking_crafting_materials"))) {
            return "Cook needs assigned input storage or carried supplies for food and fuel.";
        }
        if (reason.contains("no_cooking_crafting_table")) {
            return "Cook needs a crafting table inside the assigned work area for the filtered food recipe.";
        }
        if (reason.contains("cooking_crafting_table_unreachable")) {
            return "Cook cannot path to the crafting table inside the assigned work area.";
        }
        if (reason.contains("no_cooking_station")) {
            return "Cook needs a furnace or smoker inside the assigned work area.";
        }
        if (reason.contains("cooking_station_path_failed") || reason.contains("cooking_station_unreachable")) {
            return "Cook cannot path to the furnace or smoker inside the assigned work area.";
        }
        if (reason.contains("cooking_wrong_output")) {
            return "Cooking station output slot contains something that is not food.";
        }
        if (reason.contains("cooking_wrong_input")) {
            return "Cooking station input slot contains something the cook cannot cook into food.";
        }
        if (reason.contains("cooking_wrong_fuel")) {
            return "Cooking station fuel slot contains something that will not burn.";
        }
        if (inventoryFull) {
            return "Cook inventory is full and output storage cannot take more cooked food right now.";
        }
        return "";
    }

    private static String smeltingDiagnostic(
            HiredWorkerBrain.Snapshot brain,
            boolean inventoryFull,
            boolean noStorage,
            boolean missingMaterials,
            boolean materialStorageUnreachable,
            boolean materialInventoryFull) {
        String reason = lower(brain.failureReason());
        if (materialInventoryFull) {
            return "Smelter found raw ore or fuel in assigned storage, but the job inventory has no room for it.";
        }
        if (materialStorageUnreachable) {
            BlockPos storagePos = diagnosticStoragePos(brain);
            return limitDiagnostic(storagePos == null
                    ? "Smelter found raw ore or fuel in assigned input storage, but cannot path to that container."
                    : "Smelter found raw ore or fuel in assigned input storage at "
                            + HiredWorkerBrain.formatPos(storagePos) + ", but cannot path to it.");
        }
        if (missingMaterials) {
            return reason.contains("missing_smelting_fuel")
                    ? "Smelter needs furnace fuel in job supplies or assigned input storage."
                    : "Smelter needs input for a non-food recipe supported by this station and the assigned filter. Supply sand for glass or cobblestone for stone to a normal furnace.";
        }
        if (noStorage && (reason.contains("missing_smelting_raw_ore") || reason.contains("missing_smelting_fuel"))) {
            return "Smelter needs assigned input storage or carried supplies for a compatible non-food recipe and fuel.";
        }
        if (reason.contains("no_smelting_station")) {
            return "Smelter needs a furnace or blast furnace inside the assigned work area.";
        }
        if (reason.contains("smelting_station_path_failed") || reason.contains("smelting_station_unreachable")) {
            return "Smelter cannot path to the furnace or blast furnace inside the assigned work area.";
        }
        if (reason.contains("smelting_wrong_output")) {
            return "Smelting output must match an available non-food furnace recipe and the assigned filter; food belongs to Cook.";
        }
        if (reason.contains("smelting_wrong_input")) {
            return "This input has no allowed non-food recipe for this station. Glass and stone require a normal furnace; food belongs to Cook.";
        }
        if (reason.contains("smelting_wrong_fuel")) {
            return "Smelting station fuel slot contains something that will not burn.";
        }
        if (inventoryFull) {
            return "Smelter inventory is full and output storage cannot take more smelted products right now.";
        }
        return "";
    }

    private static String courierDiagnostic(
            HiredWorkerBrain.Snapshot brain,
            boolean inventoryFull,
            boolean noStorage) {
        String reason = lower(brain.failureReason());
        if (reason.startsWith("transfer_")) return switch (reason) {
            case "transfer_supply_or_capacity" -> "Supply the selected exact item and free space in assigned output storage.";
            case "transfer_destination_full" -> "Free space or adjust the destination's framed filters.";
            case "transfer_storage_changed" -> "Restore the selected containers and the courier's assigned input and output storage.";
            case "transfer_storage_unloaded", "transfer_storage_unobserved" -> "Load the courier's selected storage before continuing the delivery.";
            case "transfer_unreachable" -> "Clear a safe route to the selected transfer storage.";
            case "transfer_cargo_missing" -> "Restore the collected cargo to this courier's output inventory.";
            case "transfer_worker_changed", "transfer_contract_changed", "transfer_ownership_changed", "transfer_role_changed" ->
                    "Review the courier's personal contract and the owner of its recorded delivery.";
            case "transfer_queue_invalid" -> "Recover or cancel the damaged personal transfer queue.";
            default -> "";
        };
        if (reason.contains("courier_missing_route")) {
            return "Courier needs an assigned route before deliveries can begin.";
        }
        if (reason.contains("courier_missing_input_storage")) {
            return "Courier needs at least one assigned input container.";
        }
        if (reason.contains("courier_missing_output_storage")) {
            return "Courier needs at least one loaded assigned output container.";
        }
        if (reason.contains("courier_output_unavailable")) {
            return "No assigned output container can accept the courier's remaining cargo right now.";
        }
        if (reason.contains("courier_input_empty")) {
            return "Courier is waiting for items in assigned input storage.";
        }
        if (reason.contains("courier_input_unreachable")) {
            return "Courier cannot reach the selected input container.";
        }
        if (reason.contains("courier_output_unreachable")) {
            return "Courier cannot reach the selected output container.";
        }
        if (reason.contains("courier_route_unreachable")) {
            return "Courier cannot reach the next node on the assigned route.";
        }
        if (reason.contains("courier_output_full")) {
            return "Courier's assigned output storage is full.";
        }
        if (noStorage) {
            return "Courier needs assigned input and output storage.";
        }
        if (inventoryFull) {
            return "Courier inventory is full and the delivery cannot be deposited.";
        }
        return "";
    }

    private static String limitDiagnostic(String diagnostic) {
        if (diagnostic == null || diagnostic.length() <= 180) {
            return diagnostic == null ? "" : diagnostic;
        }
        return diagnostic.substring(0, 177) + "...";
    }

    private static String missingMaterialStorageWaitDiagnostic(HiredWorkerBrain.Snapshot brain) {
        BlockPos storagePos = diagnosticStoragePos(brain);
        if (storagePos == null) {
            return "The builder also cannot reach assigned storage to wait for delivery.";
        }
        return "The builder also cannot reach assigned storage at "
                + HiredWorkerBrain.formatPos(storagePos)
                + " to wait for delivery.";
    }

    private static BlockPos diagnosticStoragePos(HiredWorkerBrain.Snapshot brain) {
        return brain.storageTargetPos() == null ? brain.targetPos() : brain.storageTargetPos();
    }

    private static boolean isBuilderRestockingBatch(HiredWorkerBrain.Snapshot brain, CompoundTag state) {
        return BuilderTaskState.phase(state) == BuilderBuildPhase.COLLECTING_MATERIALS
                && (brain.taskState() == HiredWorkerTaskState.MOVING_TO_STORAGE
                || brain.taskState() == HiredWorkerTaskState.WAITING_FOR_MATERIALS);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String toolLabelFromFailure(String reason, String fallback) {
        String safeReason = lower(reason);
        if (safeReason.contains("fishing_rod")) {
            return "fishing rod";
        }
        if (safeReason.contains("pickaxe")) {
            return "pickaxe";
        }
        if (safeReason.contains("shovel")) {
            return "shovel";
        }
        if (safeReason.contains("axe")) {
            return "axe";
        }
        if (safeReason.contains("hoe")) {
            return "hoe";
        }
        if (safeReason.contains("hunting_weapon")) {
            return "bow, crossbow, axe, or sword";
        }
        return fallback == null || fallback.isBlank() ? "tool" : fallback;
    }

}
