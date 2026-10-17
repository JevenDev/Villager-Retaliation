package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen.DialogueOption;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen.DialoguePage;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.VillagerContractTime;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload;

final class ContractScreenController {
    private static final Runnable NO_ACTION = () -> {};
    private final Host host;
    private HiredVillagerRole pendingHireRole;
    private boolean confirmingPartyRecruit;
    private boolean confirmingPartyDismiss;

    ContractScreenController(Host host) { this.host = host; }

    void addHireDurationOptions() {
        if (this.pendingHireRole == null || !host.canOfferContractRole(this.pendingHireRole)) {
            addOption("recruit.nevermind", host::openRecruitPage);
            return;
        }
        host.option(DialogueOption.enabled(host.translate("recruit.hire_selected_role", this.pendingHireRole.label()), NO_ACTION));
        addPassiveOption("recruit.hire_releases_civic_duty");
        addOption("recruit.hire_one_day", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_ONE_DAY));
        addOption("recruit.hire_three_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_THREE_DAYS));
        addOption("recruit.hire_five_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_FIVE_DAYS));
        addOption("recruit.hire_seven_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_SEVEN_DAYS));
        addOption("recruit.hire_fifteen_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_FIFTEEN_DAYS));
        addOption("recruit.hire_thirty_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.HIRE_THIRTY_DAYS));
        addOption("recruit.nevermind", this::openHirePage);
    }

    void addContractOptions() {
        if (host.recruitedPartyVillager()) {
            host.option(DialogueOption.enabled(
                    host.translate("party.companion_permanent"),
                    NO_ACTION));
        }
        addOption(host.recruitedPartyVillager() ? "party.contract_days_left" : "recruit.contract_days_left",
                () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.VIEW_CONTRACT));
        if (!host.recruitedPartyVillager()) addOption("recruit.extend_contract", this::openContractExtensionPage);
        if (host.hiredByPlayer() && !host.oneOffBuilderJob()) {
            addOption("recruit.end_hire", () -> {
                openEndContractConfirmationPage();
                host.requestRecruit(VillagerRecruitRequestPayload.Action.PROMPT_END_HIRE_CONFIRMATION);
            });
        }
        addOption("recruit.nevermind", host::openRecruitPage);
    }

    void addEndContractConfirmationOptions() {
        if (this.confirmingPartyRecruit) {
            addOption("party.recruit_confirm", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.PARTY_RECRUIT));
            addOption("recruit.nevermind", () -> {
                host.requestRecruit(VillagerRecruitRequestPayload.Action.DECLINE_PARTY_RECRUIT_CONFIRMATION);
                host.openRecruitPage();
            });
            return;
        }
        if (this.confirmingPartyDismiss) {
            addOption("party.dismiss_confirm", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.PARTY_DISMISS));
            addOption("recruit.nevermind", () -> {
                host.requestRecruit(VillagerRecruitRequestPayload.Action.DECLINE_PARTY_DISMISS_CONFIRMATION);
                host.openRecruitPage();
            });
            return;
        }
        addOption("recruit.end_hire_confirm", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.END_HIRE));
        addOption("recruit.nevermind", () -> {
            host.requestRecruit(VillagerRecruitRequestPayload.Action.DECLINE_END_HIRE_CONFIRMATION);
            host.openRecruitPage();
        });
    }

