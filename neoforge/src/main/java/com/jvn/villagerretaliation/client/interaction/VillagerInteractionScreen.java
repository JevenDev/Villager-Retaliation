package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanScrollbarThumb;
import com.jvn.toucanlib.client.ToucanScrollState;
import com.jvn.toucanlib.client.ToucanScrollbars;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache;
import com.jvn.villagerretaliation.client.reputation.VillagerReputationIconSet;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.config.DialogueTextSpeed;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.ClipboardStorageActionPayload;
import com.jvn.villagerretaliation.network.HiredAnimalBreedingTargetPayload;
import com.jvn.villagerretaliation.network.HiredBuilderOrderPayload;
import com.jvn.villagerretaliation.network.HiredBrewingOrderPayload;
import com.jvn.villagerretaliation.network.HiredLoggingFilterPayload;
import com.jvn.villagerretaliation.network.HiredLoggingOptionPayload;
import com.jvn.villagerretaliation.network.VillagerConversationEndRequestPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueRequestPayload;
import com.jvn.villagerretaliation.network.VillagerGiftRequestPayload;
import com.jvn.villagerretaliation.network.VillagerInventoryRequestPayload;
import com.jvn.villagerretaliation.network.VillagerProfileRequestPayload;
import com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload;
import com.jvn.villagerretaliation.network.VillagerTradeRequestPayload;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeRank;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
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
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
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
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public class VillagerInteractionScreen extends Screen implements VillagerInteractionSessionScreen {
    private static final String GUI_KEY_PREFIX = "villagerretaliation.gui.";
    private static final String BACK_LABEL_KEY = GUI_KEY_PREFIX + "back";
    private static final String FORCED_LEAVE_OPTION_ID = "leave";
    private static final String DIALOGUE_TREE_LEAVE_OPTION_ID = DialogueTreeService.LEAVE_OPTION_ID;
    private static final String BLUEPRINT_CHANGE_OPTION_ID = "construction_blueprint_change";
    private static final String BLUEPRINT_NEVERMIND_OPTION_ID = "construction_blueprint_nevermind";
    private static final float OPTION_SCROLL_LERP = 0.32F;
    private static final float OPTION_SCROLL_STEP = 12.0F;
    private static final float OPTION_HOVER_SCALE = 0.055F;
    private static final float OPTION_SELECTED_SCALE = 0.02F;
    private static final int INFO_VALUE_COLOR = 0xFFF8F6EF;
    private static final int INFO_SECONDARY_COLOR = 0xB8D5D0C6;
    private static final int GIFT_BUTTON_WIDTH = 64;
    private static final int GIFT_BUTTON_HEIGHT = 18;
    private static final int PROFILE_CHART_RADIUS = 36;
    private static final int PROFILE_CHART_AXIS_COLOR = 0x55E8E4DA;
    private static final int PROFILE_CHART_OUTLINE_COLOR = 0x90E8E4DA;
    private static final int PROFILE_CHART_VALUE_COLOR = 0xFFE9C46A;
    private static final int PROFILE_CHART_POINT_COLOR = 0xFFFFF3B0;
    private static final int PROFILE_CHART_POINT_HOVER_COLOR = 0xFFFFFFFF;
    private static final int PROFILE_CHART_POINT_HIT_RADIUS = 6;
    private static final int PROFILE_SKILL_COLUMNS = 2;
    private static final ResourceLocation DEFAULT_CURRENCY_ICON_SPRITE = ResourceLocation.withDefaultNamespace("item/emerald");
    private static final int INTERACTION_CONTAINER_WIDTH = 282;
    private static final int INTERACTION_CONTAINER_HEIGHT = 113;
    private static final int INTERACTION_CONTAINER_HOTBAR_GAP = 24;
    private static final int INTERACTION_CONTAINER_ORNAMENT_WIDTH = 288;
    private static final int INTERACTION_CONTAINER_ORNAMENT_HEIGHT = 104;
    private static final int INTERACTION_CONTAINER_ORNAMENT_Y = -3;
    private static final int INTERACTION_CONTAINER_NAME_X = 6;
    private static final int INTERACTION_CONTAINER_NAME_Y = 4;
    private static final int INTERACTION_NAMEPLATE_X = 0;
    private static final int INTERACTION_NAMEPLATE_Y = -2;
    private static final int INTERACTION_NAMEPLATE_TEXTURE_WIDTH = 20;
    private static final int INTERACTION_NAMEPLATE_TEXTURE_HEIGHT = 20;
    private static final int INTERACTION_NAMEPLATE_SLICE_LEFT = 4;
    private static final int INTERACTION_NAMEPLATE_SLICE_RIGHT = 4;
    private static final int INTERACTION_NAMEPLATE_SLICE_TOP = 4;
    private static final int INTERACTION_NAMEPLATE_SLICE_BOTTOM = 4;
    private static final int INTERACTION_NAMEPLATE_RIGHT_PADDING = 6;
    private static final int INTERACTION_NAMEPLATE_MAX_NAME_CHARS = 16;
    private static final int INTERACTION_NAMEPLATE_ORNAMENT_TEXTURE_WIDTH = 26;
    private static final int INTERACTION_NAMEPLATE_ORNAMENT_TEXTURE_HEIGHT = 26;
    private static final int INTERACTION_NAMEPLATE_ORNAMENT_SLICE_LEFT = 7;
    private static final int INTERACTION_NAMEPLATE_ORNAMENT_SLICE_RIGHT = 7;
    private static final int INTERACTION_NAMEPLATE_ORNAMENT_SLICE_TOP = 7;
    private static final int INTERACTION_NAMEPLATE_ORNAMENT_SLICE_BOTTOM = 7;
    private static final int INTERACTION_NAMEPLATE_ORNAMENT_MARGIN = 3;
    private static final int INTERACTION_DIALOGUE_LEFT = 69;
    private static final int INTERACTION_DIALOGUE_TOP = 26;
    private static final int INTERACTION_DIALOGUE_RIGHT = 270;
    private static final int INTERACTION_DIALOGUE_BOTTOM = 68;
    private static final int INTERACTION_PORTRAIT_LEFT = 6;
    private static final int INTERACTION_PORTRAIT_TOP = 20;
    private static final int INTERACTION_PORTRAIT_RIGHT = 60;
    private static final int INTERACTION_PORTRAIT_BOTTOM = 74;
    private static final int INTERACTION_PORTRAIT_SCALE = 62;
    private static final int INTERACTION_PORTRAIT_RENDER_Y_OFFSET = 2;
    private static final int INTERACTION_PORTRAIT_ORNAMENT_WIDTH = 65;
    private static final int INTERACTION_PORTRAIT_ORNAMENT_HEIGHT = 65;
    private static final int INTERACTION_PORTRAIT_ORNAMENT_X_OFFSET = 1;
    private static final int INTERACTION_PORTRAIT_ORNAMENT_Y_OFFSET = 1;
    private static final float INTERACTION_PORTRAIT_ORNAMENT_Z = 119.0F;
    private static final int INTERACTION_CONTAINER_OVERLAY_X = 4;
    private static final int INTERACTION_CONTAINER_OVERLAY_Y = 68;
    private static final int INTERACTION_CONTAINER_OVERLAY_WIDTH = 59;
    private static final int INTERACTION_CONTAINER_OVERLAY_HEIGHT = 45;
    private static final float INTERACTION_CONTAINER_OVERLAY_Z = 120.0F;
    private static final int INTERACTION_STATS_ANCHOR_X = 276;
    private static final int INTERACTION_STATS_BASELINE_Y = 93;
    private static final int INTERACTION_STATS_TEXT_RAISE = 2;
    private static final int INTERACTION_OPTION_WIDTH = 64;
    private static final int INTERACTION_OPTION_HEIGHT = 17;
    private static final int INTERACTION_OPTION_STRIDE = 16;
    private static final int INTERACTION_OPTION_CONTAINER_GAP = 5;
    private static final int INTERACTION_OPTION_TEXT_INSET = 5;
    private static final int INTERACTION_OPTION_TEXT_TOP = 5;
    private static final int INTERACTION_OPTION_TEXT_RIGHT_PADDING = INTERACTION_OPTION_TEXT_INSET;
    private static final int INTERACTION_OPTION_ARROW_WIDTH = 9;
    private static final int INTERACTION_OPTION_ARROW_HEIGHT = 6;
    private static final int INTERACTION_OPTION_SELECTION_ARROW_WIDTH = 8;
    private static final int INTERACTION_OPTION_SELECTION_ARROW_HEIGHT = 11;
    private static final int INTERACTION_OPTION_SELECTION_ARROW_GAP = 2;
    private static final int INTERACTION_OPTION_MAX_LINE_CHARACTERS = 20;
    private static final int INTERACTION_OPTION_LINE_STEP = 10;
    private static final int INTERACTION_ICON_SIZE = 16;
    private static final int INTERACTION_ICON_TEXT_GAP = 4;
    private static final int INTERACTION_TOOLTIP_MAX_WIDTH = 220;
    private static final ResourceLocation DIALOGUE_BLIP_SOUND_ID = VillagerRetaliation.id("dialogue");
    private static final SoundEvent DIALOGUE_BLIP_SOUND = SoundEvent.createVariableRangeEvent(DIALOGUE_BLIP_SOUND_ID);
    private static final int DIALOGUE_BLIP_MIN_VISIBLE_CHARACTERS = 1;
    private static final int DIALOGUE_BLIP_MAX_VISIBLE_CHARACTERS = 3;
    private static final int INTERACTION_NAME_COLOR = 0xFFF3CA55;
    private static final int INTERACTION_DIALOGUE_COLOR = 0xFF35291C;
    private static final int INTERACTION_REPUTATION_TEXT_COLOR = 0xFFFFFF55;
    private static final int TEXT_OUTLINE_COLOR = 0xFF000000;
    private static final Runnable NO_ACTION = () -> {
    };

    private final int villagerEntityId;
    private final String villagerName;
    private final String professionName;
    private final VillagerProfessionUiColors.ColorPair professionUiColors;
    private final String genderName;
    private final boolean baby;
    private int reputation;
    private VillagerReputationLevel reputationLevel;
    private DialogueDisposition mood;
    private VillagerMood primaryMood;
    private boolean followingPlayer;
    private boolean stayingHere;
    private final boolean forcedDialogue;
    private final boolean clipboardMenu;
    private boolean hiredByPlayer;
    private final boolean hiredByOtherPlayer;
    private int hiredRemainingDays;
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
    private final HiredVillagerRole activeHiredRole;
    private boolean activeBrewingOrder;
    private boolean activeBuilderTask;
    private final Set<String> selectedLoggingFilters = new LinkedHashSet<>();
    private boolean loggingStripLogs;
    private boolean loggingHarvestLeaves;
    private boolean loggingBonemealSaplings;
    private boolean loggingPlantSaplings;
    private boolean loggingPickUpDecayDrops;
    private final Set<String> selectedAnimalBreedingTargets = new LinkedHashSet<>();
    private boolean forceCameraTowardsVillager;
    private final List<DialogueOption> options = new ArrayList<>();
    private final List<DialogueOptionDefinition> dialogueOptions = new ArrayList<>();
    private final List<String> knownLikedGiftNames = new ArrayList<>();
    private final List<String> knownDislikedGiftNames = new ArrayList<>();
    private final VillagerFamilyTreeSnapshot familyTree;
    private final VillagerRelationshipSnapshot relationships;
    private final VillagerInteractionScreenState state = new VillagerInteractionScreenState();
    private final EnumMap<DialoguePage, VillagerInteractionScreenState.OptionListPosition> rememberedPageOptionPositions =
            new EnumMap<>(DialoguePage.class);
    private DialoguePage page = DialoguePage.ROOT;
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
    private VillagerSkill selectedSkillDetails;
    private HiredBrewingRecipeCatalog.BrewingPotionChoice selectedBrewingPotionChoice;
    private HiredBrewingRecipeCatalog.BrewingDurationChoice selectedBrewingDurationChoice;
    private HiredBrewingRecipeCatalog.BrewingLevelChoice selectedBrewingLevelChoice;
    private HiredBrewingRecipeCatalog.BrewingRoute selectedBrewingRoute;
    private String selectedBuilderCategory;
    private BuilderStructureCatalog.Entry selectedBuilderStructure;
    private int selectedInventorySlot = -1;
    private int lastMouseX;
    private int lastMouseY;
    private boolean keyboardOptionFocusVisible;
    private long experimentalSkillsAnimationStartMillis = -1L;
    private long experimentalSkillsExitStartMillis = -1L;
    private Button giftButton;
    private String villagerDialogueText = "";
    private long dialogueTextAnimationStartMillis;
    private boolean dialogueTextAnimationSkipped;
    private final Random dialogueBlipRandom = new Random();
    private float dialogueBlipPitch = 1.0F;
    private int nextDialogueBlipVisibleCharacter = Integer.MAX_VALUE;
    private int lastDialogueBlipVisibleCharacters;
    private final GiftPageContext giftPageContext = new GiftPageContext();
    private final OptionListContext optionListContext = new OptionListContext();
    private final NavigationChromeContext navigationChromeContext = new NavigationChromeContext();
    private final ProfilePageContext profilePageContext = new ProfilePageContext();
    private final SkillsPageContext skillsPageContext = new SkillsPageContext();

    public VillagerInteractionScreen(
            int villagerEntityId,
            String villagerName,
            String professionName,
            VillagerProfessionUiColors.ColorPair professionUiColors,
            String genderName,
            boolean baby,
            int reputation,
            VillagerReputationLevel reputationLevel,
            DialogueDisposition mood,
            VillagerMood primaryMood,
            boolean followingPlayer,
            boolean stayingHere,
            boolean forcedDialogue,
            boolean clipboardMenu,
            boolean hiredByPlayer,
            boolean hiredByOtherPlayer,
            int hiredRemainingDays,
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
            List<String> selectedLoggingFilters,
            boolean loggingStripLogs,
            boolean loggingHarvestLeaves,
            boolean loggingBonemealSaplings,
            boolean loggingPlantSaplings,
            boolean loggingPickUpDecayDrops,
            List<String> selectedAnimalBreedingTargets,
            List<DialogueOptionDefinition> dialogueOptions,
            List<String> knownLikedGiftNames,
            List<String> knownDislikedGiftNames,
            VillagerFamilyTreeSnapshot familyTree,
            VillagerRelationshipSnapshot relationships) {
        super(Component.translatable(GUI_KEY_PREFIX + "title"));
        this.villagerEntityId = villagerEntityId;
        this.villagerName = villagerName;
        this.professionName = professionName;
        this.professionUiColors = professionUiColors == null ? VillagerProfessionUiColors.DEFAULT_COLORS : professionUiColors;
        this.genderName = localizedGenderName(genderName);
        this.baby = baby;
        this.reputation = reputation;
        this.reputationLevel = reputationLevel;
        this.mood = mood;
        this.primaryMood = primaryMood == null ? VillagerMood.NEUTRAL : primaryMood;
        this.followingPlayer = followingPlayer;
        this.stayingHere = stayingHere;
        this.forcedDialogue = forcedDialogue;
        this.clipboardMenu = clipboardMenu;
        this.hiredByPlayer = hiredByPlayer;
        this.hiredByOtherPlayer = hiredByOtherPlayer;
        this.hiredRemainingDays = Math.max(0, hiredRemainingDays);
        this.walletEmeralds = Math.max(0, walletEmeralds);
        this.maxWalletEmeralds = Math.max(0, maxWalletEmeralds);
        this.lifetimeWalletEarned = Math.max(0, lifetimeWalletEarned);
        this.lifetimeWalletDeposited = Math.max(0, lifetimeWalletDeposited);
        this.walletCurrencyName = blankToDefault(walletCurrencyName, "emerald");
        this.walletCurrencyPluralName = blankToDefault(walletCurrencyPluralName, "emeralds");
        this.walletCurrencyLabel = blankToDefault(walletCurrencyLabel, "Emeralds");
        this.walletCurrencyIconSprite = walletCurrencyIconSprite == null ? DEFAULT_CURRENCY_ICON_SPRITE : walletCurrencyIconSprite;
        this.walletCurrencyTextColor = walletCurrencyTextColor | 0xFF000000;
        this.availableHiredRoles = availableHiredRoles == null || availableHiredRoles.isEmpty()
                ? EnumSet.noneOf(HiredVillagerRole.class)
                : EnumSet.copyOf(availableHiredRoles);
        this.activeHiredRole = activeHiredRole;
        this.activeBrewingOrder = activeBrewingOrder;
        this.activeBuilderTask = activeBuilderTask;
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
        this.forceCameraTowardsVillager = forceCameraTowardsVillager;
        this.dialogueOptions.addAll(dialogueOptions);
        this.knownLikedGiftNames.addAll(knownLikedGiftNames);
        this.knownDislikedGiftNames.addAll(knownDislikedGiftNames);
        this.familyTree = familyTree == null ? VillagerFamilyTreeSnapshot.EMPTY : familyTree;
        this.relationships = relationships == null ? VillagerRelationshipSnapshot.EMPTY : relationships;
        if (forcedDialogue) {
            this.page = DialoguePage.TALK;
        }
        syncCameraFocusState();
        VillagerInteractionExperimentalChrome.resetAnimation(this.professionUiColors);
    }

    @Override
    protected void init() {
        this.giftButton = addRenderableWidget(Button.builder(Component.translatable(GUI_KEY_PREFIX + "gift.give"), button -> requestGift())
                .bounds(0, 0, GIFT_BUTTON_WIDTH, GIFT_BUTTON_HEIGHT)
                .build());
        this.giftButton.visible = false;
        rebuildOptions();
    }

    @Override
    public void tick() {
        syncCameraFocusState();
        ClientVillagerConversationState.tickCameraFocus();
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
            List<String> knownDislikedGiftNames) {
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
        this.awaitingForcedDialogueResponse = false;
        if (this.page == DialoguePage.TALK) {
            rebuildOptionsKeepingListPosition();
        }
    }

    public void replaceFromServer() {
        this.closingFromServer = true;
        this.replacingFromServer = true;
    }

    public void closeFromServer() {
        this.closingFromServer = true;
        if (Minecraft.getInstance().screen != this) {
            VillagerInteractionChatVisibility.restoreHiddenVillagerMessages(Minecraft.getInstance());
            ClientVillagerConversationState.clear();
        }
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void acceptVillagerDialogue(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        this.villagerDialogueText = text.strip();
        this.dialogueTextAnimationStartMillis = Util.getMillis();
        this.dialogueTextAnimationSkipped = dialogueTextSpeed().instant();
        this.dialogueBlipPitch = randomDialogueBlipPitch();
        this.lastDialogueBlipVisibleCharacters = 0;
        this.nextDialogueBlipVisibleCharacter = this.dialogueTextAnimationSkipped
                ? Integer.MAX_VALUE
                : randomDialogueBlipGap();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        focusVillagerOnPlayer();
        updateMouseSelection(mouseX, mouseY);
        updateOptionScroll();
        updateSkillScroll();

        VillagerClientUiUtil.pushGuiLayer(graphics, VillagerClientUiUtil.screenLayerZ());
        renderInteractionContainer(graphics);
        if (this.page == DialoguePage.GIFT) {
            renderGiftPage(graphics, mouseX, mouseY, partialTick);
        } else if (this.page == DialoguePage.PROFILE) {
            if (this.giftButton != null) {
                this.giftButton.visible = false;
            }
            renderProfilePage(graphics, mouseX, mouseY);
        } else if (this.page == DialoguePage.SKILLS) {
            if (this.giftButton != null) {
                this.giftButton.visible = false;
            }
            renderSkillsPage(graphics, mouseX, mouseY);
        } else {
            if (this.giftButton != null) {
                this.giftButton.visible = false;
            }
            renderOptions(graphics, mouseX, mouseY, optionsTop());
        }
        renderInteractionStatTooltips(graphics, mouseX, mouseY);
        VillagerClientUiUtil.popGuiLayer(graphics);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tryOpenVanillaChat(keyCode, scanCode)) {
            return true;
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                goBackOrLeaveConversation();
                yield true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                moveSelection(-1);
                yield true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                moveSelection(1);
                yield true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (this.page == DialoguePage.GIFT) {
                    requestGift();
                } else {
                    activateSelected();
                }
                yield true;
            }
            default -> super.keyPressed(keyCode, scanCode, modifiers);
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isLeftMouseButton(button)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (this.page == DialoguePage.GIFT && tryClickGiftPage(mouseX, mouseY)) {
            return true;
        }

        if (trySkipDialogueTextAnimation(mouseX, mouseY)) {
            return true;
        }

        if (trySelectSkillDetails(mouseX, mouseY)
                || tryBeginSkillInfoScrollbarDrag(mouseX, mouseY)
                || tryBeginScrollbarDrag(mouseX, mouseY)
                || tryActivateHoveredOption(mouseX, mouseY)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.page == DialoguePage.SKILLS
                && maxSkillScroll() > 0.0F
                && isPointInsideSkillsInfoScrollArea(mouseX, mouseY)) {
            setTargetSkillScroll(this.targetSkillScroll - (float) scrollY * OPTION_SCROLL_STEP);
            return true;
        }

        if (maxOptionScroll() <= 0.0F || !isPointInsideOptionScrollArea(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        setTargetOptionScroll(this.state.targetOptionScroll() - (float) scrollY * OPTION_SCROLL_STEP);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isLeftMouseButton(button) && this.draggingSkillScrollbar) {
            return dragSkillScrollbar(mouseY);
        }
        if (isLeftMouseButton(button) && this.draggingScrollbar) {
            return dragScrollbar(mouseY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
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
    public void removed() {
        if (this.openingChat) {
            this.openingChat = false;
            super.removed();
            return;
        }

        if (!this.replacingFromServer) {
            VillagerInteractionChatVisibility.restoreHiddenVillagerMessages(Minecraft.getInstance());
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

    private void openVanillaChat(String initialText) {
        this.openingChat = true;
        Minecraft.getInstance().setScreen(new VillagerInteractionChatScreen(this, initialText));
    }

    private void rebuildOptions() {
        this.options.clear();
        if (this.page == DialoguePage.TALK) {
            addDialogueOptions();
        } else if (this.page == DialoguePage.PROFILE) {
            addProfileOptions();
        } else if (this.page == DialoguePage.SKILLS) {
            addSkillsOptions();
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
        } else if (this.page == DialoguePage.LOGGING_FILTERS) {
            addLoggingFilterOptions();
        } else if (this.page == DialoguePage.ANIMAL_BREEDING_TARGETS) {
            addAnimalBreedingTargetOptions();
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
            } else {
                addRootOptions();
            }
        }
        this.state.resetOptions(!this.options.isEmpty());
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
            addDialogueOption(option.label(), option.id());
        }
    }

    private void addRootOptions() {
        addOption("root.talk", this::openTalkPage);
        if (!this.baby) {
            addOption("root.profile", this::openProfilePage);
            addOption("root.skills", this::openSkillsPage);
            this.options.add(DialogueOption.enabled(familyButtonText(), this::openFamilyPage));
            addOption("root.trade", this::requestTrade);
            if (VillagerRetaliationConfig.ENABLE_VILLAGER_GIFTS.get()) {
                addOption("root.gift", this::openGiftPage);
            }
            if (canRequestVillagerInventory()) {
                addOption("root.inventory", this::requestInventory);
            }
            this.options.add(DialogueOption.enabled(translate(this.hiredByPlayer || this.hiredByOtherPlayer ? "root.job" : "root.recruit"), this::openRecruitPage));
            if (this.relationships.hasRelationships()) {
                addOption("root.relationships", this::openRelationshipPage);
            }
            addRootRecruitmentOptions();
        }
        addOption("root.goodbye", this::leaveConversation);
    }

    private void addRootRecruitmentOptions() {
        if (this.followingPlayer) {
            if (canCommandStayHere()) {
                addOption("recruit.stay_here", () -> requestRecruit(VillagerRecruitRequestPayload.Action.STAY_HERE));
            }
        } else if (this.stayingHere) {
            addOption("recruit.follow_me", () -> requestRecruit(VillagerRecruitRequestPayload.Action.FOLLOW));
        } else {
            addOption("recruit.follow_me", () -> requestRecruit(VillagerRecruitRequestPayload.Action.FOLLOW));
            if (canCommandStayHere()) {
                addOption("recruit.stay_here", () -> requestRecruit(VillagerRecruitRequestPayload.Action.STAY_HERE));
            }
        }
    }

    private void addProfileOptions() {
    }

    private void addSkillsOptions() {
    }

    private void addRecruitOptions() {
        if (this.hiredByPlayer) {
            addOption("recruit.about_contract", this::openContractPage);
        } else if (this.hiredByOtherPlayer) {
            addOption("recruit.contract", () -> requestRecruit(VillagerRecruitRequestPayload.Action.VIEW_CONTRACT));
        } else if (canHireVillager()) {
            addOption("recruit.hire", this::openHirePage);
        }
        addOption("recruit.job_inventory", () -> requestRecruit(VillagerRecruitRequestPayload.Action.OPEN_JOB_INVENTORY));
        addOption("recruit.storage", this::openStoragePage);
        if (this.hiredByPlayer) {
            addOption("recruit.payment", this::openPaymentPage);
            addOption("recruit.about_role", this::openRolePage);
            addOption("recruit.work", this::openWorkPage);
        }
        addOption("recruit.end_hire", () -> {
            openEndContractConfirmationPage();
            requestRecruit(VillagerRecruitRequestPayload.Action.PROMPT_END_HIRE_CONFIRMATION);
        });
        if (this.followingPlayer) {
            addOption("recruit.stop_following", () -> requestRecruit(VillagerRecruitRequestPayload.Action.STOP_FOLLOWING));
        } else if (this.stayingHere) {
            addOption("recruit.stop_following", () -> requestRecruit(VillagerRecruitRequestPayload.Action.STOP_FOLLOWING));
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
        addOption("recruit.hire_one_day", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_ONE_DAY));
        addOption("recruit.hire_three_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_THREE_DAYS));
        addOption("recruit.hire_five_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_FIVE_DAYS));
        addOption("recruit.hire_seven_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_SEVEN_DAYS));
        addOption("recruit.hire_fifteen_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_FIFTEEN_DAYS));
        addOption("recruit.hire_thirty_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_THIRTY_DAYS));
        addOption("recruit.nevermind", this::openRecruitPage);
    }

    private void addContractOptions() {
        addOption("recruit.contract_days_left", () -> requestRecruit(VillagerRecruitRequestPayload.Action.VIEW_CONTRACT));
        addOption("recruit.extend_contract", this::openContractExtensionPage);
        addOption("recruit.nevermind", this::openRecruitPage);
    }

    private void addEndContractConfirmationOptions() {
        addOption("recruit.end_hire_confirm", () -> requestRecruit(VillagerRecruitRequestPayload.Action.END_HIRE));
        addOption("recruit.nevermind", () -> {
            requestRecruit(VillagerRecruitRequestPayload.Action.DECLINE_END_HIRE_CONFIRMATION);
            openRecruitPage();
        });
    }

    private void addContractExtensionOptions() {
        addOption("recruit.extend_one_day", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_ONE_DAY));
        addOption("recruit.extend_three_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_THREE_DAYS));
        addOption("recruit.extend_five_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_FIVE_DAYS));
        addOption("recruit.extend_seven_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_SEVEN_DAYS));
        addOption("recruit.extend_fifteen_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_FIFTEEN_DAYS));
        addOption("recruit.extend_thirty_days", () -> requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_THIRTY_DAYS));
        addOption("recruit.nevermind", this::openContractPage);
    }

    private void addRoleOptions() {
        addOption("recruit.current_role", () -> requestRecruit(VillagerRecruitRequestPayload.Action.VIEW_ROLE));
        addOption("recruit.change_role", this::openRoleChangePage);
        addOption("recruit.nevermind", this::openRecruitPage);
    }

    private void addRoleChangeOptions() {
        addRoleChangeOption(HiredVillagerRole.COMBAT, "recruit.role_combat", VillagerRecruitRequestPayload.Action.SET_ROLE_COMBAT);
        addRoleChangeOption(HiredVillagerRole.MINING, "recruit.role_mining", VillagerRecruitRequestPayload.Action.SET_ROLE_MINING);
        addRoleChangeOption(HiredVillagerRole.LOGGING, "recruit.role_logging", VillagerRecruitRequestPayload.Action.SET_ROLE_LOGGING);
        addRoleChangeOption(HiredVillagerRole.FARMING, "recruit.role_farming", VillagerRecruitRequestPayload.Action.SET_ROLE_FARMING);
        addRoleChangeOption(HiredVillagerRole.FISHING, "recruit.role_fishing", VillagerRecruitRequestPayload.Action.SET_ROLE_FISHING);
        addRoleChangeOption(HiredVillagerRole.BREWING, "recruit.role_brewing", VillagerRecruitRequestPayload.Action.SET_ROLE_BREWING);
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
        addRoleWorkConfigOption(HiredVillagerRole.MINING, "recruit.work_config_mining", VillagerRecruitRequestPayload.Action.CONFIGURE_MINING);
        if (isActiveHiredRole(HiredVillagerRole.LOGGING)) {
            addOption("recruit.work_config_logging", this::openLoggingFiltersPage);
        }
        addRoleWorkConfigOption(HiredVillagerRole.FARMING, "recruit.work_config_farming", VillagerRecruitRequestPayload.Action.CONFIGURE_FARMING);
        addRoleWorkConfigOption(HiredVillagerRole.FISHING, "recruit.work_config_fishing", VillagerRecruitRequestPayload.Action.CONFIGURE_FISHING);
        if (isActiveHiredRole(HiredVillagerRole.BREWING)) {
            if (this.activeBrewingOrder) {
                addOption("recruit.stop_brewing", () -> requestRecruit(VillagerRecruitRequestPayload.Action.STOP_BREWING));
            } else {
                addOption("recruit.work_config_brewing", this::openBrewingPotionPage);
            }
        }
        if (isActiveHiredRole(HiredVillagerRole.BUILDER)) {
            if (this.activeBuilderTask) {
                addOption("recruit.stop_builder_build", () -> requestRecruit(VillagerRecruitRequestPayload.Action.STOP_BUILDER_BUILD));
            } else {
                addOption("recruit.work_config_builder", this::openBuilderStructuresPage);
            }
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
        this.options.add(DialogueOption.enabled(checkmarkRowLabel("Any logs", this.selectedLoggingFilters.isEmpty()), () -> requestLoggingFilter("any")));
        List<ResourceLocation> filters = HiredLoggingFilters.options();
        if (filters.isEmpty()) {
            this.options.add(DialogueOption.enabled(translate("recruit.logging_no_filters"), NO_ACTION));
        }
        for (ResourceLocation filter : filters) {
            String id = filter.toString();
            this.options.add(DialogueOption.enabled(checkmarkRowLabel(HiredLoggingFilters.label(filter), this.selectedLoggingFilters.contains(id)), () -> requestLoggingFilter(id)));
        }
        addOption("recruit.nevermind", this::openWorkPage);
    }

    private void addAnimalBreedingTargetOptions() {
        this.options.add(DialogueOption.enabled(checkmarkRowLabel(translate("recruit.animal_breeding_all"), this.selectedAnimalBreedingTargets.isEmpty()), () -> requestAnimalBreedingTarget("all")));
        List<ResourceLocation> targets = HiredAnimalBreedingTargets.options();
        if (targets.isEmpty()) {
            this.options.add(DialogueOption.enabled(translate("recruit.animal_breeding_no_targets"), NO_ACTION));
        }
        for (ResourceLocation target : targets) {
            String id = target.toString();
            this.options.add(DialogueOption.enabled(checkmarkRowLabel(HiredAnimalBreedingTargets.label(target), this.selectedAnimalBreedingTargets.contains(id)), () -> requestAnimalBreedingTarget(id)));
        }
        addOption("recruit.nevermind", this::openWorkPage);
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
        addOption("recruit.nevermind", this::openWorkPage);
    }

    private void addLoggingOption(String optionId, String translationKey, boolean enabled) {
        this.options.add(DialogueOption.enabled(
                checkmarkRowLabel(translate(translationKey), enabled),
                () -> requestLoggingOption(optionId)));
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
        addOption("recruit.nevermind", this::openWorkPage);
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
        if (canOfferHiredRole(role)) {
            addOption(labelKey, () -> requestRecruit(action));
        }
    }

    private void addRoleWorkConfigOption(HiredVillagerRole role, String labelKey, VillagerRecruitRequestPayload.Action action) {
        if (isActiveHiredRole(role)) {
            addOption(labelKey, () -> requestRecruit(action));
        }
    }

    private boolean canOfferHiredRole(HiredVillagerRole role) {
        return this.availableHiredRoles.contains(role);
    }

    private boolean isActiveHiredRole(HiredVillagerRole role) {
        return this.activeHiredRole == role;
    }

    private boolean canHireVillager() {
        return this.reputationLevel != null
                && this.reputationLevel.trustRank() >= VillagerReputationLevel.NEUTRAL.trustRank();
    }

    private boolean canCommandStayHere() {
        return this.reputationLevel != null
                && this.reputationLevel.trustRank() >= VillagerReputationLevel.NEUTRAL.trustRank();
    }

    private void addClipboardMenuOptions() {
        addOption("clipboard.assign_storage", () -> requestClipboardStorage(ClipboardStorageActionPayload.Action.ASSIGN));
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

    private void addDialogueOption(String label, String optionId) {
        if (BLUEPRINT_CHANGE_OPTION_ID.equals(optionId)) {
            this.options.add(DialogueOption.enabled(label, this::openBuilderStructuresPage));
            return;
        }
        if (BLUEPRINT_NEVERMIND_OPTION_ID.equals(optionId)) {
            this.options.add(DialogueOption.enabled(label, this::leaveConversation));
            return;
        }
        this.options.add(DialogueOption.enabled(label, () -> requestDialogue(optionId)));
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

    private void openProfilePage() {
        this.profileRefreshRequested = false;
        requestProfileRefresh();
        openPage(DialoguePage.PROFILE);
    }

    private void openSkillsPage() {
        this.profileRefreshRequested = false;
        clearSelectedSkillDetails();
        this.draggingSkillScrollbar = false;
        requestProfileRefresh();
        openPage(DialoguePage.SKILLS);
    }

    private void openGiftPage() {
        this.selectedInventorySlot = firstGiftableInventorySlot();
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
        openPage(DialoguePage.HIRE);
    }

    private void openContractPage() {
        openPage(DialoguePage.CONTRACT);
    }

    private void openEndContractConfirmationPage() {
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

    private void openLoggingFiltersPage() {
        openPage(DialoguePage.LOGGING_FILTERS);
    }

    private void openAnimalBreedingTargetsPage() {
        openPage(DialoguePage.ANIMAL_BREEDING_TARGETS);
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

    private void openFamilyPage() {
        openPage(DialoguePage.FAMILY);
    }

    private void openRelationshipPage() {
        openPage(DialoguePage.RELATIONSHIPS);
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
        sendToServer(new VillagerProfileRequestPayload(this.villagerEntityId));
    }

    private void requestGift() {
        if (this.selectedInventorySlot < 0) {
            return;
        }
        sendToServer(new VillagerGiftRequestPayload(this.villagerEntityId, this.selectedInventorySlot));
        this.selectedInventorySlot = firstGiftableInventorySlot();
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
            openPage(DialoguePage.WORK);
            return;
        }
        if (this.page == DialoguePage.LOGGING_FILTERS) {
            openPage(DialoguePage.WORK);
            return;
        }
        if (this.page == DialoguePage.ANIMAL_BREEDING_TARGETS) {
            openPage(DialoguePage.WORK);
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
        if (canNavigateBack()) {
            navigateBackPage();
        } else {
            leaveConversation();
        }
    }

    private void leaveConversation() {
        this.minecraft.setScreen(null);
    }

    private void activateSelected() {
        if (this.state.selectedOption() < 0 || this.state.selectedOption() >= this.options.size()) {
            return;
        }
        DialogueOption option = this.options.get(this.state.selectedOption());
        option.action().run();
    }

    private void requestRecruit(VillagerRecruitRequestPayload.Action action) {
        sendToServer(new VillagerRecruitRequestPayload(this.villagerEntityId, action));
        if (action == VillagerRecruitRequestPayload.Action.FOLLOW) {
            this.followingPlayer = true;
            this.stayingHere = false;
        } else if (action == VillagerRecruitRequestPayload.Action.STAY_HERE) {
            this.followingPlayer = false;
            this.stayingHere = true;
        } else if (action == VillagerRecruitRequestPayload.Action.STOP_FOLLOWING) {
            this.followingPlayer = false;
            this.stayingHere = false;
        } else if (action == VillagerRecruitRequestPayload.Action.STOP_BREWING) {
            this.activeBrewingOrder = false;
            openWorkPage();
        } else if (action == VillagerRecruitRequestPayload.Action.STOP_BUILDER_BUILD) {
            this.activeBuilderTask = false;
            openWorkPage();
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

    private void requestAnimalBreedingTarget(String targetId) {
        sendToServer(new HiredAnimalBreedingTargetPayload(this.villagerEntityId, targetId));
        if (targetId == null || targetId.isBlank() || "all".equals(targetId)) {
            this.selectedAnimalBreedingTargets.clear();
        } else if (!this.selectedAnimalBreedingTargets.remove(targetId)) {
            this.selectedAnimalBreedingTargets.add(targetId);
        }
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

    private void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static String checkmarkRowLabel(String label, boolean selected) {
        return (selected ? "\u2713 " : "  ") + label;
    }

    private void openPage(DialoguePage page) {
        DialoguePage previousPage = this.page;
        rememberCurrentPageOptionListPosition();
        this.page = page;
        if (page == DialoguePage.SKILLS && previousPage != DialoguePage.SKILLS) {
            this.experimentalSkillsAnimationStartMillis = Util.getMillis();
            this.experimentalSkillsExitStartMillis = -1L;
        } else if (previousPage == DialoguePage.SKILLS && page != DialoguePage.SKILLS) {
            this.experimentalSkillsExitStartMillis = Util.getMillis();
        }
        rebuildOptions();
        restoreRememberedPageOptionListPosition(page);
    }

    private void clearSelectedSkillDetails() {
        this.selectedSkillDetails = null;
        resetSkillInfoScroll();
    }

    private void resetSkillInfoScroll() {
        this.skillScroll = 0.0F;
        this.targetSkillScroll = 0.0F;
    }

    private void moveSelection(int direction) {
        if (this.options.isEmpty()) {
            return;
        }
        this.state.moveSelectedOption(direction, this.options.size());
        this.keyboardOptionFocusVisible = true;
        ensureSelectedVisible();
    }

    private void updateOptionScroll() {
        this.state.tickOptionScroll(OPTION_SCROLL_LERP);
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
        VillagerInteractionSkillsPage.render(this.skillsPageContext, graphics, mouseX, mouseY);
    }

    private void renderGiftPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        VillagerInteractionGiftPage.render(
                this.giftPageContext,
                graphics,
                mouseX,
                mouseY,
                partialTick,
                optionsLeft(),
                optionWidth(),
                this.width,
                this.height,
                topBackButtonBounds().bottom()
        );
    }

    private void renderInteractionContainer(GuiGraphics graphics) {
        if (!shouldRenderInteractionContainer()) {
            return;
        }

        int left = interactionContainerLeft();
        int top = interactionContainerTop();
        VillagerInteractionTextLayout.Nameplate nameplate = interactionNameplate();
        String displayedName = nameplate.displayName();
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
        renderInteractionVillagerPortrait(graphics, left, top);
        renderInteractionPortraitOrnament(graphics, left, top);
        renderInteractionContainerOverlay(graphics, left, top);
        renderInteractionDialogue(graphics, left, top);
        renderInteractionStats(graphics, left, top);
        renderInteractionContainerOrnament(graphics, left, top);
        renderInteractionNameplate(graphics, left, top, nameplate);
        graphics.drawString(
                this.font,
                displayedName,
                left + INTERACTION_CONTAINER_NAME_X,
                top + INTERACTION_CONTAINER_NAME_Y,
                INTERACTION_NAME_COLOR,
                false
        );
    }

    private VillagerInteractionTextLayout.Nameplate interactionNameplate() {
        return VillagerInteractionTextLayout.nameplate(
                this.font,
                this.villagerName,
                INTERACTION_NAMEPLATE_MAX_NAME_CHARS,
                INTERACTION_NAMEPLATE_TEXTURE_WIDTH,
                INTERACTION_CONTAINER_NAME_X,
                INTERACTION_NAMEPLATE_RIGHT_PADDING
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
        blitNineSlicedTexture(
                graphics,
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_NAMEPLATE_ORNAMENT_TEXTURE,
                plateLeft - INTERACTION_NAMEPLATE_ORNAMENT_MARGIN,
                plateTop - INTERACTION_NAMEPLATE_ORNAMENT_MARGIN,
                width + INTERACTION_NAMEPLATE_ORNAMENT_MARGIN * 2,
                INTERACTION_NAMEPLATE_ORNAMENT_TEXTURE_HEIGHT,
                INTERACTION_NAMEPLATE_ORNAMENT_TEXTURE_WIDTH,
                INTERACTION_NAMEPLATE_ORNAMENT_TEXTURE_HEIGHT,
                INTERACTION_NAMEPLATE_ORNAMENT_SLICE_LEFT,
                INTERACTION_NAMEPLATE_ORNAMENT_SLICE_RIGHT,
                INTERACTION_NAMEPLATE_ORNAMENT_SLICE_TOP,
                INTERACTION_NAMEPLATE_ORNAMENT_SLICE_BOTTOM
        );
    }

    private void renderInteractionContainerOrnament(GuiGraphics graphics, int left, int top) {
        graphics.blit(
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_ORNAMENT_TEXTURE,
                left + (INTERACTION_CONTAINER_WIDTH - INTERACTION_CONTAINER_ORNAMENT_WIDTH) / 2,
                top + INTERACTION_CONTAINER_ORNAMENT_Y,
                0,
                0,
                INTERACTION_CONTAINER_ORNAMENT_WIDTH,
                INTERACTION_CONTAINER_ORNAMENT_HEIGHT,
                INTERACTION_CONTAINER_ORNAMENT_WIDTH,
                INTERACTION_CONTAINER_ORNAMENT_HEIGHT
        );
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

    private void renderInteractionContainerOverlay(GuiGraphics graphics, int left, int top) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, INTERACTION_CONTAINER_OVERLAY_Z);
        try {
            graphics.blit(
                    VillagerRetaliationClientAssets.INTERACTION_CONTAINER_OVERLAY_TEXTURE,
                    left + INTERACTION_CONTAINER_OVERLAY_X,
                    top + INTERACTION_CONTAINER_OVERLAY_Y,
                    0,
                    0,
                    INTERACTION_CONTAINER_OVERLAY_WIDTH,
                    INTERACTION_CONTAINER_OVERLAY_HEIGHT,
                    INTERACTION_CONTAINER_OVERLAY_WIDTH,
                    INTERACTION_CONTAINER_OVERLAY_HEIGHT
            );
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderInteractionPortraitOrnament(GuiGraphics graphics, int left, int top) {
        int portraitLeft = left + INTERACTION_PORTRAIT_LEFT;
        int portraitTop = top + INTERACTION_PORTRAIT_TOP;
        int portraitRight = left + INTERACTION_PORTRAIT_RIGHT;
        int portraitBottom = top + INTERACTION_PORTRAIT_BOTTOM;
        int ornamentLeft = (portraitLeft + portraitRight - INTERACTION_PORTRAIT_ORNAMENT_WIDTH) / 2
                + INTERACTION_PORTRAIT_ORNAMENT_X_OFFSET;
        int ornamentTop = (portraitTop + portraitBottom - INTERACTION_PORTRAIT_ORNAMENT_HEIGHT) / 2
                + INTERACTION_PORTRAIT_ORNAMENT_Y_OFFSET;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, INTERACTION_PORTRAIT_ORNAMENT_Z);
        try {
            graphics.blit(
                    VillagerRetaliationClientAssets.INTERACTION_CONTAINER_PORTRAIT_ORNAMENT_TEXTURE,
                    ornamentLeft,
                    ornamentTop,
                    0,
                    0,
                    INTERACTION_PORTRAIT_ORNAMENT_WIDTH,
                    INTERACTION_PORTRAIT_ORNAMENT_HEIGHT,
                    INTERACTION_PORTRAIT_ORNAMENT_WIDTH,
                    INTERACTION_PORTRAIT_ORNAMENT_HEIGHT
            );
        } finally {
            graphics.pose().popPose();
        }
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
        livingEntity.yBodyRot = 180.0F + mouseYaw * 20.0F;
        livingEntity.setYRot(180.0F + mouseYaw * 40.0F);
        livingEntity.setXRot(-mousePitch * 20.0F);
        livingEntity.yHeadRot = livingEntity.getYRot();
        livingEntity.yHeadRotO = livingEntity.getYRot();

        float scale = livingEntity.getScale();
        graphics.enableScissor(portraitLeft, portraitTop, portraitRight, portraitBottom);
        try {
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
        }
    }

    private boolean shouldRenderInteractionContainer() {
        return this.page != DialoguePage.GIFT
                && this.page != DialoguePage.PROFILE
                && this.page != DialoguePage.SKILLS;
    }

    private void renderInteractionDialogue(GuiGraphics graphics, int left, int top) {
        if (this.villagerDialogueText.isBlank()) {
            return;
        }

        int textLeft = left + INTERACTION_DIALOGUE_LEFT;
        int textTop = top + INTERACTION_DIALOGUE_TOP;
        int textRight = left + INTERACTION_DIALOGUE_RIGHT;
        int textBottom = top + INTERACTION_DIALOGUE_BOTTOM;
        int lineStep = this.font.lineHeight;
        int maxLines = Math.max(1, (textBottom - textTop) / lineStep);
        String displayedDialogue = displayedDialogueText();
        if (displayedDialogue.isBlank()) {
            return;
        }
        maybePlayDialogueBlip();
        List<FormattedCharSequence> lines = this.font.split(
                Component.literal(displayedDialogue),
                INTERACTION_DIALOGUE_RIGHT - INTERACTION_DIALOGUE_LEFT
        );
        graphics.enableScissor(textLeft, textTop, textRight, textBottom);
        for (int index = 0; index < Math.min(maxLines, lines.size()); index++) {
            graphics.drawString(
                    this.font,
                    lines.get(index),
                    textLeft,
                    textTop + index * lineStep,
                    INTERACTION_DIALOGUE_COLOR,
                    false
            );
        }
        graphics.disableScissor();
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

    private boolean trySkipDialogueTextAnimation(double mouseX, double mouseY) {
        if (isDialogueTextAnimationComplete() || !isPointInsideInteractionDialogue(mouseX, mouseY)) {
            return false;
        }
        this.dialogueTextAnimationSkipped = true;
        return true;
    }

    private boolean isPointInsideInteractionDialogue(double mouseX, double mouseY) {
        if (!shouldRenderInteractionContainer() || this.villagerDialogueText.isBlank()) {
            return false;
        }

        int left = interactionContainerLeft();
        int top = interactionContainerTop();
        return mouseX >= left + INTERACTION_DIALOGUE_LEFT
                && mouseX < left + INTERACTION_DIALOGUE_RIGHT
                && mouseY >= top + INTERACTION_DIALOGUE_TOP
                && mouseY < top + INTERACTION_DIALOGUE_BOTTOM;
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

    private void renderInteractionStats(GuiGraphics graphics, int left, int top) {
        InteractionStatLayout stats = interactionStatLayout(left, top);
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

    private void drawOutlinedString(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(this.font, text, x - 1, y, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x + 1, y, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x, y - 1, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x, y + 1, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x, y, color, false);
    }

    private void renderInteractionStatTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!shouldRenderInteractionContainer()) {
            return;
        }

        InteractionStatLayout stats = interactionStatLayout(interactionContainerLeft(), interactionContainerTop());
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
        graphics.renderTooltip(this.font, lines, DefaultTooltipPositioner.INSTANCE, mouseX, mouseY);
    }

    private InteractionStatLayout interactionStatLayout(int left, int top) {
        int textTop = top + INTERACTION_STATS_BASELINE_Y - this.font.lineHeight - INTERACTION_STATS_TEXT_RAISE;
        int iconTop = top + INTERACTION_STATS_BASELINE_Y - INTERACTION_ICON_SIZE;
        String currencyText = walletText();
        String reputationText = Integer.toString(this.reputation);

        int currencyTextRight = left + INTERACTION_STATS_ANCHOR_X;
        int currencyTextLeft = currencyTextRight - this.font.width(currencyText);
        int currencyIconLeft = currencyTextLeft - INTERACTION_ICON_TEXT_GAP - INTERACTION_ICON_SIZE;
        int reputationTextRight = currencyIconLeft - INTERACTION_ICON_TEXT_GAP;
        int reputationTextLeft = reputationTextRight - this.font.width(reputationText);
        int reputationIconLeft = reputationTextLeft - INTERACTION_ICON_TEXT_GAP - INTERACTION_ICON_SIZE;
        int hitTop = Math.min(textTop, iconTop);
        int hitBottom = Math.max(textTop + this.font.lineHeight, iconTop + INTERACTION_ICON_SIZE);
        return new InteractionStatLayout(
                textTop,
                iconTop,
                currencyText,
                reputationText,
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

    private int interactionContainerLeft() {
        return (this.width - INTERACTION_CONTAINER_WIDTH) / 2;
    }

    private int interactionContainerTop() {
        return Math.max(4, this.height - INTERACTION_CONTAINER_HEIGHT - INTERACTION_CONTAINER_HOTBAR_GAP);
    }

    private void renderTopBackButton(GuiGraphics graphics, int mouseX, int mouseY) {
        VillagerInteractionNavigationChrome.renderTopBackButton(this.navigationChromeContext, graphics, mouseX, mouseY);
    }

    private void renderHint(GuiGraphics graphics) {
        VillagerInteractionNavigationChrome.renderHint(this.navigationChromeContext, graphics);
    }

    void renderPositionedHudChat(GuiGraphics graphics) {
        if (experimentalSkillsBackdropVisible()) {
            VillagerClientUiUtil.pushGuiLayer(graphics, VillagerClientUiUtil.hudLayerZ());
            renderExperimentalSkillsBackdrop(graphics);
            VillagerClientUiUtil.popGuiLayer(graphics);
        }
    }

    private void renderExperimentalSkillsBackdrop(GuiGraphics graphics) {
        int left = Math.max(0, skillsPanelLeft() - skillsContainerPaddingX() - experimentalUnit(118));
        int top = Math.max(0, skillsPanelTop() - experimentalUnit(26));
        int right = Math.min(this.width, skillsPanelLeft() + skillsPanelWidth() + experimentalUnit(46));
        int bottom = Math.min(this.height, skillsPanelTop() + skillsContainerHeight() + experimentalUnit(74));
        VillagerInteractionScreenShaderRenderer.renderExperimentalSkillsPanel(
                graphics,
                left,
                top,
                right,
                bottom,
                VillagerInteractionExperimentalChrome.chromeAlpha(),
                (Util.getMillis() % 1_000_000L) / 50.0F,
                experimentalSkillsElapsedMillis(),
                experimentalSkillsExitElapsedMillis(),
                VillagerInteractionExperimentalChrome.backdropElapsedMillis(),
                VillagerInteractionExperimentalChrome.backdropExitElapsedMillis(),
                this.width,
                this.height,
                this.lastMouseX,
                this.lastMouseY,
                this.professionUiColors,
                true);
    }

    private boolean experimentalSkillsBackdropVisible() {
        if (this.page == DialoguePage.SKILLS) {
            return true;
        }
        return this.experimentalSkillsExitStartMillis >= 0L && Util.getMillis() - this.experimentalSkillsExitStartMillis < 860L;
    }

    private float experimentalSkillsElapsedMillis() {
        long now = Util.getMillis();
        if (this.experimentalSkillsAnimationStartMillis < 0L) {
            this.experimentalSkillsAnimationStartMillis = now;
        }
        return now - this.experimentalSkillsAnimationStartMillis;
    }

    private float experimentalSkillsExitElapsedMillis() {
        float chromeExitElapsedMillis = VillagerInteractionExperimentalChrome.backdropExitElapsedMillis();
        if (chromeExitElapsedMillis >= 0.0F) {
            return chromeExitElapsedMillis;
        }
        return this.experimentalSkillsExitStartMillis < 0L ? -1.0F : Util.getMillis() - this.experimentalSkillsExitStartMillis;
    }

    private boolean isFamilyPageActive() {
        return this.page == DialoguePage.FAMILY
                || this.page == DialoguePage.ANCESTRY
                || this.page == DialoguePage.DESCENDANTS;
    }

    private void updateMouseSelection(int mouseX, int mouseY) {
        int hovered = VillagerInteractionOptionList.optionAt(this.optionListContext, mouseX, mouseY);
        if (hovered >= 0) {
            this.state.setSelectedOption(hovered);
            this.keyboardOptionFocusVisible = false;
        }
    }

    private boolean tryClickBackButton(double mouseX, double mouseY) {
        if (!isTopBackButtonVisible() || !isPointInsideTopBackButton(mouseX, mouseY)) {
            return false;
        }

        if (this.page == DialoguePage.SKILLS && this.selectedSkillDetails != null) {
            clearSelectedSkillDetails();
            return true;
        }

        navigateBackPage();
        return true;
    }

    private boolean tryBeginScrollbarDrag(double mouseX, double mouseY) {
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

    private boolean trySelectSkillDetails(double mouseX, double mouseY) {
        if (this.page != DialoguePage.SKILLS) {
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
                optionsLeft(),
                optionWidth(),
                this.width,
                this.height,
                topBackButtonBounds().bottom());
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
        return VillagerInteractionGiftPage.giftInventoryLeft(optionsLeft(), optionWidth(), this.width);
    }

    private int giftInventoryTop() {
        return VillagerInteractionGiftPage.giftInventoryTop(topBackButtonBounds().bottom(), this.height);
    }

    private boolean isPointInsideOptionScrollArea(double mouseX, double mouseY) {
        int left = optionsLeft() - experimentalUnit(18);
        int right = optionsLeft() + optionWidth() + experimentalUnit(4);
        int top = optionsTop();
        int bottom = top + optionViewportHeight();
        int verticalPadding = experimentalUnit(4);
        return mouseX >= left && mouseX <= right && mouseY >= top - verticalPadding && mouseY <= bottom + verticalPadding;
    }

    private boolean isPointInsideSkillsInfoScrollArea(double mouseX, double mouseY) {
        int left = optionsLeft() - experimentalUnit(18);
        int right = optionsLeft() + optionWidth() + experimentalUnit(4);
        int top = skillInfoViewportTop();
        int bottom = skillInfoViewportBottom();
        int verticalPadding = experimentalUnit(4);
        return mouseX >= left && mouseX <= right && mouseY >= top - verticalPadding && mouseY <= bottom + verticalPadding;
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
        int verticalPadding = experimentalUnitAtLeast(2, 1);
        return VillagerClientUiUtil.containsInclusive(mouseX, mouseY, bounds.left(), bounds.top() - verticalPadding, bounds.right(), bounds.bottom() + verticalPadding);
    }

    private TopBackButtonBounds topBackButtonBounds() {
        float textScale = experimentalScaleFactor();
        int textWidth = Math.round(this.font.width(backLabel()) * textScale);
        int textHeight = Math.round(this.font.lineHeight * textScale);
        int left = experimentalOptionTextLeft();
        int contentTop = experimentalOptionViewportTop();
        int top = contentTop - textHeight - topBackButtonGap();
        int bottom = top + textHeight;
        return new TopBackButtonBounds(left, left + textWidth, top, bottom);
    }

    private int skillsPanelTop() {
        int containerHeight = skillsContainerHeight();
        return VillagerInteractionLayoutMetrics.skillsPanelTop(this.height, containerHeight);
    }

    private int skillsPanelLeft() {
        int panelWidth = skillsPanelWidth();
        int targetLeft = scrollbarRight() - panelWidth;
        return VillagerInteractionLayoutMetrics.skillsPanelLeft(this.width, panelWidth, targetLeft);
    }

    private int skillsPanelWidth() {
        return optionWidth();
    }

    private int skillsContainerHeight() {
        return VillagerInteractionLayoutMetrics.skillsContainerHeight(skillsPanelHeight());
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
            return experimentalOptionViewportTop();
        }
        return conversationInfoTop();
    }

    private int skillInfoViewportBottom() {
        if (this.page == DialoguePage.SKILLS) {
            return experimentalOptionViewportBottom();
        }
        return conversationInfoTop() + rootOptionViewportHeight();
    }

    private int skillInfoViewportHeight() {
        return Math.max(1, skillInfoViewportBottom() - skillInfoViewportTop());
    }

    private int optionsTop() {
        if (usesInteractionOptionStack()) {
            return interactionContainerTop();
        }
        return experimentalOptionsTop(optionViewportHeight());
    }

    private int experimentalOptionViewportTop() {
        return experimentalOptionsTop(experimentalFullOptionViewportHeight());
    }

    private int experimentalOptionViewportBottom() {
        return experimentalOptionViewportTop() + experimentalFullOptionViewportHeight();
    }

    private int experimentalOptionTextLeft() {
        return experimentalOptionsLeft() + optionTextInset();
    }

    private int experimentalOptionsTop(int viewportHeight) {
        return VillagerInteractionExperimentalLayout.optionsTop(this.height, viewportHeight);
    }

    private int conversationInfoTop() {
        return focusCenterY() - rootOptionViewportHeight() / 2;
    }

    private int optionsLeft() {
        if (usesInteractionOptionStack()) {
            return interactionOptionStackLeft();
        }
        return experimentalOptionsLeft();
    }

    private int contentLeft() {
        if (this.page == DialoguePage.PROFILE || this.page == DialoguePage.SKILLS) {
            return experimentalPageLeft();
        }
        return optionsLeft();
    }

    private int experimentalOptionsLeft() {
        return VillagerInteractionExperimentalLayout.optionsLeft(this.width, optionWidth());
    }

    private int experimentalPageLeft() {
        return VillagerInteractionExperimentalLayout.pageLeft(this.width, optionWidth());
    }

    private String reputationText() {
        return translate("info.reputation", this.reputation);
    }

    private String walletText() {
        return translate("info.wallet", this.walletCurrencyLabel, this.walletEmeralds, this.maxWalletEmeralds);
    }

    private String walletTooltipTitle() {
        return translate("info.wallet.tooltip.title", this.walletCurrencyLabel);
    }

    private String walletTooltipBody() {
        return translate("info.wallet.tooltip.body", this.walletCurrencyPluralName);
    }

    private String localizedReputationLevelName() {
        if (this.reputationLevel == null) {
            return "Unknown";
        }
        String key = "villagerretaliation.reputation.level." + this.reputationLevel.name().toLowerCase(Locale.ROOT);
        return I18n.exists(key) ? I18n.get(key) : this.reputationLevel.name();
    }

    private String familyButtonText() {
        int count = this.familyTree.relationshipCount();
        return count <= 0 ? translate("family.tree") : translate("family.tree_count", count);
    }

    private boolean canRequestVillagerInventory() {
        return this.reputationLevel.trustRank() >= VillagerReputationLevel.REVERED.trustRank();
    }

    private ItemStack clipboardStack() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack mainHand = minecraft.player.getMainHandItem();
        if (VillagerRetaliationItems.isClipboard(mainHand)) {
            return mainHand;
        }
        ItemStack offhand = minecraft.player.getOffhandItem();
        return VillagerRetaliationItems.isClipboard(offhand) ? offhand : ItemStack.EMPTY;
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

    private float experimentalScaleFactor() {
        return VillagerInteractionExperimentalLayout.scaleFactor();
    }

    private int experimentalUnit(int guiScaleThreeValue) {
        return VillagerInteractionExperimentalLayout.unit(guiScaleThreeValue);
    }

    private int experimentalUnitAtLeast(int guiScaleThreeValue, int minimum) {
        return VillagerInteractionExperimentalLayout.unitAtLeast(guiScaleThreeValue, minimum);
    }

    private int optionViewportHeight() {
        if (usesInteractionOptionStack()) {
            return INTERACTION_CONTAINER_HEIGHT;
        }
        return VillagerInteractionLayoutMetrics.optionViewportHeight(this.options.size());
    }

    private int experimentalFullOptionViewportHeight() {
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

    private int interactionOptionStackWidth() {
        int desiredWidth = INTERACTION_OPTION_WIDTH;
        for (int index = 0; index < this.options.size(); index++) {
            for (String line : VillagerInteractionOptionList.pixelOptionLabelLines(this.optionListContext, index)) {
                desiredWidth = Math.max(
                        desiredWidth,
                        this.font.width(line)
                                + INTERACTION_OPTION_TEXT_INSET
                                + INTERACTION_OPTION_TEXT_RIGHT_PADDING);
            }
        }
        int maxAvailableWidth = Math.max(INTERACTION_OPTION_WIDTH, this.width - interactionOptionStackLeft() - 4);
        return Math.min(desiredWidth, maxAvailableWidth);
    }

    private String interactionOptionLabel(int index) {
        if (index < 0 || index >= this.options.size()) {
            return "";
        }
        return (index + 1) + ". " + this.options.get(index).label();
    }

    private int interactionOptionStackLeft() {
        return interactionContainerLeft() + INTERACTION_CONTAINER_WIDTH + INTERACTION_OPTION_CONTAINER_GAP;
    }

    private void ensureSelectedVisible() {
        if (this.state.selectedOption() < 0 || this.state.selectedOption() >= this.options.size()) {
            return;
        }

        float optionTop = VillagerInteractionOptionList.optionOffset(this.optionListContext, this.state.selectedOption());
        float optionBottom = optionTop + VillagerInteractionOptionList.optionHeight(this.optionListContext, this.state.selectedOption());
        float viewportTop = this.state.targetOptionScroll();
        float viewportBottom = viewportTop + optionViewportHeight();
        int padding = 6;
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
        return VillagerInteractionExperimentalLayout.scrollbarLeft(
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

    private enum DialoguePage {
        ROOT,
        TALK,
        PROFILE,
        SKILLS,
        GIFT,
        FAMILY,
        ANCESTRY,
        DESCENDANTS,
        RELATIONSHIPS,
        RECRUIT,
        STORAGE,
        PAYMENT,
        HIRE,
        CONTRACT,
        END_CONTRACT_CONFIRMATION,
        CONTRACT_EXTENSION,
        ROLE,
        ROLE_CHANGE,
        WORK,
        LOGGING_FILTERS,
        ANIMAL_BREEDING_TARGETS,
        BUILDER_STRUCTURES,
        BUILDER_STRUCTURE_CATEGORY,
        BUILDER_CONFIRM,
        BREWING_POTION,
        BREWING_LEVEL,
        BREWING_DURATION,
        BREWING_TYPE,
        BREWING_AMOUNT
    }

    private record DialogueOption(String label, Runnable action) {
        static DialogueOption enabled(String label, Runnable action) {
            return new DialogueOption(label, action);
        }
    }

    private record TopBackButtonBounds(int left, int right, int top, int bottom) {
    }

    private record InteractionStatLayout(
            int textTop,
            int iconTop,
            String currencyText,
            String reputationText,
            int currencyTextLeft,
            int currencyTextRight,
            int currencyIconLeft,
            int reputationTextLeft,
            int reputationTextRight,
            int reputationIconLeft,
            int hitTop,
            int hitBottom) {
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
    }

    private final class OptionListContext implements VillagerInteractionOptionList.Context {
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
        public float experimentalTextScale() {
            return VillagerInteractionScreen.this.experimentalScaleFactor();
        }

        @Override
        public int experimentalUnit(int value) {
            return VillagerInteractionScreen.this.experimentalUnit(value);
        }

        @Override
        public int experimentalUnitAtLeast(int value, int minimum) {
            return VillagerInteractionScreen.this.experimentalUnitAtLeast(value, minimum);
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
        public boolean usePixelOptionButtons() {
            return VillagerInteractionScreen.this.usesInteractionOptionStack();
        }

        @Override
        public ResourceLocation pixelOptionTexture(boolean selected, boolean hovered) {
            return selected || hovered
                    ? VillagerRetaliationClientAssets.INTERACTION_OPTION_HOVER_TEXTURE
                    : VillagerRetaliationClientAssets.INTERACTION_OPTION_BUTTON_TEXTURE;
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
        public ResourceLocation pixelOptionArrowUpTexture() {
            return VillagerRetaliationClientAssets.INTERACTION_OPTION_ARROW_UP_TEXTURE;
        }

        @Override
        public ResourceLocation pixelOptionArrowDownTexture() {
            return VillagerRetaliationClientAssets.INTERACTION_OPTION_ARROW_DOWN_TEXTURE;
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
            return VillagerRetaliationClientAssets.INTERACTION_OPTION_SELECTION_ARROW_HOVER_TEXTURE;
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

    private final class NavigationChromeContext implements VillagerInteractionNavigationChrome.Context {
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
            return VillagerInteractionScreen.this.width - VillagerInteractionScreen.this.experimentalUnit(8);
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
            return VillagerInteractionScreen.this.experimentalScaleFactor();
        }

        @Override
        public float chromeAlpha() {
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
            return VillagerInteractionScreen.this.experimentalOptionTextLeft() - VillagerInteractionScreen.this.experimentalUnit(6);
        }

        @Override
        public int conversationInfoTop() {
            return VillagerInteractionScreen.this.conversationInfoTop();
        }

        @Override
        public int optionWidth() {
            return VillagerInteractionScreen.this.optionWidth();
        }

        @Override
        public int infoSecondaryColor() {
            return INFO_SECONDARY_COLOR;
        }

        @Override
        public int profileChartRadius() {
            return VillagerInteractionScreen.this.experimentalUnit(PROFILE_CHART_RADIUS);
        }

        @Override
        public int profileChartCenterXOffset() {
            return VillagerInteractionScreen.this.experimentalUnit(8);
        }

        @Override
        public int profileChartCenterYOffset() {
            return VillagerInteractionScreen.this.experimentalUnit(16);
        }

        @Override
        public int profileChartLabelXOffset() {
            return VillagerInteractionScreen.this.experimentalUnit(18);
        }

        @Override
        public int profileChartLabelYOffset() {
            return VillagerInteractionScreen.this.experimentalUnit(14);
        }

        @Override
        public int profileChartLoadingYOffset() {
            return VillagerInteractionScreen.this.experimentalUnit(32);
        }

        @Override
        public int profileChartTopLimit() {
            return VillagerInteractionScreen.this.topBackButtonBounds().bottom() + VillagerInteractionScreen.this.experimentalUnit(7);
        }

        @Override
        public int profileChartBottomLimit() {
            int hintHeight = Math.round(VillagerInteractionScreen.this.font.lineHeight * VillagerInteractionScreen.this.experimentalScaleFactor());
            int screenBottomLimit = VillagerInteractionScreen.this.height - hintHeight - VillagerInteractionScreen.this.experimentalUnit(8);
            int viewportBottomLimit = VillagerInteractionScreen.this.conversationInfoTop()
                    + VillagerInteractionScreen.this.rootOptionViewportHeight()
                    - VillagerInteractionScreen.this.experimentalUnit(4);
            return Math.min(screenBottomLimit, viewportBottomLimit);
        }

        @Override
        public float profileChartTextScale() {
            return VillagerInteractionScreen.this.experimentalScaleFactor();
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
            return VillagerInteractionScreen.this.experimentalUnitAtLeast(1, 1);
        }

        @Override
        public int profileChartPointHoverRadius() {
            return VillagerInteractionScreen.this.experimentalUnitAtLeast(2, 1);
        }

        @Override
        public int profileChartPointHitRadius() {
            return VillagerInteractionScreen.this.experimentalUnitAtLeast(PROFILE_CHART_POINT_HIT_RADIUS, 2);
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
            return VillagerInteractionScreen.this.experimentalOptionTextLeft();
        }

        @Override
        public int skillInfoScissorLeft() {
            return Math.max(0, VillagerInteractionScreen.this.optionsLeft() - VillagerInteractionScreen.this.optionWidth());
        }

        @Override
        public int skillInfoScissorRight() {
            return VillagerInteractionScreen.this.optionsScrollbarLeft() - VillagerInteractionScreen.this.experimentalUnit(4);
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
        public float experimentalChromeAlpha() {
            return VillagerInteractionExperimentalChrome.chromeAlpha();
        }

        @Override
        public int experimentalUnit(int value) {
            return VillagerInteractionScreen.this.experimentalUnit(value);
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
        public float experimentalTextScale() {
            return VillagerInteractionScreen.this.experimentalScaleFactor();
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
}
