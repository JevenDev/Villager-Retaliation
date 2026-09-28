package com.jvn.villagerretaliation.settlement;

final class SettlementWorkerDiagnostics {
    private SettlementWorkerDiagnostics() {
    }

    static String describe(String status) {
        return switch (status) {
            case "missing_hoe" ->
                    "Farmer needs a hoe in job gear or a supply depot.";
            case "missing_rod" ->
                    "Fisher needs a fishing rod in job gear or a supply depot.";
            case "missing_axe" ->
                    "Worker needs an axe in job gear or a supply depot.";
            case "missing_leaf_tool" ->
                    "Logger needs shears or a Silk Touch hoe for the selected leaf work.";
            case "missing_pickaxe" ->
                    "Miner needs a suitable pickaxe in job gear or a supply depot.";
            case "missing_tool" ->
                    "Provide a suitable mining tool in job gear or a supply depot.";
            case "missing_shovel" ->
                    "Miner needs a suitable shovel in job gear or a supply depot.";
            case "missing_shears" ->
                    "Animal handler needs shears in job gear or a supply depot.";
            case "missing_bucket" ->
                    "Animal handler needs an empty bucket in job supplies or a supply depot.";
            case "missing_hunting_weapon", "missing_cull_weapon" ->
                    "Provide a suitable weapon in job gear or a supply depot.";
            case "missing_ranged_ammo" ->
                    "Provide ammunition for the equipped ranged weapon in job supplies or a supply depot.";
            case "tree_needs_native_growth" ->
                    "Visit the sapling so this tree can grow in the loaded world.";
            case "sapling_blocked" ->
                    "Check the sapling's soil and clear space for tree growth.";
            case "missing_sapling" ->
                    "Supply a sapling matching the tree being replanted.";
            case "missing_breeding_food" ->
                    "Supply breeding food for the selected animals.";
            case "missing_materials", "missing_construction_materials" ->
                    "Required materials are missing. Check job supplies, depot stock, and depot filters.";
            case "missing_placement_tool", "missing_clear_tool" ->
                    "Builder needs a suitable tool for the next construction block.";
            case "missing_safe_fill", "missing_backing", "missing_ladders" ->
                    "Supply suitable blocks for safe mining access and support.";
            case "no_filter" ->
                    "Choose the recipes to produce with a filter in the worker's job inventory.";
            case "choose_brewing_order" ->
                    "Choose a potion order in the resident's brewing settings.";
            case "no_crafting_table" ->
                    "Worker needs a crafting table inside the assigned work area.";
            case "no_furnace" ->
                    "Worker needs a suitable furnace inside the assigned work area.";
            case "no_brewing_stand" ->
                    "Brewer needs a brewing stand inside the assigned work area.";
            case "output_full" ->
                    "Free output slots in the job inventory or provide a depot that accepts the output.";
            case "supply_full", "support_inventory_full", "fill_supply_full" ->
                    "Free supply slots in the job inventory for the next work batch.";
            case "storage_full" ->
                    "Provide depot space and check that depot filters accept the carried output.";
            case "awaiting_worker_observation" ->
                    "Visit this worker to enable background work.";
            case "no_observed_field", "no_observed_fishery", "no_observed_forest", "no_observed_mine",
                    "construction_needs_observation", "excavation_needs_observation", "tree_needs_observation" ->
                    "Visit the assigned work area so its terrain and access can be observed.";
            case "forest_conflict", "mine_conflict", "construction_conflict" ->
                    "Terrain changed since the last observation. Visit the work area and review the pending work.";
            case "no_safe_mining_route", "floor_unreachable", "mining_support_blocked", "no_excavation_shaft",
                    "animal_unreachable", "animal_drop_unreachable", "hunting_path_blocked",
                    "hunting_loot_unreachable", "construction_unreachable", "mining_support_unreachable",
                    "hazard_unreachable", "excavation_blocked" ->
                    "Clear a safe route to the work target and visit the area to refresh observed access.";
            case "animal_cull_failed", "hunting_combat_unavailable" ->
                    "Visit the worker and target to check what is preventing the attack.";
            case "hunting_storage_unreachable" ->
                    "Clear a route to a suitable depot and visit it to refresh observed access.";
            case "hunting_visibility_unobserved", "hunting_projectile_needs_world" ->
                    "Visit the hunting area so the target and projectile surroundings can be observed.";
            case "construction_outside_world", "construction_plan_changed" ->
                    "Review or cancel the construction project before work can continue.";
            case "construction_blocked_existing" ->
                    "Clear the blocks obstructing the construction project.";
            case "construction_blocked_support" ->
                    "Provide support beneath the next construction block.";
            case "unsafe", "construction_unsafe" ->
                    "Visit the worker and resolve the danger before work resumes.";
            default -> "";
        };
    }
}
