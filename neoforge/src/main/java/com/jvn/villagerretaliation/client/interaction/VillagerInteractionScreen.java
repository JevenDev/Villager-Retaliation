package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.config.InteractionChatPosition;
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
import com.jvn.villagerretaliation.skill.VillagerSkillValue;
import com.jvn.villagerretaliation.social.VillagerFamilyTreeSnapshot;
import com.jvn.villagerretaliation.social.VillagerRelationshipSnapshot;
import com.jvn.villagerretaliation.villager.VillagerGender;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.FormattedCharSequence;
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
    private static final int INVENTORY_COLUMNS = 9;
    private static final int INVENTORY_MAIN_ROWS = 3;
    private static final int INVENTORY_SLOT_SIZE = 18;
    private static final int INVENTORY_TEXTURE_WIDTH = 176;
    private static final int INVENTORY_TEXTURE_HEIGHT = 90;
    private static final int INVENTORY_SLOT_START_X = 7;
    private static final int INVENTORY_SLOT_START_Y = 7;
    private static final int INVENTORY_HOTBAR_Y = 65;
    private static final int INVENTORY_ITEM_OFFSET = 1;
    private static final int INVENTORY_BUTTON_WIDTH = 64;
    private static final int INVENTORY_BUTTON_HEIGHT = 18;
    private static final int INVENTORY_BUTTON_GAP = 4;
    private static final int GIFT_INFO_ICON_SIZE = 16;
    private static final int GIFT_INFO_ICON_GAP = 5;
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
    private Button giftButton;
    private Double originalChatWidth;

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
        ClientVillagerConversationState.start(villagerEntityId, forceCameraTowardsVillager);
    }

    @Override
    protected void init() {
        this.giftButton = addRenderableWidget(Button.builder(Component.translatable(GUI_KEY_PREFIX + "gift.give"), button -> requestGift())
                .bounds(0, 0, INVENTORY_BUTTON_WIDTH, INVENTORY_BUTTON_HEIGHT)
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
        focusVillagerOnPlayer();
        updateMouseSelection(mouseX, mouseY);
        updateOptionScroll();
        updateSkillScroll();

        int optionsTop = optionsTop();
        renderConversationFocus(graphics, conversationInfoTop(), mouseX, mouseY);
        renderDivider(graphics, optionsTop);
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
            renderOptions(graphics, mouseX, mouseY, optionsTop);
        }
        renderHint(graphics);
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
        ClientVillagerConversationState.clear();
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
        int left = optionsLeft();
        int viewportHeight = optionViewportHeight();
        int viewportBottom = top + viewportHeight;
        int hovered = optionAt(mouseX, mouseY);

        graphics.enableScissor(left - 24, top - 3, left + OPTION_WIDTH + 10, viewportBottom + 3);
        for (int index = 0; index < this.options.size(); index++) {
            DialogueOption option = this.options.get(index);
            float y = top + index * optionStride() - this.optionScroll;
            if (y + OPTION_HEIGHT < top - 10 || y > viewportBottom + 10) {
                continue;
            }

            renderOption(graphics, option, index, hovered, mouseX, mouseY, left, y, top, viewportBottom);
        }
        graphics.disableScissor();

        renderScrollbar(graphics);

    }

    private void renderProfilePage(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = optionsLeft() + 6;
        int top = conversationInfoTop() + 2;
        Optional<VillagerProfileClientCache.DisplayEntry> entry = VillagerProfileClientCache.get(this.villagerEntityId);
        if (entry.isEmpty()) {
            requestProfileRefresh();
            graphics.drawString(this.font, translate("profile.loading"), left, top + 32, INFO_SECONDARY_COLOR, false);
            return;
        }

        VillagerProfileClientCache.DisplayEntry profile = entry.get();
        int centerX = left + OPTION_WIDTH / 2 - 8;
        int centerY = top + PROFILE_CHART_RADIUS + 16;
        VillagerSocialAttribute hoveredAttribute = profileChartPointAt(profile, centerX, centerY, mouseX, mouseY);
        renderProfileChart(graphics, profile, centerX, centerY, hoveredAttribute);
        if (hoveredAttribute != null) {
            renderProfileAttributeTooltip(graphics, profile, hoveredAttribute, mouseX, mouseY);
        }
    }

    private void renderSkillsPage(GuiGraphics graphics, int mouseX, int mouseY) {
        renderSkillsInfo(graphics);

        int left = skillsPanelLeft();
        int top = skillsPanelTop();
        renderSkillsContainerBackground(graphics, left, top);
        Optional<VillagerProfileClientCache.DisplayEntry> entry = VillagerProfileClientCache.get(this.villagerEntityId);
        if (entry.isEmpty()) {
            requestProfileRefresh();
            graphics.drawString(this.font, translate("profile.loading"), left, top + 32, INFO_SECONDARY_COLOR, false);
            return;
        }

        VillagerProfileClientCache.DisplayEntry profile = entry.get();
        int contentTop = top + SKILLS_CONTAINER_PADDING_Y;
        VillagerSkill hoveredSkill = renderProfileSkills(graphics, profile, left, contentTop, mouseX, mouseY);
        if (hoveredSkill != null) {
            renderProfileSkillTooltip(graphics, profile, hoveredSkill, mouseX, mouseY);
        }
    }

    private void renderSkillsContainerBackground(GuiGraphics graphics, int left, int top) {
        int containerLeft = left - SKILLS_CONTAINER_PADDING_X;
        int containerTop = top;
        int containerRight = left + OPTION_WIDTH;
        int containerBottom = top + skillsContainerHeight();
        graphics.fill(containerLeft, containerTop, containerRight, containerBottom, SKILLS_CONTAINER_BACKGROUND_COLOR);
        graphics.fill(containerLeft, containerTop, containerLeft + 2, containerBottom, SKILLS_CONTAINER_STRIPE_COLOR);
        graphics.fill(containerLeft, containerBottom, containerRight, containerBottom + 1, SKILLS_CONTAINER_SHADOW_COLOR);
    }

    private void renderSkillsInfo(GuiGraphics graphics) {
        int left = optionsLeft() + 6;
        int viewportTop = skillInfoViewportTop();
        int viewportBottom = skillInfoViewportBottom();
        int top = Mth.floor(optionTextTop(viewportTop) - this.skillScroll);
        int width = OPTION_WIDTH - 12;
        graphics.enableScissor(left - 18, viewportTop, left + OPTION_WIDTH + 4, viewportBottom);
        graphics.drawString(
                this.font,
                this.selectedSkillDetails == null ? translate("profile.skills.info.title") : localizedSkill(this.selectedSkillDetails),
                left,
                top,
                INFO_VALUE_COLOR,
                false);
        int y = top + optionStride();
        Optional<VillagerProfileClientCache.DisplayEntry> entry = VillagerProfileClientCache.get(this.villagerEntityId);
        if (this.selectedSkillDetails != null && entry.isPresent()) {
            VillagerProfileClientCache.DisplayEntry profile = entry.get();
            y = renderWrappedSkillInfoLine(
                    graphics,
                    Component.translatable(
                            GUI_KEY_PREFIX + "profile.tooltip.level",
                            localizedSkillRank(profile.skillRank(this.selectedSkillDetails))),
                    left,
                    y,
                    width);
            y = renderWrappedSkillInfoLine(
                    graphics,
                    Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.score", profile.skillValue(this.selectedSkillDetails)),
                    left,
                    y + 2,
                    width);
            renderWrappedSkillInfoLine(
                    graphics,
                    Component.literal(localizedExpandedSkillDescription(this.selectedSkillDetails)),
                    left,
                    y + 4,
                    width);
        } else {
            y = renderWrappedSkillInfoLine(graphics, Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.trade"), left, y, width);
            y = renderWrappedSkillInfoLine(graphics, Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.specialty"), left, y + 4, width);
            renderWrappedSkillInfoLine(graphics, Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.recruit"), left, y + 4, width);
        }
        graphics.disableScissor();
        renderScrollbar(graphics, skillInfoScrollbarThumb(), this.skillScroll, maxSkillScroll());
    }

    private int renderWrappedInfoLine(GuiGraphics graphics, Component component, int left, int top, int width) {
        int y = top;
        for (FormattedCharSequence line : this.font.split(component, width)) {
            graphics.drawString(this.font, line, left, y, INFO_SECONDARY_COLOR, false);
            y += this.font.lineHeight + 2;
        }
        return y;
    }

    private int renderWrappedSkillInfoLine(GuiGraphics graphics, Component component, int left, int top, int width) {
        int y = top;
        int viewportTop = skillInfoViewportTop();
        int viewportBottom = skillInfoViewportBottom();
        for (FormattedCharSequence line : this.font.split(component, width)) {
            float alpha = skillInfoEdgeFadeAlpha(y, viewportTop, viewportBottom);
            graphics.drawString(this.font, line, left, y, VillagerInteractionUiUtil.withAlpha(INFO_SECONDARY_COLOR, alpha), false);
            y += this.font.lineHeight + 2;
        }
        return y;
    }

    private VillagerSkill renderProfileSkills(
            GuiGraphics graphics,
            VillagerProfileClientCache.DisplayEntry profile,
            int left,
            int top,
            int mouseX,
            int mouseY) {
        List<VillagerSkillValue> highlights = profile.bestSkills(VillagerSkill.values().length);
        int columnWidth = (OPTION_WIDTH - 8 - PROFILE_SKILL_COLUMN_GAP) / PROFILE_SKILL_COLUMNS;
        graphics.drawString(this.font, translate("profile.skills"), left, top, INFO_VALUE_COLOR, false);
        VillagerSkill hovered = null;
        for (int index = 0; index < highlights.size(); index++) {
            VillagerSkillValue skillValue = highlights.get(index);
            VillagerSkill skill = skillValue.skill();
            int column = index % PROFILE_SKILL_COLUMNS;
            int row = index / PROFILE_SKILL_COLUMNS;
            int rowLeft = left + column * (columnWidth + PROFILE_SKILL_COLUMN_GAP);
            int y = top + this.font.lineHeight + 4 + row * (PROFILE_SKILL_ROW_HEIGHT + PROFILE_SKILL_ROW_GAP);
            boolean rowHovered = mouseX >= rowLeft - 2
                    && mouseX <= rowLeft + columnWidth + 2
                    && mouseY >= y - 1
                    && mouseY <= y + PROFILE_SKILL_ROW_HEIGHT - 1;
            if (rowHovered) {
                hovered = skill;
                graphics.fill(rowLeft - 2, y - 1, rowLeft + columnWidth + 2, y + PROFILE_SKILL_ROW_HEIGHT - 1, 0x22FFFFFF);
            }

            String label = fitText(localizedSkill(skill), columnWidth);
            graphics.drawString(this.font, label, rowLeft, y, INFO_SECONDARY_COLOR, false);
            renderSkillBar(graphics, rowLeft, y + this.font.lineHeight + 1, columnWidth, skillValue.value(), skillValue.rank());
        }
        return hovered;
    }

    private void renderSkillBar(GuiGraphics graphics, int left, int top, int width, int value, VillagerSkillRank rank) {
        int fillWidth = Mth.clamp(Math.round(width * value / 100.0F), 1, width);
        graphics.fill(left, top, left + width, top + PROFILE_SKILL_BAR_HEIGHT, 0x55332F2A);
        graphics.fill(left, top, left + fillWidth, top + PROFILE_SKILL_BAR_HEIGHT, skillRankColor(rank));
        graphics.fill(left, top, left + width, top + 1, 0x40FFFFFF);
    }

    private String fitText(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        int suffixWidth = this.font.width(suffix);
        if (maxWidth <= suffixWidth) {
            return this.font.plainSubstrByWidth(text, maxWidth);
        }
        return this.font.plainSubstrByWidth(text, maxWidth - suffixWidth) + suffix;
    }

    private void renderProfileChart(
            GuiGraphics graphics,
            VillagerProfileClientCache.DisplayEntry profile,
            int centerX,
            int centerY,
            VillagerSocialAttribute hoveredAttribute) {
        VillagerSocialAttribute[] attributes = VillagerSocialAttribute.values();
        ProfilePoint[] outer = new ProfilePoint[attributes.length];
        ProfilePoint[] values = new ProfilePoint[attributes.length];

        for (int index = 0; index < attributes.length; index++) {
            double angle = profileAttributeAngle(index, attributes.length);
            outer[index] = profilePoint(centerX, centerY, angle, PROFILE_CHART_RADIUS);
            int valueRadius = Math.round(PROFILE_CHART_RADIUS * profile.value(attributes[index]) / 100.0F);
            values[index] = profilePoint(centerX, centerY, angle, valueRadius);
            drawPixelLine(graphics, centerX, centerY, outer[index].x(), outer[index].y(), PROFILE_CHART_AXIS_COLOR);

            String label = localizedAttribute(attributes[index]);
            int labelX = profilePoint(centerX, centerY, angle, PROFILE_CHART_RADIUS + 18).x() - this.font.width(label) / 2;
            int labelY = profilePoint(centerX, centerY, angle, PROFILE_CHART_RADIUS + 14).y() - this.font.lineHeight / 2;
            graphics.drawString(this.font, label, labelX, labelY, INFO_SECONDARY_COLOR, false);
        }

        for (int index = 0; index < attributes.length; index++) {
            int next = (index + 1) % attributes.length;
            drawPixelLine(graphics, outer[index].x(), outer[index].y(), outer[next].x(), outer[next].y(), PROFILE_CHART_OUTLINE_COLOR);
            drawPixelLine(graphics, values[index].x(), values[index].y(), values[next].x(), values[next].y(), PROFILE_CHART_VALUE_COLOR);
            boolean hovered = attributes[index] == hoveredAttribute;
            int pointRadius = hovered ? 2 : 1;
            int pointColor = hovered ? PROFILE_CHART_POINT_HOVER_COLOR : PROFILE_CHART_POINT_COLOR;
            graphics.fill(
                    values[index].x() - pointRadius,
                    values[index].y() - pointRadius,
                    values[index].x() + pointRadius + 1,
                    values[index].y() + pointRadius + 1,
                    pointColor
            );
        }
    }

    private VillagerSocialAttribute profileChartPointAt(
            VillagerProfileClientCache.DisplayEntry profile,
            int centerX,
            int centerY,
            int mouseX,
            int mouseY) {
        VillagerSocialAttribute[] attributes = VillagerSocialAttribute.values();
        VillagerSocialAttribute closestAttribute = null;
        int closestDistance = PROFILE_CHART_POINT_HIT_RADIUS * PROFILE_CHART_POINT_HIT_RADIUS + 1;
        for (int index = 0; index < attributes.length; index++) {
            double angle = profileAttributeAngle(index, attributes.length);
            int valueRadius = Math.round(PROFILE_CHART_RADIUS * profile.value(attributes[index]) / 100.0F);
            ProfilePoint point = profilePoint(centerX, centerY, angle, valueRadius);
            int dx = mouseX - point.x();
            int dy = mouseY - point.y();
            int distance = dx * dx + dy * dy;
            if (distance < closestDistance) {
                closestAttribute = attributes[index];
                closestDistance = distance;
            }
        }
        return closestAttribute;
    }

    private void renderProfileAttributeTooltip(
            GuiGraphics graphics,
            VillagerProfileClientCache.DisplayEntry profile,
            VillagerSocialAttribute attribute,
            int mouseX,
            int mouseY) {
        VillagerSocialAttributeRank rank = profile.rank(attribute);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(localizedAttribute(attribute)).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.level", localizedRank(rank)).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.score", profile.value(attribute)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal(localizedAttributeDescription(attribute)).withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    private void renderProfileSkillTooltip(
            GuiGraphics graphics,
            VillagerProfileClientCache.DisplayEntry profile,
            VillagerSkill skill,
            int mouseX,
            int mouseY) {
        VillagerSkillRank rank = profile.skillRank(skill);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(localizedSkill(skill)).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.level", localizedSkillRank(rank)).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.score", profile.skillValue(skill)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal(localizedSkillDescription(skill)).withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    private static int skillRankColor(VillagerSkillRank rank) {
        return switch (rank) {
            case NOVICE -> 0xB8D5D0C6;
            case APPRENTICE -> 0xD0DDE7A4;
            case SKILLED -> 0xD0A8D8F0;
            case EXPERT -> 0xD0E9C46A;
            case MASTER -> 0xFFEFB0FF;
        };
    }

    private static double profileAttributeAngle(int index, int attributeCount) {
        return -Math.PI / 2.0D + index * Math.PI * 2.0D / attributeCount;
    }

    private static ProfilePoint profilePoint(int centerX, int centerY, double angle, int radius) {
        int x = centerX + Mth.floor(Math.cos(angle) * radius);
        int y = centerY + Mth.floor(Math.sin(angle) * radius);
        return new ProfilePoint(x, y);
    }

    private static void drawPixelLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;
        int x = x0;
        int y = y0;

        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                return;
            }
            int doubledError = error * 2;
            if (doubledError > -dy) {
                error -= dy;
                x += sx;
            }
            if (doubledError < dx) {
                error += dx;
                y += sy;
            }
        }
    }

    private void renderGiftPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = giftInventoryLeft();
        int top = giftInventoryTop();
        int hoveredSlot = giftSlotAt(mouseX, mouseY);

        renderGiftSlots(graphics, left, top, hoveredSlot);
        renderGiftInfoIcon(graphics, mouseX, mouseY);
        renderGiftButton(graphics, mouseX, mouseY, partialTick);

        ItemStack hoveredStack = stackForInventorySlot(hoveredSlot);
        if (isPointInsideGiftInfoIcon(mouseX, mouseY)) {
            renderGiftKnowledgeTooltip(graphics, mouseX, mouseY);
        } else if (!hoveredStack.isEmpty()) {
            graphics.renderTooltip(this.font, hoveredStack, mouseX, mouseY);
        }
    }

    private void renderGiftSlots(GuiGraphics graphics, int left, int top, int hoveredSlot) {
        graphics.blit(
                VillagerRetaliationClientAssets.GIFT_INVENTORY_TEXTURE,
                left,
                top,
                0,
                0,
                INVENTORY_TEXTURE_WIDTH,
                INVENTORY_TEXTURE_HEIGHT,
                INVENTORY_TEXTURE_WIDTH,
                INVENTORY_TEXTURE_HEIGHT
        );

        for (int row = 0; row < INVENTORY_MAIN_ROWS; row++) {
            for (int column = 0; column < INVENTORY_COLUMNS; column++) {
                int inventorySlot = 9 + row * INVENTORY_COLUMNS + column;
                renderGiftSlot(
                        graphics,
                        inventorySlot,
                        left + INVENTORY_SLOT_START_X + column * INVENTORY_SLOT_SIZE,
                        top + INVENTORY_SLOT_START_Y + row * INVENTORY_SLOT_SIZE,
                        hoveredSlot
                );
            }
        }

        for (int column = 0; column < INVENTORY_COLUMNS; column++) {
            renderGiftSlot(
                    graphics,
                    column,
                    left + INVENTORY_SLOT_START_X + column * INVENTORY_SLOT_SIZE,
                    top + INVENTORY_HOTBAR_Y,
                    hoveredSlot
            );
        }
    }

    private void renderGiftSlot(GuiGraphics graphics, int inventorySlot, int x, int y, int hoveredSlot) {
        boolean selected = inventorySlot == this.selectedInventorySlot;
        boolean hovered = inventorySlot == hoveredSlot;
        ItemStack stack = stackForInventorySlot(inventorySlot);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + INVENTORY_ITEM_OFFSET, y + INVENTORY_ITEM_OFFSET);
            graphics.renderItemDecorations(this.font, stack, x + INVENTORY_ITEM_OFFSET, y + INVENTORY_ITEM_OFFSET);
        }
        if (selected || hovered) {
            int color = selected ? 0x88EAE6DC : 0x55FFFFFF;
            graphics.fill(x + INVENTORY_ITEM_OFFSET, y + INVENTORY_ITEM_OFFSET, x + 16 + INVENTORY_ITEM_OFFSET, y + 16 + INVENTORY_ITEM_OFFSET, color);
        }
    }

    private void renderGiftButton(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.giftButton == null) {
            return;
        }
        GiftButtonBounds bounds = giftButtonBounds();
        boolean enabled = this.selectedInventorySlot >= 0 && !stackForInventorySlot(this.selectedInventorySlot).isEmpty();
        this.giftButton.setPosition(bounds.left(), bounds.top());
        this.giftButton.setMessage(Component.literal(giftButtonLabel()));
        this.giftButton.active = enabled;
        this.giftButton.visible = true;
        this.giftButton.render(graphics, mouseX, mouseY, partialTick);
    }

    private String giftButtonLabel() {
        ItemStack selectedStack = stackForInventorySlot(this.selectedInventorySlot);
        return selectedStack.getCount() > 1 ? translate("gift.give_stack") : translate("gift.give");
    }

    private void renderOption(
            GuiGraphics graphics,
            DialogueOption option,
            int index,
            int hovered,
            int mouseX,
            int mouseY,
            int left,
            float y,
            int viewportTop,
            int viewportBottom
    ) {
        boolean selected = index == this.selectedOption;
        boolean isHovered = hovered == index;
        float hoverMix = isHovered ? hoverIntensity(mouseX, mouseY, left, y) : 0.0F;
        float scale = optionScale(selected, hoverMix);
        float cursorShiftX = isHovered ? hoverShift(mouseX, left, OPTION_WIDTH, 3.2F) * hoverMix : 0.0F;
        float cursorShiftY = isHovered ? hoverShift(mouseY, y, OPTION_HEIGHT, 1.6F) * hoverMix : 0.0F;
        float edgeAlpha = edgeFadeAlpha(y, viewportTop, viewportBottom);
        int textColor = optionTextColor(selected, isHovered);

        graphics.pose().pushPose();
        applyOptionTransform(graphics, left, y, scale, cursorShiftX, cursorShiftY);
        renderOptionBackground(graphics, isHovered, left, y, edgeAlpha);
        if (selected) {
            graphics.drawString(this.font, ">", left - 7, Mth.floor(y + 5.0F), VillagerInteractionUiUtil.withAlpha(0xFFFFFFFF, edgeAlpha), false);
        }
        graphics.drawString(this.font, option.label(), left + OPTION_TEXT_INSET, Mth.floor(y + 5.0F), VillagerInteractionUiUtil.withAlpha(textColor, edgeAlpha), false);
        graphics.pose().popPose();
    }

    private void applyOptionTransform(GuiGraphics graphics, int left, float top, float scale, float shiftX, float shiftY) {
        float pivotX = left + OPTION_WIDTH * 0.5F;
        float pivotY = top + OPTION_HEIGHT * 0.5F;
        graphics.pose().translate(pivotX + shiftX, pivotY + shiftY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-pivotX, -pivotY, 0.0F);
    }

    private void renderOptionBackground(GuiGraphics graphics, boolean hovered, int left, float top, float edgeAlpha) {
        if (!hovered) {
            return;
        }

        int bgLeft = left - 12;
        int bgTop = Mth.floor(top + 1.0F);
        int bgRight = left + OPTION_WIDTH - 8;
        int bgBottom = bgTop + OPTION_HEIGHT - 1;
        graphics.fill(bgLeft, bgTop, bgRight, bgBottom, VillagerInteractionUiUtil.withAlpha(0xFF000000, edgeAlpha * 0.16F));
    }

    private static float optionScale(boolean selected, float hoverMix) {
        return 1.0F + (selected ? OPTION_SELECTED_SCALE : 0.0F) + hoverMix * OPTION_HOVER_SCALE;
    }

    private static int optionTextColor(boolean selected, boolean hovered) {
        if (selected) {
            return 0xFFF8F8F4;
        }
        return hovered ? 0xFFE5E5DE : 0xCFC7C8C5;
    }

    private void renderTopBackButton(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isTopBackButtonVisible()) {
            return;
        }

        TopBackButtonBounds bounds = topBackButtonBounds();
        boolean hovered = isPointInsideTopBackButton(mouseX, mouseY);
        int textColor = hovered ? 0xFFF8F8F4 : 0xCFC7C8C5;
        int backgroundColor = hovered ? 0x30000000 : 0x18000000;

        graphics.fill(bounds.left() - 6, bounds.top() - 2, bounds.right() + 4, bounds.bottom() + 2, backgroundColor);
        graphics.drawString(this.font, backLabel(), bounds.left(), bounds.top(), textColor, false);
    }

    private void renderHint(GuiGraphics graphics) {
        String hintText = translate(canNavigateBack() ? "hint.back" : "hint.leave");
        graphics.drawString(this.font, hintText, this.width - this.font.width(hintText) - 8, this.height - 14, 0x66FFFFFF, false);
    }

    void renderBackdropBehindChat(GuiGraphics graphics) {
        int veilTop = interactionVeilTop();
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

    private void renderConversationFocus(GuiGraphics graphics, int optionsTop, int mouseX, int mouseY) {
        int dividerX = dividerX();
        int infoBaseY = Mth.floor(optionTextTop(optionsTop));
        int infoLineGap = optionStride();

        drawRightAlignedInfo(graphics, this.villagerName, infoBaseY, INFO_VALUE_COLOR, dividerX);
        drawRightAlignedInfo(graphics, this.professionName, infoBaseY + infoLineGap, INFO_SECONDARY_COLOR, dividerX);
        drawRightAlignedInfo(graphics, genderText(), infoBaseY + infoLineGap * 2, INFO_SECONDARY_COLOR, dividerX);
        drawRightAlignedInfo(graphics, moodText(), infoBaseY + infoLineGap * 3, moodColor(this.primaryMood), dividerX);
        drawRightAlignedInfo(graphics, reputationText(), infoBaseY + infoLineGap * 4, INFO_LABEL_COLOR, dividerX);
        renderFamilyButton(graphics, mouseX, mouseY, infoBaseY + infoLineGap * 5, dividerX);
        renderRelationshipButton(graphics, mouseX, mouseY, infoBaseY + infoLineGap * 6, dividerX);
    }

    private void drawRightAlignedInfo(GuiGraphics graphics, String text, int y, int color, int dividerX) {
        graphics.drawString(this.font, text, dividerX - 28 - this.font.width(text), y, color, false);
    }

    private void renderFamilyButton(GuiGraphics graphics, int mouseX, int mouseY, int y, int dividerX) {
        String text = familyButtonText();
        FamilyButtonBounds bounds = familyButtonBounds(y, dividerX, text);
        renderInfoActionButton(graphics, text, y, bounds, mouseX, mouseY, isFamilyPageActive());
    }

    private void renderRelationshipButton(GuiGraphics graphics, int mouseX, int mouseY, int y, int dividerX) {
        String text = relationshipButtonText();
        FamilyButtonBounds bounds = familyButtonBounds(y, dividerX, text);
        renderInfoActionButton(graphics, text, y, bounds, mouseX, mouseY, this.page == DialoguePage.RELATIONSHIPS);
    }

    private void renderInfoActionButton(
            GuiGraphics graphics,
            String text,
            int y,
            FamilyButtonBounds bounds,
            int mouseX,
            int mouseY,
            boolean active) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        int color = active ? INFO_VALUE_COLOR : hovered ? 0xFFE5E5DE : INFO_SECONDARY_COLOR;
        if (hovered || active) {
            graphics.fill(bounds.left() - 6, bounds.top() - 2, bounds.right() + 4, bounds.bottom() + 2, hovered ? 0x30000000 : 0x18000000);
        }
        graphics.drawString(this.font, text, bounds.left(), y, color, false);
    }

    private boolean isFamilyPageActive() {
        return this.page == DialoguePage.FAMILY
                || this.page == DialoguePage.ANCESTRY
                || this.page == DialoguePage.DESCENDANTS;
    }

    private void renderDivider(GuiGraphics graphics, int optionsTop) {
        int dividerX = dividerX();
        int dividerTop = conversationInfoTop() - 12;
        int dividerBottom = conversationInfoTop() + rootOptionViewportHeight() + 2;
        int lineLeft = dividerX - 1;
        int lineRight = dividerX + 1;
        int selectorTop = dividerTop + (DIVIDER_HEIGHT - DIVIDER_SELECT_HEIGHT) / 2;
        int selectorBottom = selectorTop + DIVIDER_SELECT_HEIGHT;

        float selectorAnchorY = this.page == DialoguePage.GIFT ? Float.NaN : dividerSelectorAnchorY(optionsTop, dividerTop, dividerBottom);
        if (!Float.isNaN(selectorAnchorY)) {
            selectorTop = Mth.floor(selectorAnchorY + OPTION_HEIGHT * 0.5F - DIVIDER_SELECT_HEIGHT * 0.5F);
            selectorTop = Mth.clamp(selectorTop, dividerTop, dividerBottom - DIVIDER_SELECT_HEIGHT);
            selectorBottom = selectorTop + DIVIDER_SELECT_HEIGHT;
        }

        graphics.fill(lineLeft, dividerTop, lineRight, selectorTop, DIVIDER_CORE_COLOR);
        graphics.fill(lineLeft, selectorBottom, lineRight, dividerBottom, DIVIDER_CORE_COLOR);

        int selectorLeft = lineRight - DIVIDER_SELECT_WIDTH;
        graphics.blit(
                VillagerRetaliationClientAssets.DIVIDER_SELECT_TEXTURE,
                selectorLeft,
                selectorTop,
                0,
                0,
                DIVIDER_SELECT_WIDTH,
                DIVIDER_SELECT_HEIGHT,
                DIVIDER_SELECT_WIDTH,
                DIVIDER_SELECT_HEIGHT
        );
    }

    private float dividerSelectorAnchorY(int optionsTop, int dividerTop, int dividerBottom) {
        if (this.selectedOption < 0 || this.selectedOption >= this.options.size()) {
            return Float.NaN;
        }

        int viewportTop = optionsTop;
        int viewportBottom = optionsTop + optionViewportHeight();
        float selectedY = optionsTop + this.selectedOption * optionStride() - this.optionScroll;
        if (isOptionTextFullyVisible(selectedY, viewportTop, viewportBottom)) {
            return selectedY;
        }

        float selectedTextTop = optionTextTop(selectedY);
        float selectedTextBottom = selectedTextTop + this.font.lineHeight;
        if (selectedTextBottom > viewportBottom) {
            for (int index = this.selectedOption - 1; index >= 0; index--) {
                float optionY = optionsTop + index * optionStride() - this.optionScroll;
                if (isOptionTextFullyVisible(optionY, viewportTop, viewportBottom)) {
                    return optionY;
                }
            }
        } else if (selectedTextTop < viewportTop) {
            for (int index = this.selectedOption + 1; index < this.options.size(); index++) {
                float optionY = optionsTop + index * optionStride() - this.optionScroll;
                if (isOptionTextFullyVisible(optionY, viewportTop, viewportBottom)) {
                    return optionY;
                }
            }
        }

        return Mth.clamp(selectedY, dividerTop, dividerBottom - OPTION_HEIGHT);
    }

    private boolean isOptionTextFullyVisible(float optionY, int viewportTop, int viewportBottom) {
        float textTop = optionTextTop(optionY);
        float textBottom = textTop + this.font.lineHeight;
        return textTop >= viewportTop && textBottom <= viewportBottom;
    }

    private float optionTextTop(float optionY) {
        return optionY + 5.0F;
    }

    private void updateMouseSelection(int mouseX, int mouseY) {
        int hovered = optionAt(mouseX, mouseY);
        if (hovered >= 0) {
            this.selectedOption = hovered;
        }
    }

    private int optionAt(double mouseX, double mouseY) {
        int left = optionsLeft();
        int top = optionsTop();
        int bottom = top + optionViewportHeight();
        if (mouseX < left - 18 || mouseX > left + OPTION_WIDTH) {
            return -1;
        }
        if (mouseY < top - 2 || mouseY > bottom + 2) {
            return -1;
        }
        for (int index = 0; index < this.options.size(); index++) {
            float y = top + index * optionStride() - this.optionScroll;
            if (mouseY >= y - 2.0F && mouseY <= y + OPTION_HEIGHT + 2.0F) {
                return index;
            }
        }
        return -1;
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
        if (!isPointInsideFamilyButton(mouseX, mouseY)) {
            return false;
        }
        openFamilyPage();
        return true;
    }

    private boolean tryClickRelationshipButton(double mouseX, double mouseY) {
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

        VillagerSkill clickedSkill = skillAt(entry.get(), mouseX, mouseY);
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
        int hovered = optionAt(mouseX, mouseY);
        if (hovered < 0) {
            return false;
        }

        this.selectedOption = hovered;
        ensureSelectedVisible();
        activateSelected();
        return true;
    }

    private boolean tryClickGiftPage(double mouseX, double mouseY) {
        int clickedSlot = giftSlotAt(mouseX, mouseY);
        if (clickedSlot >= 0) {
            ItemStack stack = stackForInventorySlot(clickedSlot);
            if (!stack.isEmpty()) {
                this.selectedInventorySlot = clickedSlot;
            }
            return true;
        }
        return false;
    }

    private VillagerSkill skillAt(VillagerProfileClientCache.DisplayEntry profile, double mouseX, double mouseY) {
        int left = skillsPanelLeft();
        int top = skillsPanelTop() + SKILLS_CONTAINER_PADDING_Y;
        List<VillagerSkillValue> highlights = profile.bestSkills(VillagerSkill.values().length);
        int columnWidth = (OPTION_WIDTH - 8 - PROFILE_SKILL_COLUMN_GAP) / PROFILE_SKILL_COLUMNS;
        for (int index = 0; index < highlights.size(); index++) {
            VillagerSkillValue skillValue = highlights.get(index);
            int column = index % PROFILE_SKILL_COLUMNS;
            int row = index / PROFILE_SKILL_COLUMNS;
            int rowLeft = left + column * (columnWidth + PROFILE_SKILL_COLUMN_GAP);
            int y = top + this.font.lineHeight + 4 + row * (PROFILE_SKILL_ROW_HEIGHT + PROFILE_SKILL_ROW_GAP);
            boolean rowHovered = mouseX >= rowLeft - 2
                    && mouseX <= rowLeft + columnWidth + 2
                    && mouseY >= y - 1
                    && mouseY <= y + PROFILE_SKILL_ROW_HEIGHT - 1;
            if (rowHovered) {
                return skillValue.skill();
            }
        }
        return null;
    }

    private int giftSlotAt(double mouseX, double mouseY) {
        int left = giftInventoryLeft();
        int top = giftInventoryTop();
        int slotLeft = left + INVENTORY_SLOT_START_X;
        int mainTop = top + INVENTORY_SLOT_START_Y;
        if (mouseX >= slotLeft && mouseX < slotLeft + INVENTORY_COLUMNS * INVENTORY_SLOT_SIZE
                && mouseY >= mainTop && mouseY < mainTop + INVENTORY_MAIN_ROWS * INVENTORY_SLOT_SIZE) {
            int column = Mth.floor((mouseX - slotLeft) / INVENTORY_SLOT_SIZE);
            int row = Mth.floor((mouseY - mainTop) / INVENTORY_SLOT_SIZE);
            return 9 + row * INVENTORY_COLUMNS + column;
        }

        int hotbarTop = top + INVENTORY_HOTBAR_Y;
        if (mouseX >= slotLeft && mouseX < slotLeft + INVENTORY_COLUMNS * INVENTORY_SLOT_SIZE
                && mouseY >= hotbarTop && mouseY < hotbarTop + INVENTORY_SLOT_SIZE) {
            return Mth.floor((mouseX - slotLeft) / INVENTORY_SLOT_SIZE);
        }
        return -1;
    }

    private int firstGiftableInventorySlot() {
        for (int slot = 0; slot < 36; slot++) {
            if (!stackForInventorySlot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private ItemStack stackForInventorySlot(int inventorySlot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || inventorySlot < 0 || inventorySlot >= 36) {
            return ItemStack.EMPTY;
        }
        return minecraft.player.getInventory().getItem(inventorySlot);
    }

    private int giftInventoryLeft() {
        return optionsLeft() + 10;
    }

    private int giftInventoryTop() {
        return conversationInfoTop();
    }

    private GiftButtonBounds giftButtonBounds() {
        int left = giftInventoryLeft() + INVENTORY_TEXTURE_WIDTH - INVENTORY_BUTTON_WIDTH;
        int top = giftInventoryTop() - INVENTORY_BUTTON_HEIGHT - INVENTORY_BUTTON_GAP;
        return new GiftButtonBounds(left, top, left + INVENTORY_BUTTON_WIDTH, top + INVENTORY_BUTTON_HEIGHT);
    }

    private void renderGiftInfoIcon(GuiGraphics graphics, int mouseX, int mouseY) {
        GiftInfoIconBounds bounds = giftInfoIconBounds();
        graphics.blit(
                VillagerRetaliationClientAssets.GIFT_INFO_ICON_TEXTURE,
                bounds.left(),
                bounds.top(),
                0,
                0,
                GIFT_INFO_ICON_SIZE,
                GIFT_INFO_ICON_SIZE,
                GIFT_INFO_ICON_SIZE,
                GIFT_INFO_ICON_SIZE
        );
    }

    private void renderGiftKnowledgeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + "gift.known_gifts").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal(this.professionName).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.empty());
        if (this.knownLikedGiftNames.isEmpty() && this.knownDislikedGiftNames.isEmpty()) {
            tooltip.add(Component.translatable(GUI_KEY_PREFIX + "gift.learn_more").withStyle(ChatFormatting.GRAY));
        } else {
            addGiftTooltipSection(tooltip, "gift.likes", this.knownLikedGiftNames, ChatFormatting.GREEN);
            addGiftTooltipSection(tooltip, "gift.dislikes", this.knownDislikedGiftNames, ChatFormatting.RED);
        }
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    private static void addGiftTooltipSection(List<Component> tooltip, String labelKey, List<String> giftNames, ChatFormatting color) {
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + labelKey + "_header").withStyle(color));
        if (giftNames.isEmpty()) {
            tooltip.add(Component.translatable(GUI_KEY_PREFIX + "gift.unknown_indented").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (String giftName : giftNames) {
            tooltip.add(Component.literal("  " + giftName).withStyle(color));
        }
    }

    private boolean isPointInsideGiftInfoIcon(double mouseX, double mouseY) {
        GiftInfoIconBounds bounds = giftInfoIconBounds();
        return mouseX >= bounds.left()
                && mouseX <= bounds.right()
                && mouseY >= bounds.top()
                && mouseY <= bounds.bottom();
    }

    private GiftInfoIconBounds giftInfoIconBounds() {
        GiftButtonBounds giftButton = giftButtonBounds();
        int left = giftButton.left() - GIFT_INFO_ICON_GAP - GIFT_INFO_ICON_SIZE;
        int top = giftButton.top() + (INVENTORY_BUTTON_HEIGHT - GIFT_INFO_ICON_SIZE) / 2;
        return new GiftInfoIconBounds(left, top, left + GIFT_INFO_ICON_SIZE, top + GIFT_INFO_ICON_SIZE);
    }

    private boolean isPointInsideOptionScrollArea(double mouseX, double mouseY) {
        int left = optionsLeft() - 18;
        int right = optionsLeft() + OPTION_WIDTH + 4;
        int top = optionsTop();
        int bottom = top + optionViewportHeight();
        return mouseX >= left && mouseX <= right && mouseY >= top - 4 && mouseY <= bottom + 4;
    }

    private boolean isPointInsideSkillsInfoScrollArea(double mouseX, double mouseY) {
        int left = optionsLeft() - 18;
        int right = optionsLeft() + OPTION_WIDTH + 4;
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
        int textWidth = this.font.width(backLabel());
        int left = optionsLeft() + OPTION_TEXT_INSET;
        int contentTop = this.page == DialoguePage.GIFT
                ? giftInventoryTop()
                : this.page == DialoguePage.PROFILE || this.page == DialoguePage.SKILLS ? conversationInfoTop() : optionsTop();
        int top = contentTop - this.font.lineHeight - TOP_BACK_BUTTON_GAP;
        int bottom = top + this.font.lineHeight;
        return new TopBackButtonBounds(left, left + textWidth, top, bottom);
    }

    private int skillsPanelTop() {
        int panelHeight = skillsContainerHeight();
        int minTop = 32;
        int maxTop = Math.max(minTop, this.height - panelHeight - 32);
        int centeredTop = (this.height - panelHeight) / 2;
        int aboveInfoTop = interactionVeilTop() - panelHeight - 14;
        return Mth.clamp(Math.min(centeredTop, aboveInfoTop), minTop, maxTop);
    }

    private int skillsPanelLeft() {
        int maxLeft = Math.max(8, this.width - OPTION_WIDTH - 8);
        int preferredLeft = this.width - OPTION_WIDTH - SKILLS_RIGHT_MARGIN;
        int minLeft = optionsLeft() + OPTION_WIDTH + 36;
        return Math.min(maxLeft, Math.max(minLeft, preferredLeft));
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
        return Math.max(0.0F, Mth.floor(optionTextTop(0)) + skillsInfoContentHeight() - skillInfoViewportHeight());
    }

    private void setTargetSkillScroll(float scroll) {
        this.targetSkillScroll = Mth.clamp(scroll, 0.0F, maxSkillScroll());
    }

    private int skillsInfoContentHeight() {
        int width = OPTION_WIDTH - 12;
        int y = this.font.lineHeight + optionStride() - this.font.lineHeight;
        if (this.selectedSkillDetails != null) {
            Optional<VillagerProfileClientCache.DisplayEntry> entry = VillagerProfileClientCache.get(this.villagerEntityId);
            if (entry.isPresent()) {
                VillagerProfileClientCache.DisplayEntry profile = entry.get();
                y = wrappedInfoLineBottom(
                        Component.translatable(
                                GUI_KEY_PREFIX + "profile.tooltip.level",
                                localizedSkillRank(profile.skillRank(this.selectedSkillDetails))),
                        y,
                        width);
                y = wrappedInfoLineBottom(
                        Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.score", profile.skillValue(this.selectedSkillDetails)),
                        y + 2,
                        width);
                return wrappedInfoLineBottom(Component.literal(localizedExpandedSkillDescription(this.selectedSkillDetails)), y + 4, width);
            }
        }
        y = wrappedInfoLineBottom(Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.trade"), y, width);
        y = wrappedInfoLineBottom(Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.specialty"), y + 4, width);
        return wrappedInfoLineBottom(Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.recruit"), y + 4, width);
    }

    private int wrappedInfoLineBottom(Component component, int top, int width) {
        int lines = this.font.split(component, width).size();
        return top + lines * (this.font.lineHeight + 2);
    }

    private int interactionVeilTop() {
        return Math.max(0, conversationInfoTop() + VEIL_DITHER_START_OFFSET);
    }

    private int skillInfoViewportTop() {
        return conversationInfoTop();
    }

    private int skillInfoViewportBottom() {
        int infoBaseY = Mth.floor(optionTextTop(conversationInfoTop()));
        int familyY = infoBaseY + optionStride() * 5;
        FamilyButtonBounds bounds = familyButtonBounds(familyY, dividerX(), familyButtonText());
        return bounds.bottom() + 2;
    }

    private int skillInfoViewportHeight() {
        return Math.max(1, skillInfoViewportBottom() - skillInfoViewportTop());
    }

    private int optionsTop() {
        return focusCenterY() - optionViewportHeight() / 2;
    }

    private int conversationInfoTop() {
        return focusCenterY() - rootOptionViewportHeight() / 2;
    }

    private int optionsLeft() {
        return dividerX() + 20;
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
        int infoBaseY = Mth.floor(optionTextTop(conversationInfoTop()));
        int y = infoBaseY + optionStride() * 5;
        return familyButtonBounds(y, dividerX(), familyButtonText()).contains(mouseX, mouseY);
    }

    private boolean isPointInsideRelationshipButton(double mouseX, double mouseY) {
        int infoBaseY = Mth.floor(optionTextTop(conversationInfoTop()));
        int y = infoBaseY + optionStride() * 6;
        return familyButtonBounds(y, dividerX(), relationshipButtonText()).contains(mouseX, mouseY);
    }

    private FamilyButtonBounds familyButtonBounds(int y, int dividerX, String text) {
        int width = this.font.width(text);
        int left = dividerX - 28 - width;
        return new FamilyButtonBounds(left, left + width, y, y + this.font.lineHeight);
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
        if (position.anchorsCenter()) {
            return this.width - CHAT_EDGE_MARGIN * 2;
        }
        if (position.anchorsRight()) {
            return this.width - optionsLeft() - OPTION_WIDTH - INFO_PANEL_CHAT_PADDING;
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

    private int optionViewportHeight() {
        int visibleRows = Math.min(OPTION_VIEWPORT_ROWS, Math.max(1, this.options.size()));
        return visibleRows * OPTION_HEIGHT + Math.max(0, visibleRows - 1) * OPTION_GAP;
    }

    private int rootOptionViewportHeight() {
        return INFO_PANEL_ROWS * OPTION_HEIGHT + Math.max(0, INFO_PANEL_ROWS - 1) * OPTION_GAP;
    }

    private float maxOptionScroll() {
        return Math.max(0.0F, optionContentHeight() - optionViewportHeight());
    }

    private float optionContentHeight() {
        if (this.options.isEmpty()) {
            return 0.0F;
        }
        return this.options.size() * OPTION_HEIGHT + Math.max(0, this.options.size() - 1) * OPTION_GAP;
    }

    private int optionStride() {
        return OPTION_HEIGHT + OPTION_GAP;
    }

    private void ensureSelectedVisible() {
        if (this.selectedOption < 0 || this.selectedOption >= this.options.size()) {
            return;
        }

        float optionTop = this.selectedOption * optionStride();
        float optionBottom = optionTop + OPTION_HEIGHT;
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
                optionY + OPTION_HEIGHT,
                viewportTop,
                viewportBottom,
                16.0F
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
                OPTION_SCROLLBAR_WIDTH,
                OPTION_SCROLLBAR_HIT_WIDTH,
                18,
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
                OPTION_SCROLLBAR_WIDTH,
                OPTION_SCROLLBAR_HIT_WIDTH,
                18,
                this.skillScroll,
                maxScroll,
                skillsInfoContentHeight()
        );
    }

    private int optionsScrollbarLeft() {
        return optionsLeft() + OPTION_WIDTH + OPTION_SCROLLBAR_OFFSET;
    }

    private float hoverIntensity(double mouseX, double mouseY, int left, float top) {
        double normalizedX = Math.abs(((mouseX - left) / OPTION_WIDTH) * 2.0D - 1.0D);
        double normalizedY = Math.abs(((mouseY - top) / OPTION_HEIGHT) * 2.0D - 1.0D);
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

    private record ProfilePoint(int x, int y) {
    }

    private record TopBackButtonBounds(int left, int right, int top, int bottom) {
    }

    private record GiftButtonBounds(int left, int top, int right, int bottom) {
    }

    private record GiftInfoIconBounds(int left, int top, int right, int bottom) {
    }

    record ChatRenderLayout(int left, int top, int right, int bottom, int xOffset, int yOffset) {
        int translatedMouseX(int mouseX) {
            return mouseX - this.xOffset;
        }

        int translatedMouseY(int mouseY) {
            return mouseY - this.yOffset;
        }
    }

    private record FamilyButtonBounds(int left, int right, int top, int bottom) {
        boolean contains(double mouseX, double mouseY) {
            return VillagerClientUiUtil.containsInclusive(mouseX, mouseY, this.left, this.top - 2, this.right, this.bottom + 2);
        }
    }
}
