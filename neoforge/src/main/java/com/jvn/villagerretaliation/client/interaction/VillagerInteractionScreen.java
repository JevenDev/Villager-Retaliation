package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanScrollbarThumb;
import com.jvn.toucanlib.client.ToucanScrollState;
import com.jvn.toucanlib.client.ToucanScrollbars;
import com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache;
import com.jvn.villagerretaliation.client.reputation.VillagerReputationIconSet;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.config.InteractionChatPosition;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueTreeService;
import com.jvn.villagerretaliation.network.VillagerConversationEndRequestPayload;
import com.jvn.villagerretaliation.network.VillagerDialogueRequestPayload;
import com.jvn.villagerretaliation.network.VillagerGiftRequestPayload;
import com.jvn.villagerretaliation.network.VillagerInventoryRequestPayload;
import com.jvn.villagerretaliation.network.VillagerProfileRequestPayload;
import com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload;
import com.jvn.villagerretaliation.network.VillagerTradeRequestPayload;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class VillagerInteractionScreen extends Screen implements VillagerInteractionSessionScreen {
    private static final String GUI_KEY_PREFIX = "villagerretaliation.gui.";
    private static final String BACK_LABEL_KEY = GUI_KEY_PREFIX + "back";
    private static final String FORCED_LEAVE_OPTION_ID = "leave";
    private static final String DIALOGUE_TREE_LEAVE_OPTION_ID = DialogueTreeService.LEAVE_OPTION_ID;
    private static final int OPTION_HEIGHT = 18;
    private static final int INFO_PANEL_CHAT_PADDING = 20;
    private static final int VEIL_DITHER_START_OFFSET = OPTION_HEIGHT - 81;
    private static final float EXPERIMENTAL_INFO_NAME_SCALE = 1.85F;
    private static final float EXPERIMENTAL_INFO_DETAIL_SCALE = 1.4F;
    private static final int CHAT_EDGE_MARGIN = 4;
    private static final int CHAT_TOP_MARGIN = 12;
    private static final int CHAT_INPUT_AND_GAP_HEIGHT = 38;
    private static final int CHAT_EXTRA_WIDTH = 8;
    private static final float OPTION_SCROLL_LERP = 0.32F;
    private static final float OPTION_SCROLL_STEP = 12.0F;
    private static final float OPTION_HOVER_SCALE = 0.055F;
    private static final float OPTION_SELECTED_SCALE = 0.02F;
    private static final int INFO_LABEL_COLOR = 0x96E8E4DA;
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
    private final boolean forcedDialogue;
    private boolean forceCameraTowardsVillager;
    private final List<DialogueOption> options = new ArrayList<>();
    private final List<DialogueOptionDefinition> dialogueOptions = new ArrayList<>();
    private final List<String> knownLikedGiftNames = new ArrayList<>();
    private final List<String> knownDislikedGiftNames = new ArrayList<>();
    private final VillagerFamilyTreeSnapshot familyTree;
    private final VillagerRelationshipSnapshot relationships;
    private final VillagerInteractionScreenState state = new VillagerInteractionScreenState();
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
    private int selectedInventorySlot = -1;
    private int lastMouseX;
    private int lastMouseY;
    private long experimentalSkillsAnimationStartMillis = -1L;
    private long experimentalSkillsExitStartMillis = -1L;
    private Button giftButton;
    private Double originalChatWidth;
    private final GiftPageContext giftPageContext = new GiftPageContext();
    private final OptionListContext optionListContext = new OptionListContext();
    private final NavigationChromeContext navigationChromeContext = new NavigationChromeContext();
    private final ExperimentalChromeContext experimentalChromeContext = new ExperimentalChromeContext();
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
            boolean forcedDialogue,
            boolean forceCameraTowardsVillager,
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
        this.forcedDialogue = forcedDialogue;
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
        applyChatWidthOverride();
    }

    @Override
    public void tick() {
        syncCameraFocusState();
        ClientVillagerConversationState.tickCameraFocus();
        applyChatWidthOverride();
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
        restoreChatWidthOverride();
    }

    public void closeFromServer() {
        this.closingFromServer = true;
        if (Minecraft.getInstance().screen != this) {
            restoreChatWidthOverride();
            VillagerInteractionChatVisibility.restoreHiddenVillagerMessages(Minecraft.getInstance());
            ClientVillagerConversationState.clear();
        }
        Minecraft.getInstance().setScreen(null);
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
        VillagerInteractionExperimentalChrome.renderFocus(this.experimentalChromeContext, graphics, mouseX, mouseY);
        renderTopBackButton(graphics, mouseX, mouseY);
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
        renderHint(graphics);
        VillagerInteractionExperimentalChrome.renderNameTooltip(this.experimentalChromeContext, graphics, mouseX, mouseY);
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

        if (tryClickBackButton(mouseX, mouseY)
                || trySelectSkillDetails(mouseX, mouseY)
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

        restoreChatWidthOverride();
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
        } else if (this.page == DialoguePage.ROOT) {
            if (this.forcedDialogue) {
                addDialogueOptions();
            } else {
                addRootOptions();
            }
        }
        this.state.resetOptions(!this.options.isEmpty());
        ensureSelectedVisible();
    }

    private void rebuildOptionsKeepingListPosition() {
        VillagerInteractionScreenState.OptionListPosition previousPosition = this.state.captureOptionListPosition();

        rebuildOptions();

        this.state.restoreOptionListPosition(previousPosition, this.options.size(), maxOptionScroll());
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
            addOption("root.recruit", this::openRecruitPage);
            if (this.relationships.hasRelationships()) {
                addOption("root.relationships", this::openRelationshipPage);
            }
        }
        addOption("root.goodbye", this::leaveConversation);
    }

    private void addProfileOptions() {
    }

    private void addSkillsOptions() {
    }

    private void addRecruitOptions() {
        addOption("recruit.hire", () -> requestRecruit(VillagerRecruitRequestPayload.Action.HIRE));
        this.options.add(DialogueOption.enabled(
                this.followingPlayer ? translate("recruit.stop_following") : translate("recruit.follow_me"),
                () -> requestRecruit(VillagerRecruitRequestPayload.Action.FOLLOW)));
    }

    private void addFamilyOptions() {
        if (this.familyTree.hasAncestry()) {
            this.options.add(DialogueOption.enabled(translate("family.ancestry"), this::openAncestryPage));
        }
        if (this.familyTree.hasDescendants()) {
            this.options.add(DialogueOption.enabled(translate("family.descendants"), this::openDescendantsPage));
        }
        addFamilyRows("family.father", this.familyTree.maleParents());
        addFamilyRows("family.mother", this.familyTree.femaleParents());
        addFamilyRows("family.birth_father", this.familyTree.maleBirthParents());
        addFamilyRows("family.birth_mother", this.familyTree.femaleBirthParents());
        addFamilyRows("family.adoptive_father", this.familyTree.maleAdoptiveParents());
        addFamilyRows("family.adoptive_mother", this.familyTree.femaleAdoptiveParents());
        addFamilyRows("family.step_father", this.familyTree.maleStepParents());
        addFamilyRows("family.step_mother", this.familyTree.femaleStepParents());
        addFamilyRows("family.brother", this.familyTree.brothers());
        addFamilyRows("family.sister", this.familyTree.sisters());
        addFamilyRows("family.uncle", this.familyTree.uncles());
        addFamilyRows("family.aunt", this.familyTree.aunts());
        addFamilyRows("family.nephew", this.familyTree.nephews());
        addFamilyRows("family.niece", this.familyTree.nieces());
        addGenderedFamilyRows("family.male_cousin", "family.female_cousin", this.familyTree.cousins());
        addGenderedFamilyRows("family.husband", "family.wife", this.familyTree.spouses());
        addGenderedFamilyRows("family.son", "family.daughter", this.familyTree.children());
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
            List<VillagerFamilyTreeSnapshot.FamilyMember> members
    ) {
        addFamilyRows(maleLabel, VillagerFamilyTreeSnapshot.membersByGender(members, VillagerGender.MALE));
        addFamilyRows(femaleLabel, VillagerFamilyTreeSnapshot.membersByGender(members, VillagerGender.FEMALE));
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
        VillagerInteractionExperimentalChrome.startExitAnimation(
                buildExperimentalExitTextElements(),
                buildExperimentalExitFadeTextElements(),
                buildExperimentalExitFadeRectElements());
        this.minecraft.setScreen(null);
    }

    private List<VillagerInteractionExperimentalChrome.ExitTextElement> buildExperimentalExitTextElements() {
        List<VillagerInteractionExperimentalChrome.ExitTextElement> textElements = new ArrayList<>();
        float textScale = experimentalScaleFactor();
        float nameScale = 1.85F * textScale;
        float detailScale = 1.4F * textScale;
        int lineGap = experimentalUnit(5);
        int reputationY = experimentalInfoBottom() - Math.round(this.font.lineHeight * detailScale);
        int professionY = reputationY - lineGap - Math.round(this.font.lineHeight * detailScale);
        int nameY = professionY - lineGap - Math.round(this.font.lineHeight * nameScale);
        int right = experimentalInfoRight();
        textElements.add(new VillagerInteractionExperimentalChrome.ExitTextElement(
                this.villagerName, right, nameY, moodColor(this.primaryMood), nameScale, 0.0F, -18.0F, this.height + 96.0F, true));
        textElements.add(new VillagerInteractionExperimentalChrome.ExitTextElement(
                this.professionName, right, professionY, INFO_SECONDARY_COLOR, detailScale, 45.0F, -10.0F, this.height + 82.0F, true));
        textElements.add(new VillagerInteractionExperimentalChrome.ExitTextElement(
                reputationText(), right, reputationY, reputationColor(), detailScale, 90.0F, -6.0F, this.height + 74.0F, true));

        int optionLeft = experimentalOptionsLeft();
        int textLeft = optionLeft + optionTextInset();
        int top = optionsTop();
        int viewportTop = top;
        int viewportBottom = top + optionViewportHeight();
        for (int index = 0; index < this.options.size(); index++) {
            float y = top + VillagerInteractionOptionList.optionOffset(this.optionListContext, index) - this.state.optionScroll();
            if (y + VillagerInteractionOptionList.optionHeight(this.optionListContext, index) < viewportTop || y > viewportBottom) {
                continue;
            }

            boolean selected = index == this.state.selectedOption();
            int color = selected ? 0xFFF8F8F4 : 0xCFC7C8C5;
            float scale = (1.48F + (selected ? OPTION_SELECTED_SCALE : 0.0F)) * textScale;
            float delay = 120.0F + index * 28.0F;
            int textY = Mth.floor(y + optionTextYOffset());
            if (selected) {
                int selectorX = textLeft - experimentalUnit(17);
                int selectorY = Math.round(y + VillagerInteractionOptionList.optionHeight(this.optionListContext, index) * 0.5F
                        - this.font.lineHeight * scale * 0.5F) + 3;
                textElements.add(new VillagerInteractionExperimentalChrome.ExitTextElement(
                        ">", selectorX, selectorY, 0xFFFFFFFF, scale, delay, 0.0F, this.height - selectorY + 72.0F, false));
            }
            List<String> labelLines = VillagerInteractionOptionList.wrappedOptionLabelLines(this.optionListContext, this.options.get(index).label(), scale);
            for (int lineIndex = 0; lineIndex < labelLines.size(); lineIndex++) {
                int lineY = textY + lineIndex * optionHeight();
                textElements.add(new VillagerInteractionExperimentalChrome.ExitTextElement(
                        labelLines.get(lineIndex),
                        textLeft,
                        lineY,
                        color,
                        scale,
                        delay + 24.0F,
                        0.0F,
                        this.height - lineY + 88.0F,
                        false));
            }
        }
        return textElements;
    }

    private List<VillagerInteractionExperimentalChrome.ExitFadeTextElement> buildExperimentalExitFadeTextElements() {
        List<VillagerInteractionExperimentalChrome.ExitFadeTextElement> textElements = new ArrayList<>();
        String hintText = translate(canNavigateBack() ? "hint.back" : "hint.leave");
        float scale = experimentalScaleFactor();
        int width = Math.round(this.font.width(hintText) * scale);
        int height = Math.round(this.font.lineHeight * scale);
        textElements.add(new VillagerInteractionExperimentalChrome.ExitFadeTextElement(
                hintText,
                this.width - width - experimentalUnit(8),
                this.height - height - experimentalUnit(5),
                0x66FFFFFF,
                scale));
        return textElements;
    }

    private List<VillagerInteractionExperimentalChrome.ExitFadeRectElement> buildExperimentalExitFadeRectElements() {
        List<VillagerInteractionExperimentalChrome.ExitFadeRectElement> rectElements = new ArrayList<>();
        addScrollbarExitFadeRects(rectElements, scrollbarThumb(), this.state.optionScroll(), maxOptionScroll());
        if (this.page == DialoguePage.SKILLS) {
            addScrollbarExitFadeRects(rectElements, skillInfoScrollbarThumb(), this.skillScroll, maxSkillScroll());
        }
        return rectElements;
    }

    private void addScrollbarExitFadeRects(
            List<VillagerInteractionExperimentalChrome.ExitFadeRectElement> rectElements,
            ToucanScrollbarThumb scrollbarThumb,
            float currentScroll,
            float maxScroll) {
        if (scrollbarThumb == null) {
            return;
        }

        boolean canScrollUp = currentScroll > 0.75F;
        boolean canScrollDown = currentScroll < maxScroll - 0.75F;
        int fadeLength = Math.min(8, Math.max(3, scrollbarThumb.height() / 3));
        for (int y = scrollbarThumb.top(); y < scrollbarThumb.bottom(); y++) {
            float alphaFactor = 1.0F;
            if (canScrollUp && y < scrollbarThumb.top() + fadeLength) {
                alphaFactor = Math.min(alphaFactor, (y - scrollbarThumb.top() + 1.0F) / fadeLength);
            }
            if (canScrollDown && y >= scrollbarThumb.bottom() - fadeLength) {
                alphaFactor = Math.min(alphaFactor, (scrollbarThumb.bottom() - y) / (float) fadeLength);
            }
            rectElements.add(new VillagerInteractionExperimentalChrome.ExitFadeRectElement(
                    scrollbarThumb.left(),
                    y,
                    scrollbarThumb.right(),
                    y + 1,
                    0xBFFFFFFF,
                    alphaFactor));
        }
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
            this.followingPlayer = !this.followingPlayer;
        }
    }

    private void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private void openPage(DialoguePage page) {
        DialoguePage previousPage = this.page;
        this.page = page;
        if (page == DialoguePage.SKILLS && previousPage != DialoguePage.SKILLS) {
            this.experimentalSkillsAnimationStartMillis = Util.getMillis();
            this.experimentalSkillsExitStartMillis = -1L;
        } else if (previousPage == DialoguePage.SKILLS && page != DialoguePage.SKILLS) {
            this.experimentalSkillsExitStartMillis = Util.getMillis();
        }
        rebuildOptions();
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

    private void renderTopBackButton(GuiGraphics graphics, int mouseX, int mouseY) {
        VillagerInteractionNavigationChrome.renderTopBackButton(this.navigationChromeContext, graphics, mouseX, mouseY);
    }

    private void renderHint(GuiGraphics graphics) {
        VillagerInteractionNavigationChrome.renderHint(this.navigationChromeContext, graphics);
    }

    void renderBackdropBehindChat(GuiGraphics graphics) {
        int veilTop = interactionVeilTop();
        if (experimentalSkillsBackdropVisible()) {
            VillagerClientUiUtil.pushGuiLayer(graphics, VillagerClientUiUtil.hudLayerZ());
            renderExperimentalSkillsBackdrop(graphics);
            VillagerClientUiUtil.popGuiLayer(graphics);
        }
        VillagerInteractionExperimentalChrome.renderBackdrop(graphics, this.width, this.height, veilTop, this.lastMouseX, this.lastMouseY);
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

    void renderPositionedHudChat(GuiGraphics graphics) {
        renderBackdropBehindChat(graphics);

        Minecraft minecraft = Minecraft.getInstance();
        ChatRenderLayout layout = chatRenderLayout();
        VillagerClientUiUtil.pushGuiLayer(graphics, VillagerClientUiUtil.chatLayerZ());
        graphics.enableScissor(layout.left(), layout.top(), layout.right(), layout.bottom());
        graphics.pose().pushPose();
        graphics.pose().translate(layout.xOffset(), layout.yOffset(), 0.0F);
        VillagerChatEffectRenderer.render(graphics, minecraft);
        graphics.pose().popPose();
        graphics.disableScissor();
        VillagerClientUiUtil.popGuiLayer(graphics);
    }

    ChatRenderLayout chatRenderLayout() {
        Minecraft minecraft = Minecraft.getInstance();
        ChatComponent chat = minecraft.gui.getChat();
        int chatWidth = chat.getWidth() + CHAT_EXTRA_WIDTH;
        int chatHeight = chat.getHeight();
        int groupWidth = Mth.clamp(chatWidth, 40, Math.max(40, this.width - CHAT_EDGE_MARGIN * 2));
        int groupHeight = Mth.clamp(chatHeight + CHAT_INPUT_AND_GAP_HEIGHT, 40, Math.max(40, this.height - CHAT_EDGE_MARGIN * 2));
        int vanillaTop = this.height - 40 - chatHeight;
        int vanillaLeft = 0;

        InteractionChatPosition position = VillagerRetaliationConfig.INTERACTION_CHAT_POSITION.get();
        int targetLeft;
        if (position.anchorsRight()) {
            targetLeft = this.width - groupWidth - CHAT_EDGE_MARGIN;
        } else if (position.anchorsCenter()) {
            targetLeft = (this.width - groupWidth) / 2;
        } else {
            targetLeft = vanillaLeft;
        }

        int targetTop;
        if (position.anchorsTop()) {
            targetTop = CHAT_TOP_MARGIN;
        } else if (position.anchorsMiddle()) {
            targetTop = (this.height - groupHeight) / 2;
        } else {
            targetTop = vanillaTop;
        }

        targetLeft = Mth.clamp(targetLeft, 0, Math.max(0, this.width - groupWidth));
        targetTop = Mth.clamp(targetTop, 0, Math.max(0, this.height - groupHeight));
        return new ChatRenderLayout(
                targetLeft,
                targetTop,
                targetLeft + groupWidth,
                targetTop + groupHeight,
                targetLeft - vanillaLeft,
                targetTop - vanillaTop
        );
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

    private int interactionVeilTop() {
        return Math.max(0, conversationInfoTop() + VEIL_DITHER_START_OFFSET);
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

    private int experimentalInfoRight() {
        return VillagerInteractionExperimentalLayout.infoRight(this.width, experimentalOptionsLeft());
    }

    private int experimentalInfoLeft() {
        float scale = experimentalScaleFactor();
        int nameWidth = Math.round(this.font.width(this.villagerName) * EXPERIMENTAL_INFO_NAME_SCALE * scale);
        int professionWidth = Math.round(this.font.width(this.professionName) * EXPERIMENTAL_INFO_DETAIL_SCALE * scale);
        int reputationWidth = Math.round(this.font.width(reputationText()) * EXPERIMENTAL_INFO_DETAIL_SCALE * scale);
        int infoWidth = Math.max(nameWidth, Math.max(professionWidth, reputationWidth));
        return experimentalInfoRight() - infoWidth;
    }

    private int experimentalInfoBottom() {
        return optionsTop() + optionViewportHeight();
    }

    private int experimentalPageLeft() {
        return VillagerInteractionExperimentalLayout.pageLeft(this.width, optionWidth());
    }

    private String genderText() {
        return translate("info.gender", this.genderName);
    }

    private String moodText() {
        return translate("info.mood", moodName(this.primaryMood));
    }

    private String reputationText() {
        return translate("info.reputation", this.reputation);
    }

    private int reputationColor() {
        Integer color = VillagerReputationIconSet.colorFor(this.reputationLevel).getColor();
        return color == null ? INFO_LABEL_COLOR : 0xFF000000 | color;
    }

    private String familyButtonText() {
        int count = this.familyTree.relationshipCount();
        return count <= 0 ? translate("family.tree") : translate("family.tree_count", count);
    }

    private boolean canRequestVillagerInventory() {
        return this.reputationLevel.trustRank() >= VillagerReputationLevel.REVERED.trustRank();
    }

    private void applyChatWidthOverride() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) {
            return;
        }
        if (this.originalChatWidth == null) {
            this.originalChatWidth = (Double) minecraft.options.chatWidth().get();
        }

        int targetPixelWidth = Math.max(40, interactionChatTargetPixelWidth());
        double targetChatWidth = Mth.clamp((targetPixelWidth - 40.0D) / 280.0D, 0.0D, this.originalChatWidth);
        minecraft.options.chatWidth().set(targetChatWidth);
    }

    private int interactionChatTargetPixelWidth() {
        InteractionChatPosition position = VillagerRetaliationConfig.INTERACTION_CHAT_POSITION.get();
        int padding = experimentalUnit(INFO_PANEL_CHAT_PADDING);
        if (position.anchorsCenter()) {
            return this.width - CHAT_EDGE_MARGIN * 2;
        }
        if (position.anchorsRight()) {
            return Math.max(40, optionsLeft() - padding);
        }
        return Math.max(40, experimentalInfoLeft() - padding);
    }

    private void restoreChatWidthOverride() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null || this.originalChatWidth == null) {
            return;
        }
        minecraft.options.chatWidth().set(this.originalChatWidth);
        this.originalChatWidth = null;
    }

    private int focusCenterY() {
        return VillagerInteractionLayoutMetrics.focusCenterY(this.height);
    }

    private int optionWidth() {
        return VillagerInteractionLayoutMetrics.optionWidth();
    }

    private int optionHeight() {
        return VillagerInteractionLayoutMetrics.optionHeight();
    }

    private int optionTextInset() {
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
        return VillagerInteractionLayoutMetrics.optionStride();
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
        ToucanScrollbars.renderFadedThumb(graphics, scrollbarThumb, currentScroll, maxScroll, 0xBFFFFFFF, VillagerInteractionExperimentalChrome.chromeAlpha());
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

    private static int moodColor(VillagerMood mood) {
        return switch (mood) {
            case GRATEFUL, CONTENT, HOPEFUL, PROUD -> 0xD08BE0A9;
            case SUSPICIOUS, STRESSED, LONELY -> 0xD0E6D58A;
            case AFRAID, ANGRY, GRIEVING, PROTECTIVE -> 0xD0E69A8A;
            case NEUTRAL -> 0xCFEAE6DC;
        };
    }

    private static String maleAncestorLabel(int generation) {
        return generationLabel("family.ancestor.male.grand", generation);
    }

    private static String femaleAncestorLabel(int generation) {
        return generationLabel("family.ancestor.female.grand", generation);
    }

    private static String maleDescendantLabel(int generation) {
        return generationLabel("family.descendant.male.grand", generation);
    }

    private static String femaleDescendantLabel(int generation) {
        return generationLabel("family.descendant.female.grand", generation);
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
        RECRUIT
    }

    private record DialogueOption(String label, Runnable action) {
        static DialogueOption enabled(String label, Runnable action) {
            return new DialogueOption(label, action);
        }
    }

    private record TopBackButtonBounds(int left, int right, int top, int bottom) {
    }

    record ChatRenderLayout(int left, int top, int right, int bottom, int xOffset, int yOffset) {
        int translatedMouseX(int mouseX) {
            return mouseX - this.xOffset;
        }

        int translatedMouseY(int mouseY) {
            return mouseY - this.yOffset;
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
        public void renderScrollbar(GuiGraphics graphics) {
            VillagerInteractionScreen.this.renderScrollbar(graphics);
        }
    }

    private final class ExperimentalChromeContext implements VillagerInteractionExperimentalChrome.Context {
        @Override
        public Font font() {
            return VillagerInteractionScreen.this.font;
        }

        @Override
        public int infoRight() {
            return VillagerInteractionScreen.this.experimentalInfoRight();
        }

        @Override
        public int infoBottom() {
            return VillagerInteractionScreen.this.experimentalInfoBottom();
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
        public String villagerName() {
            return VillagerInteractionScreen.this.villagerName;
        }

        @Override
        public String professionName() {
            return VillagerInteractionScreen.this.professionName;
        }

        @Override
        public String genderText() {
            return VillagerInteractionScreen.this.genderText();
        }

        @Override
        public String moodText() {
            return VillagerInteractionScreen.this.moodText();
        }

        @Override
        public String reputationText() {
            return VillagerInteractionScreen.this.reputationText();
        }

        @Override
        public int moodColor() {
            return VillagerInteractionScreen.moodColor(VillagerInteractionScreen.this.primaryMood);
        }

        @Override
        public int reputationColor() {
            return VillagerInteractionScreen.this.reputationColor();
        }

        @Override
        public int infoSecondaryColor() {
            return INFO_SECONDARY_COLOR;
        }

        @Override
        public int infoLabelColor() {
            return INFO_LABEL_COLOR;
        }

        @Override
        public float experimentalTextScale() {
            return VillagerInteractionScreen.this.experimentalScaleFactor();
        }

        @Override
        public int experimentalUnit(int value) {
            return VillagerInteractionScreen.this.experimentalUnit(value);
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
            return VillagerInteractionExperimentalChrome.chromeAlpha();
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
