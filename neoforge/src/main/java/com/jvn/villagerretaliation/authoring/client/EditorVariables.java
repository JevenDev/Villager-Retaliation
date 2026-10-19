package com.jvn.villagerretaliation.authoring.client;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EditorVariables {
    record Variable(String key, String label) {
        String token() { return "{" + key + "}"; }
    }
    record Group(String title, String availability, List<Variable> variables) { }

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("player", "Player name"), Map.entry("villager", "Villager name"),
            Map.entry("villager_name", "Villager name (alias)"), Map.entry("villager_possessive", "Villager's name (possessive)"),
            Map.entry("time", "World clock (HH:mm)"), Map.entry("time_of_day", "Time of day"), Map.entry("day", "World day number"),
            Map.entry("x", "Villager or event X"), Map.entry("y", "Villager or event Y"), Map.entry("z", "Villager or event Z"),
            Map.entry("quest", "Quest title"), Map.entry("quest_id", "Quest resource ID"),
            Map.entry("objective_progress", "Objective progress (fraction)"),
            Map.entry("objective_progress_count", "Objective progress (count)"),
            Map.entry("fiance", "Fiance / fiancee"), Map.entry("aunt_uncle", "Aunt or uncle"),
            Map.entry("niece_nephew", "Niece or nephew"), Map.entry("day_or_days", "Day / days word"),
            Map.entry("item_or_items", "Item / items word"), Map.entry("plural", "Plural suffix"),
            Map.entry("player_item_durability_percent", "Player item durability (%)"),
            Map.entry("held_item_durability_percent", "Held item durability (%)"),
            Map.entry("completed_time", "Completion game tick"), Map.entry("started_time", "Starting game tick"));

    private static final List<Group> GROUPS = List.of(
            group("Player and villager", "Names supplied by the current dialogue context.", """
                    player villager
                    """),
            group("World and time", "World time and day. Coordinates describe the villager, or the event location in triggered dialogue.", """
                    time time_of_day day x y z
                    """),
            group("Villager details", "Standard dialogue and conversations with a villager. Triggered dialogue needs these supplied as extra values.", """
                    villager_name villager_possessive profession reputation reputation_level
                    """),
            group("Player history", "The player's most recent recorded death. Values are empty before the first recorded death.", """
                    last_death_message last_death_killer last_death_dimension
                    """),
            group("Relationships", "Normal dialogue pool wording that reads the villager's relationships. Standalone trees do not supply these automatically.", """
                    partner partner_possessive crush crush_possessive dating_partner dating_partner_possessive
                    fiance fiance_possessive romantic_spouse romantic_spouse_possessive ex_partner ex_partner_possessive
                    late_partner late_partner_possessive
                    """),
            group("Family", "Normal dialogue pool wording that reads the villager's family tree. Standalone trees do not supply these automatically.", """
                    parent parent_possessive sibling sibling_possessive spouse spouse_possessive child child_possessive
                    grandparent grandparent_possessive ancestor ancestor_possessive grandchild grandchild_possessive
                    descendant descendant_possessive aunt_uncle aunt_uncle_possessive cousin cousin_possessive
                    niece_nephew niece_nephew_possessive deceased_family deceased_family_possessive
                    extended_relative extended_relative_possessive relative relative_possessive
                    """),
            group("Conversation memories", "Normal dialogue pool wording for meeting again, recruitment, curing, and remembered attacks.", """
                    days_since_seen day_or_days days_since_seen_phrase follow_biome follow_distance cured_villager
                    cured_villager_possessive attack_weapon gear_kind home_village current_village
                    """),
            group("Quests and stages", "Quest dialogue and journal text, or text after the corresponding quest action. History and branch fields require that context.", """
                    quest quest_id issuer issuer_name issuer_profession issuer_dimension issuer_location issuer_status
                    issuer_x issuer_y issuer_z quest_stage current_stage visited_target has_proof active_conditions objective
                    proof_item completion_count completed_time started_time parent_quest parent_quest_id
                    branch branch_id branch_label branch_stage branch_next_stage quest_action_status
                    quest_transition_status quest_transition_scene quest_transition_response quest_transition_prior_stage
                    quest_transition_next_stage quest_transition_source reward_id
                    """),
            group("Quest objectives", "Values from the current quest objective. Fields depend on its type. Journal-only fields include objective target coordinates.", """
                    objective_id objective_type objective_item objective_item_id objective_count objective_progress_count
                    objective_entity objective_block objective_block_id objective_memory objective_memory_id objective_gift_reaction
                    objective_reputation objective_reputation_level objective_reputation_min objective_reputation_max
                    objective_choice objective_choice_key objective_choice_value objective_fact objective_fact_id objective_fact_key
                    objective_fact_value objective_fact_scope objective_radius objective_complete objective_progress
                    objective_target_x objective_target_y objective_target_z objective_target_dimension
                    """),
            group("Quest facts", "Text after a quest fact action. The action supplies its scope, key, value, or counter.", """
                    quest_fact_scope quest_fact_scope_key quest_fact_tag quest_fact_key quest_fact_value quest_fact_counter
                    """),
            group("Locations and encounters", "Quest navigation, story hints, or encounter guidance. Destination name and target Y are supplied by journal navigation.", """
                    target target_article target_name target_kind target_type target_x target_y target_z target_dimension
                    destination_name direction vague_direction distance vertical dimension coordinates
                    encounter_variant encounter_template encounter_distance encounter_direction encounter_coordinates encounter_dimension
                    encounter_discovered encounter_arrived
                    """),
            group("Player items and enchantments", "Dialogue with a matching player-item condition or item-proximity trigger. Values describe the matched item and enchantment. Count fields require the proximity trigger.", """
                    player_item player_item_id player_item_slot player_item_count player_item_durability player_item_max_durability
                    player_item_damage player_item_durability_percent player_item_enchantment player_item_enchantment_full
                    player_item_enchantment_id player_item_enchantment_level held_item held_item_id held_item_slot held_item_count
                    held_item_durability held_item_max_durability held_item_damage held_item_durability_percent held_item_enchantment
                    held_item_enchantment_full held_item_enchantment_id held_item_enchantment_level
                    """),
            group("Gifts and payments", "Gift, advice, payment, or item-transfer text. Values require the matching interaction or action.", """
                    gift_item gift_item_id gift_subject alternative_gift tested_villager owner_villager given_count given_item_count
                    given_item given_item_id given_stack given_items payment_item payment_item_id payment_stack payment_count
                    payment_items payment_cost
                    """),
            group("Trades and special orders", "Trade, restock, special-order, or item-proximity trade dialogue. Each interaction supplies its own result fields.", """
                    trade_item trade_definition trade_count trade_word trade_items restocked_summary trade_cost_item trade_cost
                    trade_cost_count trade_result trade_result_stack trade_item_stack trade_result_count trade_offer_index
                    active_orders active_order_word max_order_count_word max_order_word max_orders cooldown_days cooldown_day_word
                    wait_days wait_day_word extra_cost offer_slot days_remaining emerald_cost emeralds
                    """),
            group("Theft and retaliation", "Triggered theft or retaliation dialogue and matching normal dialogue pools. Witness and vouching fields require those events.", """
                    container loot_table prior_container_thefts container_theft_offense prior_retaliations retaliation_offense
                    stolen_item stolen_item_id stolen_count stolen_item_count stolen_stack stolen_items stolen_container stolen_loot_table
                    stolen_count_word stolen_item_reference stolen_item_pronoun witnessed_container_thefts
                    container_theft_again_phrase container_theft_time_word container_loot_table theft_witness theft_witness_possessive
                    retaliation_target retaliation_target_name retaliation_target_kind retaliation_target_type retaliation_witness
                    retaliation_witness_possessive voucher vouching_villager vouch_chance vouch_outcome original_villager
                    victim victim_name victim_profession
                    """),
            group("Speakers and interruptions", "Multi-villager interjections and interruption events. Indices and ordinals refer to their current speaker.", """
                    interrupted_villager previous_villager current_villager interjection_index interjection_ordinal speaker_index speaker_ordinal
                    """),
            group("Jobs and construction", "Hire, work-status, clipboard, storage, route, and construction interactions. Values depend on the active job or command.", """
                    role available_roles contract_cost auto_payment activity status_detail work_area efficiency completed_tasks
                    completed_tasks_phrase completed_tasks_plural progress_summary output_summary structure blueprint_cost blocks
                    materials missing site storage_radius radius max dimensions bounds mode filter till_soil option state cap
                    shearing nodes loop range purpose targets tool logs crop placed total types
                    """),
            group("Interaction results", "Values supplied by the matching interaction, notification, or job result. These names are reused with context-specific meanings.", """
                    item item_id count item_count item_stack items item_or_items amount signed_amount cost currency remaining
                    time_remaining new_time_remaining refund_amount overflow_count deposited order plural skill level study_cooldown
                    reason mount
                    """),
            group("Duels and wagers", "Duel challenge and result text.", """
                    villager_wins villager_losses maximum_wager
                    """),
            group("Campaign transactions", "Conversation text after the matching campaign transaction or bargaining receipt.", """
                    campaign_transaction campaign_transaction_title campaign_transaction_buyer campaign_transaction_price campaign_transaction_item
                    """),
            group("Memory events", "Village memory event actions. Coordinates and dimension describe that event.", """
                    memory memory_tag event event_x event_y event_z event_dimension villager_profession memory_event
                    """),
            group("Quest trigger events", "Only quest trigger action text. Payload fields are prefixed with trigger_. Custom criterion data uses trigger_criterion_<key>.", """
                    trigger_event trigger_stage trigger_game_time trigger_mob trigger_entity trigger_block trigger_block_x trigger_block_y
                    trigger_block_z trigger_item trigger_item_count trigger_gift_reaction trigger_event_villager trigger_event_villager_type
                    trigger_trade_cost_a trigger_trade_cost_a_count trigger_trade_cost_b trigger_trade_cost_b_count
                    trigger_trade_result trigger_trade_result_count trigger_reputation trigger_criterion trigger_memory_tag
                    """),
            group("Paired player greetings", "Player greeting templates only. Their time variable means the day phase rather than the HH:mm clock.", """
                    day_phase time_greeting
                    """));

    private EditorVariables() { }

    static List<Group> groups() { return GROUPS; }

    static List<Group> matching(String query) {
        String normalized = query.toLowerCase(Locale.ROOT).replace('{', ' ').replace('}', ' ').strip();
        if (normalized.isEmpty()) return GROUPS;
        List<String> terms = Arrays.stream(normalized.split("\\s+"))
                .filter(term -> !term.isBlank()).toList();
        return GROUPS.stream().map(group -> new Group(group.title(), group.availability(), group.variables().stream()
                .filter(variable -> {
                    String text = (group.title() + " " + variable.label() + " " + variable.key()).toLowerCase(Locale.ROOT);
                    return terms.stream().allMatch(text::contains);
                }).sorted(java.util.Comparator.comparingInt(variable -> variable.key().equals(normalized) ? 0 : 1)).toList()))
                .filter(group -> !group.variables().isEmpty())
                .sorted(java.util.Comparator.comparingInt(group -> group.variables().stream().anyMatch(variable -> variable.key().equals(normalized)) ? 0 : 1)).toList();
    }

    static String customKey(String value) {
        String key = value.strip();
        if (key.startsWith("{") && key.endsWith("}")) key = key.substring(1, key.length() - 1).strip();
        return key.matches("[a-zA-Z_][a-zA-Z0-9_.:-]*") ? key : "";
    }

    private static Group group(String title, String availability, String keys) {
        return new Group(title, availability, Arrays.stream(keys.strip().split("\\s+")).map(key -> new Variable(key,
                LABELS.getOrDefault(key, Character.toUpperCase(key.charAt(0)) + key.substring(1).replace('_', ' ')))).toList());
    }
}
