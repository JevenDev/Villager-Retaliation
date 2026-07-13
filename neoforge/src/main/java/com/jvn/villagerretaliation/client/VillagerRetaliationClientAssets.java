package com.jvn.villagerretaliation.client;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import net.minecraft.resources.ResourceLocation;

public final class VillagerRetaliationClientAssets {
    public static final ResourceLocation VANILLA_VILLAGER_SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    public static final ResourceLocation COMBAT_VILLAGER_SKIN =
            texture("entity/villager/villager");
    public static final ResourceLocation VANILLA_TRADER_SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/wandering_trader.png");
    public static final ResourceLocation COMBAT_TRADER_SKIN =
            texture("entity/wandering_trader/wandering_trader");
    public static final ResourceLocation VILLAGER_TRADE_EXTENDED_TEXTURE =
            texture("gui/trade/villager_extended");

    public static final ResourceLocation GIFT_INVENTORY_TEXTURE =
            texture("gui/villager_interaction_screen/gift_inventory");
    public static final ResourceLocation GIFT_INFO_ICON_TEXTURE =
            texture("gui/villager_interaction_screen/info_icon");
    public static final ResourceLocation VILLAGER_INVENTORY_TEXTURE =
            texture("gui/villager_interaction_screen/villager_inventory");
    public static final ResourceLocation VILLAGER_JOB_INVENTORY_TEXTURE =
            texture("gui/villager_interaction_screen/villager_job_inventory");
    public static final ResourceLocation VILLAGER_PARTY_INVENTORY_TEXTURE =
            texture("gui/villager_interaction_screen/villager_party_inventory");
    public static final ResourceLocation VILLAGER_INVENTORY_NAMEPLATE_TEXTURE =
            texture("gui/villager_interaction_screen/villager_inventory_container_nameplate");
    public static final ResourceLocation VILLAGER_INVENTORY_BUTTON_LEFT_TEXTURE =
            texture("gui/villager_interaction_screen/villager_inventory_container_button_left");
    public static final ResourceLocation VILLAGER_INVENTORY_BUTTON_RIGHT_TEXTURE =
            texture("gui/villager_interaction_screen/villager_inventory_container_button_right");
    public static final ResourceLocation VILLAGER_INVENTORY_PARTY_ICON_TEXTURE =
            texture("gui/villager_interaction_screen/villager_inventory_container_party_icon");
    public static final ResourceLocation ITEM_FILTER_CONTAINER_TEXTURE =
            texture("gui/item_filter/filter_container");
    public static final ResourceLocation INTERACTION_CONTAINER_TEXTURE =
            texture("gui/villager_interaction_screen/interaction_container");
    public static final ResourceLocation INTERACTION_BUTTON_TEXTURE =
            texture("gui/villager_interaction_screen/interaction_button");
    public static final ResourceLocation INTERACTION_CONTAINER_OPTION_TEXTURE =
            texture("gui/villager_interaction_screen/interaction_container_option");
    public static final ResourceLocation INTERACTION_CONTAINER_NAMEPLATE_TEXTURE =
            texture("gui/villager_interaction_screen/interaction_container_nameplate");
    public static final ResourceLocation INTERACTION_CONTAINER_SKILLS_CONTAINER_TEXTURE =
            texture("gui/villager_interaction_screen/interaction_container_skills_container");
    public static final ResourceLocation INTERACTION_CONTAINER_SKILLS_DIALOGUE_CONTAINER_TEXTURE =
            texture("gui/villager_interaction_screen/interaction_container_skills_dialogue_container");
    public static final ResourceLocation INTERACTION_CONTAINER_SKILLS_DIALOGUE_BUTTON_LEFT_TEXTURE =
            texture("gui/villager_interaction_screen/interaction_container_skills_dialogue_container_button_left");
    public static final ResourceLocation INTERACTION_CONTAINER_SKILLS_DIALOGUE_BUTTON_RIGHT_TEXTURE =
            texture("gui/villager_interaction_screen/interaction_container_skills_dialogue_container_button_right");
    public static final ResourceLocation INTERACTION_CONTAINER_PROFILE_CONTAINER_TEXTURE =
            texture("gui/villager_interaction_screen/interaction_container_profile_container");
    public static final ResourceLocation INTERACTION_CONTAINER_SKILLS_BAR_BASE_TEXTURE =
            texture("gui/villager_interaction_screen/interaction_container_skills_bar_base");
    public static final ResourceLocation INTERACTION_CONTAINER_SKILLS_BAR_FILL_TEXTURE =
            texture("gui/villager_interaction_screen/interaction_container_skills_bar_fill");
    public static final ResourceLocation INTERACTION_REPUTATION_ICON_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_reputation_icon");
    public static final ResourceLocation INTERACTION_LOCKED_ICON_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_locked_icon");
    public static final ResourceLocation INTERACTION_BUTTON_ICON_TALK_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_button_icon_talk");
    public static final ResourceLocation INTERACTION_BUTTON_ICON_TRADE_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_button_icon_trade");
    public static final ResourceLocation INTERACTION_BUTTON_ICON_ADVENTURES_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_button_icon_adventures");
    public static final ResourceLocation INTERACTION_BUTTON_ICON_PROFILE_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_button_icon_profile");
    public static final ResourceLocation INTERACTION_BUTTON_ICON_GIFT_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_button_icon_gift");
    public static final ResourceLocation INTERACTION_BUTTON_ICON_HIRE_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_button_icon_hire");
    /** Single replacement point for the temporary party recruitment button artwork. */
    public static final ResourceLocation PARTY_RECRUITMENT_PLACEHOLDER_ICON = INTERACTION_BUTTON_ICON_HIRE_TEXTURE;
    public static final ResourceLocation INTERACTION_BUTTON_ICON_INVENTORY_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_button_icon_inventory");
    public static final ResourceLocation INTERACTION_BUTTON_ICON_START_FOLLOW_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_button_icon_start_follow");
    public static final ResourceLocation INTERACTION_BUTTON_ICON_STOP_FOLLOW_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_button_icon_stop_follow");
    public static final ResourceLocation INTERACTION_BUTTON_ICON_STAY_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_button_icon_stay");
    public static final ResourceLocation INTERACTION_SCROLL_ICON_DOWN_TEXTURE =
            texture("gui/villager_interaction_screen/icons/scroll_icon_down");
    public static final ResourceLocation INTERACTION_SCROLL_ICON_UP_TEXTURE =
            texture("gui/villager_interaction_screen/icons/scroll_icon_up");
    public static final ResourceLocation INTERACTION_CONTAINER_OPTION_SCROLL_UP_ICON_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_container_option_scroll_up_icon");
    public static final ResourceLocation INTERACTION_CONTAINER_OPTION_ACTIVE_ICON_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_container_option_active_icon");
    public static final ResourceLocation INTERACTION_CONTAINER_OPTION_SCROLL_DOWN_ICON_TEXTURE =
            texture("gui/villager_interaction_screen/icons/interaction_container_option_scroll_down_icon");
    public static final ResourceLocation CLIPBOARD_WORKFORCE_BASE_TEXTURE =
            texture("gui/clipboard_workforce/clipboard_base");
    public static final ResourceLocation CLIPBOARD_WORKFORCE_PAPER_TEXTURE =
            texture("gui/clipboard_workforce/clipboard_paper");
    public static final ResourceLocation CLIPBOARD_WORKFORCE_TAB_1_TEXTURE =
            texture("gui/clipboard_workforce/clipboard_tab_1");
    public static final ResourceLocation CLIPBOARD_WORKFORCE_TAB_2_TEXTURE =
            texture("gui/clipboard_workforce/clipboard_tab_2");
    public static final ResourceLocation CLIPBOARD_WORKFORCE_TAB_3_TEXTURE =
            texture("gui/clipboard_workforce/clipboard_tab_3");
    public static final ResourceLocation TRADE_REROLL_BUTTON_TEXTURE =
            texture("gui/trade/reroll_button");
    public static final ResourceLocation TRADE_REROLL_BUTTON_HIGHLIGHTED_TEXTURE =
            texture("gui/trade/reroll_button_highlighted");
    public static final ResourceLocation TRADE_REROLL_ICON_TEXTURE =
            texture("gui/trade/reroll_icon");
    public static final ResourceLocation TRADE_REROLL_ICON_HIGHLIGHTED_TEXTURE =
            texture("gui/trade/reroll_icon_highlighted");
    public static final ResourceLocation TRADE_REROLL_REQUEST_SENT_ICON_TEXTURE =
            texture("gui/trade/reroll_request_sent_icon");
    public static final ResourceLocation QUEST_JOURNAL_CONTAINER_TEXTURE =
            texture("gui/quest_journal/quest_journal_container");
    public static final ResourceLocation QUEST_JOURNAL_CONTAINER_OVERLAY_TEXTURE =
            texture("gui/quest_journal/quest_journal_container_overlay");
    public static final ResourceLocation QUEST_JOURNAL_BOOKMARK_RED_TEXTURE =
            texture("gui/quest_journal/quest_journal_bookmark_red");
    public static final ResourceLocation QUEST_JOURNAL_BOOKMARK_PURPLE_TEXTURE =
            texture("gui/quest_journal/quest_journal_bookmark_purple");
    public static final ResourceLocation QUEST_JOURNAL_BOOKMARK_TEAL_TEXTURE =
            texture("gui/quest_journal/quest_journal_bookmark_teal");
    public static final ResourceLocation QUEST_JOURNAL_BOOKMARK_ICON_ACTIVE_TEXTURE =
            texture("gui/quest_journal/quest_journal_bookmark_icon_active");
    public static final ResourceLocation QUEST_JOURNAL_BOOKMARK_ICON_AVAILABLE_TEXTURE =
            texture("gui/quest_journal/quest_journal_bookmark_icon_available");
    public static final ResourceLocation QUEST_JOURNAL_BOOKMARK_ICON_COMPLETED_TEXTURE =
            texture("gui/quest_journal/quest_journal_bookmark_icon_completed");
    public static final ResourceLocation QUEST_JOURNAL_DIVIDER_TEXTURE =
            texture("gui/quest_journal/quest_journal_divider");
    public static final ResourceLocation QUEST_JOURNAL_ENTRY_1_TEXTURE =
            texture("gui/quest_journal/quest_journal_entry_1");
    public static final ResourceLocation QUEST_JOURNAL_ENTRY_2_TEXTURE =
            texture("gui/quest_journal/quest_journal_entry_2");
    public static final ResourceLocation QUEST_JOURNAL_ENTRY_HIGHLIGHT_TEXTURE =
            texture("gui/quest_journal/quest_journal_entry_highlight");
    public static final ResourceLocation QUEST_JOURNAL_ICON_ACTIVE_TEXTURE =
            texture("gui/quest_journal/quest_journal_icon_active");
    public static final ResourceLocation QUEST_JOURNAL_ICON_AVAILABLE_TEXTURE =
            texture("gui/quest_journal/quest_journal_icon_available");
    public static final ResourceLocation QUEST_JOURNAL_ICON_COMPLETED_TEXTURE =
            texture("gui/quest_journal/quest_journal_icon_completed");
    public static final ResourceLocation QUEST_JOURNAL_ICON_INACTIVE_TEXTURE =
            texture("gui/quest_journal/quest_journal_icon_inactive");
    public static final ResourceLocation QUEST_JOURNAL_ICON_QUEST_STEP_TEXTURE =
            texture("gui/quest_journal/quest_journal_icon_quest_step");
    public static final ResourceLocation QUEST_JOURNAL_ICON_QUEST_STEP_COMPLETED_TEXTURE =
            texture("gui/quest_journal/quest_journal_icon_quest_step_completed");
    public static final ResourceLocation QUEST_JOURNAL_ICON_SELECTED_QUEST_TEXTURE =
            texture("gui/quest_journal/quest_journal_icon_selected_quest");
    public static final ResourceLocation QUEST_JOURNAL_ICON_SELECTED_QUEST_COMPLETED_TEXTURE =
            texture("gui/quest_journal/quest_journal_icon_selected_quest_completed");
    public static final ResourceLocation QUEST_JOURNAL_ICON_UPDATE_TEXTURE =
            texture("gui/quest_journal/quest_journal_icon_update");
    public static final ResourceLocation QUEST_JOURNAL_ICON_UPDATE_SELECTED_QUEST_TEXTURE =
            texture("gui/quest_journal/quest_journal_icon_update_selected_quest");
    public static final ResourceLocation QUEST_JOURNAL_QUEST_NUMBER_TEXTURE =
            texture("gui/quest_journal/quest_journal_quest_number");
    public static final ResourceLocation QUEST_JOURNAL_SCROLLBAR_TEXTURE =
            texture("gui/quest_journal/quest_journal_scrollbar");
    public static final ResourceLocation QUEST_JOURNAL_SCROLLER_TEXTURE =
            texture("gui/quest_journal/quest_journal_scroller");
    public static final ResourceLocation QUEST_JOURNAL_SCROLLER_HIGHLIGHT_TEXTURE =
            texture("gui/quest_journal/quest_journal_scroller_highlight");
    public static final ResourceLocation QUEST_JOURNAL_SELECTED_QUEST_TEXTURE =
            texture("gui/quest_journal/quest_journal_selected_quest");

