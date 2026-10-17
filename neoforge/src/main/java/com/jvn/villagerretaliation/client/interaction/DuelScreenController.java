package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen.DialogueOption;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen.DialoguePage;
import com.jvn.villagerretaliation.duel.DuelAvailabilityReason;
import com.jvn.villagerretaliation.duel.DuelKit;
import com.jvn.villagerretaliation.duel.DuelLoadout;
import com.jvn.villagerretaliation.network.OpenVillagerDuelPayload;
import com.jvn.villagerretaliation.network.VillagerDuelRequestPayload;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;

final class DuelScreenController {
    private static final Runnable NO_ACTION = () -> {};
    private final Host host;
    private static final int[] DUEL_STAKES = {0, 8, 16, 32, 64, Integer.MAX_VALUE};
    private OpenVillagerDuelPayload duelStatus;
    private ResourceLocation duelKitId = DuelLoadout.BARE_HANDED.id();
    private int duelStakeIndex;
    private boolean duelStartPending;

    DuelScreenController(Host host) { this.host = host; }

    public void updateDuelStatus(OpenVillagerDuelPayload status) {
        if (status == null || !host.matchesVillager(status.entityId())) {
            return;
        }
        boolean started = this.duelStartPending && status.reason() == DuelAvailabilityReason.PLAYER_BUSY;
        this.duelStartPending = false;
        this.duelStatus = status;
        if (status.duelKits().stream().noneMatch(kit -> kit.id().equals(this.duelKitId))) {
            this.duelKitId = status.duelKits().isEmpty()
                    ? DuelLoadout.BARE_HANDED.id()
                    : status.duelKits().getFirst().id();
        }
        if (started) {
            host.leaveConversation();
            return;
        }
        if (isDuelSetupPage(host.page())) {
            refreshDuelDialogue();
            host.rebuildOptionsKeepingListPosition();
        }
    }

    void addDuelOptions() {
        if (this.duelStatus == null) {
            addPassiveOption("duel.loading");
            return;
        }
        if (!this.duelStatus.available()) {
            addOption("duel.leave", host::navigateToRootPage);
            return;
        }
        host.option(DialogueOption.enabled(
                host.translate("duel.choose_loadout", selectedDuelLoadoutLabel()),
                () -> host.openPage(DialoguePage.DUEL_LOADOUT)));
        host.option(DialogueOption.enabled(
                host.translate("duel.choose_wager", selectedDuelStakeLabel()),
                () -> host.openPage(DialoguePage.DUEL_WAGER)));
        addOption("duel.issue_challenge", () -> host.openPage(DialoguePage.DUEL_CONFIRM));
        addOption("duel.nevermind", host::navigateToRootPage);
    }

    void addDuelLoadoutOptions() {
        if (!canConfigureDuel()) {
            addDuelSetupUnavailableOption();
            addOption("duel.back", () -> host.openPage(DialoguePage.DUEL));
            return;
        }
        for (DuelKit.Summary kit : this.duelStatus.duelKits()) {
            host.option(DialogueOption.checkbox(
                    kit.description(),
                    this.duelKitId.equals(kit.id()),
                    () -> {
                        this.duelKitId = kit.id();
                        host.openPage(DialoguePage.DUEL);
                    },
                    false));
        }
        addOption("duel.back", () -> host.openPage(DialoguePage.DUEL));
    }

    void addDuelWagerOptions() {
        if (!canConfigureDuel()) {
            addDuelSetupUnavailableOption();
            addOption("duel.back", () -> host.openPage(DialoguePage.DUEL));
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
            host.option(DialogueOption.checkbox(
                    duelStakeOptionLabel(stake, DUEL_STAKES[optionIndex] == Integer.MAX_VALUE),
                    selectedDuelStake() == stake,
                    affordable
                            ? () -> {
                                this.duelStakeIndex = optionIndex;
                                host.openPage(DialoguePage.DUEL);
                            }
                            : NO_ACTION,
                    !affordable));
        }
        addOption("duel.back", () -> host.openPage(DialoguePage.DUEL));
    }

    void addDuelConfirmOptions() {
        if (this.duelStartPending) {
            addPassiveOption("duel.starting");
            return;
        }
        if (!canConfigureDuel()) {
            addDuelSetupUnavailableOption();
            addOption("duel.back", () -> host.openPage(DialoguePage.DUEL));
            return;
        }
        int stake = selectedDuelStake();
        if (stake > duelMaximumStake()) {
            addPassiveOption("duel.wager_unavailable");
            addOption("duel.back", () -> host.openPage(DialoguePage.DUEL_WAGER));
            return;
        }
        addOption("duel.confirm", this::startDuel);
        addOption("duel.change_terms", () -> host.openPage(DialoguePage.DUEL));
    }

