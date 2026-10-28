package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen.DialogueOption;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen.DialoguePage;
import com.jvn.villagerretaliation.duel.DuelAvailabilityReason;
import com.jvn.villagerretaliation.duel.DuelKit;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.network.HiredBuilderOrderPayload;
import com.jvn.villagerretaliation.network.HiredBrewingOrderPayload;
import com.jvn.villagerretaliation.network.OpenVillagerDuelPayload;
import com.jvn.villagerretaliation.network.VillagerDuelRequestPayload;
import com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

public final class InteractionFlowChecks {
    private InteractionFlowChecks() {
    }

    public static void main(String[] args) {
        duelSelectionAndAcknowledgement();
        contractSelectionAndConfirmation();
        brewingMissingSelectionRecovery();
        builderSelectionAndConfirmation();
        optionAvailabilityControlsActivation();
        System.out.println("Passed 5 interaction flow checks.");
    }

    private static void duelSelectionAndAcknowledgement() {
        var host = new Host();
        host.duel.openDuelPage();
        require(host.duelRequest.action() == VillagerDuelRequestPayload.Action.OPEN, "opening requests current availability");
        host.duel.updateDuelStatus(status(2, 24, DuelAvailabilityReason.AVAILABLE));
        require(host.options.getFirst().label().equals("duel.loading"), "another villager cannot replace the pending view");
        host.duel.updateDuelStatus(status(1, 24, DuelAvailabilityReason.AVAILABLE));
        host.openPage(DialoguePage.DUEL_WAGER);
        require(host.options.stream().filter(DialogueOption::locked).count() == 2, "unaffordable fixed wagers stay locked");
        host.options.get(host.options.size() - 2).activate();
        host.duel.updateDuelStatus(status(1, 8, DuelAvailabilityReason.AVAILABLE));
        host.openPage(DialoguePage.DUEL_CONFIRM);
        host.click("duel.confirm");
        require(host.duelRequest.stake() == Integer.MAX_VALUE, "maximum wagers retain their wire sentinel after a balance update");
        require(host.duelRequest.kitId().equals(ResourceLocation.withDefaultNamespace("test_kit")), "the selected server kit is sent");
        require(host.dialogue.equals("starting"), "starting dialogue is displayed before acknowledgment");
        require(host.options.getFirst().label().equals("duel.starting"), "pending requests hide challenge actions");
        host.duel.updateDuelStatus(status(2, 8, DuelAvailabilityReason.PLAYER_BUSY));
        require(!host.closed, "unrelated acknowledgments cannot close the dialogue");
        host.duel.updateDuelStatus(status(1, 8, DuelAvailabilityReason.PLAYER_BUSY));
        require(host.closed, "a successful pending challenge closes the dialogue");
    }

    private static void contractSelectionAndConfirmation() {
        var host = new Host();
        host.contract.addHireRoleOption(HiredVillagerRole.CRAFTSMAN, "craftsman");
        host.click("craftsman");
        require(host.page == DialoguePage.HIRE_DURATION, "role selection opens durations");
        host.click("recruit.hire_three_days");
        require(host.recruitAction == VillagerRecruitRequestPayload.Action.HIRE_THREE_DAYS
                && host.recruitRole == HiredVillagerRole.CRAFTSMAN, "hire duration carries the selected role");
        require(host.contract.roleFor(VillagerRecruitRequestPayload.Action.VIEW_CONTRACT) == null, "other requests omit the hire role");
        host.contract.openHirePage();
        require(host.contract.roleFor(VillagerRecruitRequestPayload.Action.HIRE_ONE_DAY) == null, "reopening the hire list clears selection");
        host.contract.openPartyRecruitConfirmationPage();
        host.click("recruit.nevermind");
        require(host.recruitAction == VillagerRecruitRequestPayload.Action.DECLINE_PARTY_RECRUIT_CONFIRMATION
                && host.page == DialoguePage.RECRUIT, "declining recruitment sends its matching action");
        host.contract.openPartyDismissConfirmationPage();
        host.click("party.dismiss_confirm");
        require(host.recruitAction == VillagerRecruitRequestPayload.Action.PARTY_DISMISS, "dismissal has its own confirmation");
        host.contract.openEndContractConfirmationPage();
        host.click("recruit.end_hire_confirm");
        require(host.recruitAction == VillagerRecruitRequestPayload.Action.END_HIRE, "ending hire clears prior party confirmation state");
    }