    public static final ResourceLocation COMBAT_VILLAGER_MODEL =
            VillagerRetaliation.id("models/entity/villager/combat_villager.json");
    public static final ResourceLocation NON_COMBAT_VILLAGER_MODEL =
            VillagerRetaliation.id("models/entity/villager/non_combat_villager.json");
    public static final ResourceLocation VILLAGER_MODEL_OPTIONS =
            VillagerRetaliation.id("models/entity/villager/render_options.json");
    public static final ResourceLocation VANILLA_VILLAGER_CEM_MODEL =
            ResourceLocation.withDefaultNamespace("optifine/cem/villager.jem");
    public static final ResourceLocation COMBAT_VILLAGER_CEM_MODEL =
            VillagerRetaliation.id("optifine/cem/villager.jem");
    public static final ResourceLocation COMBAT_VILLAGER_CEM_MODEL_DEPRECATED =
            ResourceLocation.withDefaultNamespace("optifine/cem/modded/" + VillagerRetaliation.MOD_ID + "/villager.jem");
    public static final ResourceLocation COMBAT_VILLAGER_CEM_MODEL_LEGACY_FOLDER =
            ResourceLocation.withDefaultNamespace("optifine/cem/" + VillagerRetaliation.MOD_ID + "/villager.jem");

    public static final ResourceLocation DIALOGUE_CINEMATIC_BARS_SHADER =
            VillagerRetaliation.id("dialogue_cinematic_bars");

    public static ResourceLocation reputationIcon(VillagerReputationLevel level) {
        return switch (level) {
            case ROYALTY -> texture("gui/container/icons/royalty");
            case REVERED -> texture("gui/container/icons/revered");
            case RESPECTED -> texture("gui/container/icons/respected");
            case TRUSTED -> texture("gui/container/icons/trusted");
            case NEUTRAL -> texture("gui/container/icons/neutral");
            case SUSPICIOUS -> texture("gui/container/icons/suspicious");
            case HOSTILE -> texture("gui/container/icons/hostile");
            case DESPISED -> texture("gui/container/icons/despised");
            case FEARED -> texture("gui/container/icons/feared");
        };
    }

    private static ResourceLocation texture(String path) {
        return VillagerRetaliation.id("textures/" + path + ".png");
    }
}
