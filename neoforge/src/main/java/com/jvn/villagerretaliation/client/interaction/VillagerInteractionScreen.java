package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.config.InteractionChatPosition;
import com.jvn.villagerretaliation.config.InteractionScreenStyle;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
    private static final int OPTION_WIDTH = 180;
    private static final int OPTION_HEIGHT = 18;
    private static final int OPTION_GAP = 0;
    private static final int OPTION_VIEWPORT_ROWS = 5;
    private static final int INFO_PANEL_ROWS = 7;
    private static final int OPTION_TEXT_INSET = 10;
    private static final int OPTION_SCROLLBAR_OFFSET = 2;
    private static final int OPTION_SCROLLBAR_WIDTH = 2;
    private static final int OPTION_SCROLLBAR_HIT_WIDTH = 10;
    private static final int TOP_BACK_BUTTON_GAP = 8;
    private static final int OPTIONS_DIVIDER_GAP = 18;
    private static final int DIVIDER_HEIGHT = 80;
    private static final int INFO_PANEL_CHAT_PADDING = 20;
    private static final int VEIL_DITHER_START_OFFSET = OPTION_HEIGHT - 81;
    private static final int SCREEN_BOTTOM_MARGIN = 48;
    private static final int VEIL_TOP_DITHER_HEIGHT = 64;
    private static final int SKILLS_RIGHT_MARGIN = 36;
    private static final int SKILLS_CONTAINER_PADDING_X = 8;
    private static final int SKILLS_CONTAINER_PADDING_Y = 6;
    private static final int SKILLS_CONTAINER_BACKGROUND_COLOR = 0xA0101010;
    private static final int SKILLS_CONTAINER_STRIPE_COLOR = 0xCCECECEC;
    private static final int SKILLS_CONTAINER_SHADOW_COLOR = 0xB0000000;
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
    private static final int DIVIDER_CORE_COLOR = 0xFFFFFFFF;
    private static final int GIFT_BUTTON_WIDTH = 64;
    private static final int GIFT_BUTTON_HEIGHT = 18;
    private static final int DIVIDER_SELECT_WIDTH = 11;
    private static final int DIVIDER_SELECT_HEIGHT = 19;
    private static final int PROFILE_CHART_RADIUS = 36;
    private static final int PROFILE_CHART_AXIS_COLOR = 0x55E8E4DA;
    private static final int PROFILE_CHART_OUTLINE_COLOR = 0x90E8E4DA;
    private static final int PROFILE_CHART_VALUE_COLOR = 0xFFE9C46A;
    private static final int PROFILE_CHART_POINT_COLOR = 0xFFFFF3B0;
    private static final int PROFILE_CHART_POINT_HOVER_COLOR = 0xFFFFFFFF;
    private static final int PROFILE_CHART_POINT_HIT_RADIUS = 6;
    private static final int PROFILE_SKILL_ROW_HEIGHT = 16;
    private static final int PROFILE_SKILL_ROW_GAP = 2;
    private static final int PROFILE_SKILL_BAR_HEIGHT = 4;
    private static final int PROFILE_SKILL_COLUMNS = 2;
    private static final int PROFILE_SKILL_COLUMN_GAP = 8;
    private static final Runnable NO_ACTION = () -> {
    };

    private final int villagerEntityId;
    private final String villagerName;
    private final String professionName;
    private final String genderName;
    private final boolean baby;
    private int reputation;
    private VillagerReputationLevel reputationLevel;
    private DialogueDisposition mood;
    private VillagerMood primaryMood;
    private boolean followingPlayer;
    private final boolean forcedDialogue;
    private final List<DialogueOption> options = new ArrayList<>();
    private final List<DialogueOptionDefinition> dialogueOptions = new ArrayList<>();
    private final List<String> knownLikedGiftNames = new ArrayList<>();
    private final List<String> knownDislikedGiftNames = new ArrayList<>();
    private final VillagerFamilyTreeSnapshot familyTree;
    private final VillagerRelationshipSnapshot relationships;
    private DialoguePage page = DialoguePage.ROOT;
    private int selectedOption;
    private boolean closingFromServer;
    private boolean replacingFromServer;
    private boolean openingChat;
    private boolean awaitingForcedDialogueResponse;
    private boolean profileRefreshRequested;
    private boolean draggingScrollbar;
    private boolean draggingSkillScrollbar;
    private float scrollbarDragOffset;
    private float skillScrollbarDragOffset;
    private float optionScroll;
    private float targetOptionScroll;
    private float skillScroll;
    private float targetSkillScroll;
    private VillagerSkill selectedSkillDetails;
    private int selectedInventorySlot = -1;
    private int lastMouseX;
    private int lastMouseY;
    private Button giftButton;
    private Double originalChatWidth;
    private final GiftPageContext giftPageContext = new GiftPageContext();
    private final OptionListContext optionListContext = new OptionListContext();
    private final NavigationChromeContext navigationChromeContext = new NavigationChromeContext();
    private final ConversationPanelContext conversationPanelContext = new ConversationPanelContext();
    private final ExperimentalChromeContext experimentalChromeContext = new ExperimentalChromeContext();
    private final ProfilePageContext profilePageContext = new ProfilePageContext();
    private final SkillsPageContext skillsPageContext = new SkillsPageContext();

    public VillagerInteractionScreen(
            int villagerEntityId,
            String villagerName,
            String professionName,
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
        this.genderName = localizedGenderName(genderName);
        this.baby = baby;
        this.reputation = reputation;
        this.reputationLevel = reputationLevel;
        this.mood = mood;
        this.primaryMood = primaryMood == null ? VillagerMood.NEUTRAL : primaryMood;
        this.followingPlayer = followingPlayer;
        this.forcedDialogue = forcedDialogue;
        this.dialogueOptions.addAll(dialogueOptions);
        this.knownLikedGiftNames.addAll(knownLikedGiftNames);
        this.knownDislikedGiftNames.addAll(knownDislikedGiftNames);
        this.familyTree = familyTree == null ? VillagerFamilyTreeSnapshot.EMPTY : familyTree;
        this.relationships = relationships == null ? VillagerRelationshipSnapshot.EMPTY : relationships;
        if (forcedDialogue) {
            this.page = DialoguePage.TALK;
        }
        VillagerInteractionExperimentalChrome.resetAnimation();
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
        ClientVillagerConversationState.setForceCameraTowardsVillager(forceCameraTowardsVillager);
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

        if (isExperimentalUi()) {
            VillagerInteractionExperimentalChrome.renderFocus(this.experimentalChromeContext, graphics, mouseX, mouseY);
        } else {
            VillagerInteractionConversationPanel.render(this.conversationPanelContext, graphics, mouseX, mouseY);
        }
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
        if (isExperimentalUi()) {
            VillagerInteractionExperimentalChrome.renderNameTooltip(this.experimentalChromeContext, graphics, mouseX, mouseY);
        }
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
                || tryClickFamilyButton(mouseX, mouseY)
                || tryClickRelationshipButton(mouseX, mouseY)
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

        setTargetOptionScroll(this.targetOptionScroll - (float) scrollY * OPTION_SCROLL_STEP);
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
        this.selectedOption = this.options.isEmpty() ? -1 : 0;
        this.optionScroll = 0.0F;
        this.targetOptionScroll = 0.0F;
        ensureSelectedVisible();
    }

    private void rebuildOptionsKeepingListPosition() {
        int previousSelectedOption = this.selectedOption;
        float previousOptionScroll = this.optionScroll;
        float previousTargetOptionScroll = this.targetOptionScroll;

        rebuildOptions();

        if (!this.options.isEmpty()) {
            this.selectedOption = Mth.clamp(previousSelectedOption, 0, this.options.size() - 1);
        }
        this.optionScroll = Mth.clamp(previousOptionScroll, 0.0F, maxOptionScroll());
        this.targetOptionScroll = Mth.clamp(previousTargetOptionScroll, 0.0F, maxOptionScroll());
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
            if (isExperimentalUi()) {
                this.options.add(DialogueOption.enabled(familyButtonText(), this::openFamilyPage));
            }
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
        if (this.page != DialoguePage.ROOT) {
            this.selectedInventorySlot = -1;
            openPage(DialoguePage.ROOT);
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
        this.minecraft.setScreen(null);
    }

    private void activateSelected() {
        if (this.selectedOption < 0 || this.selectedOption >= this.options.size()) {
            return;
        }
        DialogueOption option = this.options.get(this.selectedOption);
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
        this.page = page;
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
        this.selectedOption = Mth.positiveModulo(this.selectedOption + direction, this.options.size());
        ensureSelectedVisible();
    }

    private void updateOptionScroll() {
        this.optionScroll = Mth.lerp(OPTION_SCROLL_LERP, this.optionScroll, this.targetOptionScroll);
        if (Math.abs(this.optionScroll - this.targetOptionScroll) < 0.15F) {
            this.optionScroll = this.targetOptionScroll;
        }
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
                conversationInfoTop()
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
        if (isExperimentalUi()) {
            VillagerInteractionExperimentalChrome.renderBackdrop(graphics, this.width, this.height, veilTop, this.lastMouseX, this.lastMouseY);
            return;
        }
        VillagerInteractionScreenShaderRenderer.renderInteractionVeil(graphics, this.width, this.height, veilTop, VEIL_TOP_DITHER_HEIGHT);
    }

    void renderPositionedHudChat(GuiGraphics graphics) {
        renderBackdropBehindChat(graphics);

        Minecraft minecraft = Minecraft.getInstance();
        ChatRenderLayout layout = chatRenderLayout();
        graphics.enableScissor(layout.left(), layout.top(), layout.right(), layout.bottom());
        graphics.pose().pushPose();
        graphics.pose().translate(layout.xOffset(), layout.yOffset(), 0.0F);
        minecraft.gui.getChat().render(graphics, minecraft.gui.getGuiTicks(), 0, 0, false);
        graphics.pose().popPose();
        graphics.disableScissor();
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
            this.selectedOption = hovered;
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

    private boolean tryClickFamilyButton(double mouseX, double mouseY) {
        if (isExperimentalUi()) {
            return false;
        }
        if (!isPointInsideFamilyButton(mouseX, mouseY)) {
            return false;
        }
        openFamilyPage();
        return true;
    }

    private boolean tryClickRelationshipButton(double mouseX, double mouseY) {
        if (isExperimentalUi()) {
            return false;
        }
        if (!isPointInsideRelationshipButton(mouseX, mouseY)) {
            return false;
        }
        openRelationshipPage();
        return true;
    }

    private boolean tryBeginScrollbarDrag(double mouseX, double mouseY) {
        VillagerInteractionUiUtil.ScrollbarThumb scrollbarThumb = scrollbarThumb();
        if (scrollbarThumb == null || !scrollbarThumb.contains(mouseX, mouseY)) {
            return false;
        }

        this.draggingScrollbar = true;
        this.scrollbarDragOffset = (float) mouseY - scrollbarThumb.top();
        return true;
    }

    private boolean tryBeginSkillInfoScrollbarDrag(double mouseX, double mouseY) {
        if (this.page != DialoguePage.SKILLS) {
            return false;
        }

        VillagerInteractionUiUtil.ScrollbarThumb scrollbarThumb = skillInfoScrollbarThumb();
        if (scrollbarThumb == null || !scrollbarThumb.contains(mouseX, mouseY)) {
            return false;
        }

        this.draggingSkillScrollbar = true;
        this.skillScrollbarDragOffset = (float) mouseY - scrollbarThumb.top();
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
        VillagerInteractionUiUtil.ScrollbarThumb scrollbarThumb = scrollbarThumb();
        if (scrollbarThumb == null) {
            this.draggingScrollbar = false;
            return false;
        }

        float trackTravel = scrollbarThumb.trackTravel();
        if (trackTravel <= 0.0F) {
            setTargetOptionScroll(0.0F);
        } else {
            float thumbTop = (float) mouseY - this.scrollbarDragOffset;
            float ratio = Mth.clamp((thumbTop - scrollbarThumb.viewportTop()) / trackTravel, 0.0F, 1.0F);
            setTargetOptionScroll(maxOptionScroll() * ratio);
        }
        this.optionScroll = this.targetOptionScroll;
        return true;
    }

    private boolean dragSkillScrollbar(double mouseY) {
        VillagerInteractionUiUtil.ScrollbarThumb scrollbarThumb = skillInfoScrollbarThumb();
        if (scrollbarThumb == null) {
            this.draggingSkillScrollbar = false;
            return false;
        }

        float trackTravel = scrollbarThumb.trackTravel();
        if (trackTravel <= 0.0F) {
            setTargetSkillScroll(0.0F);
        } else {
            float thumbTop = (float) mouseY - this.skillScrollbarDragOffset;
            float ratio = Mth.clamp((thumbTop - scrollbarThumb.viewportTop()) / trackTravel, 0.0F, 1.0F);
            setTargetSkillScroll(maxSkillScroll() * ratio);
        }
        this.skillScroll = this.targetSkillScroll;
        return true;
    }

    private boolean tryActivateHoveredOption(double mouseX, double mouseY) {
        int hovered = VillagerInteractionOptionList.optionAt(this.optionListContext, mouseX, mouseY);
        if (hovered < 0) {
            return false;
        }

        this.selectedOption = hovered;
        ensureSelectedVisible();
        activateSelected();
        return true;
    }

    private boolean tryClickGiftPage(double mouseX, double mouseY) {
        return VillagerInteractionGiftPage.tryClick(this.giftPageContext, mouseX, mouseY, optionsLeft(), conversationInfoTop());
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
        return VillagerInteractionGiftPage.giftInventoryLeft(optionsLeft());
    }

    private int giftInventoryTop() {
        return VillagerInteractionGiftPage.giftInventoryTop(conversationInfoTop());
    }

    private boolean isPointInsideOptionScrollArea(double mouseX, double mouseY) {
        int left = optionsLeft() - 18;
        int right = optionsLeft() + optionWidth() + 4;
        int top = optionsTop();
        int bottom = top + optionViewportHeight();
        return mouseX >= left && mouseX <= right && mouseY >= top - 4 && mouseY <= bottom + 4;
    }

    private boolean isPointInsideSkillsInfoScrollArea(double mouseX, double mouseY) {
        int left = optionsLeft() - 18;
        int right = optionsLeft() + optionWidth() + 4;
        int top = skillInfoViewportTop();
        int bottom = skillInfoViewportBottom();
        return mouseX >= left && mouseX <= right && mouseY >= top - 4 && mouseY <= bottom + 4;
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
        return VillagerClientUiUtil.containsInclusive(mouseX, mouseY, bounds.left(), bounds.top() - 2, bounds.right(), bounds.bottom() + 2);
    }

    private TopBackButtonBounds topBackButtonBounds() {
        float textScale = isExperimentalUi() ? experimentalScaleFactor() : 1.0F;
        int textWidth = Math.round(this.font.width(backLabel()) * textScale);
        int textHeight = Math.round(this.font.lineHeight * textScale);
        int left = isExperimentalUi() ? experimentalOptionTextLeft() : contentLeft() + optionTextInset();
        int contentTop = isExperimentalUi()
                ? experimentalOptionViewportTop()
                : this.page == DialoguePage.GIFT
                ? giftInventoryTop()
                : this.page == DialoguePage.PROFILE || this.page == DialoguePage.SKILLS ? conversationInfoTop() : optionsTop();
        int top = contentTop - textHeight - topBackButtonGap();
        int bottom = top + textHeight;
        return new TopBackButtonBounds(left, left + textWidth, top, bottom);
    }

    private int skillsPanelTop() {
        if (isExperimentalUi()) {
            return chatRenderLayout().top();
        }
        int panelHeight = skillsContainerHeight();
        int minTop = 32;
        int maxTop = Math.max(minTop, this.height - panelHeight - 32);
        int centeredTop = (this.height - panelHeight) / 2;
        int aboveInfoTop = interactionVeilTop() - panelHeight - 14;
        return Mth.clamp(Math.min(centeredTop, aboveInfoTop), minTop, maxTop);
    }

    private int skillsPanelLeft() {
        if (isExperimentalUi()) {
            return experimentalOptionsLeft();
        }
        int maxLeft = Math.max(8, this.width - OPTION_WIDTH - 8);
        int preferredLeft = this.width - OPTION_WIDTH - SKILLS_RIGHT_MARGIN;
        int minLeft = optionsLeft() + OPTION_WIDTH + 36;
        return Math.min(maxLeft, Math.max(minLeft, preferredLeft));
    }

    private int skillsPanelWidth() {
        if (isExperimentalUi()) {
            return Math.max(optionWidth(), optionsScrollbarLeft() - skillsPanelLeft());
        }
        return optionWidth();
    }

    private int skillsContainerHeight() {
        return skillsPanelHeight() + SKILLS_CONTAINER_PADDING_Y * 2;
    }

    private int skillsPanelHeight() {
        int rows = (VillagerSkill.values().length + PROFILE_SKILL_COLUMNS - 1) / PROFILE_SKILL_COLUMNS;
        return this.font.lineHeight + 4
                + rows * PROFILE_SKILL_ROW_HEIGHT
                + Math.max(0, rows - 1) * PROFILE_SKILL_ROW_GAP;
    }

    private float maxSkillScroll() {
        return Math.max(0.0F, Mth.floor(optionTextYOffset()) + skillsInfoContentHeight() - skillInfoViewportHeight());
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
        if (isExperimentalUi() && this.page == DialoguePage.SKILLS) {
            return experimentalOptionViewportTop();
        }
        return conversationInfoTop();
    }

    private int skillInfoViewportBottom() {
        if (isExperimentalUi() && this.page == DialoguePage.SKILLS) {
            return experimentalOptionViewportBottom();
        }
        return VillagerInteractionConversationPanel.skillInfoViewportBottom(this.conversationPanelContext);
    }

    private int skillInfoViewportHeight() {
        return Math.max(1, skillInfoViewportBottom() - skillInfoViewportTop());
    }

    private int optionsTop() {
        if (isExperimentalUi()) {
            return experimentalOptionsTop(optionViewportHeight());
        }
        return focusCenterY() - optionViewportHeight() / 2;
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
        if (isExperimentalUi()) {
            return experimentalOptionsLeft();
        }
        return dividerX() + 20;
    }

    private int contentLeft() {
        if (isExperimentalUi() && (this.page == DialoguePage.PROFILE || this.page == DialoguePage.SKILLS)) {
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

    private int experimentalInfoBottom() {
        return optionsTop() + optionViewportHeight();
    }

    private int experimentalPageLeft() {
        return VillagerInteractionExperimentalLayout.pageLeft(this.width, optionWidth());
    }

    private int dividerX() {
        return this.width / 2;
    }

    int infoPanelLeft() {
        return dividerX() - 28 - infoPanelWidth();
    }

    private int infoPanelWidth() {
        return Math.max(
                Math.max(Math.max(this.font.width(this.villagerName), this.font.width(this.professionName)),
                        Math.max(Math.max(this.font.width(genderText()), this.font.width(moodText())), this.font.width(reputationText()))),
                Math.max(this.font.width(familyButtonText()), this.font.width(relationshipButtonText()))
        );
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

    private String familyButtonText() {
        int count = this.familyTree.relationshipCount();
        return count <= 0 ? translate("family.tree") : translate("family.tree_count", count);
    }

    private String relationshipButtonText() {
        int count = this.relationships.relationshipCount();
        return count <= 0 ? translate("relationships.title") : translate("relationships.count", count);
    }

    private boolean isPointInsideFamilyButton(double mouseX, double mouseY) {
        return VillagerInteractionConversationPanel.isPointInsideFamilyButton(this.conversationPanelContext, mouseX, mouseY);
    }

    private boolean isPointInsideRelationshipButton(double mouseX, double mouseY) {
        return VillagerInteractionConversationPanel.isPointInsideRelationshipButton(this.conversationPanelContext, mouseX, mouseY);
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
        if (isExperimentalUi() && position.anchorsRight()) {
            return Math.max(40, optionsLeft() - experimentalUnit(INFO_PANEL_CHAT_PADDING));
        }
        if (position.anchorsCenter()) {
            return this.width - CHAT_EDGE_MARGIN * 2;
        }
        if (position.anchorsRight()) {
            return this.width - optionsLeft() - optionWidth() - INFO_PANEL_CHAT_PADDING;
        }
        return infoPanelLeft() - INFO_PANEL_CHAT_PADDING;
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
        return Math.max(72, this.height - SCREEN_BOTTOM_MARGIN);
    }

    private int optionWidth() {
        return isExperimentalUi() ? experimentalUnit(OPTION_WIDTH) : OPTION_WIDTH;
    }

    private int optionHeight() {
        return isExperimentalUi() ? experimentalUnit(OPTION_HEIGHT) : OPTION_HEIGHT;
    }

    private int optionTextInset() {
        return isExperimentalUi() ? experimentalUnit(OPTION_TEXT_INSET) : OPTION_TEXT_INSET;
    }

    private float optionTextYOffset() {
        return optionHeight() * (5.0F / 18.0F);
    }

    private int optionScrollbarOffset() {
        return isExperimentalUi() ? experimentalUnit(OPTION_SCROLLBAR_OFFSET) : OPTION_SCROLLBAR_OFFSET;
    }

    private int optionScrollbarWidth() {
        return isExperimentalUi() ? experimentalUnitAtLeast(OPTION_SCROLLBAR_WIDTH, 1) : OPTION_SCROLLBAR_WIDTH;
    }

    private int optionScrollbarHitWidth() {
        return isExperimentalUi() ? experimentalUnitAtLeast(OPTION_SCROLLBAR_HIT_WIDTH, 1) : OPTION_SCROLLBAR_HIT_WIDTH;
    }

    private int topBackButtonGap() {
        return isExperimentalUi() ? experimentalUnit(TOP_BACK_BUTTON_GAP) : TOP_BACK_BUTTON_GAP;
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
        int visibleRows = Math.min(OPTION_VIEWPORT_ROWS, Math.max(1, this.options.size()));
        return visibleRows * optionHeight() + Math.max(0, visibleRows - 1) * OPTION_GAP;
    }

    private int experimentalFullOptionViewportHeight() {
        return OPTION_VIEWPORT_ROWS * optionHeight() + Math.max(0, OPTION_VIEWPORT_ROWS - 1) * OPTION_GAP;
    }

    private int rootOptionViewportHeight() {
        return INFO_PANEL_ROWS * optionHeight() + Math.max(0, INFO_PANEL_ROWS - 1) * OPTION_GAP;
    }

    private float maxOptionScroll() {
        return Math.max(0.0F, optionContentHeight() - optionViewportHeight());
    }

    private float optionContentHeight() {
        if (this.options.isEmpty()) {
            return 0.0F;
        }
        return this.options.size() * optionHeight() + Math.max(0, this.options.size() - 1) * OPTION_GAP;
    }

    private int optionStride() {
        return optionHeight() + OPTION_GAP;
    }

    private void ensureSelectedVisible() {
        if (this.selectedOption < 0 || this.selectedOption >= this.options.size()) {
            return;
        }

        float optionTop = this.selectedOption * optionStride();
        float optionBottom = optionTop + optionHeight();
        float viewportTop = this.targetOptionScroll;
        float viewportBottom = viewportTop + optionViewportHeight();
        int padding = 6;
        if (optionTop < viewportTop + padding) {
            setTargetOptionScroll(optionTop - padding);
        } else if (optionBottom > viewportBottom - padding) {
            setTargetOptionScroll(optionBottom - optionViewportHeight() + padding);
        } else {
            setTargetOptionScroll(this.targetOptionScroll);
        }
    }

    private void setTargetOptionScroll(float scroll) {
        this.targetOptionScroll = Mth.clamp(scroll, 0.0F, maxOptionScroll());
    }

    private float edgeFadeAlpha(float optionY, int viewportTop, int viewportBottom) {
        return VillagerInteractionUiUtil.edgeFadeAlpha(
                this.optionScroll,
                maxOptionScroll(),
                optionY,
                optionY + optionHeight(),
                viewportTop,
                viewportBottom,
                isExperimentalUi() ? 26.0F : 16.0F
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
        VillagerInteractionUiUtil.ScrollbarThumb scrollbarThumb = scrollbarThumb();
        renderScrollbar(graphics, scrollbarThumb, this.optionScroll, maxOptionScroll());
    }

    private void renderScrollbar(
            GuiGraphics graphics,
            VillagerInteractionUiUtil.ScrollbarThumb scrollbarThumb,
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
            graphics.fill(scrollbarThumb.left(), y, scrollbarThumb.right(), y + 1, VillagerInteractionUiUtil.withAlpha(0xBFFFFFFF, alphaFactor));
        }
    }

    private VillagerInteractionUiUtil.ScrollbarThumb scrollbarThumb() {
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
                this.optionScroll,
                maxScroll,
                optionContentHeight()
        );
    }

    private VillagerInteractionUiUtil.ScrollbarThumb skillInfoScrollbarThumb() {
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
        if (isExperimentalUi()) {
            return VillagerInteractionExperimentalLayout.scrollbarLeft(
                    this.width,
                    optionsLeft(),
                    optionWidth(),
                    optionScrollbarOffset(),
                    optionScrollbarWidth());
        }
        return optionsLeft() + optionWidth() + optionScrollbarOffset();
    }

    private boolean isExperimentalUi() {
        return VillagerRetaliationConfig.INTERACTION_SCREEN_STYLE.get() == InteractionScreenStyle.EXPERIMENTAL;
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
            return VillagerInteractionScreen.this.selectedOption;
        }

        @Override
        public float optionScroll() {
            return VillagerInteractionScreen.this.optionScroll;
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
        public boolean experimentalStyle() {
            return VillagerInteractionScreen.this.isExperimentalUi();
        }

        @Override
        public float experimentalTextScale() {
            return VillagerInteractionScreen.this.isExperimentalUi() ? VillagerInteractionScreen.this.experimentalScaleFactor() : 1.0F;
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
            return VillagerInteractionScreen.this.isExperimentalUi() ? VillagerInteractionScreen.this.experimentalScaleFactor() : 1.0F;
        }
    }

    private final class ConversationPanelContext implements VillagerInteractionConversationPanel.Context {
        @Override
        public Font font() {
            return VillagerInteractionScreen.this.font;
        }

        @Override
        public int dividerX() {
            return VillagerInteractionScreen.this.dividerX();
        }

        @Override
        public int conversationInfoTop() {
            return VillagerInteractionScreen.this.conversationInfoTop();
        }

        @Override
        public int optionsTop() {
            return VillagerInteractionScreen.this.optionsTop();
        }

        @Override
        public int optionViewportHeight() {
            return VillagerInteractionScreen.this.optionViewportHeight();
        }

        @Override
        public int rootOptionViewportHeight() {
            return VillagerInteractionScreen.this.rootOptionViewportHeight();
        }

        @Override
        public int optionStride() {
            return VillagerInteractionScreen.this.optionStride();
        }

        @Override
        public int optionHeight() {
            return VillagerInteractionScreen.this.optionHeight();
        }

        @Override
        public int optionCount() {
            return VillagerInteractionScreen.this.options.size();
        }

        @Override
        public int selectedOption() {
            return VillagerInteractionScreen.this.selectedOption;
        }

        @Override
        public float optionScroll() {
            return VillagerInteractionScreen.this.optionScroll;
        }

        @Override
        public boolean giftPageActive() {
            return VillagerInteractionScreen.this.page == DialoguePage.GIFT;
        }

        @Override
        public boolean familyPageActive() {
            return VillagerInteractionScreen.this.isFamilyPageActive();
        }

        @Override
        public boolean relationshipPageActive() {
            return VillagerInteractionScreen.this.page == DialoguePage.RELATIONSHIPS;
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
        public String familyButtonText() {
            return VillagerInteractionScreen.this.familyButtonText();
        }

        @Override
        public String relationshipButtonText() {
            return VillagerInteractionScreen.this.relationshipButtonText();
        }

        @Override
        public int moodColor() {
            return VillagerInteractionScreen.moodColor(VillagerInteractionScreen.this.primaryMood);
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
        public int infoLabelColor() {
            return INFO_LABEL_COLOR;
        }

        @Override
        public int dividerCoreColor() {
            return DIVIDER_CORE_COLOR;
        }

        @Override
        public int dividerHeight() {
            return DIVIDER_HEIGHT;
        }

        @Override
        public int dividerSelectWidth() {
            return DIVIDER_SELECT_WIDTH;
        }

        @Override
        public int dividerSelectHeight() {
            return DIVIDER_SELECT_HEIGHT;
        }

        @Override
        public ResourceLocation dividerSelectTexture() {
            return VillagerRetaliationClientAssets.DIVIDER_SELECT_TEXTURE;
        }
    }

    private final class ProfilePageContext implements VillagerInteractionProfilePage.Context {
        @Override
        public Font font() {
            return VillagerInteractionScreen.this.font;
        }

        @Override
        public int optionsLeft() {
            if (VillagerInteractionScreen.this.isExperimentalUi()) {
                return VillagerInteractionScreen.this.experimentalOptionTextLeft() - VillagerInteractionScreen.this.experimentalUnit(6);
            }
            return VillagerInteractionScreen.this.contentLeft();
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
            return PROFILE_CHART_RADIUS;
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
        public int profileChartPointHitRadius() {
            return PROFILE_CHART_POINT_HIT_RADIUS;
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
            if (VillagerInteractionScreen.this.isExperimentalUi()) {
                return VillagerInteractionScreen.this.experimentalOptionTextLeft();
            }
            return VillagerInteractionScreen.this.contentLeft() + 6;
        }

        @Override
        public int skillInfoScissorLeft() {
            if (VillagerInteractionScreen.this.isExperimentalUi()) {
                return Math.max(0, VillagerInteractionScreen.this.optionsLeft() - VillagerInteractionScreen.this.optionWidth());
            }
            return skillInfoTextLeft() - 18;
        }

        @Override
        public int skillInfoScissorRight() {
            if (VillagerInteractionScreen.this.isExperimentalUi()) {
                return VillagerInteractionScreen.this.optionsScrollbarLeft() - 4;
            }
            return skillInfoTextLeft() + VillagerInteractionScreen.this.optionWidth() + 4;
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
            return SKILLS_CONTAINER_PADDING_X;
        }

        @Override
        public int skillsContainerPaddingY() {
            return SKILLS_CONTAINER_PADDING_Y;
        }

        @Override
        public int skillsContainerBackgroundColor() {
            return SKILLS_CONTAINER_BACKGROUND_COLOR;
        }

        @Override
        public int skillsContainerStripeColor() {
            return SKILLS_CONTAINER_STRIPE_COLOR;
        }

        @Override
        public int skillsContainerShadowColor() {
            return SKILLS_CONTAINER_SHADOW_COLOR;
        }

        @Override
        public int profileSkillRowHeight() {
            return PROFILE_SKILL_ROW_HEIGHT;
        }

        @Override
        public int profileSkillRowGap() {
            return PROFILE_SKILL_ROW_GAP;
        }

        @Override
        public int profileSkillBarHeight() {
            return PROFILE_SKILL_BAR_HEIGHT;
        }

        @Override
        public int profileSkillColumns() {
            return PROFILE_SKILL_COLUMNS;
        }

        @Override
        public int profileSkillColumnGap() {
            return PROFILE_SKILL_COLUMN_GAP;
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
            return VillagerInteractionScreen.this.isExperimentalUi() ? VillagerInteractionScreen.this.experimentalScaleFactor() : 1.0F;
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