    void addContractExtensionOptions() {
        if (host.recruitedPartyVillager()) {
            addOption("party.extend_one_day", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_ONE_DAY));
            addOption("party.extend_three_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_THREE_DAYS));
            addOption("party.extend_five_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_FIVE_DAYS));
            addOption("party.extend_seven_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_SEVEN_DAYS));
            addOption("party.extend_fifteen_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_FIFTEEN_DAYS));
            addPartyMaxExtensionOption();
            addOption("recruit.nevermind", this::openContractPage);
            return;
        }
        addOption("recruit.extend_one_day", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_ONE_DAY));
        addOption("recruit.extend_three_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_THREE_DAYS));
        addOption("recruit.extend_five_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_FIVE_DAYS));
        addOption("recruit.extend_seven_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_SEVEN_DAYS));
        addOption("recruit.extend_fifteen_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_FIFTEEN_DAYS));
        addOption("recruit.extend_thirty_days", () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_THIRTY_DAYS));
        addOption("recruit.nevermind", this::openContractPage);
    }

    private void addPartyMaxExtensionOption() {
        int maxExtensionDays = Math.max(0, VillagerContractTime.MAX_PREPAID_DAYS - host.partyRemainingDays());
        int cost = maxExtensionDays * PartyVillagerContractService.DAILY_EMERALD_COST;
        host.option(DialogueOption.enabled(
                host.translate("party.extend_max_days", maxExtensionDays, cost),
                () -> host.requestRecruit(VillagerRecruitRequestPayload.Action.EXTEND_MAX_DAYS)));
    }

    void addHireRoleOption(HiredVillagerRole role, String labelKey) {
        if (host.canOfferContractRole(role)) {
            addOption(labelKey, () -> {
                this.pendingHireRole = role;
                openHireDurationPage();
            });
        }
    }

    void openHirePage() {
        this.pendingHireRole = null;
        host.requestRecruit(VillagerRecruitRequestPayload.Action.VIEW_CONTRACT);
        host.openPage(DialoguePage.HIRE);
    }

    void openHireDurationPage() {
        host.openPage(DialoguePage.HIRE_DURATION);
    }

    void openContractPage() {
        host.openPage(DialoguePage.CONTRACT);
    }

    void openEndContractConfirmationPage() {
        this.confirmingPartyRecruit = false;
        this.confirmingPartyDismiss = false;
        host.openPage(DialoguePage.END_CONTRACT_CONFIRMATION);
    }

    void openPartyRecruitConfirmationPage() {
        this.confirmingPartyRecruit = true;
        this.confirmingPartyDismiss = false;
        host.requestRecruit(VillagerRecruitRequestPayload.Action.PROMPT_PARTY_RECRUIT_CONFIRMATION);
        host.openPage(DialoguePage.END_CONTRACT_CONFIRMATION);
    }

    void openPartyDismissConfirmationPage() {
        this.confirmingPartyRecruit = false;
        this.confirmingPartyDismiss = true;
        host.requestRecruit(VillagerRecruitRequestPayload.Action.PROMPT_PARTY_DISMISS_CONFIRMATION);
        host.openPage(DialoguePage.END_CONTRACT_CONFIRMATION);
    }

    void openContractExtensionPage() {
        host.openPage(DialoguePage.CONTRACT_EXTENSION);
    }

    private static boolean isHireDurationAction(VillagerRecruitRequestPayload.Action action) {
        return action == VillagerRecruitRequestPayload.Action.HIRE_ONE_DAY
                || action == VillagerRecruitRequestPayload.Action.HIRE_THREE_DAYS
                || action == VillagerRecruitRequestPayload.Action.HIRE_FIVE_DAYS
                || action == VillagerRecruitRequestPayload.Action.HIRE_SEVEN_DAYS
                || action == VillagerRecruitRequestPayload.Action.HIRE_FIFTEEN_DAYS
                || action == VillagerRecruitRequestPayload.Action.HIRE_THIRTY_DAYS;
    }

    HiredVillagerRole roleFor(VillagerRecruitRequestPayload.Action action) {
        return isHireDurationAction(action) ? this.pendingHireRole : null;
    }

    private void addOption(String key, Runnable action) {
        host.option(DialogueOption.enabled(host.translate(key), action));
    }

    private void addPassiveOption(String key) {
        addOption(key, NO_ACTION);
    }

    interface Host {
        boolean recruitedPartyVillager();
        boolean hiredByPlayer();
        boolean oneOffBuilderJob();
        int partyRemainingDays();
        boolean canOfferContractRole(HiredVillagerRole role);
        String translate(String key, Object... arguments);
        void option(DialogueOption option);
        void openPage(DialoguePage page);
        void openRecruitPage();
        void requestRecruit(VillagerRecruitRequestPayload.Action action);
    }
}
