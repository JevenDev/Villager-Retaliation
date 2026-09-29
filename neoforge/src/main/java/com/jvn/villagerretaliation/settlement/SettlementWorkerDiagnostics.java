package com.jvn.villagerretaliation.settlement;

public final class SettlementWorkerDiagnostics {
    private SettlementWorkerDiagnostics() {
    }

    public enum Severity {
        NONE, INFO, OBSERVATION, WARNING, DANGER;

        public boolean attention() { return this == OBSERVATION || this == WARNING || this == DANGER; }
    }

    public record Diagnostic(Severity severity, String recovery) { }

    public static Diagnostic diagnostic(String status) {
        String recovery = describe(status);
        Severity severity = recovery.isEmpty() ? Severity.NONE
                : status.equals("unsafe") || status.equals("construction_unsafe") || status.equals("resident_injured") ? Severity.DANGER
                : status.contains("observation") || status.contains("observed") || status.endsWith("needs_world")
                        || status.equals("tree_needs_native_growth") || status.equals("project.site_unloaded")
                        || status.equals("project.pending_world") ? Severity.OBSERVATION
                : status.startsWith("simulation_") ? Severity.INFO : Severity.WARNING;
        return new Diagnostic(severity, recovery);
    }

    public static String describe(String status) {
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
            case "no_observed_hunting_target", "no_observed_field", "no_observed_fishery", "no_observed_forest", "no_observed_mine",
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
            case "needs_attention" ->
                    "Inspect this worker's current task and job settings for the reported blocker.";
            case "profession_required" ->
                    "Assign a resident with the profession required by this job.";
            case "workforce.output_backpressure", "workforce.no_storage", "workforce.storage_full" ->
                    "Provide an accessible depot that accepts the worker's output.";
            case "workforce.inventory_full", "workforce.material_inventory_full" ->
                    "Free the required slots in the worker's job inventory.";
            case "workforce.no_work_area" ->
                    "Choose a valid work area for this resident.";
            case "workforce.missing_tools" ->
                    "Provide the tools required by this job in job gear or an accessible supply depot.";
            case "workforce.missing_materials" ->
                    "Supply the materials required by the current job.";
            case "workforce.too_far", "workforce.material_storage_unreachable", "workforce.build_site_unreachable" ->
                    "Check the assigned area and clear a safe route between the worker, supplies and work site.";
            case "workforce.unpaid" ->
                    "Review the worker's contract and outstanding payment.";
            case "project.world_conflict" ->
                    "World blocks conflict with pending construction. Visit the site and resolve the changed blocks.";
            case "project.pending_world", "project.site_unloaded" ->
                    "Visit the project site to apply or observe pending world work.";
            case "project.missing_materials" ->
                    "Supply the materials listed by the project in accessible job supplies or depots.";
            case "project.builder_material_storage_unreachable", "project.tool_storage_unreachable", "project.path_blocked", "project.site_unreachable" ->
                    "Clear a safe route to the project site.";
            case "project.blocked_existing" ->
                    "Clear the blocks obstructing the project.";
            case "project.blocked_support" ->
                    "Provide support beneath the next project block.";
            case "project.blocked_entity" ->
                    "Move entities out of the next construction block and let the builder resume.";
            case "project.tool_inventory_full", "project.output_inventory_full", "project.material_inventory_full" ->
                    "Free the required job inventory slots.";
            case "project.missing_clear_tool", "project.missing_placement_tool" ->
                    "Supply a suitable tool in builder job gear or an accessible supply depot.";
            case "project.missing_structure" ->
                    "Restore the project's prefab data or cancel the project.";
            case "project.site_outside_area" ->
                    "Review the project and construction area so the complete site lies inside its assigned bounds.";
            case "project.placement_failed" ->
                    "Visit the next placement and check its support, clearance and block placement rules.";
            case "project.plan_changed", "project.outside_world" ->
                    "Review or cancel the project because its plan or location is no longer valid.";
            case "trade.stage_locked" ->
                    "Trading requires village stage or higher.";
            case "trade.no_staff" ->
                    "Assign an eligible clerk to an enabled trading post with its required fixture.";
            case "trade.unavailable" ->
                    "Check that this item is supported by the market and available in the correct depot.";
            case "trade.price_limit" ->
                    "Review the order's price limit against the market price.";
            case "trade.no_space" ->
                    "Make room in a depot that accepts the ordered goods.";
            case "trade.budget_limit" ->
                    "Review treasury funds, reserve and the order and settlement daily budgets.";
            case "resident_hungry" ->
                    "Supply food in an accessible settlement depot and check the resident's access to it.";
            case "resident_injured" ->
                    "Visit this resident, resolve nearby danger and provide recovery food.";
            case "simulation_disabled" ->
                    "Background settlement simulation is disabled by the server's Background Settlement Limit setting.";
            case "simulation_capped" ->
                    "This settlement is outside today's background simulation allocation. Towns rotate each game day.";
            case "simulation_suspended" ->
                    "Resolve this settlement's territory conflict before background simulation can resume.";
            default -> status.startsWith("project.") ? "Review the project's reported blocker, supplies and access before continuing."
                    : status.startsWith("missing_") ? "Supply the missing requirement in job supplies or an accessible depot." : "";

        };
    }
}