    private static void brewingMissingSelectionRecovery() {
        var host = new Host();
        host.brewing.openBrewingPotionPage();
        require(host.options.getFirst().label().equals("recruit.brewing_no_recipes"), "missing worlds show the existing empty state");
        host.openPage(DialoguePage.BREWING_LEVEL);
        require(host.page == DialoguePage.BREWING_POTION, "missing potion selection returns to potion selection");
        host.openPage(DialoguePage.BREWING_AMOUNT);
        require(host.page == DialoguePage.BREWING_POTION, "missing route unwinds to the start");
        host.click("recruit.nevermind");
        require(host.page == DialoguePage.WORK, "cancelling configuration returns to work");
    }

    private static void builderSelectionAndConfirmation() {
        var original = BuilderStructureCatalog.entries();
        var entry = new BuilderStructureCatalog.Entry(ResourceLocation.withDefaultNamespace("test_house"), "houses", "house", 10);
        BuilderStructureCatalog.replaceClientEntries(List.of(entry));
        try {
            var host = new Host();
            host.openPage(DialoguePage.BUILDER_CONFIRM);
            require(host.page == DialoguePage.BUILDER_STRUCTURES, "missing structure selection returns to categories");
            host.click("recruit.builder_category");
            require(host.page == DialoguePage.BUILDER_STRUCTURE_CATEGORY, "category selection opens its structures");
            host.click("house");
            require(host.page == DialoguePage.BUILDER_CONFIRM
                    && host.builderRequest.action() == HiredBuilderOrderPayload.Action.PREVIEW
                    && host.builderRequest.structureId().equals(entry.id()), "structure selection previews the chosen structure");
            host.click("recruit.builder_confirm");
            require(host.builderRequest.action() == HiredBuilderOrderPayload.Action.CONFIRM
                    && host.builderRequest.structureId().equals(entry.id()), "confirmation sends the selected structure");
            require(host.page == DialoguePage.WORK, "confirmation returns to the existing work flow");
            host.builder.openBuilderStructuresPage();
            require(!host.builder.hasSelectedCategory(), "reopening categories clears prior selection");
            host.openPage(DialoguePage.BUILDER_CONFIRM);
            require(host.page == DialoguePage.BUILDER_STRUCTURES, "old structure cannot be confirmed after reopening");
            host.click("recruit.nevermind");
            require(host.page == DialoguePage.RECRUIT, "cancelling returns through the host");
        } finally {
            BuilderStructureCatalog.replaceClientEntries(original);
        }
    }

    private static void optionAvailabilityControlsActivation() {
        int[] calls = {0};
        Runnable action = () -> calls[0]++;
        var unavailable = DialogueOption.available("unavailable", action, false);
        unavailable.activate();
        DialogueOption.checkbox("unavailable checkbox", false, action, true).activate();
        require(calls[0] == 0, "disabled options cannot execute their actions");
        require(!unavailable.enabled() && unavailable.locked(), "disabled appearance matches availability");
        var hint = DialogueOption.hinted("quest hint", action, true);
        hint.activate();
        require(calls[0] == 1 && hint.enabled() && hint.locked(), "visual quest hints remain actionable");
        DialogueOption.available("available", action, true).activate();
        require(calls[0] == 2, "available options execute normally");
    }

