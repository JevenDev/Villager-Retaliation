package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanScrollbarThumb;
import com.jvn.toucanlib.client.ToucanScrollState;
import com.jvn.toucanlib.client.ToucanScrollbars;
import com.jvn.toucanlib.client.interaction.ToucanLimitFeedback;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.party.PartyRosterClient;
import com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.client.villager.VillagerModelPreviewRenderContext;
import com.jvn.villagerretaliation.config.DialogueTextSpeed;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.duel.DuelAvailabilityReason;
import com.jvn.villagerretaliation.duel.DuelLoadout;
import com.jvn.villagerretaliation.party.PartyAttackMode;
import com.jvn.villagerretaliation.party.PartyCombatMode;
import com.jvn.villagerretaliation.party.PartyDropCollectionMode;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalCullSettings;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalHandlingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.network.ClipboardStorageActionPayload;
import com.jvn.villagerretaliation.network.HiredAnimalBreedingTargetPayload;
import com.jvn.villagerretaliation.network.HiredAnimalCullCapPayload;
import com.jvn.villagerretaliation.network.HiredAnimalHandlingOptionPayload;
import com.jvn.villagerretaliation.network.HiredBuilderOrderPayload;
import com.jvn.villagerretaliation.network.HiredBrewingOrderPayload;
import com.jvn.villagerretaliation.network.HiredFarmingOptionPayload;
import com.jvn.villagerretaliation.network.HiredHuntingTargetPayload;
import com.jvn.villagerretaliation.network.HiredLoggingFilterPayload;
import com.jvn.villagerretaliation.network.HiredLoggingOptionPayload;
import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import com.jvn.villagerretaliation.network.VillagerConversationActivityPayload;
import com.jvn.villagerretaliation.network.VillagerConversationEndRequestPayload;
import com.jvn.villagerretaliation.network.VillagerAllegianceActionPayload;
import com.jvn.villagerretaliation.network.VillageAllegianceView;
import com.jvn.villagerretaliation.network.VillagerDialogueRequestPayload;
import com.jvn.villagerretaliation.network.VillagerGiftRequestPayload;
import com.jvn.villagerretaliation.network.VillagerInventoryRequestPayload;
import com.jvn.villagerretaliation.network.VillagerDuelRequestPayload;
import com.jvn.villagerretaliation.network.OpenVillagerDuelPayload;
import com.jvn.villagerretaliation.network.VillagerMouseEasterEggPayload;
import com.jvn.villagerretaliation.network.VillagerProfileRequestPayload;
import com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload;
import com.jvn.villagerretaliation.network.RecruitmentResultPayload;
import com.jvn.villagerretaliation.network.VillagerTradeRequestPayload;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.VillagerGiftKnowledgeService.GiftTooltipReaction;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.VillagerContractTime;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeRank;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.client.reputation.VillagerReputationNotificationOverlay;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import com.jvn.villagerretaliation.social.VillagerFamilyTreeSnapshot;
import com.jvn.villagerretaliation.social.VillagerRelationshipSnapshot;
import com.jvn.villagerretaliation.villager.VillagerGender;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public class VillagerInteractionScreen extends Screen implements VillagerInteractionSessionScreen {
    private static final String GUI_KEY_PREFIX = "villagerretaliation.gui.";
    private static final long ACTIVITY_SIGNAL_INTERVAL_MILLIS = 1_000L;
    private static final String BACK_LABEL_KEY = GUI_KEY_PREFIX + "back";
    private static final String FORCED_LEAVE_OPTION_ID = "leave";
    private static final String DIALOGUE_TREE_LEAVE_OPTION_ID = DialogueTreeService.LEAVE_OPTION_ID;
    private static final String BLUEPRINT_CHANGE_OPTION_ID = "construction_blueprint_change";
    private static final String BLUEPRINT_NEVERMIND_OPTION_ID = "construction_blueprint_nevermind";
    private static final String ITEM_FILTER_ALLOWLIST_OPTION_ID = "item_filter_use_allowlist";
    private static final String ITEM_FILTER_DENYLIST_OPTION_ID = "item_filter_use_denylist";
    private static final String ITEM_FILTER_NEVERMIND_OPTION_ID = "item_filter_nevermind";
    private static final String QUEST_V2_TAG = "quest_v2";
    private static final String QUEST_OFFER_HINT_TAG = "quest_offer_hint";
    private static final float OPTION_SCROLL_LERP = 0.32F;
    private static final float OPTION_SCROLL_STEP = 23.0F;
    private static final float OPTION_HOVER_SCALE = 0.055F;
    private static final float OPTION_SELECTED_SCALE = 0.02F;
    private static final float INTERACTION_ANIMATION_DURATION_MILLIS = 280.0F;
    private static final float INTERACTION_STATE_TRANSITION_DURATION_MILLIS = INTERACTION_ANIMATION_DURATION_MILLIS;
    private static final int INTERACTION_STATE_CONTENT_SLIDE_X = 12;
    private static final int INTERACTION_STATE_BOTTOM_ENTRANCE_PADDING = 12;
    private static final int INFO_VALUE_COLOR = 0xFFF8F6EF;
    private static final int INFO_SECONDARY_COLOR = 0xB8D5D0C6;
    private static final int GIFT_BUTTON_WIDTH = 112;
    private static final int GIFT_BUTTON_HEIGHT = 20;
    private static final int PROFILE_CHART_RADIUS = 36;
    private static final int PROFILE_CHART_AXIS_COLOR = 0x55E8E4DA;
    private static final int PROFILE_CHART_OUTLINE_COLOR = 0x90E8E4DA;
    private static final int PROFILE_CHART_VALUE_COLOR = 0xFFE9C46A;
    private static final int PROFILE_CHART_POINT_COLOR = 0xFFFFF3B0;
    private static final int PROFILE_CHART_POINT_HOVER_COLOR = 0xFFFFFFFF;
    private static final int PROFILE_CHART_POINT_HIT_RADIUS = 6;
    private static final int PROFILE_CONTAINER_CHART_RADIUS = 28;
    private static final int PROFILE_CONTAINER_CHART_CENTER_X_OFFSET = 6;
    private static final int PROFILE_CONTAINER_CHART_CENTER_Y_OFFSET = 20;
    private static final int PROFILE_CONTAINER_CHART_LABEL_X_OFFSET = 17;
    private static final int PROFILE_CONTAINER_CHART_LABEL_Y_OFFSET = 13;
    private static final int PROFILE_CONTAINER_CHART_TOP_PADDING = 10;
    private static final int PROFILE_CONTAINER_CHART_BOTTOM_PADDING = 6;
    private static final int PROFILE_CONTAINER_CHART_LOADING_Y_OFFSET = 46;
    private static final int PROFILE_CONTAINER_CHART_POINT_RADIUS = 1;
    private static final int PROFILE_CONTAINER_CHART_POINT_HOVER_RADIUS = 2;
    private static final int PROFILE_CONTAINER_CHART_POINT_HIT_RADIUS = 5;
    private static final int PROFILE_SKILL_COLUMNS = 2;
    private static final long PROFILE_KEEPALIVE_INTERVAL_MILLIS = 20_000L;
    private static final ResourceLocation DEFAULT_CURRENCY_ICON_SPRITE = ResourceLocation.withDefaultNamespace("item/emerald");
    private static final int INTERACTION_CONTAINER_WIDTH = 283;
    private static final int INTERACTION_CONTAINER_HEIGHT = 85;
    private static final int INTERACTION_CONTAINER_HOTBAR_GAP = 5;
    private static final int SKILLS_DIALOGUE_CONTAINER_WIDTH = 283;
    private static final int SKILLS_DIALOGUE_CONTAINER_HEIGHT = 64;
    private static final int SKILLS_DIALOGUE_CONTAINER_GAP = 1;
    private static final int SKILLS_DIALOGUE_BUTTON_WIDTH = 12;
    private static final int SKILLS_DIALOGUE_BUTTON_HEIGHT = 46;
    private static final int SKILLS_DIALOGUE_BUTTON_INSET = 1;
    private static final int SKILLS_DIALOGUE_BACK_HINT_GAP = 3;
    private static final int SKILLS_DIALOGUE_BACK_HINT_COLOR = 0x80FFFFFF;
    private static final String SKILLS_DIALOGUE_BACK_HINT_KEY = GUI_KEY_PREFIX + "skills.back_hint";
    private static final int PROFILE_CONTAINER_WIDTH = 168;
    private static final int PROFILE_CONTAINER_HEIGHT = 120;
    private static final int INTERACTION_BUTTON_SIZE = 28;
    private static final int INTERACTION_BUTTON_GAP = 1;
    private static final int INTERACTION_BUTTON_HIGHLIGHT_COLOR = 0x40FFFFFF;
    private static final int INTERACTION_BUTTON_DISABLED_HIGHLIGHT_COLOR = 0x28FFFFFF;
    private static final int INTERACTION_BUTTON_HIGHLIGHT_INSET = 2;
    private static final int INTERACTION_KEYBOARD_TOOLTIP_X_GAP = 8;
    private static final int INTERACTION_KEYBOARD_TOOLTIP_Y_GAP = 4;
    private static final int INTERACTION_NAMEPLATE_TEXT_HORIZONTAL_PADDING = 8;
    private static final int INTERACTION_NAMEPLATE_TEXT_Y_OFFSET = 2;
    private static final int INTERACTION_NAMEPLATE_X = 0;
    private static final int INTERACTION_NAMEPLATE_Y = -16;
    private static final int INTERACTION_NAMEPLATE_TEXTURE_WIDTH = 60;
    private static final int INTERACTION_NAMEPLATE_TEXTURE_HEIGHT = 19;
    private static final int INTERACTION_NAMEPLATE_SLICE_LEFT = 8;
    private static final int INTERACTION_NAMEPLATE_SLICE_RIGHT = 8;
    private static final int INTERACTION_NAMEPLATE_SLICE_TOP = 8;
    private static final int INTERACTION_NAMEPLATE_SLICE_BOTTOM = 8;
    private static final int INTERACTION_DIALOGUE_LEFT = 62;
    private static final int INTERACTION_DIALOGUE_TOP = 7;
    private static final int INTERACTION_DIALOGUE_RIGHT = 263;
    private static final int INTERACTION_DIALOGUE_EXTENDED_RIGHT = 275;
    private static final int INTERACTION_DIALOGUE_BOTTOM = 47;
    private static final int INTERACTION_DIALOGUE_SCROLL_LEFT = 59;
    private static final int INTERACTION_DIALOGUE_SCROLL_TOP = 4;
    private static final int INTERACTION_DIALOGUE_SCROLL_RIGHT = 287;
    private static final int INTERACTION_DIALOGUE_SCROLL_BOTTOM = 59;
    private static final int INTERACTION_DIALOGUE_SCROLL_ICON_LEFT = 62;
    private static final int INTERACTION_DIALOGUE_SCROLL_ICON_BOTTOM = 56;
    private static final int INTERACTION_DIALOGUE_SCROLL_ICON_WIDTH = 7;
    private static final int INTERACTION_DIALOGUE_SCROLL_ICON_HEIGHT = 5;
    private static final int INTERACTION_PORTRAIT_LEFT = 4;
    private static final int INTERACTION_PORTRAIT_TOP = 4;
    private static final int INTERACTION_PORTRAIT_RIGHT = 55;
    private static final int INTERACTION_PORTRAIT_BOTTOM = 60;
    private static final int INTERACTION_PORTRAIT_SCISSOR_RIGHT_EXTENSION = 1;
    private static final int INTERACTION_PORTRAIT_SCALE = 54;
    private static final int INTERACTION_PORTRAIT_RENDER_Y_OFFSET = 1;
    private static final long MOUSE_STARE_REQUIRED_MILLIS = 10_000L;
    private static final double VILLAGER_PORTRAIT_EYE_BRIDGE_RADIUS_X = 3.5D;
    private static final double VILLAGER_PORTRAIT_EYE_BRIDGE_RADIUS_Y = 4.5D;
    private static final int INTERACTION_MOOD_BASELINE_LEFT = 65;
    private static final int INTERACTION_INFO_BASELINE_RIGHT = 274;
    private static final int INTERACTION_INFO_BASELINE_Y = 79;
    private static final int INTERACTION_INFO_TEXT_Y_OFFSET = -1;
    private static final int INTERACTION_INFO_ICON_Y_OFFSET = 1;
    private static final int INTERACTION_INFO_ICON_X_OFFSET = -1;
    private static final int INTERACTION_CURRENCY_ICON_GAP = 2;
    private static final int INTERACTION_REPUTATION_GAP = 4;
    private static final int INTERACTION_OPTION_WIDTH = 64;
    private static final int INTERACTION_OPTION_HEIGHT = 23;
    private static final int INTERACTION_OPTION_STRIDE = 23;
    private static final int INTERACTION_OPTION_VISIBLE_ROWS = 5;
    private static final int INTERACTION_OPTION_SCREEN_MARGIN = 5;
    private static final int INTERACTION_OPTION_TEXT_INSET = 8;
    private static final int INTERACTION_OPTION_TEXT_TOP = 8;
    private static final int INTERACTION_OPTION_TEXT_RIGHT_PADDING = INTERACTION_OPTION_TEXT_INSET;
    private static final int INTERACTION_OPTION_ARROW_WIDTH = 9;
    private static final int INTERACTION_OPTION_ARROW_HEIGHT = 6;
    private static final int INTERACTION_OPTION_SELECTION_ARROW_WIDTH = 8;
    private static final int INTERACTION_OPTION_SELECTION_ARROW_HEIGHT = 11;
    private static final int INTERACTION_OPTION_SELECTION_ARROW_GAP = 2;
    private static final int INTERACTION_OPTION_MAX_LINE_CHARACTERS = 20;
    private static final int INTERACTION_OPTION_LINE_STEP = 10;
    private static final int INTERACTION_LOCKED_ICON_WIDTH = 6;
    private static final int INTERACTION_LOCKED_ICON_HEIGHT = 7;
    private static final int INTERACTION_LOCKED_ICON_TEXT_GAP = 3;
    private static final int INTERACTION_OPTION_CHECKBOX_SIZE = 9;
    private static final int INTERACTION_OPTION_CHECKBOX_TEXT_GAP = 3;
    private static final int INTERACTION_ICON_SIZE = 16;
    private static final int INTERACTION_TOOLTIP_MAX_WIDTH = 220;
    private static final ResourceLocation DIALOGUE_BLIP_SOUND_ID = VillagerRetaliation.id("dialogue");
    private static final SoundEvent DIALOGUE_BLIP_SOUND = SoundEvent.createVariableRangeEvent(DIALOGUE_BLIP_SOUND_ID);
    private static final int DIALOGUE_BLIP_MIN_VISIBLE_CHARACTERS = 1;
    private static final int DIALOGUE_BLIP_MAX_VISIBLE_CHARACTERS = 3;
    private static final int INTERACTION_NAME_COLOR = 0xFFFFFFFF;
    private static final int INTERACTION_DIALOGUE_COLOR = 0xFFFFFFFF;
    private static final int INTERACTION_MOOD_COLOR = 0xFF5FCDE4;
    private static final int INTERACTION_OPTION_TEXT_COLOR = 0xFFFFFFFF;
    private static final int INTERACTION_REPUTATION_TEXT_COLOR = 0xFFFFFF55;
    private static final int TEXT_OUTLINE_COLOR = 0xFF000000;
    private static final int[] DUEL_STAKES = {0, 8, 16, 32, 64, Integer.MAX_VALUE};
    private static final Runnable NO_ACTION = () -> {
    };

    private final int villagerEntityId;
    private final String villagerName;
    private final String professionName;
    private final VillagerProfessionUiColors.ColorPair professionUiColors;
    private final String genderName;
    private final boolean baby;
    private final boolean canTrade;
    private final boolean duelVisible;
    private int reputation;
    private VillagerReputationLevel reputationLevel;
    private DialogueDisposition mood;
    private VillagerMood primaryMood;
    private boolean followingPlayer;
    private boolean stayingHere;
    private long assignmentRevision;
    private boolean routineChatMuted;
    private final boolean forcedDialogue;
    private final boolean clipboardMenu;
    private final boolean clipboardSelectionAssigned;
    private boolean hiredByPlayer;
    private final boolean hiredByOtherPlayer;
    private int hiredRemainingDays;
    private final boolean inventoryAvailable;
    private final boolean jobInventoryAvailable;
    private final boolean recruitedPartyVillager;
    private final boolean partyVillagerAuthorized;
    private final boolean partyVillagerPartyMember;
    private final boolean partyRecruitAvailable;
    private final boolean mountFeatureAvailable;
    private boolean assignedMount;
    private boolean mountedTravelEnabled;
    private int partyRemainingDays;
    private final int walletEmeralds;
    private final int maxWalletEmeralds;
    private final int lifetimeWalletEarned;
    private final int lifetimeWalletDeposited;
    private final String walletCurrencyName;
    private final String walletCurrencyPluralName;
    private final String walletCurrencyLabel;
    private final ResourceLocation walletCurrencyIconSprite;
    private final int walletCurrencyTextColor;
    private final EnumSet<HiredVillagerRole> availableHiredRoles;
    private HiredVillagerRole activeHiredRole;
    private boolean activeBrewingOrder;
    private boolean activeBuilderTask;
    private boolean oneOffBuilderJob;
    private boolean farmingTillSoil;
    private boolean huntingAnimals;
    private boolean huntingHostiles;
    private boolean huntingPlayers;
    private final Set<String> selectedLoggingFilters = new LinkedHashSet<>();
    private boolean loggingStripLogs;
    private boolean loggingHarvestLeaves;
    private boolean loggingBonemealSaplings;
    private boolean loggingPlantSaplings;
    private boolean loggingPickUpDecayDrops;
    private final Set<String> selectedAnimalBreedingTargets = new LinkedHashSet<>();
    private int animalCullCap;
    private boolean animalShearing;
    private boolean forceCameraTowardsVillager;
    private OpenVillagerDuelPayload duelStatus;
    private DuelLoadout duelLoadout = DuelLoadout.BARE_HANDED;
    private int duelStakeIndex;
    private boolean duelStartPending;
    private final List<DialogueOption> options = new ArrayList<>();
    private final List<DialogueOptionDefinition> dialogueOptions = new ArrayList<>();
    private final List<String> knownLikedGiftNames = new ArrayList<>();
    private final List<String> knownDislikedGiftNames = new ArrayList<>();
    private final List<GiftTooltipReaction> giftTooltipReactions = new ArrayList<>();
    private final VillagerFamilyTreeSnapshot familyTree;
    private final VillagerRelationshipSnapshot relationships;
    private final VillageAllegianceView allegiance;
    private final float cinematicBarSlant;
    private final VillagerInteractionScreenState state = new VillagerInteractionScreenState();
    private final EnumMap<DialoguePage, VillagerInteractionScreenState.OptionListPosition> rememberedPageOptionPositions =
            new EnumMap<>(DialoguePage.class);
    private DialoguePage page = DialoguePage.ROOT;
    private boolean closingWithAnimation;
    private boolean closingFromServer;
    private boolean replacingFromServer;
    private boolean openingChat;
    private boolean awaitingForcedDialogueResponse;
    private boolean profileRefreshRequested;
    private boolean draggingScrollbar;
    private boolean draggingSkillScrollbar;
    private float scrollbarDragOffset;
    private float skillScrollbarDragOffset;
    private float skillScroll;
    private float targetSkillScroll;
    private long lastOptionScrollRenderMillis = -1L;
    private long lastActivitySignalMillis = -1L;
    private VillagerSkill selectedSkillDetails;
    private VillagerSocialAttribute selectedProfileAttributeDetails;
    private HiredVillagerRole selectedJobDetails;
    private SkillsProfilePanel skillsProfilePanel = SkillsProfilePanel.SKILLS;
    private long lastProfileRequestMillis = -1L;
    private HiredBrewingRecipeCatalog.BrewingPotionChoice selectedBrewingPotionChoice;
    private HiredBrewingRecipeCatalog.BrewingDurationChoice selectedBrewingDurationChoice;
    private HiredBrewingRecipeCatalog.BrewingLevelChoice selectedBrewingLevelChoice;
    private HiredBrewingRecipeCatalog.BrewingRoute selectedBrewingRoute;
    private HiredVillagerRole pendingHireRole;
    private boolean confirmingPartyRecruit;
    private boolean confirmingPartyDismiss;
    private String selectedBuilderCategory;
    private BuilderStructureCatalog.Entry selectedBuilderStructure;
    private int selectedInventorySlot = -1;
    private int selectedGiftAmount = 0;
    private final ToucanLimitFeedback giftLimitFeedback = new ToucanLimitFeedback();
    private boolean pixelOptionEdgeScaleInitialized;
    private float pixelOptionTopEdgeScaleBlend;
    private float pixelOptionBottomEdgeScaleBlend;
    private int lastMouseX;
    private int lastMouseY;
    private long mouseStareStartMillis = -1L;
    private boolean mouseStareEasterEggTriggered;
    private int renderSlideOffsetY;
    private int renderContentOffsetX;
    private boolean keyboardOptionFocusVisible;
    private int selectedInteractionMenuButton;
    private boolean keyboardInteractionMenuFocusVisible;
    private long animationStartMillis = -1L;
    private long interactionStateTransitionStartMillis = -1L;
    private int interactionStateTransitionStartOffsetY;
    private int interactionStateTransitionStartOffsetX;
    private DialoguePage replacementTransitionPreviousPage;
    private int replacementTransitionPreviousTop;
    private Button giftButton;
    private String villagerDialogueText = "";
    private List<DialogueTextSegment> villagerDialogueTextSegments = List.of();
    private int dialogueLineScroll;
    private long dialogueTextAnimationStartMillis;
    private boolean dialogueTextAnimationSkipped;
    private long optionLayoutVersion;
    private long interactionOptionWidthVersion = Long.MIN_VALUE;
    private int cachedInteractionOptionWidth = INTERACTION_OPTION_WIDTH;
    private final VillagerInteractionOptionList.LayoutCache optionLayout = new VillagerInteractionOptionList.LayoutCache();
    private final Random dialogueBlipRandom = new Random();
    private float dialogueBlipPitch = 1.0F;
    private int nextDialogueBlipVisibleCharacter = Integer.MAX_VALUE;
    private int lastDialogueBlipVisibleCharacters;
    private final GiftPageContext giftPageContext = new GiftPageContext();
    private final OptionListContext optionListContext = new OptionListContext();
    private final NavigationContext navigationContext = new NavigationContext();
    private final ProfilePageContext profilePageContext = new ProfilePageContext();
    private final SkillsPageContext skillsPageContext = new SkillsPageContext();
    private final JobStatsPageContext jobStatsPageContext = new JobStatsPageContext();

    public VillagerInteractionScreen(
            int villagerEntityId,
            String villagerName,
            String professionName,
            VillagerProfessionUiColors.ColorPair professionUiColors,
            String genderName,
            boolean baby,
            boolean canTrade,
            boolean duelVisible,
            int reputation,
            VillagerReputationLevel reputationLevel,
            DialogueDisposition mood,
            VillagerMood primaryMood,
            boolean followingPlayer,
            boolean stayingHere,
            long assignmentRevision,
            boolean routineChatMuted,
            boolean forcedDialogue,
            boolean clipboardMenu,
            boolean clipboardSelectionAssigned,
            boolean hiredByPlayer,
            boolean hiredByOtherPlayer,
            int hiredRemainingDays,
            boolean inventoryAvailable,
            boolean jobInventoryAvailable,
            boolean recruitedPartyVillager,
            boolean partyVillagerAuthorized,
            boolean partyVillagerPartyMember,
            boolean partyRecruitAvailable,
            boolean mountFeatureAvailable,
            boolean assignedMount,
            boolean mountedTravelEnabled,
            int partyRemainingDays,
            int walletEmeralds,
            int maxWalletEmeralds,
            int lifetimeWalletEarned,
            int lifetimeWalletDeposited,
            String walletCurrencyName,
            String walletCurrencyPluralName,
            String walletCurrencyLabel,
            ResourceLocation walletCurrencyIconSprite,
            int walletCurrencyTextColor,
            boolean forceCameraTowardsVillager,
            List<HiredVillagerRole> availableHiredRoles,
            HiredVillagerRole activeHiredRole,
            boolean activeBrewingOrder,
            boolean activeBuilderTask,
            boolean oneOffBuilderJob,
            boolean farmingTillSoil,
            boolean huntingAnimals,
            boolean huntingHostiles,
            boolean huntingPlayers,
            List<String> selectedLoggingFilters,
            boolean loggingStripLogs,
            boolean loggingHarvestLeaves,
            boolean loggingBonemealSaplings,
            boolean loggingPlantSaplings,
            boolean loggingPickUpDecayDrops,
            List<String> selectedAnimalBreedingTargets,
            int animalCullCap,
            boolean animalShearing,
            List<DialogueOptionDefinition> dialogueOptions,
            List<String> knownLikedGiftNames,
            List<String> knownDislikedGiftNames,
            List<GiftTooltipReaction> giftTooltipReactions,
            VillageAllegianceView allegiance,
            VillagerFamilyTreeSnapshot familyTree,
            VillagerRelationshipSnapshot relationships) {
        super(Component.translatable(GUI_KEY_PREFIX + "title"));
        this.villagerEntityId = villagerEntityId;
        this.villagerName = villagerName;
        this.professionName = professionName;
        this.professionUiColors = professionUiColors == null ? VillagerProfessionUiColors.DEFAULT_COLORS : professionUiColors;
        this.genderName = localizedGenderName(genderName);
        this.baby = baby;
        this.canTrade = canTrade;
        this.duelVisible = duelVisible;
        this.reputation = reputation;
        this.reputationLevel = reputationLevel;
        this.mood = mood;
        this.primaryMood = primaryMood == null ? VillagerMood.NEUTRAL : primaryMood;
        this.followingPlayer = followingPlayer;
        this.stayingHere = stayingHere;
        this.assignmentRevision = Math.max(0L, assignmentRevision);
        this.routineChatMuted = routineChatMuted;
        this.forcedDialogue = forcedDialogue;
        this.clipboardMenu = clipboardMenu;
        this.clipboardSelectionAssigned = clipboardSelectionAssigned;
        this.hiredByPlayer = hiredByPlayer;
        this.hiredByOtherPlayer = hiredByOtherPlayer;
        this.hiredRemainingDays = Math.max(0, hiredRemainingDays);
        this.inventoryAvailable = inventoryAvailable;
        this.jobInventoryAvailable = jobInventoryAvailable;
        this.recruitedPartyVillager = recruitedPartyVillager;
        this.partyVillagerAuthorized = partyVillagerAuthorized;
        this.partyVillagerPartyMember = partyVillagerPartyMember;
        this.partyRecruitAvailable = partyRecruitAvailable;
        this.mountFeatureAvailable = mountFeatureAvailable;
        this.assignedMount = assignedMount;
        this.mountedTravelEnabled = mountedTravelEnabled;
        this.partyRemainingDays = Math.max(0, partyRemainingDays);
        this.walletEmeralds = Math.max(0, walletEmeralds);
        this.maxWalletEmeralds = Math.max(0, maxWalletEmeralds);
        this.lifetimeWalletEarned = Math.max(0, lifetimeWalletEarned);
        this.lifetimeWalletDeposited = Math.max(0, lifetimeWalletDeposited);
        this.walletCurrencyName = blankToDefault(walletCurrencyName, "emerald");
        this.walletCurrencyPluralName = blankToDefault(walletCurrencyPluralName, "emeralds");
        this.walletCurrencyLabel = blankToDefault(walletCurrencyLabel, "Emeralds");
        this.walletCurrencyIconSprite = walletCurrencyIconSprite == null ? DEFAULT_CURRENCY_ICON_SPRITE : walletCurrencyIconSprite;
        this.walletCurrencyTextColor = walletCurrencyTextColor | 0xFF000000;
        this.cinematicBarSlant = VillagerDialogueCinematicBars.sampleSlant();
        this.availableHiredRoles = availableHiredRoles == null || availableHiredRoles.isEmpty()
                ? EnumSet.noneOf(HiredVillagerRole.class)
                : EnumSet.copyOf(availableHiredRoles);
        this.activeHiredRole = activeHiredRole;
        this.activeBrewingOrder = activeBrewingOrder;
        this.activeBuilderTask = activeBuilderTask;
        this.oneOffBuilderJob = oneOffBuilderJob;
        this.farmingTillSoil = farmingTillSoil;
        this.huntingAnimals = huntingAnimals;
        this.huntingHostiles = huntingHostiles;
        this.huntingPlayers = huntingPlayers;
        this.loggingStripLogs = loggingStripLogs;
        this.loggingHarvestLeaves = loggingHarvestLeaves;
        this.loggingBonemealSaplings = loggingBonemealSaplings;
        this.loggingPlantSaplings = loggingPlantSaplings;
        this.loggingPickUpDecayDrops = loggingPickUpDecayDrops;
        if (selectedLoggingFilters != null) {
            this.selectedLoggingFilters.addAll(selectedLoggingFilters);
        }
        if (selectedAnimalBreedingTargets != null) {
            this.selectedAnimalBreedingTargets.addAll(selectedAnimalBreedingTargets);
        }
        this.animalCullCap = HiredAnimalCullSettings.isValidCap(animalCullCap)
                ? animalCullCap
                : HiredAnimalCullSettings.DISABLED_CAP;
        this.animalShearing = animalShearing;
        this.forceCameraTowardsVillager = forceCameraTowardsVillager;
        this.dialogueOptions.addAll(dialogueOptions);
        this.knownLikedGiftNames.addAll(knownLikedGiftNames);
        this.knownDislikedGiftNames.addAll(knownDislikedGiftNames);
        this.giftTooltipReactions.addAll(giftTooltipReactions);
        this.familyTree = familyTree == null ? VillagerFamilyTreeSnapshot.EMPTY : familyTree;
        this.relationships = relationships == null ? VillagerRelationshipSnapshot.EMPTY : relationships;
        this.allegiance = allegiance == null ? VillageAllegianceView.EMPTY : allegiance;
        if (forcedDialogue) {
            this.page = DialoguePage.TALK;
        }
        syncCameraFocusState();
        VillagerInteractionUiAnimation.resetAnimation();
        this.animationStartMillis = Util.getMillis();
    }

    @Override
    protected void init() {
        this.lastOptionScrollRenderMillis = Util.getMillis();
        this.giftButton = addRenderableWidget(Button.builder(Component.translatable(GUI_KEY_PREFIX + "gift.give"), button -> requestGift())
                .bounds(0, 0, GIFT_BUTTON_WIDTH, GIFT_BUTTON_HEIGHT)
                .build());
        this.giftButton.visible = false;
        rebuildOptions();
        startPreparedReplacementTransition();
    }

    @Override
    public void tick() {
        if (this.closingWithAnimation && animationElapsedMillis() >= INTERACTION_ANIMATION_DURATION_MILLIS) {
            finishClosingAnimation();
            return;
        }
        this.giftLimitFeedback.tick();
        tickSkillsProfileKeepAlive();
        updateDialogueMouthAnimation();
        syncCameraFocusState();
        ClientVillagerConversationState.tickCameraFocus();
        tickMouseEasterEggs();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public boolean matchesVillager(int entityId) {
        return this.villagerEntityId == entityId;
    }

    public void updateReputation(
            int reputation,
            VillagerReputationLevel reputationLevel,
            DialogueDisposition mood,
            VillagerMood primaryMood,
            boolean forceCameraTowardsVillager,
            List<DialogueOptionDefinition> dialogueOptions,
            List<String> knownLikedGiftNames,
            List<String> knownDislikedGiftNames,
            List<GiftTooltipReaction> giftTooltipReactions) {
        this.reputation = reputation;
        this.reputationLevel = reputationLevel;
        this.mood = mood;
        this.primaryMood = primaryMood == null ? VillagerMood.NEUTRAL : primaryMood;
        this.forceCameraTowardsVillager = forceCameraTowardsVillager;
        syncCameraFocusState();
        this.dialogueOptions.clear();
        this.dialogueOptions.addAll(dialogueOptions);
        this.knownLikedGiftNames.clear();
        this.knownLikedGiftNames.addAll(knownLikedGiftNames);
        this.knownDislikedGiftNames.clear();
        this.knownDislikedGiftNames.addAll(knownDislikedGiftNames);
        this.giftTooltipReactions.clear();
        this.giftTooltipReactions.addAll(giftTooltipReactions);
        this.awaitingForcedDialogueResponse = false;
        if (this.page == DialoguePage.TALK || this.page == DialoguePage.ADVENTURES) {
            rebuildOptionsKeepingListPosition();
        }
    }

    public void updateDuelStatus(OpenVillagerDuelPayload status) {
        if (status == null || !matchesVillager(status.entityId())) {
            return;
        }
        boolean started = this.duelStartPending && status.reason() == DuelAvailabilityReason.PLAYER_BUSY;
        this.duelStartPending = false;
        this.duelStatus = status;
        if (!status.bringYourOwnAllowed() && this.duelLoadout == DuelLoadout.BRING_YOUR_OWN) {
            this.duelLoadout = DuelLoadout.BARE_HANDED;
        }
        if (started) {
            leaveConversation();
            return;
        }
        if (isDuelSetupPage(this.page)) {
            refreshDuelDialogue();
            rebuildOptionsKeepingListPosition();
        }
    }

    public void replaceFromServer() {
        this.closingFromServer = true;
        this.replacingFromServer = true;
    }

    public void continueOpenSession() {
        this.animationStartMillis = -1L;
        VillagerInteractionUiAnimation.completeAnimation();
    }

    public void closeFromServer() {
        this.closingFromServer = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != this) {
            minecraft.setScreen(this);
        }
        startClosingAnimation();
    }

    @Override
    public void acceptVillagerDialogue(String text, List<DialogueTextSegment> textSegments) {
        if (text == null || text.isBlank()) {
            return;
        }
        this.villagerDialogueTextSegments = normalizeDialogueSegments(text, textSegments);
        this.villagerDialogueText = DialogueTextSegment.plainText(this.villagerDialogueTextSegments);
        if (this.villagerDialogueText.isBlank()) {
            return;
        }
        this.dialogueLineScroll = 0;
        this.dialogueTextAnimationStartMillis = Util.getMillis();
        this.dialogueTextAnimationSkipped = dialogueTextSpeed().instant();
        this.dialogueBlipPitch = randomDialogueBlipPitch();
        this.lastDialogueBlipVisibleCharacters = 0;
        this.nextDialogueBlipVisibleCharacter = this.dialogueTextAnimationSkipped
                ? Integer.MAX_VALUE
                : randomDialogueBlipGap();
        updateDialogueMouthAnimation();
    }

    @Override
    public void copyCurrentDialogueTo(VillagerInteractionScreen target) {
        if (target == null || this.villagerDialogueText.isBlank()) {
            return;
        }
        target.acceptVillagerDialogue(this.villagerDialogueText, this.villagerDialogueTextSegments);
    }

    @Override
    public void prepareReplacementTransition(VillagerInteractionScreen target) {
        if (target == null) {
            return;
        }
        target.replacementTransitionPreviousPage = this.page;
        target.replacementTransitionPreviousTop = interactionContainerTopForPage(this.page)
                + interactionStateTransitionOffsetY();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int slideOffset = slideOffsetY();
        int stateOffsetY = interactionStateTransitionOffsetY();
        int contentOffsetX = interactionStateTransitionContentOffsetX();
        int totalOffsetY = slideOffset + stateOffsetY;
        this.renderSlideOffsetY = totalOffsetY;
        this.renderContentOffsetX = contentOffsetX;
        int interactionMouseY = mouseY - totalOffsetY;
        int interactionContentMouseX = mouseX - contentOffsetX;
        this.lastMouseX = mouseX;
        this.lastMouseY = interactionMouseY;
        updateDialogueMouthAnimation();
        focusVillagerOnPlayer();
        if (!this.closingWithAnimation) {
            updateMouseSelection(interactionContentMouseX, interactionMouseY);
            updateOptionScroll();
            updateSkillScroll();
        }

        VillagerClientUiUtil.pushGuiLayer(graphics, VillagerClientUiUtil.screenLayerZ());
        VillagerDialogueCinematicBars.render(graphics, this.width, this.height, screenVisibility(), this.cinematicBarSlant);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, totalOffsetY, 0.0F);
        renderInteractionContainer(graphics);
        graphics.pose().pushPose();
        graphics.pose().translate(contentOffsetX, 0.0F, 0.0F);
        if (usesRootIconMenu()) {
            if (this.giftButton != null) {
                this.giftButton.visible = false;
            }
            renderInteractionMenuButtons(graphics, interactionContentMouseX, interactionMouseY);
        } else if (this.page == DialoguePage.GIFT) {
            renderGiftPage(graphics, interactionContentMouseX, interactionMouseY, partialTick);
        } else if (this.page == DialoguePage.PROFILE) {
            if (this.giftButton != null) {
                this.giftButton.visible = false;
            }
            renderProfilePage(graphics, interactionContentMouseX, interactionMouseY);
        } else if (this.page == DialoguePage.SKILLS) {
            if (this.giftButton != null) {
                this.giftButton.visible = false;
            }
            renderSkillsPage(graphics, interactionContentMouseX, interactionMouseY);
        } else {
            if (this.giftButton != null) {
                this.giftButton.visible = false;
            }
            renderOptions(graphics, interactionContentMouseX, interactionMouseY, optionsTop());
        }
        graphics.pose().popPose();
        renderInteractionStatTooltips(graphics, mouseX, interactionMouseY);
        graphics.pose().popPose();
        VillagerClientUiUtil.popGuiLayer(graphics);
        VillagerReputationNotificationOverlay.renderAboveInteractionMenu(graphics, partialTick);
        if (this.page == DialoguePage.GIFT && !this.closingWithAnimation) {
            VillagerClientUiUtil.pushGuiLayer(graphics, VillagerClientUiUtil.tooltipLayerZ());
            VillagerInteractionGiftPage.renderGiftButtonTooltip(
                    this.giftPageContext,
                    graphics,
                    mouseX,
                    mouseY,
                    this.width,
                    this.height,
                    contentOffsetX,
                    totalOffsetY);
            VillagerClientUiUtil.popGuiLayer(graphics);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.closingWithAnimation) {
            return true;
        }
        noteInteractionActivity();
        if (tryOpenVanillaChat(keyCode, scanCode)) {
            return true;
        }

        if (tryActivateInteractionMenuShortcut(keyCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            goBackOrLeaveConversation();
            return true;
        }
        if (isConfirmKey(keyCode)) {
            if (this.page == DialoguePage.GIFT) {
                requestGift();
            } else {
                activateSelected();
            }
            return true;
        }
        if (isPreviousSelectionKey(keyCode, scanCode)) {
            moveSelection(-1);
            return true;
        }
        if (isNextSelectionKey(keyCode, scanCode)) {
            moveSelection(1);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.closingWithAnimation) {
            return true;
        }
        noteInteractionActivity();
        if (!isLeftMouseButton(button)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        double interactionMouseY = interactionMouseY(mouseY);
        double interactionContentMouseX = interactionContentMouseX(mouseX);
        if (this.page == DialoguePage.GIFT
                && (tryClickGiftPage(interactionContentMouseX, interactionMouseY)
                || this.giftButton != null && this.giftButton.isMouseOver(interactionContentMouseX, interactionMouseY))) {
            return true;
        }

        if (tryActivateInteractionMenuButton(interactionContentMouseX, interactionMouseY)) {
            return true;
        }


        if (tryClickSkillsProfileCycleButton(interactionContentMouseX, interactionMouseY)
                || trySelectProfileAttributeDetails(interactionContentMouseX, interactionMouseY)
                || trySelectSkillDetails(interactionContentMouseX, interactionMouseY)
                || trySelectJobDetails(interactionContentMouseX, interactionMouseY)
                || tryBeginSkillInfoScrollbarDrag(interactionContentMouseX, interactionMouseY)
                || tryBeginScrollbarDrag(interactionContentMouseX, interactionMouseY)
                || tryActivateHoveredOption(interactionContentMouseX, interactionMouseY)) {
            return true;
        }
        if (trySkipDialogueTextAnimation()) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.closingWithAnimation) {
            return true;
        }
        noteInteractionActivity();
        double interactionMouseY = interactionMouseY(mouseY);
        double interactionContentMouseX = interactionContentMouseX(mouseX);
        if (this.page == DialoguePage.GIFT
                && VillagerInteractionGiftPage.tryScroll(
                this.giftPageContext,
                interactionContentMouseX,
                interactionMouseY,
                scrollY,
                this.width,
                this.height)) {
            return true;
        }
        if (this.page == DialoguePage.SKILLS
                && maxSkillScroll() > 0.0F
                && isPointInsideSkillsInfoScrollArea(interactionContentMouseX, interactionMouseY)) {
            int direction = scrollY < 0.0D ? 1 : -1;
            setTargetSkillScroll(this.targetSkillScroll + direction);
            this.skillScroll = this.targetSkillScroll;
            return true;
        }

        if (tryScrollInteractionDialogue(mouseX, interactionMouseY, scrollY)) {
            return true;
        }

        if (maxOptionScroll() <= 0.0F || !isPointInsideOptionScrollArea(interactionContentMouseX, interactionMouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (usesRootIconMenu()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        setTargetOptionScroll(this.state.targetOptionScroll() - (float) scrollY * OPTION_SCROLL_STEP);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.closingWithAnimation) {
            return true;
        }
        noteInteractionActivity();
        double interactionMouseY = interactionMouseY(mouseY);
        if (isLeftMouseButton(button) && this.draggingSkillScrollbar) {
            return dragSkillScrollbar(interactionMouseY);
        }
        if (isLeftMouseButton(button) && this.draggingScrollbar) {
            return dragScrollbar(interactionMouseY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        noteInteractionActivity();
        if (isLeftMouseButton(button) && this.draggingSkillScrollbar) {
            this.draggingSkillScrollbar = false;
            return true;
        }
        if (isLeftMouseButton(button) && this.draggingScrollbar) {
            this.draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        noteInteractionActivity();
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        noteInteractionActivity();
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        clearDialogueMouthAnimation();
        if (this.openingChat) {
            this.openingChat = false;
            super.removed();
            return;
        }

        if (!this.replacingFromServer) {
            VillagerInteractionChatVisibility.restoreHiddenVillagerMessages(Minecraft.getInstance());
            VillagerChatEffectRenderer.startReappearFade();
            ClientVillagerConversationState.clear();
        }
        if (!this.closingFromServer) {
            sendToServer(new VillagerConversationEndRequestPayload(this.villagerEntityId));
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        goBackOrLeaveConversation();
    }

    private boolean tryOpenVanillaChat(int keyCode, int scanCode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.keyChat.matches(keyCode, scanCode)) {
            openVanillaChat("");
            return true;
        }
        if (minecraft.options.keyCommand.matches(keyCode, scanCode)) {
            openVanillaChat("/");
            return true;
        }
        return false;
    }

    private boolean tryActivateInteractionMenuShortcut(int keyCode) {
        if (!usesRootIconMenu()) {
            return false;
        }

        int index = interactionMenuShortcutIndex(keyCode);
        if (index < 0) {
            return false;
        }
        activateInteractionMenuButton(index, true);
        return true;
    }

    private static int interactionMenuShortcutIndex(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            return keyCode - GLFW.GLFW_KEY_1;
        }
        if (keyCode == GLFW.GLFW_KEY_0) {
            return 9;
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            return keyCode - GLFW.GLFW_KEY_KP_1;
        }
        if (keyCode == GLFW.GLFW_KEY_KP_0) {
            return 9;
        }
        return -1;
    }

    private static boolean isConfirmKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE;
    }

    private static boolean isPreviousSelectionKey(int keyCode, int scanCode) {
        Minecraft minecraft = Minecraft.getInstance();
        return keyCode == GLFW.GLFW_KEY_UP
                || keyCode == GLFW.GLFW_KEY_LEFT
                || minecraft.options.keyUp.matches(keyCode, scanCode)
                || minecraft.options.keyLeft.matches(keyCode, scanCode);
    }

    private static boolean isNextSelectionKey(int keyCode, int scanCode) {
        Minecraft minecraft = Minecraft.getInstance();
        return keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_RIGHT
                || minecraft.options.keyDown.matches(keyCode, scanCode)
                || minecraft.options.keyRight.matches(keyCode, scanCode);
    }

    private void openVanillaChat(String initialText) {
        this.openingChat = true;
        clearDialogueMouthAnimation();
        Minecraft.getInstance().setScreen(new VillagerInteractionChatScreen(this, initialText));
    }

    private void rebuildOptions() {
        this.options.clear();
        this.optionLayoutVersion++;
        if (this.page == DialoguePage.TALK) {
            if (this.forcedDialogue) {
                addDialogueOptions();
            } else {
                addDialogueOptions(false);
            }
        } else if (this.page == DialoguePage.ADVENTURES) {
            addDialogueOptions(true);
        } else if (this.page == DialoguePage.DUEL) {
            addDuelOptions();
        } else if (this.page == DialoguePage.DUEL_LOADOUT) {
            addDuelLoadoutOptions();
        } else if (this.page == DialoguePage.DUEL_WAGER) {
            addDuelWagerOptions();
        } else if (this.page == DialoguePage.DUEL_CONFIRM) {
            addDuelConfirmOptions();
        } else if (this.page == DialoguePage.PROFILE) {
            addProfileOptions();
        } else if (this.page == DialoguePage.SKILLS) {
            addSkillsOptions();
        } else if (this.page == DialoguePage.ALLEGIANCE) {
            addAllegianceOptions();
        } else if (this.page == DialoguePage.FAMILY) {
            addFamilyOptions();
        } else if (this.page == DialoguePage.ANCESTRY) {
            addAncestryOptions();
        } else if (this.page == DialoguePage.DESCENDANTS) {
            addDescendantOptions();
        } else if (this.page == DialoguePage.RELATIONSHIPS) {
            addRelationshipOptions();
        } else if (this.page == DialoguePage.RECRUIT) {
            addRecruitOptions();
        } else if (this.page == DialoguePage.STORAGE) {
            addStorageOptions();
        } else if (this.page == DialoguePage.PAYMENT) {
            addPaymentOptions();
        } else if (this.page == DialoguePage.HIRE) {
            addHireOptions();
        } else if (this.page == DialoguePage.HIRE_DURATION) {
            addHireDurationOptions();
        } else if (this.page == DialoguePage.CONTRACT) {
            addContractOptions();
        } else if (this.page == DialoguePage.END_CONTRACT_CONFIRMATION) {
            addEndContractConfirmationOptions();
        } else if (this.page == DialoguePage.CONTRACT_EXTENSION) {
            addContractExtensionOptions();
        } else if (this.page == DialoguePage.ROLE) {
            addRoleOptions();
        } else if (this.page == DialoguePage.ROLE_CHANGE) {
            addRoleChangeOptions();
        } else if (this.page == DialoguePage.WORK) {
            addWorkOptions();
        } else if (this.page == DialoguePage.HUNTING_OPTIONS) {
            addHuntingOptions();
        } else if (this.page == DialoguePage.FARMING_OPTIONS) {
            addFarmingOptions();
        } else if (this.page == DialoguePage.LOGGING_FILTERS) {
            addLoggingFilterOptions();
        } else if (this.page == DialoguePage.ANIMAL_HANDLING_OPTIONS) {
            addAnimalHandlingOptions();
        } else if (this.page == DialoguePage.ANIMAL_BREEDING_TARGETS) {
            addAnimalBreedingTargetOptions();
        } else if (this.page == DialoguePage.ANIMAL_CULL_CAPS) {
            addAnimalCullCapOptions();
        } else if (this.page == DialoguePage.BUILDER_STRUCTURES) {
            addBuilderCategoryOptions();
        } else if (this.page == DialoguePage.BUILDER_STRUCTURE_CATEGORY) {
            addBuilderStructureOptions();
        } else if (this.page == DialoguePage.BUILDER_CONFIRM) {
            addBuilderConfirmOptions();
        } else if (this.page == DialoguePage.BREWING_POTION) {
            addBrewingPotionOptions();
        } else if (this.page == DialoguePage.BREWING_LEVEL) {
            addBrewingLevelOptions();
        } else if (this.page == DialoguePage.BREWING_DURATION) {
            addBrewingDurationOptions();
        } else if (this.page == DialoguePage.BREWING_TYPE) {
            addBrewingTypeOptions();
        } else if (this.page == DialoguePage.BREWING_AMOUNT) {
            addBrewingAmountOptions();
        } else if (this.page == DialoguePage.ROOT) {
            if (this.clipboardMenu) {
                addClipboardMenuOptions();
            } else if (this.forcedDialogue) {
                addDialogueOptions();
            }
        }
        this.state.resetOptions(!this.options.isEmpty());
        resetPixelOptionEdgeScaleBlends();
        this.keyboardOptionFocusVisible = false;
        ensureSelectedVisible();
    }

    private void rebuildOptionsKeepingListPosition() {
        VillagerInteractionScreenState.OptionListPosition previousPosition = this.state.captureOptionListPosition();

        rebuildOptions();

        this.state.restoreOptionListPosition(previousPosition, this.options.size(), maxOptionScroll());
        ensureSelectedVisible();
    }

    private void rememberCurrentPageOptionListPosition() {
        if (this.page == DialoguePage.ROOT) {
            return;
        }
        this.rememberedPageOptionPositions.put(this.page, this.state.captureOptionListPosition());
    }

    private void restoreRememberedPageOptionListPosition(DialoguePage page) {
        if (page == DialoguePage.ROOT) {
            return;
        }
        VillagerInteractionScreenState.OptionListPosition rememberedPosition = this.rememberedPageOptionPositions.get(page);
        if (rememberedPosition == null) {
            return;
        }
        this.state.restoreOptionListPosition(rememberedPosition, this.options.size(), maxOptionScroll());
        ensureSelectedVisible();
    }

    private void addDialogueOptions() {
        for (DialogueOptionDefinition option : this.dialogueOptions) {
            addDialogueOption(option);
        }
    }

    private void addDialogueOptions(boolean adventuresOnly) {
        for (DialogueOptionDefinition option : this.dialogueOptions) {
            if (isQuestOption(option) != adventuresOnly) {
                continue;
            }
            addDialogueOption(option);
        }
    }


    private void addProfileOptions() {
    }

    private void addDuelOptions() {
        if (this.duelStatus == null) {
            addPassiveOption("duel.loading");
            return;
        }
        if (!this.duelStatus.available()) {
            addOption("duel.leave", this::navigateToRootPage);
            return;
        }
        this.options.add(DialogueOption.enabled(
                translate("duel.choose_loadout", selectedDuelLoadoutLabel()),
                () -> openPage(DialoguePage.DUEL_LOADOUT)));
        this.options.add(DialogueOption.enabled(
                translate("duel.choose_wager", selectedDuelStakeLabel()),
                () -> openPage(DialoguePage.DUEL_WAGER)));
        addOption("duel.issue_challenge", () -> openPage(DialoguePage.DUEL_CONFIRM));
        addOption("duel.nevermind", this::navigateToRootPage);
    }

    private void addDuelLoadoutOptions() {
        if (!canConfigureDuel()) {
            addDuelSetupUnavailableOption();
            addOption("duel.back", () -> openPage(DialoguePage.DUEL));
            return;
        }
        for (DuelLoadout loadout : DuelLoadout.values()) {
            boolean allowed = loadout != DuelLoadout.BRING_YOUR_OWN || this.duelStatus.bringYourOwnAllowed();
            this.options.add(DialogueOption.checkbox(
                    duelLoadoutOptionLabel(loadout),
                    this.duelLoadout == loadout,
                    allowed
                            ? () -> {
                                this.duelLoadout = loadout;
                                openPage(DialoguePage.DUEL);
                            }
                            : NO_ACTION,
                    !allowed));
        }
        addOption("duel.back", () -> openPage(DialoguePage.DUEL));
    }

    private void addDuelWagerOptions() {
        if (!canConfigureDuel()) {
            addDuelSetupUnavailableOption();
            addOption("duel.back", () -> openPage(DialoguePage.DUEL));
            return;
        }
        int maximumStake = duelMaximumStake();
        Set<Integer> offeredStakes = new LinkedHashSet<>();
        for (int index = 0; index < DUEL_STAKES.length; index++) {
            int optionIndex = index;
            int stake = duelStakeForIndex(optionIndex);
            if (!offeredStakes.add(stake)) {
                continue;
            }
            boolean affordable = stake <= maximumStake;
            this.options.add(DialogueOption.checkbox(
                    duelStakeOptionLabel(stake, DUEL_STAKES[optionIndex] == Integer.MAX_VALUE),
                    selectedDuelStake() == stake,
                    affordable
                            ? () -> {
                                this.duelStakeIndex = optionIndex;
                                openPage(DialoguePage.DUEL);
                            }
                            : NO_ACTION,
                    !affordable));
        }
        addOption("duel.back", () -> openPage(DialoguePage.DUEL));
    }

    private void addDuelConfirmOptions() {
        if (this.duelStartPending) {
            addPassiveOption("duel.starting");
            return;
        }
        if (!canConfigureDuel()) {
            addDuelSetupUnavailableOption();
            addOption("duel.back", () -> openPage(DialoguePage.DUEL));
            return;
        }
        int stake = selectedDuelStake();
        if (stake > duelMaximumStake()) {
            addPassiveOption("duel.wager_unavailable");
            addOption("duel.back", () -> openPage(DialoguePage.DUEL_WAGER));
            return;
        }
        addOption("duel.confirm", this::startDuel);
        addOption("duel.change_terms", () -> openPage(DialoguePage.DUEL));
    }

    private void addDuelSetupUnavailableOption() {
        this.options.add(DialogueOption.enabled(
                this.duelStatus == null ? translate("duel.loading") : duelUnavailableText(),
                NO_ACTION,
                true));
    }

    private boolean canConfigureDuel() {
        return this.duelStatus != null && this.duelStatus.available();
    }

    private int duelMaximumStake() {
        return this.duelStatus == null ? 0 : this.duelStatus.maximumStake();
    }

    private int selectedDuelStake() {
        return duelStakeForIndex(this.duelStakeIndex);
    }

    private int duelStakeForIndex(int index) {
        int resolvedIndex = Math.max(0, Math.min(index, DUEL_STAKES.length - 1));
        int configuredStake = DUEL_STAKES[resolvedIndex];
        return configuredStake == Integer.MAX_VALUE ? duelMaximumStake() : configuredStake;
    }

    private int selectedDuelWireStake() {
        int resolvedIndex = Math.max(0, Math.min(this.duelStakeIndex, DUEL_STAKES.length - 1));
        return DUEL_STAKES[resolvedIndex];
    }

    private String selectedDuelLoadoutLabel() {
        return duelLoadoutLabel(this.duelLoadout);
    }

    private static String duelLoadoutLabel(DuelLoadout loadout) {
        return translate("duel.loadout." + loadout.name().toLowerCase(Locale.ROOT));
    }

    private static String duelLoadoutOptionLabel(DuelLoadout loadout) {
        return translate("duel.loadout_option." + loadout.name().toLowerCase(Locale.ROOT));
    }

    private String selectedDuelStakeLabel() {
        int stake = selectedDuelStake();
        return stake == 0
                ? translate("duel.stake.none")
                : translate("duel.stake.summary", stake, duelCurrencyName());
    }

    private String duelStakeOptionLabel(int stake, boolean maximum) {
        if (stake == 0) {
            return translate("duel.stake_option.none");
        }
        return translate(maximum ? "duel.stake_option.maximum" : "duel.stake_option.amount",
                stake, duelCurrencyName());
    }

    private String duelCurrencyName() {
        return this.duelStatus == null ? "" : this.duelStatus.currencyName();
    }

    private String duelUnavailableText() {
        if (this.duelStatus == null) {
            return translate("duel.loading");
        }
        if (this.duelStatus.reason() == DuelAvailabilityReason.COOLDOWN) {
            return I18n.get("villagerretaliation.duel.unavailable.cooldown_remaining",
                    formatDuelTicks(this.duelStatus.cooldownTicks()));
        }
        return I18n.get("villagerretaliation.duel.unavailable."
                + this.duelStatus.reason().name().toLowerCase(Locale.ROOT));
    }

    private void refreshDuelDialogue() {
        if (this.duelStatus == null) {
            return;
        }
        String dialogue = this.duelStartPending
                ? this.duelStatus.startingDialogue()
                : switch (this.page) {
                    case DUEL_LOADOUT -> this.duelStatus.loadoutDialogue();
                    case DUEL_WAGER -> this.duelStatus.wagerDialogue();
                    case DUEL_CONFIRM -> this.duelStatus.confirmationDialogue();
                    default -> this.duelStatus.openingDialogue();
                };
        acceptVillagerDialogue(dialogue, List.of());
    }

    private void startDuel() {
        if (!canConfigureDuel() || selectedDuelStake() > duelMaximumStake()) {
            return;
        }
        this.duelStartPending = true;
        refreshDuelDialogue();
        rebuildOptionsKeepingListPosition();
        sendToServer(new VillagerDuelRequestPayload(
                this.villagerEntityId,
                VillagerDuelRequestPayload.Action.START,
                this.duelLoadout,
                selectedDuelWireStake()));
    }

    private static String formatDuelTicks(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        if (seconds >= 60L && seconds % 60L == 0L) {
            return (seconds / 60L) + "m";
        }
        return seconds + "s";
    }

    private void addSkillsOptions() {
    }

    private void addRecruitOptions() {
        if (this.recruitedPartyVillager) {
            if (!this.partyVillagerPartyMember) {
                addPassiveOption("party.leader_only");
                return;
            }
            addOption("party.about_contract", this::openContractPage);
            if (canRequestPartyVillagerInventory()) {
                addOption("party.inventory", () -> requestRecruit(VillagerRecruitRequestPayload.Action.OPEN_JOB_INVENTORY));
            }
            if (this.partyVillagerAuthorized) {
                PartyRosterSyncPayload.VillagerEntry settings = partyVillagerSettings();
                PartyCombatMode combatMode = settings == null
                        ? PartyCombatMode.ATTACK_WITH_PARTY
                        : settings.combatMode();
                PartyAttackMode attackMode = settings == null ? PartyAttackMode.ALL : settings.attackMode();
                PartyDropCollectionMode dropMode = settings == null
                        ? PartyDropCollectionMode.OFF
                        : settings.dropCollectionMode();
                addPartySettingOption(
                        "party.combat_mode",
                        "party.combat_mode." + combatMode.name().toLowerCase(java.util.Locale.ROOT),
                        VillagerRecruitRequestPayload.Action.CYCLE_PARTY_COMBAT_MODE);
                addPartySettingOption(
                        "party.attack_mode",
                        "party.attack_mode." + attackMode.name().toLowerCase(java.util.Locale.ROOT),
                        VillagerRecruitRequestPayload.Action.CYCLE_PARTY_ATTACK_MODE);
                addPartySettingOption(
                        "party.collect_drops",
                        switch (dropMode) {
                            case OFF -> "party.drop_collection.off";
                            case SLAIN_ENTITIES -> "party.drop_collection.slain_entities";
                            case ALL_DROPS -> "party.drop_collection.all_drops";
                        },
                        VillagerRecruitRequestPayload.Action.CYCLE_PARTY_DROP_COLLECTION);
                if (this.stayingHere) {
                    addOption("party.follow_me", () -> requestRecruit(VillagerRecruitRequestPayload.Action.FOLLOW));
                } else {
                    addOption("party.stay_here", () -> requestRecruit(VillagerRecruitRequestPayload.Action.STAY_HERE));
                }
                if (this.mountFeatureAvailable) {
                    addOption(this.assignedMount ? "party.unassign_mount" : "party.assign_mount",
                            () -> requestRecruit(this.assignedMount
                                    ? VillagerRecruitRequestPayload.Action.UNASSIGN_MOUNT
                                    : VillagerRecruitRequestPayload.Action.START_MOUNT_ASSIGNMENT));
                }
                addOption("party.unequip_weapons",
                        () -> requestRecruit(VillagerRecruitRequestPayload.Action.UNEQUIP_PARTY_WEAPONS));
                addOption("party.dismiss", this::openPartyDismissConfirmationPage);
            }
            return;
        }
        if (this.partyRecruitAvailable) {
            addOption("party.recruit", this::openPartyRecruitConfirmationPage);
        }
        if (this.hiredByPlayer && !this.oneOffBuilderJob) {
            addOption("recruit.about_contract", this::openContractPage);
        } else if (this.hiredByOtherPlayer) {
            addOption("recruit.contract", () -> requestRecruit(VillagerRecruitRequestPayload.Action.VIEW_CONTRACT));
        } else if (canHireVillager()) {
            addOption("recruit.hire", this::openHirePage);
        }
        if (this.jobInventoryAvailable) {
            addOption("recruit.job_inventory", () -> requestRecruit(VillagerRecruitRequestPayload.Action.OPEN_JOB_INVENTORY));
        }
        if (this.hiredByPlayer) {
            addOption("recruit.storage", this::openStoragePage);
        }
        if (this.hiredByPlayer) {
            if (!this.oneOffBuilderJob) {
                addOption("recruit.payment", this::openPaymentPage);
                addOption("recruit.about_role", this::openRolePage);
            }
            addOption("recruit.work", this::openWorkPage);
            if (this.mountFeatureAvailable) {
                addOption(this.assignedMount ? "recruit.unassign_mount" : "recruit.assign_mount",
                        () -> requestRecruit(this.assignedMount
                                ? VillagerRecruitRequestPayload.Action.UNASSIGN_MOUNT
                                : VillagerRecruitRequestPayload.Action.START_MOUNT_ASSIGNMENT));
            }
        }
        if (this.hiredByPlayer && !this.oneOffBuilderJob) {
            addOption("recruit.end_hire", () -> {
                openEndContractConfirmationPage();
                requestRecruit(VillagerRecruitRequestPayload.Action.PROMPT_END_HIRE_CONFIRMATION);
            });
        }
        if (this.followingPlayer) {
            addOption("recruit.stop_following", () -> requestRecruit(VillagerRecruitRequestPayload.Action.STOP_FOLLOWING));
        } else if (this.stayingHere) {
            addOption("recruit.stop_staying_here", () -> requestRecruit(VillagerRecruitRequestPayload.Action.STOP_STAYING_HERE));
        }
    }

    private void addStorageOptions() {
        addOption("recruit.show_storage", () -> requestRecruit(VillagerRecruitRequestPayload.Action.SHOW_STORAGE));
        addOption("recruit.deposit_earnings", () -> requestRecruit(VillagerRecruitRequestPayload.Action.DEPOSIT_EARNINGS));
        addOption("recruit.remove_storage", () -> requestRecruit(VillagerRecruitRequestPayload.Action.REMOVE_STORAGE));
        addOption("recruit.nevermind", this::openRecruitPage);
    }

    private void addPaymentOptions() {
        addOption("recruit.auto_payment", () -> requestRecruit(VillagerRecruitRequestPayload.Action.TOGGLE_AUTO_PAYMENT));
        addOption("recruit.show_payment_storage", () -> requestRecruit(VillagerRecruitRequestPayload.Action.SHOW_PAYMENT_STORAGE));
        addOption("recruit.remove_payment_storage", () -> requestRecruit(VillagerRecruitRequestPayload.Action.REMOVE_PAYMENT_STORAGE));
        addOption("recruit.nevermind", this::openRecruitPage);
    }

    private void addHireOptions() {
        addPassiveOption("recruit.hire_services_intro");
        addHireRoleOption(HiredVillagerRole.COMBAT, "recruit.role_combat");
        addHireRoleOption(HiredVillagerRole.HUNTING, "recruit.role_hunting");
        addHireRoleOption(HiredVillagerRole.MINING, "recruit.role_mining");
        addHireRoleOption(HiredVillagerRole.LOGGING, "recruit.role_logging");
        addHireRoleOption(HiredVillagerRole.FARMING, "recruit.role_farming");
        addHireRoleOption(HiredVillagerRole.FISHING, "recruit.role_fishing");
        addHireRoleOption(HiredVillagerRole.BREWING, "recruit.role_brewing");
        addHireRoleOption(HiredVillagerRole.CRAFTSMAN, "recruit.role_craftsman");
        addHireRoleOption(HiredVillagerRole.COOK, "recruit.role_cook");
        addHireRoleOption(HiredVillagerRole.SMELTER, "recruit.role_smelter");
        addHireRoleOption(HiredVillagerRole.COURIER, "recruit.role_courier");
        addHireRoleOption(HiredVillagerRole.ANIMAL_HANDLING, "recruit.role_animal_handling");
        addHireRoleOption(HiredVillagerRole.NITWIT, "recruit.role_nitwit");
        if (canOfferBuilderService()) {
            addOption("recruit.buy_blueprints", this::openBuilderStructuresPage);
        }
        addOption("recruit.nevermind", this::openRecruitPage);
    }

    private void addHireDurationOptions() {
        if (this.pendingHireRole == null || !canOfferContractRole(this.pendingHireRole)) {
            addOption("recruit.nevermind", this::openRecruitPage);
            return;
        }
        this.options.add(DialogueOption.enabled(translate("recruit.hire_selected_role", this.pendingHireRole.label()), NO_ACTION));
        addOption("recruit.hire_one_day", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_ONE_DAY));
        addOption("recruit.hire_three_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_THREE_DAYS));
        addOption("recruit.hire_five_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_FIVE_DAYS));
        addOption("recruit.hire_seven_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_SEVEN_DAYS));
        addOption("recruit.hire_fifteen_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_FIFTEEN_DAYS));
        addOption("recruit.hire_thirty_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_THIRTY_DAYS));
        addOption("recruit.nevermind", this::openHirePage);
    }

    private void addContractOptions() {
        if (this.recruitedPartyVillager) {
            this.options.add(DialogueOption.enabled(
                    translate("party.remaining_days", this.partyRemainingDays),
                    NO_ACTION));
        }
        addOption(this.recruitedPartyVillager ? "party.contract_days_left" : "recruit.contract_days_left",
                () -> requestRecruit(VillagerRecruitRequestPayload.Action.VIEW_CONTRACT));
        addOption("recruit.extend_contract", this::openContractExtensionPage);
        addOption("recruit.nevermind", this::openRecruitPage);
    }

    private void addEndContractConfirmationOptions() {
        if (this.confirmingPartyRecruit) {
            addOption("party.recruit_confirm", () -> requestRecruit(VillagerRecruitRequestPayload.Action.PARTY_RECRUIT));
            addOption("recruit.nevermind", () -> {
                requestRecruit(VillagerRecruitRequestPayload.Action.DECLINE_PARTY_RECRUIT_CONFIRMATION);
                openRecruitPage();
            });
            return;
        }
        if (this.confirmingPartyDismiss) {
            addOption("party.dismiss_confirm", () -> requestRecruit(VillagerRecruitRequestPayload.Action.PARTY_DISMISS));
            addOption("recruit.nevermind", () -> {
                requestRecruit(VillagerRecruitRequestPayload.Action.DECLINE_PARTY_DISMISS_CONFIRMATION);
                openRecruitPage();
            });
            return;
        }
        addOption("recruit.end_hire_confirm", () -> requestRecruit(VillagerRecruitRequestPayload.Action.END_HIRE));
        addOption("recruit.nevermind", () -> {
            requestRecruit(VillagerRecruitRequestPayload.Action.DECLINE_END_HIRE_CONFIRMATION);
            openRecruitPage();
        });
    }

    private void addContractExtensionOptions() {
        if (this.recruitedPartyVillager) {
            addOption("party.extend_one_day", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_ONE_DAY));
            addOption("party.extend_three_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_THREE_DAYS));
            addOption("party.extend_five_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_FIVE_DAYS));
            addOption("party.extend_seven_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_SEVEN_DAYS));
            addOption("party.extend_fifteen_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_FIFTEEN_DAYS));
            addPartyMaxExtensionOption();
            addOption("recruit.nevermind", this::openContractPage);
            return;
        }
        addOption("recruit.extend_one_day", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_ONE_DAY));
        addOption("recruit.extend_three_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_THREE_DAYS));
        addOption("recruit.extend_five_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_FIVE_DAYS));
        addOption("recruit.extend_seven_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_SEVEN_DAYS));
        addOption("recruit.extend_fifteen_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_FIFTEEN_DAYS));
        addOption("recruit.extend_thirty_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_THIRTY_DAYS));
        addOption("recruit.nevermind", this::openContractPage);
    }

    private void addPartyMaxExtensionOption() {
        int maxExtensionDays = Math.max(0, VillagerContractTime.MAX_PREPAID_DAYS - this.partyRemainingDays);
        int cost = maxExtensionDays * PartyVillagerContractService.DAILY_EMERALD_COST;
        this.options.add(DialogueOption.enabled(
                translate("party.extend_max_days", maxExtensionDays, cost),
                () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_MAX_DAYS)));
    }

    private void addRoleOptions() {
        addOption("recruit.current_role", () -> requestRecruit(VillagerRecruitRequestPayload.Action.VIEW_ROLE));
        addOption("recruit.change_role", this::openRoleChangePage);
        if (this.mountFeatureAvailable && this.assignedMount) {
            addOption(this.mountedTravelEnabled
                            ? "recruit.mounted_travel.disable"
                            : "recruit.mounted_travel.enable",
                    () -> requestRecruit(VillagerRecruitRequestPayload.Action.TOGGLE_MOUNTED_TRAVEL));
        }
        addOption("recruit.nevermind", this::openRecruitPage);
    }

    private void addRoleChangeOptions() {
        addRoleChangeOption(HiredVillagerRole.COMBAT, "recruit.role_combat", VillagerRecruitRequestPayload.Action.SET_ROLE_COMBAT);
        addRoleChangeOption(HiredVillagerRole.HUNTING, "recruit.role_hunting", VillagerRecruitRequestPayload.Action.SET_ROLE_HUNTING);
        addRoleChangeOption(HiredVillagerRole.MINING, "recruit.role_mining", VillagerRecruitRequestPayload.Action.SET_ROLE_MINING);
        addRoleChangeOption(HiredVillagerRole.LOGGING, "recruit.role_logging", VillagerRecruitRequestPayload.Action.SET_ROLE_LOGGING);
        addRoleChangeOption(HiredVillagerRole.FARMING, "recruit.role_farming", VillagerRecruitRequestPayload.Action.SET_ROLE_FARMING);
        addRoleChangeOption(HiredVillagerRole.FISHING, "recruit.role_fishing", VillagerRecruitRequestPayload.Action.SET_ROLE_FISHING);
        addRoleChangeOption(HiredVillagerRole.BREWING, "recruit.role_brewing", VillagerRecruitRequestPayload.Action.SET_ROLE_BREWING);
        addRoleChangeOption(HiredVillagerRole.CRAFTSMAN, "recruit.role_craftsman", VillagerRecruitRequestPayload.Action.SET_ROLE_CRAFTSMAN);
        addRoleChangeOption(HiredVillagerRole.COOK, "recruit.role_cook", VillagerRecruitRequestPayload.Action.SET_ROLE_COOK);
        addRoleChangeOption(HiredVillagerRole.SMELTER, "recruit.role_smelter", VillagerRecruitRequestPayload.Action.SET_ROLE_SMELTER);
        addRoleChangeOption(HiredVillagerRole.COURIER, "recruit.role_courier", VillagerRecruitRequestPayload.Action.SET_ROLE_COURIER);
        addRoleChangeOption(HiredVillagerRole.BUILDER, "recruit.role_builder", VillagerRecruitRequestPayload.Action.SET_ROLE_BUILDER);
        addRoleChangeOption(HiredVillagerRole.ANIMAL_HANDLING, "recruit.role_animal_handling", VillagerRecruitRequestPayload.Action.SET_ROLE_ANIMAL_HANDLING);
        addRoleChangeOption(HiredVillagerRole.NITWIT, "recruit.role_nitwit", VillagerRecruitRequestPayload.Action.SET_ROLE_NITWIT);
        addOption("recruit.nevermind", this::openRolePage);
    }

    private void addWorkOptions() {
        addOption("recruit.work_status", () -> requestRecruit(VillagerRecruitRequestPayload.Action.VIEW_WORK_STATUS));
        addOption("recruit.work_toggle", () -> requestRecruit(VillagerRecruitRequestPayload.Action.TOGGLE_WORK_ENABLED));
        addOption("recruit.work_assigned_supplies", () -> requestRecruit(VillagerRecruitRequestPayload.Action.TOGGLE_USE_ASSIGNED_SUPPLIES));
        addOption("recruit.work_auto_deposit", () -> requestRecruit(VillagerRecruitRequestPayload.Action.TOGGLE_AUTO_DEPOSIT_OUTPUTS));
        addRoleWorkConfigOption(HiredVillagerRole.COMBAT, "recruit.work_config_combat", VillagerRecruitRequestPayload.Action.CONFIGURE_COMBAT);
        if (isActiveHiredRole(HiredVillagerRole.HUNTING)) {
            addOption("recruit.work_config_hunting", this::openHuntingOptionsPage);
        }
        addRoleWorkConfigOption(HiredVillagerRole.MINING, "recruit.work_config_mining", VillagerRecruitRequestPayload.Action.CONFIGURE_MINING);
        if (isActiveHiredRole(HiredVillagerRole.MINING)) {
            addOption("recruit.work_horizontal_floor_patching", () -> requestRecruit(
                    VillagerRecruitRequestPayload.Action.TOGGLE_HORIZONTAL_MINING_FLOOR_PATCHING));
        }
        if (isActiveHiredRole(HiredVillagerRole.LOGGING)) {
            addOption("recruit.work_config_logging", this::openLoggingFiltersPage);
        }
        if (isActiveHiredRole(HiredVillagerRole.FARMING)) {
            addOption("recruit.work_config_farming", this::openFarmingOptionsPage);
        }
        addRoleWorkConfigOption(HiredVillagerRole.FISHING, "recruit.work_config_fishing", VillagerRecruitRequestPayload.Action.CONFIGURE_FISHING);
        if (isActiveHiredRole(HiredVillagerRole.BREWING)) {
            if (this.activeBrewingOrder) {
                addOption("recruit.stop_brewing", () -> requestRecruit(VillagerRecruitRequestPayload.Action.STOP_BREWING));
            } else {
                addOption("recruit.work_config_brewing", this::openBrewingPotionPage);
            }
        }
        if (isActiveHiredRole(HiredVillagerRole.CRAFTSMAN)) {
            addOption("recruit.work_config_craftsman", () -> requestRecruit(VillagerRecruitRequestPayload.Action.CYCLE_CRAFTSMAN_MODE));
        }
        if (isActiveHiredRole(HiredVillagerRole.BUILDER)) {
            if (this.activeBuilderTask) {
                addOption("recruit.stop_builder_build", () -> requestRecruit(VillagerRecruitRequestPayload.Action.STOP_BUILDER_BUILD));
            } else {
                addOption("recruit.work_config_builder", this::openBuilderStructuresPage);
            }
        }
        if (isActiveHiredRole(HiredVillagerRole.ANIMAL_HANDLING)) {
            addOption("recruit.work_config_animal_handling", this::openAnimalHandlingOptionsPage);
        }
        addRoleWorkConfigOption(HiredVillagerRole.NITWIT, "recruit.work_config_nitwit", VillagerRecruitRequestPayload.Action.CONFIGURE_NITWIT);
        addOption("recruit.nevermind", this::openRecruitPage);
    }

    private void addLoggingFilterOptions() {
        addLoggingOption(HiredLoggingOptions.STRIP_LOGS, "recruit.logging_strip_logs", this.loggingStripLogs);
        addLoggingOption(HiredLoggingOptions.HARVEST_LEAVES, "recruit.logging_harvest_leaves", this.loggingHarvestLeaves);
        addLoggingOption(HiredLoggingOptions.BONEMEAL_SAPLINGS, "recruit.logging_bonemeal_saplings", this.loggingBonemealSaplings);
        addLoggingOption(HiredLoggingOptions.PLANT_SAPLINGS, "recruit.logging_plant_saplings", this.loggingPlantSaplings);
        addLoggingOption(HiredLoggingOptions.PICK_UP_DECAY_DROPS, "recruit.logging_pick_up_decay_drops", this.loggingPickUpDecayDrops);
        this.options.add(DialogueOption.checkbox(
                translate("recruit.logging_any"),
                this.selectedLoggingFilters.isEmpty(),
                () -> requestLoggingFilter("any")));
        List<ResourceLocation> filters = HiredLoggingFilters.options();
        if (filters.isEmpty()) {
            this.options.add(DialogueOption.enabled(translate("recruit.logging_no_filters"), NO_ACTION));
        }
        for (ResourceLocation filter : filters) {
            String id = filter.toString();
            this.options.add(DialogueOption.checkbox(
                    HiredLoggingFilters.label(filter),
                    this.selectedLoggingFilters.contains(id),
                    () -> requestLoggingFilter(id)));
        }
        addOption("recruit.nevermind", this::openWorkPage);
    }

    private void addFarmingOptions() {
        addFarmingOption(HiredFarmingOptions.TILL_SOIL, "recruit.farming_till_soil", this.farmingTillSoil);
        addOption("recruit.nevermind", this::openWorkPage);
    }

    private void addHuntingOptions() {
        addHuntingTargetOption(HiredHuntingTargets.ANIMALS, "recruit.hunting_animals", this.huntingAnimals);
        addHuntingTargetOption(HiredHuntingTargets.HOSTILES, "recruit.hunting_hostiles", this.huntingHostiles);
        addHuntingTargetOption(HiredHuntingTargets.PLAYERS, "recruit.hunting_players", this.huntingPlayers);
        addHuntingTargetOption(HiredHuntingTargets.ALL, "recruit.hunting_all", this.huntingAnimals && this.huntingHostiles);
        addOption("recruit.nevermind", this::openWorkPage);
    }

    private void addAnimalHandlingOptions() {
        addOption("recruit.animal_handling_targets", this::openAnimalBreedingTargetsPage);
        this.options.add(DialogueOption.checkbox(
                translate("recruit.animal_shearing"),
                this.animalShearing,
                () -> requestAnimalHandlingOption(HiredAnimalHandlingOptions.SHEAR_SHEEP)));
        this.options.add(DialogueOption.enabled(
                translate("recruit.animal_cull_cap", animalCullCapLabel(this.animalCullCap)),
                this::openAnimalCullCapsPage));
        addOption("recruit.nevermind", this::openWorkPage);
    }

    private void addAnimalBreedingTargetOptions() {
        this.options.add(DialogueOption.checkbox(
                translate("recruit.animal_breeding_all"),
                this.selectedAnimalBreedingTargets.isEmpty(),
                () -> requestAnimalBreedingTarget("all")));
        List<ResourceLocation> targets = HiredAnimalBreedingTargets.options();
        if (targets.isEmpty()) {
            this.options.add(DialogueOption.enabled(translate("recruit.animal_breeding_no_targets"), NO_ACTION));
        }
        for (ResourceLocation target : targets) {
            String id = target.toString();
            this.options.add(DialogueOption.checkbox(
                    HiredAnimalBreedingTargets.label(target),
                    this.selectedAnimalBreedingTargets.contains(id),
                    () -> requestAnimalBreedingTarget(id)));
        }
        addOption("recruit.nevermind", this::openAnimalHandlingOptionsPage);
    }

    private void addAnimalCullCapOptions() {
        this.options.add(DialogueOption.checkbox(
                translate("recruit.animal_cull_disabled"),
                this.animalCullCap == HiredAnimalCullSettings.DISABLED_CAP,
                () -> requestAnimalCullCap(HiredAnimalCullSettings.DISABLED_CAP)));
        for (int cap : HiredAnimalCullSettings.capOptions()) {
            this.options.add(DialogueOption.checkbox(
                    translate("recruit.animal_cull_cap_option", cap),
                    this.animalCullCap == cap,
                    () -> requestAnimalCullCap(cap)));
        }
        addOption("recruit.nevermind", this::openAnimalHandlingOptionsPage);
    }

    private void addBrewingPotionOptions() {
        if (this.minecraft == null || this.minecraft.level == null) {
            this.options.add(DialogueOption.enabled(translate("recruit.brewing_no_recipes"), NO_ACTION));
        } else {
            List<HiredBrewingRecipeCatalog.BrewingPotionChoice> choices = HiredBrewingRecipeCatalog.potionChoices(this.minecraft.level);
            if (choices.isEmpty()) {
                this.options.add(DialogueOption.enabled(translate("recruit.brewing_no_recipes"), NO_ACTION));
            }
            for (HiredBrewingRecipeCatalog.BrewingPotionChoice choice : choices) {
                this.options.add(DialogueOption.enabled(choice.label(), () -> {
                    this.selectedBrewingPotionChoice = choice;
                    this.selectedBrewingDurationChoice = null;
                    this.selectedBrewingLevelChoice = null;
                    this.selectedBrewingRoute = null;
                    openPage(DialoguePage.BREWING_LEVEL);
                }));
            }
        }
        addOption("recruit.nevermind", this::openWorkPage);
    }

    private void addBrewingLevelOptions() {
        if (this.selectedBrewingPotionChoice == null) {
            openBrewingPotionPage();
            return;
        }
        List<HiredBrewingRecipeCatalog.BrewingLevelChoice> choices = HiredBrewingRecipeCatalog.levelChoices(this.selectedBrewingPotionChoice);
        if (choices.isEmpty()) {
            this.options.add(DialogueOption.enabled(translate("recruit.brewing_no_variants"), NO_ACTION));
        }
        for (HiredBrewingRecipeCatalog.BrewingLevelChoice choice : choices) {
            this.options.add(DialogueOption.enabled(levelLabel(choice.level()), () -> {
                this.selectedBrewingLevelChoice = choice;
                this.selectedBrewingDurationChoice = null;
                this.selectedBrewingRoute = null;
                openPage(DialoguePage.BREWING_DURATION);
            }));
        }
        addOption("recruit.nevermind", this::openBrewingPotionPage);
    }

    private void addBrewingDurationOptions() {
        if (this.selectedBrewingLevelChoice == null) {
            openPage(DialoguePage.BREWING_LEVEL);
            return;
        }
        List<HiredBrewingRecipeCatalog.BrewingDurationChoice> choices = HiredBrewingRecipeCatalog.durationChoices(this.selectedBrewingLevelChoice);
        if (choices.isEmpty()) {
            this.options.add(DialogueOption.enabled(translate("recruit.brewing_no_variants"), NO_ACTION));
        }
        for (HiredBrewingRecipeCatalog.BrewingDurationChoice choice : choices) {
            this.options.add(DialogueOption.enabled(durationLabel(choice.durationTicks()), () -> {
                this.selectedBrewingDurationChoice = choice;
                this.selectedBrewingRoute = null;
                openPage(DialoguePage.BREWING_TYPE);
            }));
        }
        addOption("recruit.nevermind", () -> openPage(DialoguePage.BREWING_LEVEL));
    }

    private void addBrewingTypeOptions() {
        if (this.selectedBrewingDurationChoice == null) {
            openPage(DialoguePage.BREWING_DURATION);
            return;
        }
        List<HiredBrewingRecipeCatalog.BrewingTypeChoice> choices = HiredBrewingRecipeCatalog.typeChoices(this.selectedBrewingDurationChoice);
        if (choices.isEmpty()) {
            this.options.add(DialogueOption.enabled(translate("recruit.brewing_no_variants"), NO_ACTION));
        }
        for (HiredBrewingRecipeCatalog.BrewingTypeChoice choice : choices) {
            this.options.add(DialogueOption.enabled(typeLabel(choice), () -> {
                this.selectedBrewingRoute = choice.route();
                openPage(DialoguePage.BREWING_AMOUNT);
            }));
        }
        addOption("recruit.nevermind", () -> openPage(DialoguePage.BREWING_DURATION));
    }

    private void addBrewingAmountOptions() {
        if (this.selectedBrewingRoute == null) {
            openPage(DialoguePage.BREWING_TYPE);
            return;
        }
        addBrewingAmountOption("recruit.brewing_amount_1", 1, false);
        addBrewingAmountOption("recruit.brewing_amount_3", 3, false);
        addBrewingAmountOption("recruit.brewing_amount_6", 6, false);
        addBrewingAmountOption("recruit.brewing_amount_9", 9, false);
        addBrewingAmountOption("recruit.brewing_amount_27", 27, false);
        addBrewingAmountOption("recruit.brewing_amount_continuous", 0, true);
        addOption("recruit.nevermind", () -> openPage(DialoguePage.BREWING_TYPE));
    }

    private void addBrewingAmountOption(String labelKey, int amount, boolean continuous) {
        addOption(labelKey, () -> requestBrewingOrder(this.selectedBrewingRoute, amount, continuous));
    }

    private void addBuilderCategoryOptions() {
        Set<String> categories = new LinkedHashSet<>();
        for (BuilderStructureCatalog.Entry entry : BuilderStructureCatalog.entries()) {
            categories.add(entry.category());
        }
        if (categories.isEmpty()) {
            this.options.add(DialogueOption.enabled(translate("recruit.builder_no_structures"), NO_ACTION));
        }
        for (String category : categories) {
            this.options.add(DialogueOption.enabled(
                    translate("recruit.builder_category", category),
                    () -> openBuilderStructureCategoryPage(category)));
        }
        addOption("recruit.nevermind", this::openBuilderReturnPage);
    }

    private void addLoggingOption(String optionId, String translationKey, boolean enabled) {
        this.options.add(DialogueOption.checkbox(
                translate(translationKey),
                enabled,
                () -> requestLoggingOption(optionId)));
    }

    private void addFarmingOption(String optionId, String translationKey, boolean enabled) {
        this.options.add(DialogueOption.checkbox(
                translate(translationKey),
                enabled,
                () -> requestFarmingOption(optionId)));
    }

    private void addHuntingTargetOption(String targetId, String translationKey, boolean enabled) {
        this.options.add(DialogueOption.checkbox(
                translate(translationKey),
                enabled,
                () -> requestHuntingTarget(targetId)));
    }

    private void addBuilderStructureOptions() {
        if (this.selectedBuilderCategory == null || this.selectedBuilderCategory.isBlank()) {
            openBuilderStructuresPage();
            return;
        }
        int added = 0;
        for (BuilderStructureCatalog.Entry entry : BuilderStructureCatalog.entries()) {
            if (!entry.category().equals(this.selectedBuilderCategory)) {
                continue;
            }
            this.options.add(DialogueOption.enabled(entry.label(), () -> {
                this.selectedBuilderStructure = entry;
                requestBuilderOrder(HiredBuilderOrderPayload.Action.PREVIEW, entry);
                openPage(DialoguePage.BUILDER_CONFIRM);
            }));
            added++;
        }
        if (added == 0) {
            this.options.add(DialogueOption.enabled(translate("recruit.builder_no_category_structures"), NO_ACTION));
        }
        addOption("recruit.nevermind", this::openBuilderStructuresPage);
    }

    private void addBuilderConfirmOptions() {
        if (this.selectedBuilderStructure == null) {
            openBuilderStructuresPage();
            return;
        }
        this.options.add(DialogueOption.enabled(translate("recruit.builder_selected", this.selectedBuilderStructure.menuLabel()), NO_ACTION));
        addOption("recruit.builder_confirm", () -> requestBuilderOrder(HiredBuilderOrderPayload.Action.CONFIRM, this.selectedBuilderStructure));
        addOption("recruit.builder_pick_another", this::openBuilderStructuresPage);
        addOption("recruit.nevermind", this::openBuilderReturnPage);
    }

    private String durationLabel(int durationTicks) {
        if (durationTicks <= 0) {
            return translate("recruit.brewing_duration_instant");
        }
        int totalSeconds = Math.max(1, durationTicks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return translate("recruit.brewing_duration", String.format(Locale.ROOT, "%d:%02d", minutes, seconds));
    }

    private String levelLabel(int level) {
        return translate("recruit.brewing_level", romanNumeral(Math.max(1, level)));
    }

    private String typeLabel(HiredBrewingRecipeCatalog.BrewingTypeChoice choice) {
        String labelKey = choice.type().labelKey();
        if (!labelKey.isBlank()) {
            return translate(labelKey);
        }
        return choice.route().output().getHoverName().getString();
    }

    private static String romanNumeral(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(number);
        };
    }

    private void addRoleChangeOption(HiredVillagerRole role, String labelKey, VillagerRecruitRequestPayload.Action action) {
        if (canOfferContractRole(role)) {
            addOption(labelKey, () -> requestRecruit(action));
        }
    }

    private void addHireRoleOption(HiredVillagerRole role, String labelKey) {
        if (canOfferContractRole(role)) {
            addOption(labelKey, () -> {
                this.pendingHireRole = role;
                openHireDurationPage();
            });
        }
    }

    private void addRoleWorkConfigOption(HiredVillagerRole role, String labelKey, VillagerRecruitRequestPayload.Action action) {
        if (isActiveHiredRole(role)) {
            addOption(labelKey, () -> requestRecruit(action));
        }
    }

    private boolean canOfferContractRole(HiredVillagerRole role) {
        return role != HiredVillagerRole.BUILDER && this.availableHiredRoles.contains(role);
    }

    private boolean canOfferBuilderService() {
        return this.availableHiredRoles.contains(HiredVillagerRole.BUILDER);
    }

    private boolean isActiveHiredRole(HiredVillagerRole role) {
        return this.activeHiredRole == role;
    }

    private boolean canHireVillager() {
        return this.reputationLevel != null
                && this.reputationLevel.trustRank() >= VillagerReputationLevel.NEUTRAL.trustRank();
    }

    private boolean canCommandStayHere() {
        return this.hiredByPlayer;
    }

    private void addClipboardMenuOptions() {
        addOption("clipboard.assign_storage", () -> requestClipboardStorage(ClipboardStorageActionPayload.Action.ASSIGN));
        addOption("clipboard.assign_storage_keep_selection",
                () -> requestClipboardStorage(ClipboardStorageActionPayload.Action.ASSIGN_KEEP_SELECTION));
        if (this.clipboardSelectionAssigned) {
            addOption("clipboard.remove_selected_storage",
                    () -> requestClipboardStorage(ClipboardStorageActionPayload.Action.REMOVE_SELECTION));
            addOption("clipboard.change_selected_storage",
                    () -> requestClipboardStorage(ClipboardStorageActionPayload.Action.CHANGE_SELECTION));
        }
        addOption("clipboard.show_storage", () -> requestClipboardStorage(ClipboardStorageActionPayload.Action.SHOW));
        addOption("clipboard.remove_storage", () -> requestClipboardStorage(ClipboardStorageActionPayload.Action.REMOVE));
        addOption("root.goodbye", this::leaveConversation);
    }

    private void addFamilyOptions() {
        if (this.familyTree.hasAncestry()) {
            this.options.add(DialogueOption.enabled(translate("family.ancestry"), this::openAncestryPage));
        }
        if (this.familyTree.hasDescendants()) {
            this.options.add(DialogueOption.enabled(translate("family.descendants"), this::openDescendantsPage));
        }
        addGenderedFamilyRows("family.father", "family.mother", "family.parent", this.familyTree.parents());
        addGenderedFamilyRows("family.birth_father", "family.birth_mother", "family.birth_parent", this.familyTree.birthParents());
        addGenderedFamilyRows("family.adoptive_father", "family.adoptive_mother", "family.adoptive_parent", this.familyTree.adoptiveParents());
        addGenderedFamilyRows("family.step_father", "family.step_mother", "family.step_parent", this.familyTree.stepParents());
        addGenderedFamilyRows("family.brother", "family.sister", "family.sibling", this.familyTree.siblings());
        addGenderedFamilyRows("family.uncle", "family.aunt", "family.aunt_uncle", this.familyTree.auntsUncles());
        addGenderedFamilyRows("family.nephew", "family.niece", "family.nibling", this.familyTree.niecesNephews());
        addGenderedFamilyRows("family.male_cousin", "family.female_cousin", "family.non_binary_cousin", this.familyTree.cousins());
        addGenderedFamilyRows("family.husband", "family.wife", "family.spouse", this.familyTree.spouses());
        addGenderedFamilyRows("family.son", "family.daughter", "family.child", this.familyTree.children());
        addFamilyRows("family.friend", this.familyTree.friends());
        addFamilyRows("family.rival", this.familyTree.rivals());
        if (this.options.isEmpty()) {
            addPassiveOption("family.none");
        }
    }

    private void addDescendantOptions() {
        for (VillagerFamilyTreeSnapshot.DescendantGeneration generation : this.familyTree.descendants()) {
            addFamilyRows(
                    maleDescendantLabel(generation.generation()),
                    VillagerFamilyTreeSnapshot.membersByGender(generation.descendants(), VillagerGender.MALE)
            );
            addFamilyRows(
                    femaleDescendantLabel(generation.generation()),
                    VillagerFamilyTreeSnapshot.membersByGender(generation.descendants(), VillagerGender.FEMALE)
            );
            addFamilyRows(
                    nonBinaryDescendantLabel(generation.generation()),
                    VillagerFamilyTreeSnapshot.membersByGender(generation.descendants(), VillagerGender.NON_BINARY)
            );
        }
        if (this.options.isEmpty()) {
            addPassiveOption("family.no_descendants");
        }
    }

    private void addAncestryOptions() {
        for (VillagerFamilyTreeSnapshot.AncestorGeneration generation : this.familyTree.ancestry()) {
            addFamilyRows(
                    maleAncestorLabel(generation.generation()),
                    VillagerFamilyTreeSnapshot.membersByGender(generation.ancestors(), VillagerGender.MALE)
            );
            addFamilyRows(
                    femaleAncestorLabel(generation.generation()),
                    VillagerFamilyTreeSnapshot.membersByGender(generation.ancestors(), VillagerGender.FEMALE)
            );
            addFamilyRows(
                    nonBinaryAncestorLabel(generation.generation()),
                    VillagerFamilyTreeSnapshot.membersByGender(generation.ancestors(), VillagerGender.NON_BINARY)
            );
        }
        if (this.options.isEmpty()) {
            addPassiveOption("family.no_ancestry");
        }
    }

    private void addRelationshipOptions() {
        for (VillagerRelationshipSnapshot.RomanticBondView bond : this.relationships.current()) {
            this.options.add(DialogueOption.enabled(relationshipLabel(bond), NO_ACTION));
        }
        for (VillagerRelationshipSnapshot.RomanticBondView bond : this.relationships.past()) {
            this.options.add(DialogueOption.enabled(relationshipLabel(bond), NO_ACTION));
        }
        if (this.options.isEmpty()) {
            addPassiveOption("relationships.none");
        }
    }

    private void addGenderedFamilyRows(
            String maleLabel,
            String femaleLabel,
            String nonBinaryLabel,
            List<VillagerFamilyTreeSnapshot.FamilyMember> members
    ) {
        addFamilyRows(maleLabel, VillagerFamilyTreeSnapshot.membersByGender(members, VillagerGender.MALE));
        addFamilyRows(femaleLabel, VillagerFamilyTreeSnapshot.membersByGender(members, VillagerGender.FEMALE));
        addFamilyRows(nonBinaryLabel, VillagerFamilyTreeSnapshot.membersByGender(members, VillagerGender.NON_BINARY));
    }

    private void addFamilyRows(String labelKey, List<VillagerFamilyTreeSnapshot.FamilyMember> members) {
        for (VillagerFamilyTreeSnapshot.FamilyMember member : members) {
            if (member != null && !member.name().isBlank()) {
                this.options.add(DialogueOption.enabled(
                        translate("family.row", localizedLabel(labelKey), familyMemberLabel(member)),
                        NO_ACTION));
            }
        }
    }

    private void addDialogueOption(DialogueOptionDefinition option) {
        addDialogueOption(option.label(), option.id(), option.metadata().tags().contains(QUEST_OFFER_HINT_TAG));
    }

    private boolean hasQuestOptions() {
        for (DialogueOptionDefinition option : this.dialogueOptions) {
            if (isQuestOption(option)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isQuestOption(DialogueOptionDefinition option) {
        if (option == null) {
            return false;
        }
        if (!option.questAction().isEmpty()) {
            return true;
        }
        DialogueEntryMetadata metadata = option.metadata();
        return metadata != null
                && (!metadata.quest().isBlank()
                        || metadata.tags().contains(QUEST_V2_TAG)
                        || metadata.tags().contains(QUEST_OFFER_HINT_TAG));
    }

    private void addDialogueOption(String label, String optionId, boolean locked) {
        label = switch (optionId) {
            case ITEM_FILTER_ALLOWLIST_OPTION_ID -> translate("item_filter.assign.allowlist");
            case ITEM_FILTER_DENYLIST_OPTION_ID -> translate("item_filter.assign.denylist");
            case ITEM_FILTER_NEVERMIND_OPTION_ID -> translate("item_filter.assign.nevermind");
            default -> label;
        };
        if (BLUEPRINT_CHANGE_OPTION_ID.equals(optionId)) {
            this.options.add(DialogueOption.enabled(label, this::openBuilderStructuresPage));
            return;
        }
        if (BLUEPRINT_NEVERMIND_OPTION_ID.equals(optionId)) {
            this.options.add(DialogueOption.enabled(label, this::leaveConversation));
            return;
        }
        this.options.add(DialogueOption.enabled(label, () -> requestDialogue(optionId), locked));
    }

    private void addOption(String labelKey, Runnable action) {
        this.options.add(DialogueOption.enabled(translate(labelKey), action));
    }

    private void addPassiveOption(String labelKey) {
        addOption(labelKey, NO_ACTION);
    }

    private void openTalkPage() {
        openPage(DialoguePage.TALK);
    }

    private void openAdventuresPage() {
        openPage(DialoguePage.ADVENTURES);
    }

    private void openProfilePage() {
        this.profileRefreshRequested = false;
        clearSelectedSkillDetails();
        clearSelectedProfileAttributeDetails();
        clearSelectedJobDetails();
        this.draggingSkillScrollbar = false;
        this.skillsProfilePanel = SkillsProfilePanel.PROFILE;
        requestProfileRefresh();
        openPage(DialoguePage.SKILLS);
    }

    private void openAllegiancePage() {
        acceptVillagerDialogue(allegianceText(this.allegiance.prompt(), "allegiance.prompt"), List.of());
        openPage(DialoguePage.ALLEGIANCE);
    }

    private void addAllegianceOptions() {
        this.options.add(DialogueOption.enabled(
                allegianceText(this.allegiance.askHomeLabel(), "allegiance.ask_home"),
                this::showHomeVillageDialogue));
        this.options.add(DialogueOption.enabled(
                allegianceText(this.allegiance.askCurrentVillageLabel(), "allegiance.ask_here"),
                this::showCurrentVillageDialogue));
        if (!this.baby && this.allegiance.inVillage() && !this.allegiance.atHome()) {
            this.options.add(DialogueOption.enabled(
                    allegianceText(this.allegiance.reassignLabel(), "allegiance.reassign"),
                    () -> sendToServer(new VillagerAllegianceActionPayload(
                            this.villagerEntityId,
                            VillagerAllegianceActionPayload.Action.REASSIGN_TO_CURRENT_VILLAGE))));
        }
    }

    private void showHomeVillageDialogue() {
        acceptVillagerDialogue(allegianceText(this.allegiance.homeAnswer(), "allegiance.answer.unknown"), List.of());
    }

    private void showCurrentVillageDialogue() {
        acceptVillagerDialogue(allegianceText(this.allegiance.currentVillageAnswer(), "allegiance.answer.here_outside"), List.of());
    }

    private static String allegianceText(String text, String fallbackKey) {
        if (text != null && !text.isBlank()) {
            return text;
        }
        return switch (fallbackKey) {
            case "allegiance.prompt" -> "Is there something you would like to ask about where I belong?";
            case "allegiance.ask_home" -> "Where do you call home?";
            case "allegiance.ask_here" -> "Do you belong to this village?";
            case "allegiance.reassign" -> "Would you make this village your home?";
            case "allegiance.answer.here_outside" -> "We are not standing in a village right now.";
            default -> "I am not certain where I belong. I wish I had a clearer answer for you.";
        };
    }


    private void openGiftPage() {
        this.selectedInventorySlot = firstGiftableInventorySlot();
        this.selectedGiftAmount = 0;
        this.giftLimitFeedback.trigger(0);
        openPage(DialoguePage.GIFT);
    }

    private void openRecruitPage() {
        openPage(DialoguePage.RECRUIT);
    }

    private void openStoragePage() {
        openPage(DialoguePage.STORAGE);
    }

    private void openPaymentPage() {
        openPage(DialoguePage.PAYMENT);
    }

    private void openHirePage() {
        this.pendingHireRole = null;
        requestRecruit(VillagerRecruitRequestPayload.Action.VIEW_CONTRACT);
        openPage(DialoguePage.HIRE);
    }

    private void addPartySettingOption(
            String labelKey,
            String valueKey,
            VillagerRecruitRequestPayload.Action action) {
        this.options.add(DialogueOption.enabled(
                translate("party.setting", translate(labelKey), translate(valueKey)),
                () -> requestRecruit(action)));
    }

    private void openHireDurationPage() {
        openPage(DialoguePage.HIRE_DURATION);
    }

    private void openContractPage() {
        openPage(DialoguePage.CONTRACT);
    }

    private void openEndContractConfirmationPage() {
        this.confirmingPartyRecruit = false;
        this.confirmingPartyDismiss = false;
        openPage(DialoguePage.END_CONTRACT_CONFIRMATION);
    }

    private void openPartyRecruitConfirmationPage() {
        this.confirmingPartyRecruit = true;
        this.confirmingPartyDismiss = false;
        requestRecruit(VillagerRecruitRequestPayload.Action.PROMPT_PARTY_RECRUIT_CONFIRMATION);
        openPage(DialoguePage.END_CONTRACT_CONFIRMATION);
    }

    private void openPartyDismissConfirmationPage() {
        this.confirmingPartyRecruit = false;
        this.confirmingPartyDismiss = true;
        requestRecruit(VillagerRecruitRequestPayload.Action.PROMPT_PARTY_DISMISS_CONFIRMATION);
        openPage(DialoguePage.END_CONTRACT_CONFIRMATION);
    }

    private void openContractExtensionPage() {
        openPage(DialoguePage.CONTRACT_EXTENSION);
    }

    private void openRolePage() {
        openPage(DialoguePage.ROLE);
    }

    private void openRoleChangePage() {
        openPage(DialoguePage.ROLE_CHANGE);
    }

    private void openWorkPage() {
        openPage(DialoguePage.WORK);
    }

    private void openHuntingOptionsPage() {
        openPage(DialoguePage.HUNTING_OPTIONS);
    }

    private void openLoggingFiltersPage() {
        openPage(DialoguePage.LOGGING_FILTERS);
    }

    private void openFarmingOptionsPage() {
        openPage(DialoguePage.FARMING_OPTIONS);
    }

    private void openAnimalHandlingOptionsPage() {
        openPage(DialoguePage.ANIMAL_HANDLING_OPTIONS);
    }

    private void openAnimalBreedingTargetsPage() {
        openPage(DialoguePage.ANIMAL_BREEDING_TARGETS);
    }

    private void openAnimalCullCapsPage() {
        openPage(DialoguePage.ANIMAL_CULL_CAPS);
    }

    private void openBrewingPotionPage() {
        this.selectedBrewingPotionChoice = null;
        this.selectedBrewingDurationChoice = null;
        this.selectedBrewingLevelChoice = null;
        this.selectedBrewingRoute = null;
        openPage(DialoguePage.BREWING_POTION);
    }

    private void openBuilderStructuresPage() {
        this.selectedBuilderCategory = null;
        this.selectedBuilderStructure = null;
        openPage(DialoguePage.BUILDER_STRUCTURES);
    }

    private void openBuilderStructureCategoryPage(String category) {
        this.selectedBuilderCategory = category;
        this.selectedBuilderStructure = null;
        openPage(DialoguePage.BUILDER_STRUCTURE_CATEGORY);
    }



    private void openDuelPage() {
        this.duelStartPending = false;
        openPage(DialoguePage.DUEL);
        requestDuelStatus();
    }

    private void requestDuelStatus() {
        sendToServer(new VillagerDuelRequestPayload(this.villagerEntityId, VillagerDuelRequestPayload.Action.OPEN));
    }

    private void openAncestryPage() {
        openPage(DialoguePage.ANCESTRY);
    }

    private void openDescendantsPage() {
        openPage(DialoguePage.DESCENDANTS);
    }

    private void requestTrade() {
        sendToServer(new VillagerTradeRequestPayload(this.villagerEntityId));
    }


    private void requestInventory() {
        sendToServer(new VillagerInventoryRequestPayload(this.villagerEntityId));
    }

    private void requestProfileRefresh() {
        if (this.profileRefreshRequested) {
            return;
        }
        this.profileRefreshRequested = true;
        sendProfileRequest();
    }

    private void tickSkillsProfileKeepAlive() {
        if (this.closingWithAnimation || this.page != DialoguePage.SKILLS) {
            return;
        }
        long now = Util.getMillis();
        if (this.lastProfileRequestMillis >= 0L
                && now - this.lastProfileRequestMillis < PROFILE_KEEPALIVE_INTERVAL_MILLIS) {
            return;
        }
        sendProfileRequest();
    }

    private void sendProfileRequest() {
        this.lastProfileRequestMillis = Util.getMillis();
        sendToServer(new VillagerProfileRequestPayload(this.villagerEntityId));
    }

    private void requestGift() {
        if (this.selectedInventorySlot < 0) {
            return;
        }
        sendToServer(new VillagerGiftRequestPayload(this.villagerEntityId, this.selectedInventorySlot, this.selectedGiftAmount));
        this.selectedInventorySlot = firstGiftableInventorySlot();
        this.selectedGiftAmount = 0;
        this.giftLimitFeedback.trigger(0);
    }

    private void requestDialogue(String optionId) {
        if (this.forcedDialogue) {
            if (this.awaitingForcedDialogueResponse) {
                return;
            }
            this.awaitingForcedDialogueResponse = true;
        }
        sendToServer(new VillagerDialogueRequestPayload(this.villagerEntityId, optionId));
    }

    private void tickMouseEasterEggs() {
        if (this.closingWithAnimation || !shouldRenderInteractionContainer()) {
            resetMouseEasterEggProgress();
            return;
        }

        tickMouseStareEasterEgg();
    }

    private void tickMouseStareEasterEgg() {
        if (this.mouseStareEasterEggTriggered) {
            return;
        }
        if (!isMouseOverVillagerEyeBridge(this.lastMouseX, this.lastMouseY)) {
            this.mouseStareStartMillis = -1L;
            return;
        }

        long now = Util.getMillis();
        if (this.mouseStareStartMillis < 0L) {
            this.mouseStareStartMillis = now;
        }
        if (now - this.mouseStareStartMillis >= MOUSE_STARE_REQUIRED_MILLIS) {
            this.mouseStareEasterEggTriggered = true;
            sendToServer(new VillagerMouseEasterEggPayload(
                    this.villagerEntityId,
                    VillagerMouseEasterEggPayload.Kind.STARE));
        }
    }

    private void resetMouseEasterEggProgress() {
        this.mouseStareStartMillis = -1L;
    }

    private boolean isMouseOverVillagerEyeBridge(double mouseX, double mouseY) {
        double portraitTop = interactionContainerTop() + INTERACTION_PORTRAIT_TOP;
        return isPointInsideEllipse(
                mouseX,
                mouseY,
                villagerPortraitCenterX(),
                portraitTop + 19.0D,
                VILLAGER_PORTRAIT_EYE_BRIDGE_RADIUS_X,
                VILLAGER_PORTRAIT_EYE_BRIDGE_RADIUS_Y);
    }

    private double villagerPortraitCenterX() {
        int left = interactionContainerLeft() + INTERACTION_PORTRAIT_LEFT;
        int right = interactionContainerLeft() + INTERACTION_PORTRAIT_RIGHT;
        return (left + right) / 2.0D;
    }

    private static boolean isPointInsideEllipse(
            double pointX,
            double pointY,
            double centerX,
            double centerY,
            double radiusX,
            double radiusY) {
        double normalizedX = (pointX - centerX) / radiusX;
        double normalizedY = (pointY - centerY) / radiusY;
        return normalizedX * normalizedX + normalizedY * normalizedY <= 1.0D;
    }

    private void navigateToRootPage() {
        if (this.forcedDialogue) {
            return;
        }
        boolean leavingTalk = this.page == DialoguePage.TALK;
        if (this.page != DialoguePage.ROOT) {
            this.selectedInventorySlot = -1;
            openPage(DialoguePage.ROOT);
        }
        if (leavingTalk) {
            requestDialogue(DIALOGUE_TREE_LEAVE_OPTION_ID);
        }
    }

    private void navigateBackPage() {
        if (this.page == DialoguePage.DUEL_LOADOUT
                || this.page == DialoguePage.DUEL_WAGER
                || this.page == DialoguePage.DUEL_CONFIRM) {
            openPage(DialoguePage.DUEL);
            return;
        }
        if (this.page == DialoguePage.ANCESTRY || this.page == DialoguePage.DESCENDANTS) {
            openPage(DialoguePage.FAMILY);
            return;
        }
        if (this.page == DialoguePage.HIRE) {
            openPage(DialoguePage.RECRUIT);
            return;
        }
        if (this.page == DialoguePage.STORAGE || this.page == DialoguePage.PAYMENT) {
            openPage(DialoguePage.RECRUIT);
            return;
        }
        if (this.page == DialoguePage.END_CONTRACT_CONFIRMATION) {
            openPage(DialoguePage.RECRUIT);
            return;
        }
        if (this.page == DialoguePage.CONTRACT_EXTENSION) {
            openPage(DialoguePage.CONTRACT);
            return;
        }
        if (this.page == DialoguePage.CONTRACT) {
            openPage(DialoguePage.RECRUIT);
            return;
        }
        if (this.page == DialoguePage.ROLE_CHANGE) {
            openPage(DialoguePage.ROLE);
            return;
        }
        if (this.page == DialoguePage.ROLE) {
            openPage(DialoguePage.RECRUIT);
            return;
        }
        if (this.page == DialoguePage.BREWING_AMOUNT) {
            openPage(DialoguePage.BREWING_TYPE);
            return;
        }
        if (this.page == DialoguePage.BREWING_TYPE) {
            openPage(DialoguePage.BREWING_DURATION);
            return;
        }
        if (this.page == DialoguePage.BREWING_DURATION) {
            openPage(DialoguePage.BREWING_LEVEL);
            return;
        }
        if (this.page == DialoguePage.BREWING_LEVEL) {
            openPage(DialoguePage.BREWING_POTION);
            return;
        }
        if (this.page == DialoguePage.BREWING_POTION) {
            openPage(DialoguePage.WORK);
            return;
        }
        if (this.page == DialoguePage.BUILDER_CONFIRM) {
            if (this.selectedBuilderCategory != null && !this.selectedBuilderCategory.isBlank()) {
                openPage(DialoguePage.BUILDER_STRUCTURE_CATEGORY);
            } else {
                openPage(DialoguePage.BUILDER_STRUCTURES);
            }
            return;
        }
        if (this.page == DialoguePage.BUILDER_STRUCTURE_CATEGORY) {
            openPage(DialoguePage.BUILDER_STRUCTURES);
            return;
        }
        if (this.page == DialoguePage.BUILDER_STRUCTURES) {
            openBuilderReturnPage();
            return;
        }
        if (this.page == DialoguePage.HIRE_DURATION) {
            openPage(DialoguePage.HIRE);
            return;
        }
        if (this.page == DialoguePage.HUNTING_OPTIONS
                || this.page == DialoguePage.FARMING_OPTIONS
                || this.page == DialoguePage.LOGGING_FILTERS) {
            openPage(DialoguePage.WORK);
            return;
        }
        if (this.page == DialoguePage.ANIMAL_HANDLING_OPTIONS) {
            openPage(DialoguePage.WORK);
            return;
        }
        if (this.page == DialoguePage.ANIMAL_BREEDING_TARGETS) {
            openPage(DialoguePage.ANIMAL_HANDLING_OPTIONS);
            return;
        }
        if (this.page == DialoguePage.ANIMAL_CULL_CAPS) {
            openPage(DialoguePage.ANIMAL_HANDLING_OPTIONS);
            return;
        }
        if (this.page == DialoguePage.WORK) {
            openPage(DialoguePage.RECRUIT);
            return;
        }
        navigateToRootPage();
    }

    private void goBackOrLeaveConversation() {
        if (this.forcedDialogue) {
            requestDialogue(FORCED_LEAVE_OPTION_ID);
            return;
        }
        if (this.page == DialoguePage.SKILLS && this.selectedSkillDetails != null) {
            clearSelectedSkillDetails();
            return;
        }
        if (this.page == DialoguePage.SKILLS && this.selectedProfileAttributeDetails != null) {
            clearSelectedProfileAttributeDetails();
            return;
        }
        if (this.page == DialoguePage.SKILLS && this.selectedJobDetails != null) {
            clearSelectedJobDetails();
            return;
        }
        if (canNavigateBack()) {
            navigateBackPage();
        } else {
            leaveConversation();
        }
    }

    private void leaveConversation() {
        startClosingAnimation();
    }

    private void startClosingAnimation() {
        if (this.closingWithAnimation) {
            return;
        }
        this.closingWithAnimation = true;
        this.draggingScrollbar = false;
        this.draggingSkillScrollbar = false;
        clearDialogueMouthAnimation();
        this.animationStartMillis = Util.getMillis();
    }

    private void finishClosingAnimation() {
        if (this.minecraft != null && this.minecraft.screen == this) {
            this.minecraft.setScreen(null);
        }
    }

    private double interactionMouseY(double mouseY) {
        return mouseY - slideOffsetY() - interactionStateTransitionOffsetY();
    }

    private double interactionContentMouseX(double mouseX) {
        return mouseX - interactionStateTransitionContentOffsetX();
    }

    private int slideOffsetY() {
        float visibility = screenVisibility();
        int offscreenDistance = this.height + 12;
        return Math.round((1.0F - visibility) * offscreenDistance);
    }

    private float screenVisibility() {
        float progress = Mth.clamp(animationElapsedMillis() / INTERACTION_ANIMATION_DURATION_MILLIS, 0.0F, 1.0F);
        return this.closingWithAnimation ? 1.0F - easeInCubic(progress) : easeOutCubic(progress);
    }

    private float animationElapsedMillis() {
        if (this.animationStartMillis < 0L) {
            return INTERACTION_ANIMATION_DURATION_MILLIS;
        }
        return Util.getMillis() - this.animationStartMillis;
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float easeInCubic(float value) {
        return value * value * value;
    }

    private void startInteractionStateTransition(DialoguePage previousPage, DialoguePage nextPage, int previousTop, int nextTop) {
        startInteractionStateTransition(previousPage, nextPage, previousTop, nextTop, false);
    }

    private void startInteractionStateTransition(
            DialoguePage previousPage,
            DialoguePage nextPage,
            int previousTop,
            int nextTop,
            boolean animateSamePage) {
        if (previousPage == nextPage && !animateSamePage) {
            return;
        }

        int depthDirection = interactionPageDepth(nextPage) >= interactionPageDepth(previousPage) ? 1 : -1;
        this.interactionStateTransitionStartOffsetY = isReturningFromProfileOrSkills(previousPage, nextPage)
                ? this.height + INTERACTION_STATE_BOTTOM_ENTRANCE_PADDING - nextTop
                : previousTop - nextTop;
        this.interactionStateTransitionStartOffsetX = usesContainerOnlyTransition(previousPage, nextPage)
                ? 0
                : depthDirection * INTERACTION_STATE_CONTENT_SLIDE_X;
        this.interactionStateTransitionStartMillis = Util.getMillis();
    }

    private void startPreparedReplacementTransition() {
        if (this.replacementTransitionPreviousPage == null) {
            return;
        }
        startInteractionStateTransition(
                this.replacementTransitionPreviousPage,
                this.page,
                this.replacementTransitionPreviousTop,
                interactionContainerTopForPage(this.page),
                true);
        this.replacementTransitionPreviousPage = null;
    }

    private static boolean usesContainerOnlyTransition(DialoguePage previousPage, DialoguePage nextPage) {
        return previousPage == DialoguePage.SKILLS
                || nextPage == DialoguePage.SKILLS
                || previousPage == DialoguePage.GIFT
                || nextPage == DialoguePage.GIFT;
    }

    private static boolean isReturningFromProfileOrSkills(DialoguePage previousPage, DialoguePage nextPage) {
        return nextPage == DialoguePage.ROOT
                && (previousPage == DialoguePage.PROFILE || previousPage == DialoguePage.SKILLS);
    }

    private int interactionStateTransitionOffsetY() {
        return Math.round(this.interactionStateTransitionStartOffsetY * interactionStateTransitionRemaining());
    }

    private int interactionStateTransitionContentOffsetX() {
        return Math.round(this.interactionStateTransitionStartOffsetX * interactionStateTransitionRemaining());
    }

    private float interactionStateTransitionRemaining() {
        if (this.interactionStateTransitionStartMillis < 0L) {
            return 0.0F;
        }

        float elapsedMillis = Util.getMillis() - this.interactionStateTransitionStartMillis;
        float progress = Mth.clamp(elapsedMillis / INTERACTION_STATE_TRANSITION_DURATION_MILLIS, 0.0F, 1.0F);
        return 1.0F - easeOutCubic(progress);
    }

    private void activateSelected() {
        if (usesRootIconMenu()) {
            activateInteractionMenuButton(this.selectedInteractionMenuButton, true);
            return;
        }
        if (this.state.selectedOption() < 0 || this.state.selectedOption() >= this.options.size()) {
            return;
        }
        DialogueOption option = this.options.get(this.state.selectedOption());
        option.action().run();
    }

    private void requestRecruit(VillagerRecruitRequestPayload.Action action) {
        HiredVillagerRole selectedRole = isHireDurationAction(action) ? this.pendingHireRole : null;
        sendToServer(new VillagerRecruitRequestPayload(
                this.villagerEntityId, action, selectedRole, this.assignmentRevision));
        if (action == VillagerRecruitRequestPayload.Action.STOP_BREWING) {
            this.activeBrewingOrder = false;
            openWorkPage();
        } else if (action == VillagerRecruitRequestPayload.Action.STOP_BUILDER_BUILD) {
            boolean wasOneOffBuilderJob = this.oneOffBuilderJob;
            this.activeBuilderTask = false;
            this.oneOffBuilderJob = false;
            if (wasOneOffBuilderJob) {
                openRecruitPage();
            } else {
                openBuilderReturnPage();
            }
        } else if (action == VillagerRecruitRequestPayload.Action.UNASSIGN_MOUNT) {
            this.assignedMount = false;
        }
    }

    public void acceptRecruitmentResult(RecruitmentResultPayload payload) {
        if (payload == null || payload.entityId() != this.villagerEntityId) return;
        boolean ownsAssignment = payload.assignment().owner()
                .filter(owner -> Minecraft.getInstance().player != null
                        && owner.equals(Minecraft.getInstance().player.getUUID()))
                .isPresent();
        this.hiredByPlayer = ownsAssignment;
        this.followingPlayer = ownsAssignment
                && payload.assignment().command() == com.jvn.villagerretaliation.interaction.VillagerAssignmentCommand.FOLLOW;
        this.stayingHere = ownsAssignment
                && payload.assignment().command() == com.jvn.villagerretaliation.interaction.VillagerAssignmentCommand.STAY;
        this.activeHiredRole = payload.assignment().role();
        this.assignmentRevision = payload.assignment().revision();
        rebuildOptionsKeepingListPosition();
    }

    private void openBuilderReturnPage() {
        if (this.hiredByPlayer && !this.oneOffBuilderJob && isActiveHiredRole(HiredVillagerRole.BUILDER)) {
            openWorkPage();
        } else {
            openRecruitPage();
        }
    }

    private void requestLoggingFilter(String filterId) {
        sendToServer(new HiredLoggingFilterPayload(this.villagerEntityId, filterId));
        if (filterId == null || filterId.isBlank() || "any".equals(filterId)) {
            this.selectedLoggingFilters.clear();
        } else if (!this.selectedLoggingFilters.remove(filterId)) {
            this.selectedLoggingFilters.add(filterId);
        }
        rebuildOptionsKeepingListPosition();
    }

    private void requestLoggingOption(String optionId) {
        sendToServer(new HiredLoggingOptionPayload(this.villagerEntityId, optionId));
        switch (optionId) {
            case HiredLoggingOptions.STRIP_LOGS -> this.loggingStripLogs = !this.loggingStripLogs;
            case HiredLoggingOptions.HARVEST_LEAVES -> this.loggingHarvestLeaves = !this.loggingHarvestLeaves;
            case HiredLoggingOptions.BONEMEAL_SAPLINGS -> this.loggingBonemealSaplings = !this.loggingBonemealSaplings;
            case HiredLoggingOptions.PLANT_SAPLINGS -> this.loggingPlantSaplings = !this.loggingPlantSaplings;
            case HiredLoggingOptions.PICK_UP_DECAY_DROPS -> this.loggingPickUpDecayDrops = !this.loggingPickUpDecayDrops;
            default -> {
                return;
            }
        }
        rebuildOptionsKeepingListPosition();
    }

    private void requestFarmingOption(String optionId) {
        sendToServer(new HiredFarmingOptionPayload(this.villagerEntityId, optionId));
        if (HiredFarmingOptions.TILL_SOIL.equals(optionId)) {
            this.farmingTillSoil = !this.farmingTillSoil;
            rebuildOptionsKeepingListPosition();
        }
    }

    private void requestHuntingTarget(String targetId) {
        sendToServer(new HiredHuntingTargetPayload(this.villagerEntityId, targetId));
        switch (targetId) {
            case HiredHuntingTargets.ANIMALS -> this.huntingAnimals = !this.huntingAnimals;
            case HiredHuntingTargets.HOSTILES -> this.huntingHostiles = !this.huntingHostiles;
            case HiredHuntingTargets.PLAYERS -> this.huntingPlayers = !this.huntingPlayers;
            case HiredHuntingTargets.ALL -> {
                boolean enabled = !(this.huntingAnimals && this.huntingHostiles);
                this.huntingAnimals = enabled;
                this.huntingHostiles = enabled;
            }
            default -> {
                return;
            }
        }
        rebuildOptionsKeepingListPosition();
    }

    private static boolean isHireDurationAction(VillagerRecruitRequestPayload.Action action) {
        return action == VillagerRecruitRequestPayload.Action.HIRE_ONE_DAY
                || action == VillagerRecruitRequestPayload.Action.HIRE_THREE_DAYS
                || action == VillagerRecruitRequestPayload.Action.HIRE_FIVE_DAYS
                || action == VillagerRecruitRequestPayload.Action.HIRE_SEVEN_DAYS
                || action == VillagerRecruitRequestPayload.Action.HIRE_FIFTEEN_DAYS
                || action == VillagerRecruitRequestPayload.Action.HIRE_THIRTY_DAYS;
    }

    private void requestAnimalHandlingOption(String optionId) {
        if (!HiredAnimalHandlingOptions.SHEAR_SHEEP.equals(optionId)) {
            return;
        }
        sendToServer(new HiredAnimalHandlingOptionPayload(this.villagerEntityId, optionId));
        this.animalShearing = !this.animalShearing;
        rebuildOptionsKeepingListPosition();
    }

    private void requestAnimalBreedingTarget(String targetId) {
        sendToServer(new HiredAnimalBreedingTargetPayload(this.villagerEntityId, targetId));
        if (targetId == null || targetId.isBlank() || "all".equals(targetId)) {
            this.selectedAnimalBreedingTargets.clear();
        } else if (!this.selectedAnimalBreedingTargets.remove(targetId)) {
            this.selectedAnimalBreedingTargets.add(targetId);
        }
        rebuildOptionsKeepingListPosition();
    }

    private void requestAnimalCullCap(int cap) {
        if (cap != HiredAnimalCullSettings.DISABLED_CAP && !HiredAnimalCullSettings.isValidCap(cap)) {
            return;
        }
        sendToServer(new HiredAnimalCullCapPayload(this.villagerEntityId, cap));
        this.animalCullCap = cap;
        rebuildOptionsKeepingListPosition();
    }

    private void requestBrewingOrder(HiredBrewingRecipeCatalog.BrewingRoute route, int amount, boolean continuous) {
        if (route == null) {
            return;
        }
        sendToServer(new HiredBrewingOrderPayload(
                this.villagerEntityId,
                route.itemId(),
                route.potionId(),
                amount,
                continuous));
        this.activeBrewingOrder = true;
        openWorkPage();
    }

    private void requestBuilderOrder(HiredBuilderOrderPayload.Action action, BuilderStructureCatalog.Entry entry) {
        ResourceLocation structureId = entry == null ? ResourceLocation.withDefaultNamespace("empty") : entry.id();
        sendToServer(new HiredBuilderOrderPayload(this.villagerEntityId, action, structureId));
        if (action == HiredBuilderOrderPayload.Action.CONFIRM || action == HiredBuilderOrderPayload.Action.CANCEL) {
            openWorkPage();
        }
    }

    private void requestClipboardStorage(ClipboardStorageActionPayload.Action action) {
        sendToServer(new ClipboardStorageActionPayload(this.villagerEntityId, action));
        if (action == ClipboardStorageActionPayload.Action.CLEAR_SELECTION) {
            rebuildOptionsKeepingListPosition();
        }
    }

    void noteInteractionActivity() {
        if (this.closingWithAnimation) {
            return;
        }
        long now = Util.getMillis();
        if (this.lastActivitySignalMillis >= 0L && now - this.lastActivitySignalMillis < ACTIVITY_SIGNAL_INTERVAL_MILLIS) {
            return;
        }
        this.lastActivitySignalMillis = now;
        sendToServer(new VillagerConversationActivityPayload(this.villagerEntityId));
    }

    private void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }


    private void openPage(DialoguePage page) {
        DialoguePage previousPage = this.page;
        int previousInteractionTop = interactionContainerTopForPage(previousPage) + interactionStateTransitionOffsetY();
        rememberCurrentPageOptionListPosition();
        this.page = page;
        if (isDuelSetupPage(page)) {
            refreshDuelDialogue();
        }
        rebuildOptions();
        restoreRememberedPageOptionListPosition(page);
        startInteractionStateTransition(previousPage, page, previousInteractionTop, interactionContainerTopForPage(page));
    }

    private void clearSelectedSkillDetails() {
        this.selectedSkillDetails = null;
        resetSkillInfoScroll();
    }

    private void clearSelectedProfileAttributeDetails() {
        this.selectedProfileAttributeDetails = null;
        resetSkillInfoScroll();
    }

    private void clearSelectedJobDetails() {
        this.selectedJobDetails = null;
        resetSkillInfoScroll();
    }

    private void resetSkillInfoScroll() {
        this.skillScroll = 0.0F;
        this.targetSkillScroll = 0.0F;
    }

    private void moveSelection(int direction) {
        if (usesRootIconMenu()) {
            moveInteractionMenuSelection(direction);
            return;
        }
        if (this.options.isEmpty()) {
            return;
        }
        this.state.moveSelectedOption(direction, this.options.size());
        this.keyboardOptionFocusVisible = true;
        ensureSelectedVisible();
    }

    private void moveInteractionMenuSelection(int direction) {
        int buttonCount = interactionMenuButtons().size();
        if (buttonCount <= 0) {
            return;
        }
        int selected = isValidInteractionMenuButton(this.selectedInteractionMenuButton, buttonCount)
                ? this.selectedInteractionMenuButton
                : 0;
        this.selectedInteractionMenuButton = wrapIndex(selected + direction, buttonCount);
        this.keyboardInteractionMenuFocusVisible = true;
    }

    private static boolean isValidInteractionMenuButton(int index, int buttonCount) {
        return index >= 0 && index < buttonCount;
    }

    private static int wrapIndex(int index, int size) {
        int wrapped = index % size;
        return wrapped < 0 ? wrapped + size : wrapped;
    }

    private void updateOptionScroll() {
        long now = Util.getMillis();
        float frames = this.lastOptionScrollRenderMillis < 0L
                ? 1.0F
                : Mth.clamp((now - this.lastOptionScrollRenderMillis) / 16.6667F, 0.0F, 4.0F);
        this.lastOptionScrollRenderMillis = now;
        float frameAdjustedLerp = 1.0F - (float) Math.pow(1.0F - OPTION_SCROLL_LERP, frames);
        this.state.tickOptionScroll(frameAdjustedLerp);
    }

    private void updateSkillScroll() {
        this.skillScroll = Mth.clamp(this.skillScroll, 0.0F, maxSkillScroll());
        this.targetSkillScroll = Mth.clamp(this.targetSkillScroll, 0.0F, maxSkillScroll());
        this.skillScroll = Mth.lerp(OPTION_SCROLL_LERP, this.skillScroll, this.targetSkillScroll);
        if (Math.abs(this.skillScroll - this.targetSkillScroll) < 0.15F) {
            this.skillScroll = this.targetSkillScroll;
        }
    }

    private void renderOptions(GuiGraphics graphics, int mouseX, int mouseY, int top) {
        VillagerInteractionOptionList.render(this.optionListContext, graphics, mouseX, mouseY);
    }

    private void renderProfilePage(GuiGraphics graphics, int mouseX, int mouseY) {
        VillagerInteractionProfilePage.render(this.profilePageContext, graphics, mouseX, mouseY);
    }

    private void renderSkillsPage(GuiGraphics graphics, int mouseX, int mouseY) {
        renderSkillsDialogueContainer(graphics, mouseX, mouseY);
        if (this.skillsProfilePanel == SkillsProfilePanel.PROFILE) {
            renderProfileContainer(graphics, mouseX, mouseY);
            renderSkillsProfileCycleButtonTooltip(graphics, mouseX, mouseY);
            return;
        }
        if (this.skillsProfilePanel == SkillsProfilePanel.JOBS) {
            VillagerInteractionJobStatsPage.render(this.jobStatsPageContext, graphics, mouseX, mouseY);
            renderSkillsProfileCycleButtonTooltip(graphics, mouseX, mouseY);
            return;
        }
        VillagerInteractionSkillsPage.render(this.skillsPageContext, graphics, mouseX, mouseY);
        renderSkillsProfileCycleButtonTooltip(graphics, mouseX, mouseY);
    }

    private void renderSkillsDialogueContainer(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = skillsDialogueContainerLeft();
        int top = skillsDialogueContainerTop();
        renderSkillsProfileCycleButtons(graphics, mouseX, mouseY);
        graphics.blit(
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_SKILLS_DIALOGUE_CONTAINER_TEXTURE,
                left,
                top,
                0,
                0,
                SKILLS_DIALOGUE_CONTAINER_WIDTH,
                SKILLS_DIALOGUE_CONTAINER_HEIGHT,
                SKILLS_DIALOGUE_CONTAINER_WIDTH,
                SKILLS_DIALOGUE_CONTAINER_HEIGHT);
        renderInteractionVillagerPortrait(graphics, left, top);
        renderSkillsDialogueInfo(graphics, left, top);
    }

    private void renderSkillsProfileCycleButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = skillsDialogueButtonTop();
        int leftButtonLeft = skillsDialogueLeftButtonLeft();
        int rightButtonLeft = skillsDialogueRightButtonLeft();
        graphics.blit(
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_SKILLS_DIALOGUE_BUTTON_LEFT_TEXTURE,
                leftButtonLeft,
                top,
                0,
                0,
                SKILLS_DIALOGUE_BUTTON_WIDTH,
                SKILLS_DIALOGUE_BUTTON_HEIGHT,
                SKILLS_DIALOGUE_BUTTON_WIDTH,
                SKILLS_DIALOGUE_BUTTON_HEIGHT);
        graphics.blit(
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_SKILLS_DIALOGUE_BUTTON_RIGHT_TEXTURE,
                rightButtonLeft,
                top,
                0,
                0,
                SKILLS_DIALOGUE_BUTTON_WIDTH,
                SKILLS_DIALOGUE_BUTTON_HEIGHT,
                SKILLS_DIALOGUE_BUTTON_WIDTH,
                SKILLS_DIALOGUE_BUTTON_HEIGHT);
    }

    private void renderSkillsProfileCycleButtonTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        SkillsProfileCycleButton hovered = hoveredSkillsProfileCycleButton(mouseX, mouseY);
        if (hovered == null) {
            return;
        }
        SkillsProfilePanel target = adjacentSkillsProfilePanel(hovered);
        String key = switch (target) {
            case SKILLS -> "profile.cycle.skills";
            case PROFILE -> "profile.cycle.profile";
            case JOBS -> "profile.cycle.jobs";
        };
        renderInteractionTooltip(graphics, List.of(Component.literal(translate(key))), mouseX, mouseY);
    }

    private void renderProfileContainer(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.blit(
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_PROFILE_CONTAINER_TEXTURE,
                profileContainerLeft(),
                profileContainerTop(),
                0,
                0,
                PROFILE_CONTAINER_WIDTH,
                PROFILE_CONTAINER_HEIGHT,
                PROFILE_CONTAINER_WIDTH,
                PROFILE_CONTAINER_HEIGHT);
        VillagerInteractionProfilePage.render(this.profilePageContext, graphics, mouseX, mouseY);
    }

    private void renderSkillsDialogueInfo(GuiGraphics graphics, int left, int top) {
        String text = skillsDialogueInfoText();
        if (text.isBlank()) {
            return;
        }

        List<String> lines = wrappedInteractionDialogueLines(text);
        if (lines.isEmpty()) {
            return;
        }

        int visibleLines = interactionDialogueVisibleLineCount();
        int maxScroll = maxInteractionDialogueLineScroll(lines);
        int lineScroll = Mth.clamp(Math.round(this.skillScroll), 0, maxScroll);
        this.skillScroll = lineScroll;
        this.targetSkillScroll = Mth.clamp(this.targetSkillScroll, 0.0F, maxScroll);
        int drawLeft = left + INTERACTION_DIALOGUE_LEFT + 1;
        int drawTop = top + INTERACTION_DIALOGUE_TOP + 1;
        int lineStep = this.font.lineHeight;
        int linesToDraw = Math.min(visibleLines, lines.size() - lineScroll);
        for (int row = 0; row < linesToDraw; row++) {
            int lineIndex = lineScroll + row;
            int lineTop = drawTop + row * lineStep;
            int lineRight = left + (row == visibleLines - 1
                    ? INTERACTION_DIALOGUE_RIGHT
                    : INTERACTION_DIALOGUE_EXTENDED_RIGHT);
            graphics.enableScissor(
                    left + INTERACTION_DIALOGUE_LEFT,
                    top + INTERACTION_DIALOGUE_TOP + row * lineStep + this.renderSlideOffsetY,
                    lineRight,
                    Math.min(
                            top + INTERACTION_DIALOGUE_BOTTOM + this.renderSlideOffsetY,
                            top + INTERACTION_DIALOGUE_TOP + (row + 1) * lineStep + 2 + this.renderSlideOffsetY));
            drawOutlinedString(graphics, lines.get(lineIndex), drawLeft, lineTop, INTERACTION_DIALOGUE_COLOR);
            graphics.disableScissor();
        }

        ResourceLocation scrollIcon = skillsDialogueScrollIcon(lines, lineScroll);
        if (scrollIcon != null) {
            int iconLeft = left + INTERACTION_DIALOGUE_SCROLL_ICON_LEFT;
            int iconTop = top + INTERACTION_DIALOGUE_SCROLL_ICON_BOTTOM - INTERACTION_DIALOGUE_SCROLL_ICON_HEIGHT;
            graphics.blit(
                    scrollIcon,
                    iconLeft,
                    iconTop,
                    0,
                    0,
                    INTERACTION_DIALOGUE_SCROLL_ICON_WIDTH,
                    INTERACTION_DIALOGUE_SCROLL_ICON_HEIGHT,
                    INTERACTION_DIALOGUE_SCROLL_ICON_WIDTH,
                    INTERACTION_DIALOGUE_SCROLL_ICON_HEIGHT);
            graphics.drawString(
                    this.font,
                    Component.translatable(SKILLS_DIALOGUE_BACK_HINT_KEY),
                    iconLeft + INTERACTION_DIALOGUE_SCROLL_ICON_WIDTH + SKILLS_DIALOGUE_BACK_HINT_GAP,
                    iconTop + (INTERACTION_DIALOGUE_SCROLL_ICON_HEIGHT - this.font.lineHeight) / 2,
                    SKILLS_DIALOGUE_BACK_HINT_COLOR,
                    false);
        }
    }

    private String skillsDialogueInfoText() {
        Optional<VillagerProfileClientCache.DisplayEntry> entry = VillagerProfileClientCache.get(this.villagerEntityId);
        if (this.skillsProfilePanel == SkillsProfilePanel.PROFILE) {
            return profileDialogueInfoText(entry);
        }
        if (this.skillsProfilePanel == SkillsProfilePanel.JOBS) {
            return jobStatsDialogueInfoText(entry);
        }
        return skillsDialogueInfoText(entry);
    }

    private String profileDialogueInfoText(Optional<VillagerProfileClientCache.DisplayEntry> entry) {
        if (this.selectedProfileAttributeDetails != null) {
            if (entry.isEmpty()) {
                return localizedAttribute(this.selectedProfileAttributeDetails) + "\n\n" + translate("profile.loading");
            }
            VillagerProfileClientCache.DisplayEntry profile = entry.get();
            return String.join(
                    "\n",
                    localizedAttribute(this.selectedProfileAttributeDetails),
                    "",
                    translate(
                            "profile.tooltip.level",
                            localizedRank(profile.rank(this.selectedProfileAttributeDetails))),
                    translate("profile.tooltip.score", profile.value(this.selectedProfileAttributeDetails)),
                    "",
                    localizedAttributeDescription(this.selectedProfileAttributeDetails));
        }
        return String.join(
                "\n\n",
                translate("profile.attributes.info.title"),
                translate("profile.attributes.info.personality"),
                translate("profile.attributes.info.behavior"),
                translate("profile.attributes.info.growth"));
    }

    private String skillsDialogueInfoText(Optional<VillagerProfileClientCache.DisplayEntry> entry) {
        if (this.selectedSkillDetails != null) {
            if (entry.isEmpty()) {
                return localizedSkill(this.selectedSkillDetails) + "\n" + translate("profile.loading");
            }
            VillagerProfileClientCache.DisplayEntry profile = entry.get();
            return String.join(
                    "\n",
                    localizedSkill(this.selectedSkillDetails),
                    "",
                    translate("profile.tooltip.level", localizedSkillRank(profile.skillRank(this.selectedSkillDetails))),
                    translate("profile.tooltip.score", profile.skillValue(this.selectedSkillDetails)),
                    "",
                    localizedExpandedSkillDescription(this.selectedSkillDetails));
        }
        return String.join(
                "\n\n",
                translate("profile.skills.info.title"),
                translate("profile.skills.info.trade"),
                translate("profile.skills.info.specialty"),
                translate("profile.skills.info.recruit"));
    }

    private String jobStatsDialogueInfoText(Optional<VillagerProfileClientCache.DisplayEntry> entry) {
        if (this.selectedJobDetails == null) {
            return String.join(
                    "\n\n",
                    translate("job_stats.info.title"),
                    translate("job_stats.info.qualification"),
                    translate("job_stats.info.profession"),
                    translate("job_stats.info.practice"));
        }
        HiredVillagerRole role = this.selectedJobDetails;
        if (entry.isEmpty()) {
            return jobRoleLabel(role) + "\n\n" + translate("profile.loading");
        }
        VillagerProfileClientCache.DisplayEntry profile = entry.get();
        HiredVillagerRoles.RoleDefinition definition = HiredVillagerRoles.definition(role);
        int primary = profile.skillValue(definition.primarySkill());
        int support = profile.skillValue(definition.supportSkill());
        int total = HiredVillagerRoles.qualificationTotal(profile.skills(), role);
        int aptitude = HiredVillagerRoles.roleScore(profile.skills(), role);
        int workSpeed = HiredVillagerRoles.skillWorkSpeedPercent(aptitude);
        int transferBase = HiredVillagerRoles.baseTransferItems(role);
        boolean ready = HiredVillagerRoles.isSkillUnlocked(
                profile.professionKey(), this.baby, profile.skills(), role);
        List<String> lines = new ArrayList<>();
        lines.add(jobRoleLabel(role));
        lines.add("");
        lines.add(translate("job_stats.detail.readiness", translate(ready ? "job_stats.ready" : "job_stats.locked")));
        lines.add(translate("job_stats.detail.qualification", total, HiredVillagerRoles.QUALIFICATION_REQUIRED_TOTAL));
        lines.add(translate("job_stats.detail.primary", localizedSkill(definition.primarySkill()), primary));
        lines.add(translate("job_stats.detail.support", localizedSkill(definition.supportSkill()), support));
        lines.add("");
        lines.add(translate("job_stats.detail.aptitude", aptitude));
        lines.add(translate("job_stats.detail.work_speed", workSpeed));
        if (transferBase > 0) {
            int transfer = role == HiredVillagerRole.COURIER
                    ? HiredVillagerRoles.courierTransferLimit(aptitude)
                    : HiredVillagerRoles.transferLimit(
                            transferBase, HiredVillagerRoles.transferCapacityPercent(aptitude));
            lines.add(translate(role == HiredVillagerRole.COURIER
                    ? "job_stats.detail.courier_transfer"
                    : "job_stats.detail.transfer", transfer));
        }
        lines.add(translate("job_stats.detail.effect." + roleEffectKey(role), workSpeed));
        lines.add("");
        lines.add(translate("job_stats.detail.reason." + readinessReason(profile, role, ready)));
        lines.add(translate("job_stats.detail.practice"));
        return String.join("\n", lines);
    }

    private String readinessReason(
            VillagerProfileClientCache.DisplayEntry profile,
            HiredVillagerRole role,
            boolean ready) {
        if (this.baby) {
            return "baby";
        }
        if (HiredVillagerRoles.isUniversal(role)) {
            return "universal";
        }
        if (HiredVillagerRoles.isCanonicalProfession(profile.professionKey(), role)) {
            return "profession";
        }
        if (HiredVillagerRoles.isProfessionRestricted(role)) {
            return "restricted";
        }
        return ready ? "skills" : "locked";
    }

    private static String roleEffectKey(HiredVillagerRole role) {
        return switch (role) {
            case MINING, LOGGING, BUILDER -> "block";
            case COMBAT, HUNTING -> "combat";
            case FISHING -> "fishing";
            case COOK, SMELTER, BREWING, COURIER -> "transfer";
            case NITWIT -> "nitwit";
            default -> "cadence";
        };
    }

    private static String jobRoleLabel(HiredVillagerRole role) {
        return translate("job_stats.role." + role.serializedName());
    }

    private ResourceLocation skillsDialogueScrollIcon(List<String> lines, int lineScroll) {
        int maxScroll = maxInteractionDialogueLineScroll(lines);
        if (maxScroll <= 0) {
            return null;
        }
        if (lineScroll >= maxScroll) {
            return VillagerRetaliationClientAssets.INTERACTION_SCROLL_ICON_UP_TEXTURE;
        }
        return VillagerRetaliationClientAssets.INTERACTION_SCROLL_ICON_DOWN_TEXTURE;
    }

    private void renderGiftPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        VillagerInteractionGiftPage.render(
                this.giftPageContext,
                graphics,
                mouseX,
                mouseY,
                partialTick,
                this.width,
                this.height
        );
    }

    private void renderInteractionContainer(GuiGraphics graphics) {
        if (!shouldRenderInteractionContainer()) {
            return;
        }

        int left = interactionContainerLeft();
        int top = interactionContainerTop();
        graphics.blit(
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_TEXTURE,
                left,
                top,
                0,
                0,
                INTERACTION_CONTAINER_WIDTH,
                INTERACTION_CONTAINER_HEIGHT,
                INTERACTION_CONTAINER_WIDTH,
                INTERACTION_CONTAINER_HEIGHT
        );
        renderInteractionNameplate(graphics, left, top, interactionNameplate());
        renderInteractionVillagerPortrait(graphics, left, top);
        renderInteractionDialogue(graphics, left, top);
        renderInteractionInfoRow(graphics, left, top);
    }

    private void renderInteractionMenuButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        List<InteractionMenuButton> buttons = interactionMenuButtons();
        int hovered = interactionMenuButtonAt(mouseX, mouseY);
        int highlighted = highlightedInteractionMenuButton(hovered, buttons.size());
        int top = interactionMenuButtonTop();
        for (int index = 0; index < buttons.size(); index++) {
            InteractionMenuButton button = buttons.get(index);
            int left = interactionMenuButtonLeft(index, buttons.size());
            graphics.blit(
                    VillagerRetaliationClientAssets.INTERACTION_BUTTON_TEXTURE,
                    left,
                    top,
                    0,
                    0,
                    INTERACTION_BUTTON_SIZE,
                    INTERACTION_BUTTON_SIZE,
                    INTERACTION_BUTTON_SIZE,
                    INTERACTION_BUTTON_SIZE
            );
            graphics.blit(
                    button.icon(),
                    left,
                    top,
                    0,
                    0,
                    INTERACTION_BUTTON_SIZE,
                    INTERACTION_BUTTON_SIZE,
                    INTERACTION_BUTTON_SIZE,
                    INTERACTION_BUTTON_SIZE
            );
            if (index == highlighted) {
                renderInteractionButtonHighlight(graphics, left, top, button.active());
            }
        }

        if (highlighted >= 0 && highlighted < buttons.size()) {
            InteractionMenuButton button = buttons.get(highlighted);
            List<Component> tooltip = List.of(
                    Component.literal(button.title()).withStyle(ChatFormatting.YELLOW),
                    Component.literal(button.description()).withStyle(ChatFormatting.GRAY));
            if (hovered < 0 && this.keyboardInteractionMenuFocusVisible && highlighted == this.selectedInteractionMenuButton) {
                renderKeyboardInteractionMenuTooltip(graphics, tooltip, highlighted);
            } else {
                renderInteractionTooltip(graphics, tooltip, mouseX, mouseY);
            }
        }
    }

    private void renderKeyboardInteractionMenuTooltip(GuiGraphics graphics, List<Component> tooltip, int buttonIndex) {
        int buttonLeft = interactionMenuButtonLeft(buttonIndex);
        int buttonTop = interactionMenuButtonTop();
        int minX = -this.renderContentOffsetX;
        int minY = -this.renderSlideOffsetY;
        int maxX = graphics.guiWidth() - this.renderContentOffsetX;
        int maxY = graphics.guiHeight() - this.renderSlideOffsetY;
        renderInteractionTooltip(
                graphics,
                tooltip,
                new InteractionKeyboardTooltipPositioner(
                        buttonLeft,
                        buttonLeft + INTERACTION_BUTTON_SIZE,
                        buttonTop,
                        buttonTop + INTERACTION_BUTTON_SIZE,
                        minX,
                        minY,
                        maxX,
                        maxY),
                buttonLeft + INTERACTION_BUTTON_SIZE,
                buttonTop);
    }

    private int highlightedInteractionMenuButton(int hovered, int buttonCount) {
        if (hovered >= 0 && hovered < buttonCount) {
            return hovered;
        }
        if (this.keyboardInteractionMenuFocusVisible && isValidInteractionMenuButton(this.selectedInteractionMenuButton, buttonCount)) {
            return this.selectedInteractionMenuButton;
        }
        return -1;
    }

    private void renderInteractionButtonHighlight(GuiGraphics graphics, int left, int top, boolean active) {
        int color = active ? INTERACTION_BUTTON_HIGHLIGHT_COLOR : INTERACTION_BUTTON_DISABLED_HIGHLIGHT_COLOR;
        int highlightLeft = left + INTERACTION_BUTTON_HIGHLIGHT_INSET;
        int highlightTop = top + INTERACTION_BUTTON_HIGHLIGHT_INSET;
        int highlightRight = left + INTERACTION_BUTTON_SIZE - INTERACTION_BUTTON_HIGHLIGHT_INSET;
        int highlightBottom = top + INTERACTION_BUTTON_SIZE - INTERACTION_BUTTON_HIGHLIGHT_INSET;
        graphics.fillGradient(RenderType.guiOverlay(), highlightLeft, highlightTop, highlightRight, highlightBottom, color, color, 0);
    }

    private List<InteractionMenuButton> interactionMenuButtons() {
        boolean inventoryAvailable = canRequestVillagerInventory();
        boolean stayAvailable = this.recruitedPartyVillager
                ? this.partyVillagerAuthorized
                : this.stayingHere || canCommandStayHere();
        List<InteractionMenuButton> buttons = new ArrayList<>();
        buttons.add(new InteractionMenuButton(
                VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_TALK_TEXTURE,
                translate("root.talk"),
                translate("interaction_button.talk.description"),
                this::openTalkPage,
                true));
        if (!this.baby && !this.recruitedPartyVillager && this.canTrade) {
            buttons.add(new InteractionMenuButton(
                    VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_TRADE_TEXTURE,
                    translate("root.trade"),
                    translate("interaction_button.trade.description"),
                    this::requestTrade,
                    true));
        }
        if (this.reputationLevel != VillagerReputationLevel.FEARED
                && hasQuestOptions()
                && (!this.recruitedPartyVillager || !this.partyVillagerPartyMember)) {
            buttons.add(new InteractionMenuButton(
                    VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_ADVENTURES_TEXTURE,
                    translate("root.adventures"),
                    translate("interaction_button.adventures.description"),
                    this::openAdventuresPage,
                    true));
        }
        buttons.add(new InteractionMenuButton(
                VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_PROFILE_TEXTURE,
                translate("root.profile"),
                translate("interaction_button.profile.description"),
                this::openProfilePage,
                true));
        buttons.add(new InteractionMenuButton(
                VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_PROFILE_TEXTURE,
                translate("root.allegiance"),
                translate("interaction_button.allegiance.description"),
                this::openAllegiancePage,
                true));
        if (!this.baby && this.duelVisible) {
            buttons.add(new InteractionMenuButton(
                    VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_ADVENTURES_TEXTURE,
                    translate("root.duel"),
                    translate("interaction_button.duel.description"),
                    this::openDuelPage,
                    true));
        }
        if (VillagerRetaliationConfig.ENABLE_VILLAGER_GIFTS.get()) {
            buttons.add(new InteractionMenuButton(
                    VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_GIFT_TEXTURE,
                    translate("root.gift"),
                    translate("interaction_button.gift.description"),
                    this::openGiftPage,
                    true));
        }
        if (!this.baby) {
            buttons.add(new InteractionMenuButton(
                    VillagerRetaliationClientAssets.PARTY_RECRUITMENT_PLACEHOLDER_ICON,
                    translate(this.recruitedPartyVillager
                            ? "root.party"
                            : this.hiredByPlayer || this.hiredByOtherPlayer ? "root.job" : "interaction_button.hire_job"),
                    translate(this.recruitedPartyVillager
                            ? "interaction_button.party.description"
                            : this.hiredByPlayer || this.hiredByOtherPlayer
                                    ? "interaction_button.job.description"
                                    : "interaction_button.hire_job.description"),
                    this::openRecruitPage,
                    true));
        }
        buttons.add(new InteractionMenuButton(
                VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_INVENTORY_TEXTURE,
                translate("root.inventory"),
                translate(inventoryAvailable
                        ? "interaction_button.inventory.description"
                        : "interaction_button.inventory.locked_description"),
                this::requestInventory,
                inventoryAvailable));
        if (this.recruitedPartyVillager) {
            buttons.add(this.stayingHere ? followInteractionButton() : stayInteractionButton(stayAvailable));
        } else {
            buttons.add(followInteractionButton());
            buttons.add(stayInteractionButton(stayAvailable));
        }
        return buttons;
    }

    private InteractionMenuButton followInteractionButton() {
        if (this.recruitedPartyVillager) {
            return new InteractionMenuButton(
                    VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_START_FOLLOW_TEXTURE,
                    translate(this.stayingHere ? "party.follow_me" : "party.following"),
                    translate("interaction_button.follow_me.description"),
                    () -> requestRecruit(VillagerRecruitRequestPayload.Action.FOLLOW),
                    this.partyVillagerAuthorized && this.stayingHere);
        }
        if (this.followingPlayer) {
            return new InteractionMenuButton(
                    VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_STOP_FOLLOW_TEXTURE,
                    translate("recruit.stop_following"),
                    translate("interaction_button.stop_following.description"),
                    () -> requestRecruit(VillagerRecruitRequestPayload.Action.STOP_FOLLOWING),
                    true);
        }
        return new InteractionMenuButton(
                VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_START_FOLLOW_TEXTURE,
                translate("recruit.follow_me"),
                translate("interaction_button.follow_me.description"),
                () -> requestRecruit(VillagerRecruitRequestPayload.Action.FOLLOW),
                true);
    }

    private InteractionMenuButton stayInteractionButton(boolean active) {
        if (this.recruitedPartyVillager) {
            return new InteractionMenuButton(
                    VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_STAY_TEXTURE,
                    translate(this.stayingHere ? "party.staying" : "party.stay_here"),
                    translate("interaction_button.stay_here.description"),
                    () -> requestRecruit(VillagerRecruitRequestPayload.Action.STAY_HERE),
                    this.partyVillagerAuthorized && !this.stayingHere);
        }
        if (this.stayingHere) {
            return new InteractionMenuButton(
                    VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_STAY_TEXTURE,
                    translate("interaction_button.move_freely"),
                    translate("interaction_button.move_freely.description"),
                    () -> requestRecruit(VillagerRecruitRequestPayload.Action.STOP_STAYING_HERE),
                    true);
        }
        return new InteractionMenuButton(
                VillagerRetaliationClientAssets.INTERACTION_BUTTON_ICON_STAY_TEXTURE,
                translate("recruit.stay_here"),
                translate(active
                        ? "interaction_button.stay_here.description"
                        : "interaction_button.stay_here.locked_description"),
                () -> requestRecruit(VillagerRecruitRequestPayload.Action.STAY_HERE),
                active);
    }

    private VillagerInteractionTextLayout.Nameplate interactionNameplate() {
        return VillagerInteractionTextLayout.nameplate(
                this.font,
                this.villagerName,
                INTERACTION_NAMEPLATE_TEXTURE_WIDTH,
                INTERACTION_NAMEPLATE_TEXT_HORIZONTAL_PADDING
        );
    }

    private void renderInteractionNameplate(GuiGraphics graphics, int left, int top, VillagerInteractionTextLayout.Nameplate nameplate) {
        int width = nameplate.width();
        int plateLeft = left + INTERACTION_NAMEPLATE_X;
        int plateTop = top + INTERACTION_NAMEPLATE_Y;
        blitNineSlicedTexture(
                graphics,
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_NAMEPLATE_TEXTURE,
                plateLeft,
                plateTop,
                width,
                INTERACTION_NAMEPLATE_TEXTURE_HEIGHT,
                INTERACTION_NAMEPLATE_TEXTURE_WIDTH,
                INTERACTION_NAMEPLATE_TEXTURE_HEIGHT,
                INTERACTION_NAMEPLATE_SLICE_LEFT,
                INTERACTION_NAMEPLATE_SLICE_RIGHT,
                INTERACTION_NAMEPLATE_SLICE_TOP,
                INTERACTION_NAMEPLATE_SLICE_BOTTOM
        );
        String displayName = nameplate.displayName();
        if (!displayName.isBlank()) {
            int textLeft = plateLeft + (width - this.font.width(displayName)) / 2;
            int textTop = plateTop + (INTERACTION_NAMEPLATE_TEXTURE_HEIGHT - this.font.lineHeight) / 2
                    + INTERACTION_NAMEPLATE_TEXT_Y_OFFSET;
            drawOutlinedString(graphics, displayName, textLeft, textTop, INTERACTION_NAME_COLOR);
        }
    }

    private void blitNineSlicedTexture(
            GuiGraphics graphics,
            ResourceLocation texture,
            int left,
            int top,
            int width,
            int height,
            int textureWidth,
            int textureHeight,
            int sliceLeft,
            int sliceRight,
            int sliceTop,
            int sliceBottom) {
        int centerSourceWidth = textureWidth - sliceLeft - sliceRight;
        int centerSourceHeight = textureHeight - sliceTop - sliceBottom;
        int centerWidth = Math.max(0, width - sliceLeft - sliceRight);
        int centerHeight = Math.max(0, height - sliceTop - sliceBottom);

        blitNineSlicedTexturePart(graphics, texture, left, top, sliceLeft, sliceTop, 0, 0, sliceLeft, sliceTop, textureWidth, textureHeight);
        blitNineSlicedTexturePart(graphics, texture, left + sliceLeft, top, centerWidth, sliceTop, sliceLeft, 0, centerSourceWidth, sliceTop, textureWidth, textureHeight);
        blitNineSlicedTexturePart(graphics, texture, left + width - sliceRight, top, sliceRight, sliceTop, textureWidth - sliceRight, 0, sliceRight, sliceTop, textureWidth, textureHeight);

        blitNineSlicedTexturePart(graphics, texture, left, top + sliceTop, sliceLeft, centerHeight, 0, sliceTop, sliceLeft, centerSourceHeight, textureWidth, textureHeight);
        blitNineSlicedTexturePart(graphics, texture, left + sliceLeft, top + sliceTop, centerWidth, centerHeight, sliceLeft, sliceTop, centerSourceWidth, centerSourceHeight, textureWidth, textureHeight);
        blitNineSlicedTexturePart(graphics, texture, left + width - sliceRight, top + sliceTop, sliceRight, centerHeight, textureWidth - sliceRight, sliceTop, sliceRight, centerSourceHeight, textureWidth, textureHeight);

        blitNineSlicedTexturePart(graphics, texture, left, top + height - sliceBottom, sliceLeft, sliceBottom, 0, textureHeight - sliceBottom, sliceLeft, sliceBottom, textureWidth, textureHeight);
        blitNineSlicedTexturePart(graphics, texture, left + sliceLeft, top + height - sliceBottom, centerWidth, sliceBottom, sliceLeft, textureHeight - sliceBottom, centerSourceWidth, sliceBottom, textureWidth, textureHeight);
        blitNineSlicedTexturePart(graphics, texture, left + width - sliceRight, top + height - sliceBottom, sliceRight, sliceBottom, textureWidth - sliceRight, textureHeight - sliceBottom, sliceRight, sliceBottom, textureWidth, textureHeight);
    }

    private void blitNineSlicedTexturePart(
            GuiGraphics graphics,
            ResourceLocation texture,
            int destLeft,
            int destTop,
            int destWidth,
            int destHeight,
            int sourceLeft,
            int sourceTop,
            int sourceWidth,
            int sourceHeight,
            int textureWidth,
            int textureHeight) {
        if (destWidth <= 0 || destHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        graphics.blit(
                texture,
                destLeft,
                destTop,
                destWidth,
                destHeight,
                (float) sourceLeft,
                (float) sourceTop,
                sourceWidth,
                sourceHeight,
                textureWidth,
                textureHeight
        );
    }

    private void renderInteractionVillagerPortrait(GuiGraphics graphics, int left, int top) {
        Entity entity = Minecraft.getInstance().level == null
                ? null
                : Minecraft.getInstance().level.getEntity(this.villagerEntityId);
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        int portraitLeft = left + INTERACTION_PORTRAIT_LEFT;
        int portraitTop = top + INTERACTION_PORTRAIT_TOP;
        int portraitRight = left + INTERACTION_PORTRAIT_RIGHT;
        int portraitBottom = top + INTERACTION_PORTRAIT_BOTTOM;
        float centerX = (portraitLeft + portraitRight) / 2.0F;
        float centerY = (portraitTop + portraitBottom) / 2.0F;
        float renderY = portraitBottom + INTERACTION_PORTRAIT_RENDER_Y_OFFSET;
        float mouseYaw = (float) Math.atan((centerX - this.lastMouseX) / 40.0F);
        float mousePitch = (float) Math.atan((centerY - this.lastMouseY) / 40.0F);
        Quaternionf entityRotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraRotation = new Quaternionf().rotateX(mousePitch * 20.0F * ((float) Math.PI / 180.0F));
        entityRotation.mul(cameraRotation);

        float previousBodyRot = livingEntity.yBodyRot;
        float previousYRot = livingEntity.getYRot();
        float previousXRot = livingEntity.getXRot();
        float previousHeadRotO = livingEntity.yHeadRotO;
        float previousHeadRot = livingEntity.yHeadRot;
        boolean previousSprinting = livingEntity.isSprinting();
        livingEntity.yBodyRot = 180.0F + mouseYaw * 20.0F;
        livingEntity.setYRot(180.0F + mouseYaw * 40.0F);
        livingEntity.setXRot(-mousePitch * 20.0F);
        livingEntity.yHeadRot = livingEntity.getYRot();
        livingEntity.yHeadRotO = livingEntity.getYRot();
        livingEntity.setSprinting(isDialogueMouthAnimationActive());

        float scale = livingEntity.getScale();
        graphics.enableScissor(
                portraitLeft,
                portraitTop + this.renderSlideOffsetY,
                portraitRight + INTERACTION_PORTRAIT_SCISSOR_RIGHT_EXTENSION,
                portraitBottom + this.renderSlideOffsetY);
        try (VillagerModelPreviewRenderContext.Scope ignored = VillagerModelPreviewRenderContext.begin(
                livingEntity,
                VillagerModelPreviewRenderContext.PreviewType.INTERACTION)) {
            InventoryScreen.renderEntityInInventory(
                    graphics,
                    centerX,
                    renderY,
                    INTERACTION_PORTRAIT_SCALE / scale,
                    new Vector3f(0.0F, livingEntity.getBbHeight() / 2.0F + 0.0625F * scale, 0.0F),
                    entityRotation,
                    cameraRotation,
                    livingEntity
            );
        } finally {
            graphics.disableScissor();
            livingEntity.yBodyRot = previousBodyRot;
            livingEntity.setYRot(previousYRot);
            livingEntity.setXRot(previousXRot);
            livingEntity.yHeadRotO = previousHeadRotO;
            livingEntity.yHeadRot = previousHeadRot;
            livingEntity.setSprinting(previousSprinting);
        }
    }

    private boolean shouldRenderInteractionContainer() {
        return this.page != DialoguePage.PROFILE
                && this.page != DialoguePage.SKILLS;
    }

    private void renderInteractionDialogue(GuiGraphics graphics, int left, int top) {
        if (this.villagerDialogueText.isBlank()) {
            return;
        }

        String displayedDialogue = displayedDialogueText();
        if (displayedDialogue.isBlank()) {
            return;
        }
        maybePlayDialogueBlip();

        List<String> lines = wrappedInteractionDialogueLines(displayedDialogue);
        if (lines.isEmpty()) {
            return;
        }
        List<List<DialogueTextSegment>> styledLines = interactionDialogueLineSegments(
                displayedDialogue,
                displayedDialogueSegments(),
                lines);

        int visibleLines = interactionDialogueVisibleLineCount();
        int maxScroll = maxInteractionDialogueLineScroll(lines);
        this.dialogueLineScroll = Mth.clamp(this.dialogueLineScroll, 0, maxScroll);
        int drawLeft = left + INTERACTION_DIALOGUE_LEFT + 1;
        int drawTop = top + INTERACTION_DIALOGUE_TOP + 1;
        int lineStep = this.font.lineHeight;
        int linesToDraw = Math.min(visibleLines, lines.size() - this.dialogueLineScroll);
        for (int row = 0; row < linesToDraw; row++) {
            int lineIndex = this.dialogueLineScroll + row;
            int lineTop = drawTop + row * lineStep;
            int lineRight = left + (row == visibleLines - 1
                    ? INTERACTION_DIALOGUE_RIGHT
                    : INTERACTION_DIALOGUE_EXTENDED_RIGHT);
            graphics.enableScissor(
                    left + INTERACTION_DIALOGUE_LEFT,
                    top + INTERACTION_DIALOGUE_TOP + row * lineStep + this.renderSlideOffsetY,
                    lineRight,
                    Math.min(
                            top + INTERACTION_DIALOGUE_BOTTOM + this.renderSlideOffsetY,
                            top + INTERACTION_DIALOGUE_TOP + (row + 1) * lineStep + 2 + this.renderSlideOffsetY));
            drawOutlinedDialogueLine(
                    graphics,
                    lines.get(lineIndex),
                    styledLines.get(lineIndex),
                    drawLeft,
                    lineTop,
                    INTERACTION_DIALOGUE_COLOR
            );
            graphics.disableScissor();
        }

        ResourceLocation scrollIcon = interactionDialogueScrollIcon(lines);
        if (scrollIcon != null) {
            graphics.blit(
                    scrollIcon,
                    left + INTERACTION_DIALOGUE_SCROLL_ICON_LEFT,
                    top + INTERACTION_DIALOGUE_SCROLL_ICON_BOTTOM - INTERACTION_DIALOGUE_SCROLL_ICON_HEIGHT,
                    0,
                    0,
                    INTERACTION_DIALOGUE_SCROLL_ICON_WIDTH,
                    INTERACTION_DIALOGUE_SCROLL_ICON_HEIGHT,
                    INTERACTION_DIALOGUE_SCROLL_ICON_WIDTH,
                    INTERACTION_DIALOGUE_SCROLL_ICON_HEIGHT
            );
        }
    }

    private List<String> wrappedInteractionDialogueLines(String dialogue) {
        if (dialogue == null || dialogue.isBlank()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (String paragraph : dialogue.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            appendWrappedInteractionDialogueParagraph(lines, paragraph);
        }
        return lines;
    }

    private void appendWrappedInteractionDialogueParagraph(List<String> lines, String paragraph) {
        if (paragraph == null || paragraph.isBlank()) {
            lines.add("");
            return;
        }

        String remaining = paragraph.stripLeading();
        while (!remaining.isEmpty()) {
            int wrapWidth = interactionDialogueLineWidth(lines.size());
            String line = this.font.plainSubstrByWidth(remaining, wrapWidth);
            if (line.isEmpty()) {
                int nextCodePointEnd = remaining.offsetByCodePoints(0, 1);
                lines.add(remaining.substring(0, nextCodePointEnd));
                remaining = remaining.substring(nextCodePointEnd).stripLeading();
                continue;
            }

            if (line.length() < remaining.length()) {
                int breakIndex = lastWhitespaceBreak(line);
                if (breakIndex > 0) {
                    line = remaining.substring(0, breakIndex);
                    remaining = remaining.substring(breakIndex).stripLeading();
                } else {
                    remaining = remaining.substring(line.length()).stripLeading();
                }
            } else {
                remaining = "";
            }
            lines.add(line.stripTrailing());
        }
    }

    private int interactionDialogueLineWidth(int lineIndex) {
        int visibleLines = interactionDialogueVisibleLineCount();
        boolean bottomRow = visibleLines > 0 && Math.floorMod(lineIndex, visibleLines) == visibleLines - 1;
        int right = bottomRow ? INTERACTION_DIALOGUE_RIGHT : INTERACTION_DIALOGUE_EXTENDED_RIGHT;
        return Math.max(1, right - INTERACTION_DIALOGUE_LEFT - 2);
    }

    private static int lastWhitespaceBreak(String text) {
        for (int index = text.length(); index > 0; ) {
            int codePoint = text.codePointBefore(index);
            index -= Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                return index;
            }
        }
        return -1;
    }

    private int interactionDialogueVisibleLineCount() {
        int strokedHeight = Math.max(1, INTERACTION_DIALOGUE_BOTTOM - INTERACTION_DIALOGUE_TOP - 2);
        return Math.max(1, strokedHeight / Math.max(1, this.font.lineHeight));
    }

    private int maxInteractionDialogueLineScroll(List<String> lines) {
        return Math.max(0, lines.size() - interactionDialogueVisibleLineCount());
    }

    private ResourceLocation interactionDialogueScrollIcon(List<String> lines) {
        int maxScroll = maxInteractionDialogueLineScroll(lines);
        if (maxScroll <= 0 || !isDialogueTextAnimationComplete()) {
            return null;
        }
        if (this.dialogueLineScroll >= maxScroll) {
            return VillagerRetaliationClientAssets.INTERACTION_SCROLL_ICON_UP_TEXTURE;
        }
        return VillagerRetaliationClientAssets.INTERACTION_SCROLL_ICON_DOWN_TEXTURE;
    }

    private List<DialogueTextSegment> displayedDialogueSegments() {
        String displayedDialogue = displayedDialogueText();
        if (displayedDialogue.isBlank()) {
            return List.of();
        }
        if (VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()) {
            return DialogueTextSegment.plain(displayedDialogue, DialogueTextEffects.NONE);
        }
        if (isDialogueTextAnimationComplete()) {
            return this.villagerDialogueTextSegments;
        }
        return DialogueTextSegment.slice(this.villagerDialogueTextSegments, 0, displayedDialogue.length());
    }

    private String displayedDialogueText() {
        if (isDialogueTextAnimationComplete()) {
            return this.villagerDialogueText;
        }

        int visibleCharacters = visibleDialogueTextCharacters();
        if (visibleCharacters <= 0) {
            return "";
        }
        int endIndex = this.villagerDialogueText.offsetByCodePoints(
                0,
                Math.min(visibleCharacters, this.villagerDialogueText.codePointCount(0, this.villagerDialogueText.length()))
        );
        return this.villagerDialogueText.substring(0, endIndex);
    }

    private static List<DialogueTextSegment> normalizeDialogueSegments(String text, List<DialogueTextSegment> textSegments) {
        List<DialogueTextSegment> sourceSegments;
        if (textSegments == null || textSegments.isEmpty()) {
            sourceSegments = DialogueTextSegment.parse(text.strip(), DialogueTextEffects.NONE);
        } else {
            sourceSegments = textSegments.stream()
                    .filter(segment -> segment != null && !segment.text().isEmpty())
                    .toList();
        }
        if (sourceSegments.isEmpty()) {
            return DialogueTextSegment.plain(text.strip(), DialogueTextEffects.NONE);
        }
        return stripDialogueSegments(sourceSegments);
    }

    private static List<DialogueTextSegment> stripDialogueSegments(List<DialogueTextSegment> segments) {
        String plainText = DialogueTextSegment.plainText(segments);
        int start = 0;
        while (start < plainText.length()) {
            int codePoint = plainText.codePointAt(start);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }

        int end = plainText.length();
        while (end > start) {
            int codePoint = plainText.codePointBefore(end);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return start >= end ? List.of() : DialogueTextSegment.slice(segments, start, end);
    }

    private static int findDisplayedLineStart(String text, String lineText, int cursor) {
        if (lineText.isEmpty()) {
            return Math.min(cursor, text.length());
        }
        int start = text.indexOf(lineText, Math.min(cursor, text.length()));
        return start >= 0 ? start : text.indexOf(lineText);
    }

    private static List<List<DialogueTextSegment>> interactionDialogueLineSegments(
            String dialogue,
            List<DialogueTextSegment> segments,
            List<String> lines) {
        List<List<DialogueTextSegment>> styledLines = new ArrayList<>(lines.size());
        int cursor = 0;
        for (String line : lines) {
            int lineStart = findDisplayedLineStart(dialogue, line, cursor);
            if (lineStart < 0) {
                styledLines.add(DialogueTextSegment.plain(line, DialogueTextEffects.NONE));
                continue;
            }

            int lineEnd = lineStart + line.length();
            styledLines.add(DialogueTextSegment.slice(segments, lineStart, lineEnd));
            cursor = lineEnd;
        }
        return List.copyOf(styledLines);
    }

    private boolean trySkipDialogueTextAnimation() {
        if (isDialogueTextAnimationComplete()) {
            return false;
        }
        this.dialogueTextAnimationSkipped = true;
        updateDialogueMouthAnimation();
        return true;
    }

    private void updateDialogueMouthAnimation() {
        boolean talking = isDialogueMouthAnimationActive();
        VillagerDialogueMouthAnimation.update(this.villagerEntityId, talking);
    }

    private boolean isDialogueMouthAnimationActive() {
        return shouldRenderInteractionContainer()
                && !this.villagerDialogueText.isBlank()
                && !isDialogueTextAnimationComplete();
    }

    private void clearDialogueMouthAnimation() {
        VillagerDialogueMouthAnimation.clear(this.villagerEntityId);
    }

    private boolean tryScrollInteractionDialogue(double mouseX, double mouseY, double scrollY) {
        if (!isPointInsideInteractionDialogueScrollArea(mouseX, mouseY) || scrollY == 0.0D) {
            return false;
        }

        List<String> lines = wrappedInteractionDialogueLines(displayedDialogueText());
        int maxScroll = maxInteractionDialogueLineScroll(lines);
        if (maxScroll <= 0) {
            return true;
        }

        int direction = scrollY < 0.0D ? 1 : -1;
        this.dialogueLineScroll = Mth.clamp(this.dialogueLineScroll + direction, 0, maxScroll);
        return true;
    }


    private boolean isPointInsideInteractionDialogueScrollArea(double mouseX, double mouseY) {
        if (!shouldRenderInteractionContainer() || this.villagerDialogueText.isBlank()) {
            return false;
        }

        int left = interactionContainerLeft();
        int top = interactionContainerTop();
        return mouseX >= left + INTERACTION_DIALOGUE_SCROLL_LEFT
                && mouseX < left + INTERACTION_DIALOGUE_SCROLL_RIGHT
                && mouseY >= top + INTERACTION_DIALOGUE_SCROLL_TOP
                && mouseY < top + INTERACTION_DIALOGUE_SCROLL_BOTTOM;
    }

    private boolean isDialogueTextAnimationComplete() {
        if (this.villagerDialogueText.isBlank() || this.dialogueTextAnimationSkipped) {
            return true;
        }
        DialogueTextSpeed speed = dialogueTextSpeed();
        return speed.instant()
                || visibleDialogueTextCharacters() >= this.villagerDialogueText.codePointCount(0, this.villagerDialogueText.length());
    }

    private int visibleDialogueTextCharacters() {
        DialogueTextSpeed speed = dialogueTextSpeed();
        if (speed.instant()) {
            return this.villagerDialogueText.codePointCount(0, this.villagerDialogueText.length());
        }
        long elapsedMillis = Math.max(0L, Util.getMillis() - this.dialogueTextAnimationStartMillis);
        return Math.max(1, (int) (elapsedMillis / speed.millisPerCharacter()) + 1);
    }

    private void maybePlayDialogueBlip() {
        if (this.dialogueTextAnimationSkipped || this.villagerDialogueText.isBlank()
                || !VillagerRetaliationConfig.ENABLE_DIALOGUE_BLIP_AUDIO.get()) {
            return;
        }

        int totalCharacters = this.villagerDialogueText.codePointCount(0, this.villagerDialogueText.length());
        int visibleCharacters = Math.min(visibleDialogueTextCharacters(), totalCharacters);
        if (visibleCharacters <= this.lastDialogueBlipVisibleCharacters
                || visibleCharacters < this.nextDialogueBlipVisibleCharacter) {
            return;
        }

        float volume = dialogueBlipVolume();
        if (volume > 0.0F && hasAudibleDialogueCharacter(this.lastDialogueBlipVisibleCharacters, visibleCharacters)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    DIALOGUE_BLIP_SOUND,
                    this.dialogueBlipPitch,
                    volume
            ));
        }
        this.lastDialogueBlipVisibleCharacters = visibleCharacters;
        this.nextDialogueBlipVisibleCharacter = visibleCharacters + randomDialogueBlipGap();
    }

    private boolean hasAudibleDialogueCharacter(int startCodePoint, int endCodePoint) {
        int totalCharacters = this.villagerDialogueText.codePointCount(0, this.villagerDialogueText.length());
        int safeStart = Math.max(0, startCodePoint);
        int safeEnd = Math.min(endCodePoint, totalCharacters);
        for (int index = safeStart; index < safeEnd; index++) {
            int charIndex = this.villagerDialogueText.offsetByCodePoints(0, index);
            int codePoint = this.villagerDialogueText.codePointAt(charIndex);
            if (!Character.isWhitespace(codePoint)) {
                return true;
            }
        }
        return false;
    }

    private float randomDialogueBlipPitch() {
        float minPitch = dialogueBlipMinPitch();
        float maxPitch = dialogueBlipMaxPitch();
        if (maxPitch < minPitch) {
            float swap = minPitch;
            minPitch = maxPitch;
            maxPitch = swap;
        }
        return minPitch + this.dialogueBlipRandom.nextFloat() * (maxPitch - minPitch);
    }

    private int randomDialogueBlipGap() {
        return DIALOGUE_BLIP_MIN_VISIBLE_CHARACTERS
                + this.dialogueBlipRandom.nextInt(DIALOGUE_BLIP_MAX_VISIBLE_CHARACTERS - DIALOGUE_BLIP_MIN_VISIBLE_CHARACTERS + 1);
    }

    private static float dialogueBlipVolume() {
        return Mth.clamp(VillagerRetaliationConfig.DIALOGUE_BLIP_VOLUME.get().floatValue(), 0.0F, 1.0F);
    }

    private static float dialogueBlipMinPitch() {
        return Mth.clamp(VillagerRetaliationConfig.DIALOGUE_BLIP_MIN_PITCH.get().floatValue(), 0.5F, 2.0F);
    }

    private static float dialogueBlipMaxPitch() {
        return Mth.clamp(VillagerRetaliationConfig.DIALOGUE_BLIP_MAX_PITCH.get().floatValue(), 0.5F, 2.0F);
    }

    private static DialogueTextSpeed dialogueTextSpeed() {
        DialogueTextSpeed speed = VillagerRetaliationConfig.DIALOGUE_TEXT_SPEED.get();
        return speed == null ? DialogueTextSpeed.MEDIUM : speed;
    }

    private void renderInteractionInfoRow(GuiGraphics graphics, int left, int top) {
        InteractionStatLayout stats = interactionStatLayout(left, top);
        drawOutlinedString(
                graphics,
                stats.moodText(),
                stats.moodTextLeft(),
                stats.textTop(),
                INTERACTION_MOOD_COLOR
        );

        graphics.blit(
                VillagerRetaliationClientAssets.INTERACTION_REPUTATION_ICON_TEXTURE,
                stats.reputationIconLeft(),
                stats.iconTop(),
                0,
                0,
                INTERACTION_ICON_SIZE,
                INTERACTION_ICON_SIZE,
                INTERACTION_ICON_SIZE,
                INTERACTION_ICON_SIZE
        );
        drawOutlinedString(
                graphics,
                stats.reputationText(),
                stats.reputationTextLeft(),
                stats.textTop(),
                INTERACTION_REPUTATION_TEXT_COLOR
        );
        TextureAtlasSprite currencySprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(this.walletCurrencyIconSprite);
        graphics.blit(stats.currencyIconLeft(), stats.iconTop(), 0, INTERACTION_ICON_SIZE, INTERACTION_ICON_SIZE, currencySprite);
        drawOutlinedString(
                graphics,
                stats.currencyText(),
                stats.currencyTextLeft(),
                stats.textTop(),
                this.walletCurrencyTextColor
        );
    }

    private int textTopFromBottomBaseline(int baselineY) {
        return baselineY - this.font.lineHeight;
    }

    private void drawOutlinedString(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(this.font, text, x - 1, y, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x + 1, y, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x, y - 1, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x, y + 1, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x, y, color, false);
    }

    private void drawOutlinedDialogueLine(
            GuiGraphics graphics,
            String text,
            List<DialogueTextSegment> segments,
            int x,
            int y,
            int fallbackColor) {
        graphics.drawString(this.font, text, x - 1, y, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x + 1, y, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x, y - 1, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x, y + 1, TEXT_OUTLINE_COLOR, false);

        Component styledText = VillagerStyledTextRenderer.component(
                segments,
                Style.EMPTY,
                fallbackColor & 0x00FFFFFF);
        VillagerStyledTextRenderer.renderLine(
                graphics,
                this.font,
                styledText.getVisualOrderText(),
                segments,
                x,
                y,
                fallbackColor,
                (fallbackColor >>> 24) & 0xFF,
                Minecraft.getInstance().gui.getGuiTicks());
    }

    private void renderInteractionStatTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!shouldRenderInteractionContainer()) {
            return;
        }

        if (isPointInsideInteractionNameplate(mouseX, mouseY)) {
            renderInteractionTooltip(
                    graphics,
                    List.of(
                            Component.literal(this.villagerName).withStyle(ChatFormatting.WHITE),
                            Component.literal(this.professionName).withStyle(ChatFormatting.GRAY)),
                    mouseX,
                    mouseY);
            return;
        }

        InteractionStatLayout stats = interactionStatLayout(interactionContainerLeft(), interactionContainerTop());
        if (stats.containsMood(mouseX, mouseY)) {
            renderInteractionTooltip(
                    graphics,
                    List.of(
                            Component.literal(moodText()).withStyle(ChatFormatting.AQUA),
                            Component.literal(localizedDialogueDispositionName()).withStyle(ChatFormatting.GRAY)),
                    mouseX,
                    mouseY);
            return;
        }

        if (stats.containsCurrency(mouseX, mouseY)) {
            renderInteractionTooltip(
                    graphics,
                    List.of(
                            Component.literal(walletTooltipTitle()).withStyle(ChatFormatting.GREEN),
                            Component.literal(walletTooltipBody()).withStyle(ChatFormatting.GRAY)),
                    mouseX,
                    mouseY);
            return;
        }

        if (stats.containsReputation(mouseX, mouseY)) {
            renderInteractionTooltip(
                    graphics,
                    List.of(
                            Component.literal(reputationText()).withStyle(ChatFormatting.YELLOW),
                            Component.literal(localizedReputationLevelName()).withStyle(ChatFormatting.GRAY)),
                    mouseX,
                    mouseY);
        }
    }

    private void renderInteractionTooltip(GuiGraphics graphics, List<Component> tooltip, int mouseX, int mouseY) {
        renderInteractionTooltip(graphics, tooltip, DefaultTooltipPositioner.INSTANCE, mouseX, mouseY);
    }

    private void renderInteractionTooltip(
            GuiGraphics graphics,
            List<Component> tooltip,
            ClientTooltipPositioner positioner,
            int mouseX,
            int mouseY) {
        if (tooltip.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component component : tooltip) {
            List<FormattedCharSequence> wrappedLines = this.font.split(component, INTERACTION_TOOLTIP_MAX_WIDTH);
            if (wrappedLines.isEmpty()) {
                lines.add(component.getVisualOrderText());
            } else {
                lines.addAll(wrappedLines);
            }
        }
        graphics.renderTooltip(this.font, lines, positioner, mouseX, mouseY);
    }

    private InteractionStatLayout interactionStatLayout(int left, int top) {
        int textTop = textTopFromBottomBaseline(top + INTERACTION_INFO_BASELINE_Y + INTERACTION_INFO_TEXT_Y_OFFSET);
        int iconTop = top + INTERACTION_INFO_BASELINE_Y - INTERACTION_ICON_SIZE + INTERACTION_INFO_ICON_Y_OFFSET;
        String moodText = moodName(this.primaryMood);
        String currencyText = walletAmountText();
        String reputationText = Integer.toString(this.reputation);

        int moodTextLeft = left + INTERACTION_MOOD_BASELINE_LEFT;
        int moodTextRight = moodTextLeft + this.font.width(moodText);
        int currencyTextRight = left + INTERACTION_INFO_BASELINE_RIGHT;
        int currencyTextLeft = currencyTextRight - this.font.width(currencyText);
        int currencyIconLeft = currencyTextLeft - INTERACTION_CURRENCY_ICON_GAP - INTERACTION_ICON_SIZE;
        int reputationTextRight = currencyIconLeft - INTERACTION_REPUTATION_GAP;
        int reputationTextLeft = reputationTextRight - this.font.width(reputationText);
        int reputationIconLeft = reputationTextLeft - INTERACTION_CURRENCY_ICON_GAP - INTERACTION_ICON_SIZE;
        currencyIconLeft += INTERACTION_INFO_ICON_X_OFFSET;
        reputationIconLeft += INTERACTION_INFO_ICON_X_OFFSET;
        int hitTop = Math.min(textTop, iconTop);
        int hitBottom = Math.max(textTop + this.font.lineHeight, iconTop + INTERACTION_ICON_SIZE);
        return new InteractionStatLayout(
                textTop,
                iconTop,
                moodText,
                currencyText,
                reputationText,
                moodTextLeft,
                moodTextRight,
                currencyTextLeft,
                currencyTextRight,
                currencyIconLeft,
                reputationTextLeft,
                reputationTextRight,
                reputationIconLeft,
                hitTop,
                hitBottom
        );
    }

    private boolean isPointInsideInteractionNameplate(double mouseX, double mouseY) {
        VillagerInteractionTextLayout.Nameplate nameplate = interactionNameplate();
        int plateLeft = interactionContainerLeft() + INTERACTION_NAMEPLATE_X;
        int plateTop = interactionContainerTop() + INTERACTION_NAMEPLATE_Y;
        return mouseX >= plateLeft
                && mouseX < plateLeft + nameplate.width()
                && mouseY >= plateTop
                && mouseY < plateTop + INTERACTION_NAMEPLATE_TEXTURE_HEIGHT;
    }

    private int interactionContainerLeft() {
        return (this.width - INTERACTION_CONTAINER_WIDTH) / 2;
    }

    private int interactionContainerTop() {
        return interactionContainerTopForPage(this.page);
    }


    private int interactionContainerTopForPage(DialoguePage page) {
        if (page == DialoguePage.SKILLS) {
            return skillsProfilePanelTop();
        }
        int buttonRowHeight = usesRootIconMenu(page) ? INTERACTION_BUTTON_GAP + INTERACTION_BUTTON_SIZE : 0;
        return Math.max(4, this.height - INTERACTION_CONTAINER_HEIGHT - buttonRowHeight - INTERACTION_CONTAINER_HOTBAR_GAP);
    }

    private int skillsDialogueContainerLeft() {
        return skillsPanelLeft() + (skillsPanelWidth() - SKILLS_DIALOGUE_CONTAINER_WIDTH) / 2;
    }

    private int skillsDialogueContainerTop() {
        return Math.max(4, this.height - SKILLS_DIALOGUE_CONTAINER_HEIGHT - INTERACTION_CONTAINER_HOTBAR_GAP);
    }

    private int skillsDialogueButtonTop() {
        return skillsDialogueContainerTop() + (SKILLS_DIALOGUE_CONTAINER_HEIGHT - SKILLS_DIALOGUE_BUTTON_HEIGHT) / 2;
    }

    private int skillsDialogueLeftButtonLeft() {
        return skillsDialogueContainerLeft() - SKILLS_DIALOGUE_BUTTON_WIDTH + SKILLS_DIALOGUE_BUTTON_INSET;
    }

    private int skillsDialogueRightButtonLeft() {
        return skillsDialogueContainerLeft() + SKILLS_DIALOGUE_CONTAINER_WIDTH - SKILLS_DIALOGUE_BUTTON_INSET;
    }

    private SkillsProfileCycleButton hoveredSkillsProfileCycleButton(double mouseX, double mouseY) {
        int top = skillsDialogueButtonTop();
        int bottom = top + SKILLS_DIALOGUE_BUTTON_HEIGHT;
        if (mouseY < top || mouseY >= bottom) {
            return null;
        }
        int leftButtonLeft = skillsDialogueLeftButtonLeft();
        if (mouseX >= leftButtonLeft && mouseX < leftButtonLeft + SKILLS_DIALOGUE_BUTTON_WIDTH) {
            return SkillsProfileCycleButton.LEFT;
        }
        int rightButtonLeft = skillsDialogueRightButtonLeft();
        if (mouseX >= rightButtonLeft && mouseX < rightButtonLeft + SKILLS_DIALOGUE_BUTTON_WIDTH) {
            return SkillsProfileCycleButton.RIGHT;
        }
        return null;
    }

    private int profileContainerLeft() {
        return skillsPanelLeft() + (skillsPanelWidth() - PROFILE_CONTAINER_WIDTH) / 2;
    }

    private int profileContainerTop() {
        return skillsDialogueContainerTop() - PROFILE_CONTAINER_HEIGHT - SKILLS_DIALOGUE_CONTAINER_GAP;
    }

    private int skillsProfilePanelTop() {
        return this.skillsProfilePanel == SkillsProfilePanel.PROFILE ? profileContainerTop() : skillsPanelTop();
    }

    private boolean isEmbeddedProfilePanelActive() {
        return this.page == DialoguePage.SKILLS && this.skillsProfilePanel == SkillsProfilePanel.PROFILE;
    }

    private int interactionMenuButtonTop() {
        return interactionContainerTop() + INTERACTION_CONTAINER_HEIGHT + INTERACTION_BUTTON_GAP;
    }

    private int interactionMenuButtonLeft(int index) {
        return interactionMenuButtonLeft(index, interactionMenuButtons().size());
    }

    private int interactionMenuButtonLeft(int index, int buttonCount) {
        int rowWidth = buttonCount * INTERACTION_BUTTON_SIZE
                + Math.max(0, buttonCount - 1) * INTERACTION_BUTTON_GAP;
        int rowLeft = interactionContainerLeft() + (INTERACTION_CONTAINER_WIDTH - rowWidth) / 2;
        return rowLeft + index * (INTERACTION_BUTTON_SIZE + INTERACTION_BUTTON_GAP);
    }

    private int interactionMenuButtonAt(double mouseX, double mouseY) {
        if (!usesRootIconMenu()) {
            return -1;
        }
        int top = interactionMenuButtonTop();
        int bottom = top + INTERACTION_BUTTON_SIZE;
        if (mouseY < top || mouseY >= bottom) {
            return -1;
        }
        int buttonCount = interactionMenuButtons().size();
        for (int index = 0; index < buttonCount; index++) {
            int left = interactionMenuButtonLeft(index, buttonCount);
            int right = left + INTERACTION_BUTTON_SIZE;
            if (mouseX >= left && mouseX < right) {
                return index;
            }
        }
        return -1;
    }

    private boolean tryActivateInteractionMenuButton(double mouseX, double mouseY) {
        int hovered = interactionMenuButtonAt(mouseX, mouseY);
        if (hovered < 0) {
            return false;
        }
        activateInteractionMenuButton(hovered, false);
        return true;
    }

    private void activateInteractionMenuButton(int index, boolean keyboardFocusVisible) {
        List<InteractionMenuButton> buttons = interactionMenuButtons();
        if (!isValidInteractionMenuButton(index, buttons.size())) {
            return;
        }
        this.selectedInteractionMenuButton = index;
        this.keyboardInteractionMenuFocusVisible = keyboardFocusVisible;
        InteractionMenuButton button = buttons.get(index);
        if (button.active()) {
            button.action().run();
        }
    }

    private void renderTopBackButton(GuiGraphics graphics, int mouseX, int mouseY) {
        VillagerInteractionNavigation.renderTopBackButton(this.navigationContext, graphics, mouseX, mouseY);
    }

    private void renderHint(GuiGraphics graphics) {
        VillagerInteractionNavigation.renderHint(this.navigationContext, graphics);
    }


    private void updateMouseSelection(int mouseX, int mouseY) {
        if (usesRootIconMenu()) {
            updateInteractionMenuMouseSelection(mouseX, mouseY);
            return;
        }
        int hovered = VillagerInteractionOptionList.optionAt(this.optionListContext, mouseX, mouseY);
        if (hovered >= 0) {
            this.state.setSelectedOption(hovered);
            this.keyboardOptionFocusVisible = false;
        }
    }

    private void updateInteractionMenuMouseSelection(int mouseX, int mouseY) {
        int hovered = interactionMenuButtonAt(mouseX, mouseY);
        if (hovered >= 0) {
            this.selectedInteractionMenuButton = hovered;
            this.keyboardInteractionMenuFocusVisible = false;
        }
    }


    private boolean tryBeginScrollbarDrag(double mouseX, double mouseY) {
        // The compact interaction stack uses scroll arrows instead of a scrollbar.
        // Do not let its otherwise invisible scrollbar hit box consume clicks.
        if (usesInteractionOptionStack()) {
            return false;
        }
        ToucanScrollbarThumb scrollbarThumb = scrollbarThumb();
        if (scrollbarThumb == null || !scrollbarThumb.contains(mouseX, mouseY)) {
            return false;
        }

        this.draggingScrollbar = true;
        this.scrollbarDragOffset = ToucanScrollbars.dragOffset(mouseY, scrollbarThumb);
        return true;
    }

    private boolean tryBeginSkillInfoScrollbarDrag(double mouseX, double mouseY) {
        if (this.page != DialoguePage.SKILLS) {
            return false;
        }

        ToucanScrollbarThumb scrollbarThumb = skillInfoScrollbarThumb();
        if (scrollbarThumb == null || !scrollbarThumb.contains(mouseX, mouseY)) {
            return false;
        }

        this.draggingSkillScrollbar = true;
        this.skillScrollbarDragOffset = ToucanScrollbars.dragOffset(mouseY, scrollbarThumb);
        return true;
    }

    private boolean tryClickSkillsProfileCycleButton(double mouseX, double mouseY) {
        if (this.page != DialoguePage.SKILLS) {
            return false;
        }
        SkillsProfileCycleButton direction = hoveredSkillsProfileCycleButton(mouseX, mouseY);
        if (direction == null) {
            return false;
        }
        showSkillsProfilePanel(adjacentSkillsProfilePanel(direction));
        return true;
    }

    private SkillsProfilePanel adjacentSkillsProfilePanel(SkillsProfileCycleButton direction) {
        SkillsProfilePanel[] panels = SkillsProfilePanel.values();
        int offset = direction == SkillsProfileCycleButton.LEFT ? -1 : 1;
        return panels[Math.floorMod(this.skillsProfilePanel.ordinal() + offset, panels.length)];
    }

    private void showSkillsProfilePanel(SkillsProfilePanel panel) {
        if (this.skillsProfilePanel == panel) {
            return;
        }
        this.skillsProfilePanel = panel;
        clearSelectedSkillDetails();
        clearSelectedProfileAttributeDetails();
        clearSelectedJobDetails();
        this.draggingSkillScrollbar = false;
        if (panel == SkillsProfilePanel.PROFILE) {
            requestProfileRefresh();
        }
    }

    private boolean trySelectProfileAttributeDetails(double mouseX, double mouseY) {
        if (this.page != DialoguePage.SKILLS || this.skillsProfilePanel != SkillsProfilePanel.PROFILE) {
            return false;
        }

        Optional<VillagerProfileClientCache.DisplayEntry> entry = VillagerProfileClientCache.get(this.villagerEntityId);
        if (entry.isEmpty()) {
            return false;
        }

        VillagerSocialAttribute clickedAttribute =
                VillagerInteractionProfilePage.attributeAt(this.profilePageContext, entry.get(), mouseX, mouseY);
        if (clickedAttribute == null) {
            return false;
        }

        this.selectedProfileAttributeDetails =
                clickedAttribute == this.selectedProfileAttributeDetails ? null : clickedAttribute;
        resetSkillInfoScroll();
        return true;
    }

    private boolean trySelectSkillDetails(double mouseX, double mouseY) {
        if (this.page != DialoguePage.SKILLS || this.skillsProfilePanel != SkillsProfilePanel.SKILLS) {
            return false;
        }

        Optional<VillagerProfileClientCache.DisplayEntry> entry = VillagerProfileClientCache.get(this.villagerEntityId);
        if (entry.isEmpty()) {
            return false;
        }

        VillagerSkill clickedSkill = VillagerInteractionSkillsPage.skillAt(this.skillsPageContext, entry.get(), mouseX, mouseY);
        if (clickedSkill == null) {
            return false;
        }

        this.selectedSkillDetails = clickedSkill == this.selectedSkillDetails ? null : clickedSkill;
        resetSkillInfoScroll();
        return true;
    }

    private boolean trySelectJobDetails(double mouseX, double mouseY) {
        if (this.page != DialoguePage.SKILLS || this.skillsProfilePanel != SkillsProfilePanel.JOBS) {
            return false;
        }
        HiredVillagerRole clickedRole = VillagerInteractionJobStatsPage.roleAt(
                this.jobStatsPageContext, mouseX, mouseY);
        if (clickedRole == null) {
            return false;
        }
        this.selectedJobDetails = clickedRole == this.selectedJobDetails ? null : clickedRole;
        resetSkillInfoScroll();
        return true;
    }

    private boolean dragScrollbar(double mouseY) {
        ToucanScrollbarThumb scrollbarThumb = scrollbarThumb();
        if (scrollbarThumb == null) {
            this.draggingScrollbar = false;
            return false;
        }

        setTargetOptionScroll(ToucanScrollbars.scrollFromThumbDrag(mouseY, this.scrollbarDragOffset, scrollbarThumb, maxOptionScroll()));
        this.state.jumpOptionScrollToTarget();
        return true;
    }

    private boolean dragSkillScrollbar(double mouseY) {
        ToucanScrollbarThumb scrollbarThumb = skillInfoScrollbarThumb();
        if (scrollbarThumb == null) {
            this.draggingSkillScrollbar = false;
            return false;
        }

        setTargetSkillScroll(ToucanScrollbars.scrollFromThumbDrag(mouseY, this.skillScrollbarDragOffset, scrollbarThumb, maxSkillScroll()));
        this.skillScroll = this.targetSkillScroll;
        return true;
    }

    private boolean tryActivateHoveredOption(double mouseX, double mouseY) {
        if (usesRootIconMenu()) {
            return false;
        }
        int hovered = VillagerInteractionOptionList.optionAt(this.optionListContext, mouseX, mouseY);
        if (hovered < 0) {
            return false;
        }

        this.state.setSelectedOption(hovered);
        this.keyboardOptionFocusVisible = false;
        ensureSelectedVisible();
        activateSelected();
        return true;
    }

    private boolean tryClickGiftPage(double mouseX, double mouseY) {
        return VillagerInteractionGiftPage.tryClick(
                this.giftPageContext,
                mouseX,
                mouseY,
                this.width,
                this.height);
    }

    private int firstGiftableInventorySlot() {
        return VillagerInteractionGiftPage.firstGiftableInventorySlot(this.giftPageContext);
    }

    private ItemStack stackForInventorySlot(int inventorySlot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || inventorySlot < 0 || inventorySlot >= 36) {
            return ItemStack.EMPTY;
        }
        return minecraft.player.getInventory().getItem(inventorySlot);
    }

    private int giftInventoryLeft() {
        return VillagerInteractionGiftPage.giftInventoryLeft(this.width);
    }

    private int giftInventoryTop() {
        return VillagerInteractionGiftPage.giftInventoryTop(this.height);
    }

    private boolean isPointInsideOptionScrollArea(double mouseX, double mouseY) {
        int left = optionsLeft() - uiUnit(18);
        int right = usesInteractionOptionStack()
                ? optionsLeft() + optionWidth() + INTERACTION_OPTION_SELECTION_ARROW_GAP + INTERACTION_OPTION_SELECTION_ARROW_WIDTH
                : optionsLeft() + optionWidth() + uiUnit(4);
        int top = optionsTop();
        int bottom = top + optionViewportHeight();
        int verticalPadding = uiUnit(4);
        return mouseX >= left && mouseX <= right && mouseY >= top - verticalPadding && mouseY <= bottom + verticalPadding;
    }

    private boolean isPointInsideSkillsInfoScrollArea(double mouseX, double mouseY) {
        if (this.page != DialoguePage.SKILLS) {
            return false;
        }
        int left = skillsDialogueContainerLeft() + INTERACTION_DIALOGUE_SCROLL_LEFT;
        int right = skillsDialogueContainerLeft() + INTERACTION_DIALOGUE_SCROLL_RIGHT;
        int top = skillsDialogueContainerTop() + INTERACTION_DIALOGUE_SCROLL_TOP;
        int bottom = skillsDialogueContainerTop() + INTERACTION_DIALOGUE_SCROLL_BOTTOM;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private static boolean isLeftMouseButton(int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    private boolean isTopBackButtonVisible() {
        return !this.forcedDialogue && canNavigateBack();
    }

    private boolean canNavigateBack() {
        return !this.forcedDialogue && this.page != DialoguePage.ROOT;
    }

    private boolean isPointInsideTopBackButton(double mouseX, double mouseY) {
        if (!isTopBackButtonVisible()) {
            return false;
        }

        TopBackButtonBounds bounds = topBackButtonBounds();
        int verticalPadding = uiUnitAtLeast(2, 1);
        return VillagerClientUiUtil.containsInclusive(mouseX, mouseY, bounds.left(), bounds.top() - verticalPadding, bounds.right(), bounds.bottom() + verticalPadding);
    }

    private TopBackButtonBounds topBackButtonBounds() {
        float textScale = uiScaleFactor();
        int textWidth = Math.round(this.font.width(backLabel()) * textScale);
        int textHeight = Math.round(this.font.lineHeight * textScale);
        int left = optionTextLeft();
        int contentTop = optionViewportTop();
        int top = contentTop - textHeight - topBackButtonGap();
        int bottom = top + textHeight;
        return new TopBackButtonBounds(left, left + textWidth, top, bottom);
    }

    private int skillsPanelTop() {
        int containerHeight = skillsContainerHeight();
        return Math.max(4, skillsDialogueContainerTop() - containerHeight - SKILLS_DIALOGUE_CONTAINER_GAP);
    }

    private int skillsPanelLeft() {
        int panelWidth = skillsPanelWidth();
        return VillagerInteractionLayoutMetrics.skillsPanelLeft(this.width, panelWidth);
    }

    private int skillsPanelWidth() {
        return VillagerInteractionLayoutMetrics.skillsContainerWidth();
    }

    private int skillsContainerHeight() {
        return VillagerInteractionLayoutMetrics.skillsContainerHeight();
    }

    private int skillsPanelHeight() {
        return VillagerInteractionLayoutMetrics.skillsPanelHeight(this.font);
    }

    private int skillsContainerPaddingX() {
        return VillagerInteractionLayoutMetrics.skillsContainerPaddingX();
    }

    private int skillsContainerPaddingY() {
        return VillagerInteractionLayoutMetrics.skillsContainerPaddingY();
    }

    private int profileSkillRowHeight() {
        return VillagerInteractionLayoutMetrics.profileSkillRowHeight();
    }

    private int profileSkillRowGap() {
        return VillagerInteractionLayoutMetrics.profileSkillRowGap();
    }

    private int profileSkillBarHeight() {
        return VillagerInteractionLayoutMetrics.profileSkillBarHeight();
    }

    private int profileSkillColumnGap() {
        return VillagerInteractionLayoutMetrics.profileSkillColumnGap();
    }

    private float maxSkillScroll() {
        if (this.page == DialoguePage.SKILLS) {
            return maxInteractionDialogueLineScroll(wrappedInteractionDialogueLines(skillsDialogueInfoText()));
        }
        return ToucanScrollState.maxScroll(Mth.floor(optionTextYOffset()) + skillsInfoContentHeight(), skillInfoViewportHeight());
    }

    private void setTargetSkillScroll(float scroll) {
        this.targetSkillScroll = Mth.clamp(scroll, 0.0F, maxSkillScroll());
    }

    private int skillsInfoContentHeight() {
        return VillagerInteractionSkillsPage.skillsInfoContentHeight(this.skillsPageContext);
    }

    private int skillInfoViewportTop() {
        if (this.page == DialoguePage.SKILLS) {
            return skillsDialogueContainerTop() + INTERACTION_DIALOGUE_TOP;
        }
        return conversationInfoTop();
    }

    private int skillInfoViewportBottom() {
        if (this.page == DialoguePage.SKILLS) {
            return skillsDialogueContainerTop() + INTERACTION_DIALOGUE_BOTTOM;
        }
        return conversationInfoTop() + rootOptionViewportHeight();
    }

    private int skillInfoViewportHeight() {
        return Math.max(1, skillInfoViewportBottom() - skillInfoViewportTop());
    }

    private int optionsTop() {
        if (usesInteractionOptionStack()) {
            return interactionOptionStackTop();
        }
        return optionsTopForViewport(optionViewportHeight());
    }

    private int optionViewportTop() {
        return optionsTopForViewport(fullOptionViewportHeight());
    }


    private int optionTextLeft() {
        return layoutOptionsLeft() + optionTextInset();
    }

    private int optionsTopForViewport(int viewportHeight) {
        return VillagerInteractionLayout.optionsTop(this.height, viewportHeight);
    }

    private int conversationInfoTop() {
        return focusCenterY() - rootOptionViewportHeight() / 2;
    }

    private int optionsLeft() {
        if (usesInteractionOptionStack()) {
            return interactionOptionStackLeft();
        }
        return layoutOptionsLeft();
    }

    private int contentLeft() {
        if (this.page == DialoguePage.PROFILE || this.page == DialoguePage.SKILLS) {
            return layoutPageLeft();
        }
        return optionsLeft();
    }

    private int layoutOptionsLeft() {
        return VillagerInteractionLayout.optionsLeft(this.width, optionWidth());
    }

    private int layoutPageLeft() {
        return VillagerInteractionLayout.pageLeft(this.width, optionWidth());
    }

    private String reputationText() {
        return translate("info.reputation", this.reputation);
    }


    private String moodText() {
        return translate("info.mood", moodName(this.primaryMood));
    }

    private String walletAmountText() {
        if (this.walletEmeralds > this.maxWalletEmeralds) {
            return this.walletEmeralds + " " + this.walletCurrencyPluralName;
        }
        return this.walletEmeralds + " / " + this.maxWalletEmeralds;
    }

    private String walletTooltipTitle() {
        return translate("info.wallet.tooltip.title", this.walletCurrencyLabel);
    }

    private String walletTooltipBody() {
        if (this.walletEmeralds > this.maxWalletEmeralds) {
            return translate("info.wallet.tooltip.body.over_cap", this.maxWalletEmeralds, this.walletCurrencyPluralName);
        }
        return translate("info.wallet.tooltip.body", this.walletCurrencyPluralName);
    }

    private String localizedReputationLevelName() {
        if (this.reputationLevel == null) {
            return translate("reputation.unknown");
        }
        String key = "villagerretaliation.reputation.level." + this.reputationLevel.name().toLowerCase(Locale.ROOT);
        return I18n.exists(key) ? I18n.get(key) : this.reputationLevel.name();
    }

    private String localizedDialogueDispositionName() {
        DialogueDisposition disposition = this.mood == null ? DialogueDisposition.NEUTRAL : this.mood;
        String key = GUI_KEY_PREFIX + "mood." + disposition.name().toLowerCase(Locale.ROOT);
        return I18n.exists(key) ? I18n.get(key) : disposition.displayName();
    }

    private boolean canRequestVillagerInventory() {
        return this.inventoryAvailable;
    }

    private boolean canRequestPartyVillagerInventory() {
        return this.recruitedPartyVillager
                && this.partyVillagerPartyMember
                && this.partyVillagerAuthorized;
    }

    private PartyRosterSyncPayload.VillagerEntry partyVillagerSettings() {
        return PartyRosterClient.roster().villagers().stream()
                .filter(villager -> villager.entityId() == this.villagerEntityId)
                .findFirst()
                .orElse(null);
    }


    private int focusCenterY() {
        return VillagerInteractionLayoutMetrics.focusCenterY(this.height);
    }

    private int optionWidth() {
        if (usesInteractionOptionStack()) {
            return interactionOptionStackWidth();
        }
        return VillagerInteractionLayoutMetrics.optionWidth();
    }

    private int optionHeight() {
        if (usesInteractionOptionStack()) {
            return INTERACTION_OPTION_HEIGHT;
        }
        return VillagerInteractionLayoutMetrics.optionHeight();
    }

    private int optionTextInset() {
        if (usesInteractionOptionStack()) {
            return INTERACTION_OPTION_TEXT_INSET;
        }
        return VillagerInteractionLayoutMetrics.optionTextInset();
    }

    private float optionTextYOffset() {
        return VillagerInteractionLayoutMetrics.optionTextYOffset(optionHeight());
    }

    private int optionScrollbarOffset() {
        return VillagerInteractionLayoutMetrics.optionScrollbarOffset();
    }

    private int optionScrollbarWidth() {
        return VillagerInteractionLayoutMetrics.optionScrollbarWidth();
    }

    private int optionScrollbarHitWidth() {
        return VillagerInteractionLayoutMetrics.optionScrollbarHitWidth();
    }

    private int topBackButtonGap() {
        return VillagerInteractionLayoutMetrics.topBackButtonGap();
    }

    private float uiScaleFactor() {
        return VillagerInteractionLayout.scaleFactor();
    }

    private int uiUnit(int guiScaleThreeValue) {
        return VillagerInteractionLayout.unit(guiScaleThreeValue);
    }

    private int uiUnitAtLeast(int guiScaleThreeValue, int minimum) {
        return VillagerInteractionLayout.unitAtLeast(guiScaleThreeValue, minimum);
    }

    private int optionViewportHeight() {
        if (usesInteractionOptionStack()) {
            return interactionOptionViewportHeight();
        }
        return VillagerInteractionLayoutMetrics.optionViewportHeight(this.options.size());
    }

    private int interactionOptionViewportHeight() {
        int maximumHeight = INTERACTION_OPTION_VISIBLE_ROWS * INTERACTION_OPTION_STRIDE;
        if (this.options.isEmpty()) {
            return INTERACTION_OPTION_HEIGHT;
        }
        return Math.min(maximumHeight, Mth.ceil(optionContentHeight()));
    }

    private int fullOptionViewportHeight() {
        return VillagerInteractionLayoutMetrics.fullOptionViewportHeight();
    }

    private int rootOptionViewportHeight() {
        return VillagerInteractionLayoutMetrics.rootOptionViewportHeight();
    }

    private float maxOptionScroll() {
        return VillagerInteractionLayoutMetrics.maxOptionScroll(optionContentHeight(), optionViewportHeight());
    }

    private float optionContentHeight() {
        return VillagerInteractionOptionList.optionContentHeight(this.optionListContext);
    }

    private int optionStride() {
        if (usesInteractionOptionStack()) {
            return INTERACTION_OPTION_STRIDE;
        }
        return VillagerInteractionLayoutMetrics.optionStride();
    }

    private boolean usesInteractionOptionStack() {
        return shouldRenderInteractionContainer();
    }

    private boolean usesRootIconMenu() {
        return usesRootIconMenu(this.page);
    }

    private boolean usesRootIconMenu(DialoguePage page) {
        return !this.forcedDialogue && !this.clipboardMenu && page == DialoguePage.ROOT;
    }

    private int interactionOptionStackWidth() {
        if (this.interactionOptionWidthVersion == this.optionLayoutVersion) {
            return this.cachedInteractionOptionWidth;
        }
        int desiredWidth = INTERACTION_OPTION_WIDTH;
        for (int index = 0; index < this.options.size(); index++) {
            for (String line : VillagerInteractionOptionList.pixelOptionLabelLines(
                    interactionOptionLabel(index),
                    INTERACTION_OPTION_MAX_LINE_CHARACTERS)) {
                int iconWidth = this.options.get(index).locked()
                        ? INTERACTION_LOCKED_ICON_WIDTH + INTERACTION_LOCKED_ICON_TEXT_GAP
                        : 0;
                int checkboxWidth = this.options.get(index).checkbox()
                        ? INTERACTION_OPTION_CHECKBOX_SIZE + INTERACTION_OPTION_CHECKBOX_TEXT_GAP
                        : 0;
                desiredWidth = Math.max(
                        desiredWidth,
                        this.font.width(line)
                                + INTERACTION_OPTION_TEXT_INSET
                                + iconWidth
                                + checkboxWidth
                                + INTERACTION_OPTION_TEXT_RIGHT_PADDING);
            }
        }
        int maxAvailableWidth = Math.max(
                INTERACTION_OPTION_WIDTH,
                this.width - INTERACTION_OPTION_SCREEN_MARGIN - interactionOptionStackRightClearance());
        this.cachedInteractionOptionWidth = Math.min(desiredWidth, maxAvailableWidth);
        this.interactionOptionWidthVersion = this.optionLayoutVersion;
        return this.cachedInteractionOptionWidth;
    }

    private String interactionOptionLabel(int index) {
        if (index < 0 || index >= this.options.size()) {
            return "";
        }
        return this.options.get(index).label();
    }

    private int interactionOptionStackLeft() {
        return Math.max(
                INTERACTION_OPTION_SCREEN_MARGIN,
                this.width - interactionOptionStackWidth() - interactionOptionStackRightClearance());
    }

    private int interactionOptionStackTop() {
        int viewportHeight = interactionOptionViewportHeight();
        int centeredTop = (this.height - viewportHeight) / 2;
        int maxTop = Math.max(
                INTERACTION_OPTION_SCREEN_MARGIN,
                this.height - viewportHeight - INTERACTION_OPTION_SCREEN_MARGIN);
        return Mth.clamp(centeredTop, INTERACTION_OPTION_SCREEN_MARGIN, maxTop);
    }

    private int interactionOptionStackRightClearance() {
        return INTERACTION_OPTION_SCREEN_MARGIN
                + INTERACTION_OPTION_SELECTION_ARROW_GAP
                + INTERACTION_OPTION_SELECTION_ARROW_WIDTH;
    }

    private void ensureSelectedVisible() {
        if (this.state.selectedOption() < 0 || this.state.selectedOption() >= this.options.size()) {
            return;
        }

        float optionTop = VillagerInteractionOptionList.optionOffset(this.optionListContext, this.state.selectedOption());
        float optionBottom = optionTop + VillagerInteractionOptionList.optionHeight(this.optionListContext, this.state.selectedOption());
        float viewportTop = this.state.targetOptionScroll();
        float viewportBottom = viewportTop + optionViewportHeight();
        int padding = usesInteractionOptionStack() ? 0 : 6;
        if (optionTop < viewportTop + padding) {
            setTargetOptionScroll(optionTop - padding);
        } else if (optionBottom > viewportBottom - padding) {
            setTargetOptionScroll(optionBottom - optionViewportHeight() + padding);
        } else {
            setTargetOptionScroll(this.state.targetOptionScroll());
        }
    }

    private void setTargetOptionScroll(float scroll) {
        this.state.setTargetOptionScroll(scroll, maxOptionScroll());
    }

    private void resetPixelOptionEdgeScaleBlends() {
        this.pixelOptionEdgeScaleInitialized = false;
        this.pixelOptionTopEdgeScaleBlend = 0.0F;
        this.pixelOptionBottomEdgeScaleBlend = 0.0F;
    }

    private float pixelOptionEdgeScaleBlend(int index, int edgePosition) {
        if (!usesInteractionOptionStack() || index < 0 || index >= this.options.size()) {
            return 0.0F;
        }
        return switch (edgePosition) {
            case -1 -> this.pixelOptionTopEdgeScaleBlend;
            case 1 -> this.pixelOptionBottomEdgeScaleBlend;
            default -> 0.0F;
        };
    }

    private void updatePixelOptionEdgeScaling(boolean topEdgeVisible, boolean bottomEdgeVisible) {
        if (!usesInteractionOptionStack()) {
            return;
        }
        updatePixelOptionEdgeSlotBlends(topEdgeVisible, bottomEdgeVisible);
    }

    private void updatePixelOptionEdgeSlotBlends(boolean topEdgeVisible, boolean bottomEdgeVisible) {
        float topTarget = topEdgeVisible ? 1.0F : 0.0F;
        float bottomTarget = bottomEdgeVisible ? 1.0F : 0.0F;
        if (!this.pixelOptionEdgeScaleInitialized) {
            this.pixelOptionTopEdgeScaleBlend = topTarget;
            this.pixelOptionBottomEdgeScaleBlend = bottomTarget;
            this.pixelOptionEdgeScaleInitialized = true;
            return;
        }
        this.pixelOptionTopEdgeScaleBlend = lerpPixelOptionEdgeBlend(this.pixelOptionTopEdgeScaleBlend, topTarget);
        this.pixelOptionBottomEdgeScaleBlend = lerpPixelOptionEdgeBlend(this.pixelOptionBottomEdgeScaleBlend, bottomTarget);
    }

    private static float lerpPixelOptionEdgeBlend(float current, float target) {
        float blend = Mth.lerp(OPTION_SCROLL_LERP, current, target);
        return Math.abs(blend - target) < 0.01F ? target : blend;
    }

    private float edgeFadeAlpha(float optionY, int viewportTop, int viewportBottom) {
        return VillagerInteractionUiUtil.edgeFadeAlpha(
                this.state.optionScroll(),
                maxOptionScroll(),
                optionY,
                optionY + optionHeight(),
                viewportTop,
                viewportBottom,
                26.0F
        );
    }

    private float skillInfoEdgeFadeAlpha(float lineY, int viewportTop, int viewportBottom) {
        return VillagerInteractionUiUtil.edgeFadeAlpha(
                this.skillScroll,
                maxSkillScroll(),
                lineY,
                lineY + this.font.lineHeight,
                viewportTop,
                viewportBottom,
                16.0F
        );
    }

    private void renderScrollbar(GuiGraphics graphics) {
        ToucanScrollbarThumb scrollbarThumb = scrollbarThumb();
        renderScrollbar(graphics, scrollbarThumb, this.state.optionScroll(), maxOptionScroll());
    }

    private void renderScrollbar(
            GuiGraphics graphics,
            ToucanScrollbarThumb scrollbarThumb,
            float currentScroll,
            float maxScroll) {
        ToucanScrollbars.renderFadedThumb(graphics, scrollbarThumb, currentScroll, maxScroll, 0xBFFFFFFF, 1.0F);
    }

    private ToucanScrollbarThumb scrollbarThumb() {
        float maxScroll = maxOptionScroll();
        int viewportTop = optionsTop();
        int viewportHeight = optionViewportHeight();
        return VillagerInteractionUiUtil.buildScrollbarThumb(
                viewportTop,
                viewportHeight,
                optionsScrollbarLeft(),
                optionScrollbarWidth(),
                optionScrollbarHitWidth(),
                optionHeight(),
                this.state.optionScroll(),
                maxScroll,
                optionContentHeight()
        );
    }

    private ToucanScrollbarThumb skillInfoScrollbarThumb() {
        if (this.page == DialoguePage.SKILLS) {
            return null;
        }
        float maxScroll = maxSkillScroll();
        int viewportTop = skillInfoViewportTop();
        int viewportHeight = skillInfoViewportHeight();
        return VillagerInteractionUiUtil.buildScrollbarThumb(
                viewportTop,
                viewportHeight,
                optionsScrollbarLeft(),
                optionScrollbarWidth(),
                optionScrollbarHitWidth(),
                optionHeight(),
                this.skillScroll,
                maxScroll,
                skillsInfoContentHeight()
        );
    }

    private int optionsScrollbarLeft() {
        if (usesInteractionOptionStack()) {
            return optionsLeft() + optionWidth() + 1;
        }
        return VillagerInteractionLayout.scrollbarLeft(
                this.width,
                optionsLeft(),
                optionWidth(),
                optionScrollbarOffset(),
                optionScrollbarWidth());
    }

    private int scrollbarRight() {
        return optionsScrollbarLeft() + optionScrollbarWidth();
    }

    private float hoverIntensity(double mouseX, double mouseY, int left, float top) {
        double normalizedX = Math.abs(((mouseX - left) / optionWidth()) * 2.0D - 1.0D);
        double normalizedY = Math.abs(((mouseY - top) / optionHeight()) * 2.0D - 1.0D);
        double distance = Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);
        return (float) Mth.clamp(1.0D - distance / Math.sqrt(2.0D), 0.0D, 1.0D);
    }

    private float hoverShift(double mouse, float start, float size, float strength) {
        return (float) ((((mouse - start) / size) * 2.0D) - 1.0D) * strength;
    }

    private void focusVillagerOnPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(this.villagerEntityId);
        if (entity instanceof Villager villager) {
            villager.getLookControl().setLookAt(minecraft.player, 30.0F, 30.0F);
        }
    }

    private void syncCameraFocusState() {
        boolean forceCamera = cameraShouldForceTowardsVillager();
        if (!ClientVillagerConversationState.active()) {
            ClientVillagerConversationState.start(this.villagerEntityId, forceCamera);
            return;
        }
        if (ClientVillagerConversationState.focusedVillagerEntityId() != this.villagerEntityId) {
            ClientVillagerConversationState.retarget(this.villagerEntityId, forceCamera);
            return;
        }
        ClientVillagerConversationState.setForceCameraTowardsVillager(forceCamera);
    }

    private boolean cameraShouldForceTowardsVillager() {
        return this.forcedDialogue || this.forceCameraTowardsVillager;
    }

    private static String maleAncestorLabel(int generation) {
        return generationLabel("family.ancestor.male.grand", generation);
    }

    private static String femaleAncestorLabel(int generation) {
        return generationLabel("family.ancestor.female.grand", generation);
    }

    private static String nonBinaryAncestorLabel(int generation) {
        return generationLabel("family.ancestor.non_binary.grand", generation);
    }

    private static String maleDescendantLabel(int generation) {
        return generationLabel("family.descendant.male.grand", generation);
    }

    private static String femaleDescendantLabel(int generation) {
        return generationLabel("family.descendant.female.grand", generation);
    }

    private static String nonBinaryDescendantLabel(int generation) {
        return generationLabel("family.descendant.non_binary.grand", generation);
    }

    private static String generationLabel(String grandKey, int generation) {
        String label = translate(grandKey);
        for (int i = 0; i < Math.max(0, generation - 2); i++) {
            label = translate("family.great_prefix", label);
        }
        return label;
    }

    private static String relationshipLabel(VillagerRelationshipSnapshot.RomanticBondView bond) {
        String stage = translate("relationship.stage." + bond.stage().serializedName());
        String status = translate(bond.partnerAlive() ? "relationships.status.alive" : "relationships.status.deceased");
        if (bond.stage().active()) {
            return translate(
                    "relationships.active_format",
                    stage,
                    bond.partnerName(),
                    status,
                    bond.affection(),
                    bond.compatibility()
            );
        }
        if (bond.endReason().isBlank()) {
            return translate("relationships.past_format", stage, bond.partnerName(), status);
        }
        return translate("relationships.past_format_reason", stage, bond.partnerName(), status, bond.endReason());
    }

    private static String familyMemberLabel(VillagerFamilyTreeSnapshot.FamilyMember member) {
        if (member.alive()) {
            return member.name();
        }
        return translate("family.member.deceased_format", member.name(), translate("relationships.status.deceased"));
    }

    private static String localizedLabel(String keyOrLabel) {
        return hasTranslation(keyOrLabel) ? translate(keyOrLabel) : keyOrLabel;
    }

    private static String localizedAttribute(VillagerSocialAttribute attribute) {
        return I18n.exists(attribute.translationKey()) ? I18n.get(attribute.translationKey()) : attribute.serializedName();
    }

    private static String localizedAttributeDescription(VillagerSocialAttribute attribute) {
        String key = attribute.translationKey() + ".description";
        return I18n.exists(key) ? I18n.get(key) : attribute.serializedName();
    }

    private static String localizedRank(VillagerSocialAttributeRank rank) {
        return I18n.exists(rank.translationKey()) ? I18n.get(rank.translationKey()) : rank.serializedName();
    }

    private static String localizedSkill(VillagerSkill skill) {
        return I18n.exists(skill.translationKey()) ? I18n.get(skill.translationKey()) : skill.serializedName();
    }

    private static String localizedSkillDescription(VillagerSkill skill) {
        return I18n.exists(skill.descriptionTranslationKey()) ? I18n.get(skill.descriptionTranslationKey()) : skill.serializedName();
    }

    private static String localizedExpandedSkillDescription(VillagerSkill skill) {
        String key = skill.descriptionTranslationKey() + ".details";
        if (I18n.exists(key)) {
            return I18n.get(key);
        }
        return localizedSkillDescription(skill);
    }

    private static String localizedSkillRank(VillagerSkillRank rank) {
        return I18n.exists(rank.translationKey()) ? I18n.get(rank.translationKey()) : rank.serializedName();
    }

    private static String moodName(VillagerMood mood) {
        if (mood == null) {
            return translate("mood.neutral");
        }
        return translate("mood." + mood.serializedName());
    }

    private static String localizedGenderName(String genderName) {
        if (genderName == null || genderName.isBlank()) {
            return translate("gender.unknown");
        }
        String key = "gender." + genderName.trim().toLowerCase(Locale.ROOT);
        return hasTranslation(key) ? translate(key) : genderName;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String backLabel() {
        return I18n.get(BACK_LABEL_KEY);
    }

    private static boolean hasTranslation(String key) {
        return I18n.exists(GUI_KEY_PREFIX + key);
    }

    private static String translate(String key, Object... args) {
        return I18n.get(GUI_KEY_PREFIX + key, args);
    }

    private static String animalCullCapLabel(int cap) {
        return cap > HiredAnimalCullSettings.DISABLED_CAP ? Integer.toString(cap) : translate("recruit.animal_cull_disabled");
    }

    private static int interactionPageDepth(DialoguePage page) {
        return switch (page) {
            case ROOT -> 0;
            case TALK, ADVENTURES, DUEL, PROFILE, SKILLS, ALLEGIANCE, GIFT, FAMILY, RELATIONSHIPS, RECRUIT -> 1;
            case DUEL_LOADOUT, DUEL_WAGER, DUEL_CONFIRM, ANCESTRY, DESCENDANTS, STORAGE, PAYMENT, HIRE, CONTRACT, ROLE, WORK -> 2;
            case HIRE_DURATION,
                    END_CONTRACT_CONFIRMATION,
                    CONTRACT_EXTENSION,
                    ROLE_CHANGE,
                    HUNTING_OPTIONS,
                    FARMING_OPTIONS,
                    LOGGING_FILTERS,
                    ANIMAL_HANDLING_OPTIONS,
                    BUILDER_STRUCTURES,
                    BREWING_POTION -> 3;
            case BUILDER_STRUCTURE_CATEGORY,
                    ANIMAL_BREEDING_TARGETS,
                    ANIMAL_CULL_CAPS,
                    BREWING_LEVEL,
                    BREWING_DURATION,
                    BREWING_TYPE -> 4;
            case BUILDER_CONFIRM, BREWING_AMOUNT -> 5;
        };
    }

    private static boolean isDuelSetupPage(DialoguePage page) {
        return page == DialoguePage.DUEL
                || page == DialoguePage.DUEL_LOADOUT
                || page == DialoguePage.DUEL_WAGER
                || page == DialoguePage.DUEL_CONFIRM;
    }

    private enum DialoguePage {
        ROOT,
        TALK,
        ADVENTURES,
        DUEL,
        DUEL_LOADOUT,
        DUEL_WAGER,
        DUEL_CONFIRM,
        PROFILE,
        SKILLS,
        ALLEGIANCE,
        GIFT,
        FAMILY,
        ANCESTRY,
        DESCENDANTS,
        RELATIONSHIPS,
        RECRUIT,
        STORAGE,
        PAYMENT,
        HIRE,
        HIRE_DURATION,
        CONTRACT,
        END_CONTRACT_CONFIRMATION,
        CONTRACT_EXTENSION,
        ROLE,
        ROLE_CHANGE,
        WORK,
        HUNTING_OPTIONS,
        FARMING_OPTIONS,
        LOGGING_FILTERS,
        ANIMAL_HANDLING_OPTIONS,
        ANIMAL_BREEDING_TARGETS,
        ANIMAL_CULL_CAPS,
        BUILDER_STRUCTURES,
        BUILDER_STRUCTURE_CATEGORY,
        BUILDER_CONFIRM,
        BREWING_POTION,
        BREWING_LEVEL,
        BREWING_DURATION,
        BREWING_TYPE,
        BREWING_AMOUNT
    }

    private enum SkillsProfilePanel {
        SKILLS,
        PROFILE,
        JOBS
    }

    private enum SkillsProfileCycleButton {
        LEFT,
        RIGHT
    }

    private record DialogueOption(String label, Runnable action, boolean locked, boolean checkbox, boolean checked) {
        static DialogueOption enabled(String label, Runnable action) {
            return enabled(label, action, false);
        }

        static DialogueOption enabled(String label, Runnable action, boolean locked) {
            return new DialogueOption(label, action, locked, false, false);
        }

        static DialogueOption checkbox(String label, boolean checked, Runnable action) {
            return checkbox(label, checked, action, false);
        }

        static DialogueOption checkbox(String label, boolean checked, Runnable action, boolean locked) {
            return new DialogueOption(label, action, locked, true, checked);
        }
    }

    private record InteractionMenuButton(ResourceLocation icon, String title, String description, Runnable action, boolean active) {
    }

    private record TopBackButtonBounds(int left, int right, int top, int bottom) {
    }

    private record InteractionKeyboardTooltipPositioner(
            int anchorLeft,
            int anchorRight,
            int anchorTop,
            int anchorBottom,
            int minX,
            int minY,
            int maxX,
            int maxY) implements ClientTooltipPositioner {
        private static final int EDGE_MARGIN = 4;

        @Override
        public Vector2ic positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight) {
            int left = this.anchorRight + INTERACTION_KEYBOARD_TOOLTIP_X_GAP;
            int top = this.anchorTop - tooltipHeight - INTERACTION_KEYBOARD_TOOLTIP_Y_GAP;
            int rightLimit = this.maxX - tooltipWidth - EDGE_MARGIN;
            int bottomLimit = this.maxY - tooltipHeight - EDGE_MARGIN;
            if (top < this.minY + EDGE_MARGIN) {
                top = this.anchorBottom + INTERACTION_KEYBOARD_TOOLTIP_Y_GAP;
            }
            if (left > rightLimit) {
                left = this.anchorLeft;
            }
            left = Mth.clamp(left, this.minX + EDGE_MARGIN, Math.max(this.minX + EDGE_MARGIN, rightLimit));
            top = Mth.clamp(top, this.minY + EDGE_MARGIN, Math.max(this.minY + EDGE_MARGIN, bottomLimit));
            return new Vector2i(left, top);
        }
    }

    private record InteractionStatLayout(
            int textTop,
            int iconTop,
            String moodText,
            String currencyText,
            String reputationText,
            int moodTextLeft,
            int moodTextRight,
            int currencyTextLeft,
            int currencyTextRight,
            int currencyIconLeft,
            int reputationTextLeft,
            int reputationTextRight,
            int reputationIconLeft,
            int hitTop,
            int hitBottom) {
        boolean containsMood(double mouseX, double mouseY) {
            return contains(mouseX, mouseY, this.moodTextLeft, this.moodTextRight);
        }

        boolean containsCurrency(double mouseX, double mouseY) {
            return contains(mouseX, mouseY, this.currencyIconLeft, this.currencyTextRight);
        }

        boolean containsReputation(double mouseX, double mouseY) {
            return contains(mouseX, mouseY, this.reputationIconLeft, this.reputationTextRight);
        }

        private boolean contains(double mouseX, double mouseY, int left, int right) {
            return mouseX >= left && mouseX <= right && mouseY >= this.hitTop && mouseY <= this.hitBottom;
        }
    }

    private final class GiftPageContext implements VillagerInteractionGiftPage.Context {
        @Override
        public Font font() {
            return VillagerInteractionScreen.this.font;
        }

        @Override
        public int selectedInventorySlot() {
            return VillagerInteractionScreen.this.selectedInventorySlot;
        }

        @Override
        public void setSelectedInventorySlot(int slot) {
            VillagerInteractionScreen.this.selectedInventorySlot = slot;
            VillagerInteractionScreen.this.selectedGiftAmount = 0;
            VillagerInteractionScreen.this.giftLimitFeedback.trigger(0);
        }

        @Override
        public int selectedGiftAmount() {
            return VillagerInteractionScreen.this.selectedGiftAmount;
        }

        @Override
        public void setSelectedGiftAmount(int amount) {
            ItemStack stack = stackForInventorySlot(VillagerInteractionScreen.this.selectedInventorySlot);
            if (stack.isEmpty() || amount == 0) {
                VillagerInteractionScreen.this.selectedGiftAmount = 0;
                return;
            }
            VillagerInteractionScreen.this.selectedGiftAmount = Mth.clamp(amount, 1, stack.getCount());
        }

        @Override
        public int giftLimitFeedbackOffset() {
            return VillagerInteractionScreen.this.giftLimitFeedback.horizontalOffset();
        }

        @Override
        public void triggerGiftLimitFeedback(int durationTicks) {
            VillagerInteractionScreen.this.giftLimitFeedback.trigger(durationTicks);
        }

        @Override
        public Button giftButton() {
            return VillagerInteractionScreen.this.giftButton;
        }

        @Override
        public ItemStack stackForInventorySlot(int inventorySlot) {
            return VillagerInteractionScreen.this.stackForInventorySlot(inventorySlot);
        }

        @Override
        public String professionName() {
            return VillagerInteractionScreen.this.professionName;
        }

        @Override
        public List<String> knownLikedGiftNames() {
            return VillagerInteractionScreen.this.knownLikedGiftNames;
        }

        @Override
        public List<String> knownDislikedGiftNames() {
            return VillagerInteractionScreen.this.knownDislikedGiftNames;
        }

        @Override
        public Optional<GiftTooltipReaction> giftTooltipReaction(ItemStack stack) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            return VillagerInteractionScreen.this.giftTooltipReactions.stream()
                    .filter(reaction -> reaction.itemId().equals(itemId)).findFirst();
        }
    }

    private final class OptionListContext implements VillagerInteractionOptionList.Context {
        @Override
        public long optionLayoutVersion() {
            return VillagerInteractionScreen.this.optionLayoutVersion;
        }

        @Override
        public VillagerInteractionOptionList.LayoutCache optionLayout() {
            return VillagerInteractionScreen.this.optionLayout;
        }

        @Override
        public Font font() {
            return VillagerInteractionScreen.this.font;
        }

        @Override
        public int optionsLeft() {
            return VillagerInteractionScreen.this.optionsLeft();
        }

        @Override
        public int optionsTop() {
            return VillagerInteractionScreen.this.optionsTop();
        }

        @Override
        public int optionWidth() {
            return VillagerInteractionScreen.this.optionWidth();
        }

        @Override
        public int optionHeight() {
            return VillagerInteractionScreen.this.optionHeight();
        }

        @Override
        public int optionTextInset() {
            return VillagerInteractionScreen.this.optionTextInset();
        }

        @Override
        public int optionCount() {
            return VillagerInteractionScreen.this.options.size();
        }

        @Override
        public String optionLabel(int index) {
            return VillagerInteractionScreen.this.options.get(index).label();
        }

        @Override
        public int selectedOption() {
            return VillagerInteractionScreen.this.state.selectedOption();
        }

        @Override
        public float optionScroll() {
            return VillagerInteractionScreen.this.state.optionScroll();
        }

        @Override
        public int optionViewportHeight() {
            return VillagerInteractionScreen.this.optionViewportHeight();
        }

        @Override
        public int optionStride() {
            return VillagerInteractionScreen.this.optionStride();
        }

        @Override
        public float edgeFadeAlpha(float optionY, int viewportTop, int viewportBottom) {
            return VillagerInteractionScreen.this.edgeFadeAlpha(optionY, viewportTop, viewportBottom);
        }

        @Override
        public float hoverIntensity(double mouseX, double mouseY, int left, float top) {
            return VillagerInteractionScreen.this.hoverIntensity(mouseX, mouseY, left, top);
        }

        @Override
        public float hoverShift(double mouse, float start, float size, float strength) {
            return VillagerInteractionScreen.this.hoverShift(mouse, start, size, strength);
        }

        @Override
        public float optionHoverScale() {
            return OPTION_HOVER_SCALE;
        }

        @Override
        public float optionSelectedScale() {
            return OPTION_SELECTED_SCALE;
        }

        @Override
        public float textScale() {
            return VillagerInteractionScreen.this.uiScaleFactor();
        }

        @Override
        public int uiUnit(int value) {
            return VillagerInteractionScreen.this.uiUnit(value);
        }

        @Override
        public int uiUnitAtLeast(int value, int minimum) {
            return VillagerInteractionScreen.this.uiUnitAtLeast(value, minimum);
        }

        @Override
        public int optionsScrollbarLeft() {
            return VillagerInteractionScreen.this.optionsScrollbarLeft();
        }

        @Override
        public float textAlpha() {
            return 1.0F;
        }

        @Override
        public int guiScissorOffsetY() {
            return VillagerInteractionScreen.this.renderSlideOffsetY;
        }

        @Override
        public int guiScissorOffsetX() {
            return VillagerInteractionScreen.this.renderContentOffsetX;
        }

        @Override
        public boolean usePixelOptionButtons() {
            return VillagerInteractionScreen.this.usesInteractionOptionStack();
        }

        @Override
        public ResourceLocation pixelOptionTexture(boolean selected, boolean hovered) {
            return VillagerRetaliationClientAssets.INTERACTION_CONTAINER_OPTION_TEXTURE;
        }

        @Override
        public boolean pixelOptionKeyboardFocusVisible() {
            return VillagerInteractionScreen.this.keyboardOptionFocusVisible;
        }

        @Override
        public String pixelOptionLabel(int index) {
            return VillagerInteractionScreen.this.interactionOptionLabel(index);
        }

        @Override
        public int pixelOptionTextTop() {
            return INTERACTION_OPTION_TEXT_TOP;
        }

        @Override
        public int pixelOptionTextRightPadding() {
            return INTERACTION_OPTION_TEXT_RIGHT_PADDING;
        }

        @Override
        public int pixelOptionMaxLineCharacters() {
            return INTERACTION_OPTION_MAX_LINE_CHARACTERS;
        }

        @Override
        public int pixelOptionLineStep() {
            return INTERACTION_OPTION_LINE_STEP;
        }

        @Override
        public int pixelOptionTextColor(boolean selected, boolean hovered) {
            return INTERACTION_OPTION_TEXT_COLOR;
        }

        @Override
        public boolean pixelOptionHighlightActive(int index) {
            return index >= 0
                    && index < VillagerInteractionScreen.this.options.size()
                    && !VillagerInteractionScreen.this.options.get(index).locked();
        }

        @Override
        public ResourceLocation pixelOptionIconTexture(int index) {
            return VillagerInteractionScreen.this.options.get(index).locked()
                    ? VillagerRetaliationClientAssets.INTERACTION_LOCKED_ICON_TEXTURE
                    : null;
        }

        @Override
        public int pixelOptionIconWidth(int index) {
            return VillagerInteractionScreen.this.options.get(index).locked()
                    ? INTERACTION_LOCKED_ICON_WIDTH
                    : 0;
        }

        @Override
        public int pixelOptionIconHeight(int index) {
            return VillagerInteractionScreen.this.options.get(index).locked()
                    ? INTERACTION_LOCKED_ICON_HEIGHT
                    : 0;
        }

        @Override
        public int pixelOptionIconTextGap(int index) {
            return VillagerInteractionScreen.this.options.get(index).locked()
                    ? INTERACTION_LOCKED_ICON_TEXT_GAP
                    : 0;
        }

        @Override
        public boolean pixelOptionHasCheckbox(int index) {
            return VillagerInteractionScreen.this.options.get(index).checkbox();
        }

        @Override
        public boolean pixelOptionChecked(int index) {
            return VillagerInteractionScreen.this.options.get(index).checked();
        }

        @Override
        public ResourceLocation pixelOptionCheckboxTexture() {
            return VillagerRetaliationClientAssets.INTERACTION_CONTAINER_OPTION_CHECKBOX_TEXTURE;
        }

        @Override
        public ResourceLocation pixelOptionCheckmarkTexture() {
            return VillagerRetaliationClientAssets.QUEST_JOURNAL_ICON_COMPLETED_TEXTURE;
        }

        @Override
        public int pixelOptionCheckboxWidth() {
            return INTERACTION_OPTION_CHECKBOX_SIZE;
        }

        @Override
        public int pixelOptionCheckboxHeight() {
            return INTERACTION_OPTION_CHECKBOX_SIZE;
        }

        @Override
        public int pixelOptionCheckboxTextGap() {
            return INTERACTION_OPTION_CHECKBOX_TEXT_GAP;
        }

        @Override
        public float pixelOptionEdgeScaleBlend(int index, int edgePosition) {
            return VillagerInteractionScreen.this.pixelOptionEdgeScaleBlend(index, edgePosition);
        }

        @Override
        public void updatePixelOptionEdgeScaling(boolean topEdgeVisible, boolean bottomEdgeVisible) {
            VillagerInteractionScreen.this.updatePixelOptionEdgeScaling(topEdgeVisible, bottomEdgeVisible);
        }

        @Override
        public ResourceLocation pixelOptionArrowUpTexture() {
            return VillagerRetaliationClientAssets.INTERACTION_CONTAINER_OPTION_SCROLL_UP_ICON_TEXTURE;
        }

        @Override
        public ResourceLocation pixelOptionArrowDownTexture() {
            return VillagerRetaliationClientAssets.INTERACTION_CONTAINER_OPTION_SCROLL_DOWN_ICON_TEXTURE;
        }

        @Override
        public int pixelOptionArrowWidth() {
            return INTERACTION_OPTION_ARROW_WIDTH;
        }

        @Override
        public int pixelOptionArrowHeight() {
            return INTERACTION_OPTION_ARROW_HEIGHT;
        }

        @Override
        public ResourceLocation pixelOptionSelectionArrowTexture() {
            return VillagerRetaliationClientAssets.INTERACTION_CONTAINER_OPTION_ACTIVE_ICON_TEXTURE;
        }

        @Override
        public int pixelOptionSelectionArrowWidth() {
            return INTERACTION_OPTION_SELECTION_ARROW_WIDTH;
        }

        @Override
        public int pixelOptionSelectionArrowHeight() {
            return INTERACTION_OPTION_SELECTION_ARROW_HEIGHT;
        }

        @Override
        public int pixelOptionSelectionArrowGap() {
            return INTERACTION_OPTION_SELECTION_ARROW_GAP;
        }

        @Override
        public void renderScrollbar(GuiGraphics graphics) {
            if (usePixelOptionButtons()) {
                return;
            }
            VillagerInteractionScreen.this.renderScrollbar(graphics);
        }
    }

    private final class NavigationContext implements VillagerInteractionNavigation.Context {
        @Override
        public Font font() {
            return VillagerInteractionScreen.this.font;
        }

        @Override
        public int screenWidth() {
            return VillagerInteractionScreen.this.width;
        }

        @Override
        public int screenHeight() {
            return VillagerInteractionScreen.this.height;
        }

        @Override
        public int hintRight() {
            if (VillagerInteractionScreen.this.page == DialoguePage.SKILLS) {
                return VillagerInteractionScreen.this.scrollbarRight();
            }
            return VillagerInteractionScreen.this.width - VillagerInteractionScreen.this.uiUnit(8);
        }

        @Override
        public boolean topBackButtonVisible() {
            return VillagerInteractionScreen.this.isTopBackButtonVisible();
        }

        @Override
        public boolean topBackButtonHovered(int mouseX, int mouseY) {
            return VillagerInteractionScreen.this.isPointInsideTopBackButton(mouseX, mouseY);
        }

        @Override
        public int topBackLeft() {
            return VillagerInteractionScreen.this.topBackButtonBounds().left();
        }

        @Override
        public int topBackRight() {
            return VillagerInteractionScreen.this.topBackButtonBounds().right();
        }

        @Override
        public int topBackTop() {
            return VillagerInteractionScreen.this.topBackButtonBounds().top();
        }

        @Override
        public int topBackBottom() {
            return VillagerInteractionScreen.this.topBackButtonBounds().bottom();
        }

        @Override
        public String backLabel() {
            return VillagerInteractionScreen.backLabel();
        }

        @Override
        public String hintText() {
            return VillagerInteractionScreen.this.translate(VillagerInteractionScreen.this.canNavigateBack() ? "hint.back" : "hint.leave");
        }

        @Override
        public float textScale() {
            return VillagerInteractionScreen.this.uiScaleFactor();
        }

        @Override
        public float uiAlpha() {
            return 1.0F;
        }
    }

    private final class ProfilePageContext implements VillagerInteractionProfilePage.Context {
        @Override
        public Font font() {
            return VillagerInteractionScreen.this.font;
        }

        @Override
        public int optionsLeft() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return VillagerInteractionScreen.this.profileContainerLeft();
            }
            return VillagerInteractionScreen.this.optionTextLeft() - VillagerInteractionScreen.this.uiUnit(6);
        }

        @Override
        public int conversationInfoTop() {
            return VillagerInteractionScreen.this.conversationInfoTop();
        }

        @Override
        public int optionWidth() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return PROFILE_CONTAINER_WIDTH;
            }
            return VillagerInteractionScreen.this.optionWidth();
        }

        @Override
        public int infoSecondaryColor() {
            return INFO_SECONDARY_COLOR;
        }

        @Override
        public int profileChartRadius() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return PROFILE_CONTAINER_CHART_RADIUS;
            }
            return VillagerInteractionScreen.this.uiUnit(PROFILE_CHART_RADIUS);
        }

        @Override
        public int profileChartCenterXOffset() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return PROFILE_CONTAINER_CHART_CENTER_X_OFFSET;
            }
            return VillagerInteractionScreen.this.uiUnit(8);
        }

        @Override
        public int profileChartCenterYOffset() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return PROFILE_CONTAINER_CHART_CENTER_Y_OFFSET;
            }
            return VillagerInteractionScreen.this.uiUnit(16);
        }

        @Override
        public int profileChartLabelXOffset() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return PROFILE_CONTAINER_CHART_LABEL_X_OFFSET;
            }
            return VillagerInteractionScreen.this.uiUnit(18);
        }

        @Override
        public int profileChartLabelYOffset() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return PROFILE_CONTAINER_CHART_LABEL_Y_OFFSET;
            }
            return VillagerInteractionScreen.this.uiUnit(14);
        }

        @Override
        public int profileChartLoadingYOffset() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return PROFILE_CONTAINER_CHART_LOADING_Y_OFFSET;
            }
            return VillagerInteractionScreen.this.uiUnit(32);
        }

        @Override
        public int profileChartTopLimit() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return VillagerInteractionScreen.this.profileContainerTop() + PROFILE_CONTAINER_CHART_TOP_PADDING;
            }
            return VillagerInteractionScreen.this.topBackButtonBounds().bottom() + VillagerInteractionScreen.this.uiUnit(7);
        }

        @Override
        public int profileChartBottomLimit() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return VillagerInteractionScreen.this.profileContainerTop()
                        + PROFILE_CONTAINER_HEIGHT
                        - PROFILE_CONTAINER_CHART_BOTTOM_PADDING;
            }
            int hintHeight = Math.round(VillagerInteractionScreen.this.font.lineHeight * VillagerInteractionScreen.this.uiScaleFactor());
            int screenBottomLimit = VillagerInteractionScreen.this.height - hintHeight - VillagerInteractionScreen.this.uiUnit(8);
            int viewportBottomLimit = VillagerInteractionScreen.this.conversationInfoTop()
                    + VillagerInteractionScreen.this.rootOptionViewportHeight()
                    - VillagerInteractionScreen.this.uiUnit(4);
            return Math.min(screenBottomLimit, viewportBottomLimit);
        }

        @Override
        public float profileChartTextScale() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return 1.0F;
            }
            return VillagerInteractionScreen.this.uiScaleFactor();
        }

        @Override
        public int profileChartAxisColor() {
            return PROFILE_CHART_AXIS_COLOR;
        }

        @Override
        public int profileChartOutlineColor() {
            return PROFILE_CHART_OUTLINE_COLOR;
        }

        @Override
        public int profileChartValueColor() {
            return PROFILE_CHART_VALUE_COLOR;
        }

        @Override
        public int profileChartPointColor() {
            return PROFILE_CHART_POINT_COLOR;
        }

        @Override
        public int profileChartPointHoverColor() {
            return PROFILE_CHART_POINT_HOVER_COLOR;
        }

        @Override
        public int profileChartPointRadius() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return PROFILE_CONTAINER_CHART_POINT_RADIUS;
            }
            return VillagerInteractionScreen.this.uiUnitAtLeast(1, 1);
        }

        @Override
        public int profileChartPointHoverRadius() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return PROFILE_CONTAINER_CHART_POINT_HOVER_RADIUS;
            }
            return VillagerInteractionScreen.this.uiUnitAtLeast(2, 1);
        }

        @Override
        public int profileChartPointHitRadius() {
            if (VillagerInteractionScreen.this.isEmbeddedProfilePanelActive()) {
                return PROFILE_CONTAINER_CHART_POINT_HIT_RADIUS;
            }
            return VillagerInteractionScreen.this.uiUnitAtLeast(PROFILE_CHART_POINT_HIT_RADIUS, 2);
        }

        @Override
        public String localizedAttribute(VillagerSocialAttribute attribute) {
            return VillagerInteractionScreen.localizedAttribute(attribute);
        }

        @Override
        public String localizedRank(VillagerSocialAttributeRank rank) {
            return VillagerInteractionScreen.localizedRank(rank);
        }

        @Override
        public String localizedAttributeDescription(VillagerSocialAttribute attribute) {
            return VillagerInteractionScreen.localizedAttributeDescription(attribute);
        }

        @Override
        public String translate(String key, Object... args) {
            return VillagerInteractionScreen.translate(key, args);
        }

        @Override
        public Optional<VillagerProfileClientCache.DisplayEntry> profileEntry() {
            return VillagerProfileClientCache.get(VillagerInteractionScreen.this.villagerEntityId);
        }

        @Override
        public void requestProfileRefresh() {
            VillagerInteractionScreen.this.requestProfileRefresh();
        }
    }

    private final class SkillsPageContext implements VillagerInteractionSkillsPage.Context {
        @Override
        public Font font() {
            return VillagerInteractionScreen.this.font;
        }

        @Override
        public int optionsLeft() {
            return VillagerInteractionScreen.this.contentLeft();
        }

        @Override
        public int skillInfoTextLeft() {
            return VillagerInteractionScreen.this.optionTextLeft();
        }

        @Override
        public int skillInfoScissorLeft() {
            return Math.max(0, VillagerInteractionScreen.this.optionsLeft() - VillagerInteractionScreen.this.optionWidth());
        }

        @Override
        public int skillInfoScissorRight() {
            return VillagerInteractionScreen.this.optionsScrollbarLeft() - VillagerInteractionScreen.this.uiUnit(4);
        }

        @Override
        public int optionWidth() {
            return VillagerInteractionScreen.this.optionWidth();
        }

        @Override
        public int skillsPanelLeft() {
            return VillagerInteractionScreen.this.skillsPanelLeft();
        }

        @Override
        public int skillsPanelWidth() {
            return VillagerInteractionScreen.this.skillsPanelWidth();
        }

        @Override
        public int skillsPanelTop() {
            return VillagerInteractionScreen.this.skillsPanelTop();
        }

        @Override
        public int skillsContainerHeight() {
            return VillagerInteractionScreen.this.skillsContainerHeight();
        }

        @Override
        public int skillsContainerPaddingX() {
            return VillagerInteractionScreen.this.skillsContainerPaddingX();
        }

        @Override
        public int skillsContainerPaddingY() {
            return VillagerInteractionScreen.this.skillsContainerPaddingY();
        }

        @Override
        public int profileSkillRowHeight() {
            return VillagerInteractionScreen.this.profileSkillRowHeight();
        }

        @Override
        public int profileSkillRowGap() {
            return VillagerInteractionScreen.this.profileSkillRowGap();
        }

        @Override
        public int profileSkillBarHeight() {
            return VillagerInteractionScreen.this.profileSkillBarHeight();
        }

        @Override
        public int profileSkillColumns() {
            return PROFILE_SKILL_COLUMNS;
        }

        @Override
        public int profileSkillColumnGap() {
            return VillagerInteractionScreen.this.profileSkillColumnGap();
        }

        @Override
        public float uiAlpha() {
            return VillagerInteractionUiAnimation.uiAlpha();
        }

        @Override
        public int uiUnit(int value) {
            return VillagerInteractionScreen.this.uiUnit(value);
        }

        @Override
        public int infoValueColor() {
            return INFO_VALUE_COLOR;
        }

        @Override
        public int infoSecondaryColor() {
            return INFO_SECONDARY_COLOR;
        }

        @Override
        public float skillScroll() {
            return VillagerInteractionScreen.this.skillScroll;
        }

        @Override
        public int optionStride() {
            return VillagerInteractionScreen.this.optionStride();
        }

        @Override
        public float optionTextYOffset() {
            return VillagerInteractionScreen.this.optionTextYOffset();
        }

        @Override
        public float textScale() {
            return VillagerInteractionScreen.this.uiScaleFactor();
        }

        @Override
        public int skillInfoViewportTop() {
            return VillagerInteractionScreen.this.skillInfoViewportTop();
        }

        @Override
        public int skillInfoViewportBottom() {
            return VillagerInteractionScreen.this.skillInfoViewportBottom();
        }

        @Override
        public float skillInfoEdgeFadeAlpha(float lineY, int viewportTop, int viewportBottom) {
            return VillagerInteractionScreen.this.skillInfoEdgeFadeAlpha(lineY, viewportTop, viewportBottom);
        }

        @Override
        public int guiScissorOffsetY() {
            return VillagerInteractionScreen.this.renderSlideOffsetY;
        }

        @Override
        public int guiScissorOffsetX() {
            return VillagerInteractionScreen.this.renderContentOffsetX;
        }

        @Override
        public VillagerSkill selectedSkillDetails() {
            return VillagerInteractionScreen.this.selectedSkillDetails;
        }

        @Override
        public String localizedSkill(VillagerSkill skill) {
            return VillagerInteractionScreen.localizedSkill(skill);
        }

        @Override
        public String localizedSkillRank(VillagerSkillRank rank) {
            return VillagerInteractionScreen.localizedSkillRank(rank);
        }

        @Override
        public String localizedExpandedSkillDescription(VillagerSkill skill) {
            return VillagerInteractionScreen.localizedExpandedSkillDescription(skill);
        }

        @Override
        public String localizedSkillDescription(VillagerSkill skill) {
            return VillagerInteractionScreen.localizedSkillDescription(skill);
        }

        @Override
        public String translate(String key, Object... args) {
            return VillagerInteractionScreen.translate(key, args);
        }

        @Override
        public Optional<VillagerProfileClientCache.DisplayEntry> profileEntry() {
            return VillagerProfileClientCache.get(VillagerInteractionScreen.this.villagerEntityId);
        }

        @Override
        public void requestProfileRefresh() {
            VillagerInteractionScreen.this.requestProfileRefresh();
        }

        @Override
        public void renderSkillInfoScrollbar(GuiGraphics graphics) {
            VillagerInteractionScreen.this.renderScrollbar(
                    graphics,
                    VillagerInteractionScreen.this.skillInfoScrollbarThumb(),
                    VillagerInteractionScreen.this.skillScroll,
                    VillagerInteractionScreen.this.maxSkillScroll());
        }
    }

    private final class JobStatsPageContext implements VillagerInteractionJobStatsPage.Context {
        @Override
        public Font font() {
            return VillagerInteractionScreen.this.font;
        }

        @Override
        public int panelLeft() {
            return VillagerInteractionScreen.this.skillsPanelLeft();
        }

        @Override
        public int panelTop() {
            return VillagerInteractionScreen.this.skillsPanelTop();
        }

        @Override
        public boolean baby() {
            return VillagerInteractionScreen.this.baby;
        }

        @Override
        public String translate(String key, Object... args) {
            return VillagerInteractionScreen.translate(key, args);
        }

        @Override
        public String roleLabel(HiredVillagerRole role) {
            return VillagerInteractionScreen.jobRoleLabel(role);
        }

        @Override
        public Optional<VillagerProfileClientCache.DisplayEntry> profileEntry() {
            return VillagerProfileClientCache.get(VillagerInteractionScreen.this.villagerEntityId);
        }

        @Override
        public void requestProfileRefresh() {
            VillagerInteractionScreen.this.requestProfileRefresh();
        }
    }
}