    void addDuelSetupUnavailableOption() {
        host.option(DialogueOption.enabled(
                this.duelStatus == null ? host.translate("duel.loading") : duelUnavailableText(),
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
        if (this.duelStatus != null) {
            for (DuelKit.Summary kit : this.duelStatus.duelKits()) {
                if (kit.id().equals(this.duelKitId)) return kit.name();
            }
        }
        return this.duelKitId.toString();
    }

    private String selectedDuelStakeLabel() {
        int stake = selectedDuelStake();
        return stake == 0
                ? host.translate("duel.stake.none")
                : host.translate("duel.stake.summary", stake, duelCurrencyName());
    }

    private String duelStakeOptionLabel(int stake, boolean maximum) {
        if (stake == 0) {
            return host.translate("duel.stake_option.none");
        }
        return host.translate(maximum ? "duel.stake_option.maximum" : "duel.stake_option.amount",
                stake, duelCurrencyName());
    }

    private String duelCurrencyName() {
        return this.duelStatus == null ? "" : this.duelStatus.currencyName();
    }

    private String duelUnavailableText() {
        if (this.duelStatus == null) {
            return host.translate("duel.loading");
        }
        if (this.duelStatus.reason() == DuelAvailabilityReason.COOLDOWN) {
            return I18n.get("villagerretaliation.duel.unavailable.cooldown_remaining",
                    formatDuelTicks(this.duelStatus.cooldownTicks()));
        }
        return I18n.get("villagerretaliation.duel.unavailable."
                + this.duelStatus.reason().name().toLowerCase(Locale.ROOT));
    }

    void refreshDuelDialogue() {
        if (this.duelStatus == null) {
            return;
        }
        String dialogue = this.duelStartPending
                ? this.duelStatus.startingDialogue()
                : switch (host.page()) {
                    case DUEL_LOADOUT -> this.duelStatus.loadoutDialogue();
                    case DUEL_WAGER -> this.duelStatus.wagerDialogue();
                    case DUEL_CONFIRM -> this.duelStatus.confirmationDialogue();
                    default -> this.duelStatus.openingDialogue();
                };
        host.dialogue(dialogue);
    }

    private void startDuel() {
        if (!canConfigureDuel() || selectedDuelStake() > duelMaximumStake()) {
            return;
        }
        this.duelStartPending = true;
        refreshDuelDialogue();
        host.rebuildOptionsKeepingListPosition();
        host.sendToServer(new VillagerDuelRequestPayload(
                host.entityId(),
                VillagerDuelRequestPayload.Action.START,
                this.duelKitId,
                selectedDuelWireStake()));
    }

    private static String formatDuelTicks(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        if (seconds >= 60L && seconds % 60L == 0L) {
            return (seconds / 60L) + "m";
        }
        return seconds + "s";
    }

    void openDuelPage() {
        this.duelStartPending = false;
        host.openPage(DialoguePage.DUEL);
        requestDuelStatus();
    }

    private void requestDuelStatus() {
        host.sendToServer(new VillagerDuelRequestPayload(host.entityId(), VillagerDuelRequestPayload.Action.OPEN));
    }

    static boolean isDuelSetupPage(DialoguePage page) {
        return page == DialoguePage.DUEL
                || page == DialoguePage.DUEL_LOADOUT
                || page == DialoguePage.DUEL_WAGER
                || page == DialoguePage.DUEL_CONFIRM;
    }

    private void addOption(String key, Runnable action) {
        host.option(DialogueOption.enabled(host.translate(key), action));
    }

    private void addPassiveOption(String key) {
        host.option(DialogueOption.enabled(host.translate(key), NO_ACTION));
    }

    interface Host {
        int entityId();
        boolean matchesVillager(int entityId);
        DialoguePage page();
        String translate(String key, Object... arguments);
        void option(DialogueOption option);
        void openPage(DialoguePage page);
        void navigateToRootPage();
        void leaveConversation();
        void rebuildOptionsKeepingListPosition();
        void dialogue(String dialogue);
        void sendToServer(VillagerDuelRequestPayload payload);
    }
}