    private static OpenVillagerDuelPayload status(int entity, int balance, DuelAvailabilityReason reason) {
        return new OpenVillagerDuelPayload(entity, "villager", reason == DuelAvailabilityReason.AVAILABLE, reason,
                0, 0, 0, 0L, 8, 20, 1200, 1, balance, balance, "emeralds", false,
                List.of(new DuelKit.Summary(ResourceLocation.withDefaultNamespace("test_kit"), "kit", "kit description")),
                "opening", "loadout", "wager", "confirmation", "starting");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Host implements DuelScreenController.Host, ContractScreenController.Host, BrewingWorkScreenController.Host, BuilderScreenController.Host {
        private final DuelScreenController duel = new DuelScreenController(this);
        private final ContractScreenController contract = new ContractScreenController(this);
        private final BuilderScreenController builder = new BuilderScreenController(this);
        private final BrewingWorkScreenController brewing = new BrewingWorkScreenController(this);
        private final List<DialogueOption> options = new ArrayList<>();
        private DialoguePage page = DialoguePage.ROOT;
        private VillagerDuelRequestPayload duelRequest;
        private HiredBuilderOrderPayload builderRequest;
        private VillagerRecruitRequestPayload.Action recruitAction;
        private HiredVillagerRole recruitRole;
        private String dialogue = "";
        private boolean closed;

        private void click(String label) {
            options.stream().filter(option -> option.label().equals(label)).findFirst().orElseThrow().activate();
        }

        @Override public int entityId() { return 1; }
        @Override public boolean matchesVillager(int entityId) { return entityId == 1; }
        @Override public DialoguePage page() { return page; }
        @Override public String translate(String key, Object... arguments) { return key; }
        @Override public void option(DialogueOption option) { options.add(option); }
        @Override public void navigateToRootPage() { openPage(DialoguePage.ROOT); }
        @Override public void leaveConversation() { closed = true; }
        @Override public void dialogue(String text) { dialogue = text; }
        @Override public void sendToServer(VillagerDuelRequestPayload payload) { duelRequest = payload; }
        @Override public void sendToServer(HiredBrewingOrderPayload payload) { throw new AssertionError("unexpected order"); }
        @Override public void sendToServer(HiredBuilderOrderPayload payload) { builderRequest = payload; }
        @Override public void openReturnPage() { openPage(DialoguePage.RECRUIT); }
        @Override public void openWorkPage() { openPage(DialoguePage.WORK); }
        @Override public ClientLevel level() { return null; }
        @Override public boolean recruitedPartyVillager() { return false; }
        @Override public boolean hiredByPlayer() { return true; }
        @Override public boolean oneOffBuilderJob() { return false; }
        @Override public int partyRemainingDays() { return 2; }
        @Override public boolean canOfferContractRole(HiredVillagerRole role) { return true; }
        @Override public void openRecruitPage() { openPage(DialoguePage.RECRUIT); }
        @Override public void requestRecruit(VillagerRecruitRequestPayload.Action action) {
            recruitAction = action;
            recruitRole = contract.roleFor(action);
        }
        @Override public void openPage(DialoguePage target) {
            page = target;
            if (DuelScreenController.isDuelSetupPage(target)) duel.refreshDuelDialogue();
            rebuildOptionsKeepingListPosition();
        }
        @Override public void rebuildOptionsKeepingListPosition() {
            options.clear();
            switch (page) {
                case DUEL -> duel.addDuelOptions();
                case DUEL_WAGER -> duel.addDuelWagerOptions();
                case DUEL_CONFIRM -> duel.addDuelConfirmOptions();
                case HIRE_DURATION -> contract.addHireDurationOptions();
                case END_CONTRACT_CONFIRMATION -> contract.addEndContractConfirmationOptions();
                case BUILDER_STRUCTURES -> builder.addBuilderCategoryOptions();
                case BUILDER_STRUCTURE_CATEGORY -> builder.addBuilderStructureOptions();
                case BUILDER_CONFIRM -> builder.addBuilderConfirmOptions();
                case BREWING_POTION -> brewing.addBrewingPotionOptions();
                case BREWING_LEVEL -> brewing.addBrewingLevelOptions();
                case BREWING_DURATION -> brewing.addBrewingDurationOptions();
                case BREWING_TYPE -> brewing.addBrewingTypeOptions();
                case BREWING_AMOUNT -> brewing.addBrewingAmountOptions();
                default -> {}
            }
        }
    }
}
