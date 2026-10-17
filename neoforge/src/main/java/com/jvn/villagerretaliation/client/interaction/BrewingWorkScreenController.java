package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen.DialogueOption;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen.DialoguePage;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.network.HiredBrewingOrderPayload;
import java.util.List;
import java.util.Locale;

final class BrewingWorkScreenController {
    private static final Runnable NO_ACTION = () -> {};
    private final Host host;
    private HiredBrewingRecipeCatalog.BrewingPotionChoice selectedBrewingPotionChoice;
    private HiredBrewingRecipeCatalog.BrewingDurationChoice selectedBrewingDurationChoice;
    private HiredBrewingRecipeCatalog.BrewingLevelChoice selectedBrewingLevelChoice;
    private HiredBrewingRecipeCatalog.BrewingRoute selectedBrewingRoute;
    private boolean activeBrewingOrder;

    BrewingWorkScreenController(Host host) { this.host = host; }

    void addBrewingPotionOptions() {
        if (host.level() == null) {
            host.option(DialogueOption.enabled(host.translate("recruit.brewing_no_recipes"), NO_ACTION));
        } else {
            List<HiredBrewingRecipeCatalog.BrewingPotionChoice> choices = HiredBrewingRecipeCatalog.potionChoices(host.level());
            if (choices.isEmpty()) {
                host.option(DialogueOption.enabled(host.translate("recruit.brewing_no_recipes"), NO_ACTION));
            }
            for (HiredBrewingRecipeCatalog.BrewingPotionChoice choice : choices) {
                host.option(DialogueOption.enabled(choice.label(), () -> {
                    this.selectedBrewingPotionChoice = choice;
                    this.selectedBrewingDurationChoice = null;
                    this.selectedBrewingLevelChoice = null;
                    this.selectedBrewingRoute = null;
                    host.openPage(DialoguePage.BREWING_LEVEL);
                }));
            }
        }
        addOption("recruit.nevermind", () -> host.openPage(DialoguePage.WORK));
    }

    void addBrewingLevelOptions() {
        if (this.selectedBrewingPotionChoice == null) {
            openBrewingPotionPage();
            return;
        }
        List<HiredBrewingRecipeCatalog.BrewingLevelChoice> choices = HiredBrewingRecipeCatalog.levelChoices(this.selectedBrewingPotionChoice);
        if (choices.isEmpty()) {
            host.option(DialogueOption.enabled(host.translate("recruit.brewing_no_variants"), NO_ACTION));
        }
        for (HiredBrewingRecipeCatalog.BrewingLevelChoice choice : choices) {
            host.option(DialogueOption.enabled(levelLabel(choice.level()), () -> {
                this.selectedBrewingLevelChoice = choice;
                this.selectedBrewingDurationChoice = null;
                this.selectedBrewingRoute = null;
                host.openPage(DialoguePage.BREWING_DURATION);
            }));
        }
        addOption("recruit.nevermind", this::openBrewingPotionPage);
    }

    void addBrewingDurationOptions() {
        if (this.selectedBrewingLevelChoice == null) {
            host.openPage(DialoguePage.BREWING_LEVEL);
            return;
        }
        List<HiredBrewingRecipeCatalog.BrewingDurationChoice> choices = HiredBrewingRecipeCatalog.durationChoices(this.selectedBrewingLevelChoice);
        if (choices.isEmpty()) {
            host.option(DialogueOption.enabled(host.translate("recruit.brewing_no_variants"), NO_ACTION));
        }
        for (HiredBrewingRecipeCatalog.BrewingDurationChoice choice : choices) {
            host.option(DialogueOption.enabled(durationLabel(choice.durationTicks()), () -> {
                this.selectedBrewingDurationChoice = choice;
                this.selectedBrewingRoute = null;
                host.openPage(DialoguePage.BREWING_TYPE);
            }));
        }
        addOption("recruit.nevermind", () -> host.openPage(DialoguePage.BREWING_LEVEL));
    }

    void addBrewingTypeOptions() {
        if (this.selectedBrewingDurationChoice == null) {
            host.openPage(DialoguePage.BREWING_DURATION);
            return;
        }
        List<HiredBrewingRecipeCatalog.BrewingTypeChoice> choices = HiredBrewingRecipeCatalog.typeChoices(this.selectedBrewingDurationChoice);
        if (choices.isEmpty()) {
            host.option(DialogueOption.enabled(host.translate("recruit.brewing_no_variants"), NO_ACTION));
        }
        for (HiredBrewingRecipeCatalog.BrewingTypeChoice choice : choices) {
            host.option(DialogueOption.enabled(typeLabel(choice), () -> {
                this.selectedBrewingRoute = choice.route();
                host.openPage(DialoguePage.BREWING_AMOUNT);
            }));
        }
        addOption("recruit.nevermind", () -> host.openPage(DialoguePage.BREWING_DURATION));
    }

    void addBrewingAmountOptions() {
        if (this.selectedBrewingRoute == null) {
            host.openPage(DialoguePage.BREWING_TYPE);
            return;
        }
        addBrewingAmountOption("recruit.brewing_amount_1", 1, false);
        addBrewingAmountOption("recruit.brewing_amount_3", 3, false);
        addBrewingAmountOption("recruit.brewing_amount_6", 6, false);
        addBrewingAmountOption("recruit.brewing_amount_9", 9, false);
        addBrewingAmountOption("recruit.brewing_amount_27", 27, false);
        addBrewingAmountOption("recruit.brewing_amount_continuous", 0, true);
        addOption("recruit.nevermind", () -> host.openPage(DialoguePage.BREWING_TYPE));
    }

    void addBrewingAmountOption(String labelKey, int amount, boolean continuous) {
        addOption(labelKey, () -> requestBrewingOrder(this.selectedBrewingRoute, amount, continuous));
    }

    void openBrewingPotionPage() {
        this.selectedBrewingPotionChoice = null;
        this.selectedBrewingDurationChoice = null;
        this.selectedBrewingLevelChoice = null;
        this.selectedBrewingRoute = null;
        host.openPage(DialoguePage.BREWING_POTION);
    }

    private void requestBrewingOrder(HiredBrewingRecipeCatalog.BrewingRoute route, int amount, boolean continuous) {
        if (route == null) {
            return;
        }
        host.sendToServer(new HiredBrewingOrderPayload(
                host.entityId(),
                route.itemId(),
                route.potionId(),
                amount,
                continuous));
        this.activeBrewingOrder = true;
        host.openPage(DialoguePage.WORK);
    }

    private String durationLabel(int durationTicks) {
        if (durationTicks <= 0) {
            return host.translate("recruit.brewing_duration_instant");
        }
        int totalSeconds = Math.max(1, durationTicks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return host.translate("recruit.brewing_duration", String.format(Locale.ROOT, "%d:%02d", minutes, seconds));
    }

    private String levelLabel(int level) {
        return host.translate("recruit.brewing_level", romanNumeral(Math.max(1, level)));
    }

    private String typeLabel(HiredBrewingRecipeCatalog.BrewingTypeChoice choice) {
        String labelKey = choice.type().labelKey();
        if (!labelKey.isBlank()) {
            return host.translate(labelKey);
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

    boolean active() { return this.activeBrewingOrder; }
    void active(boolean active) { this.activeBrewingOrder = active; }

    private void addOption(String key, Runnable action) {
        host.option(DialogueOption.enabled(host.translate(key), action));
    }

    interface Host {
        int entityId();
        net.minecraft.client.multiplayer.ClientLevel level();
        String translate(String key, Object... arguments);
        void option(DialogueOption option);
        void openPage(DialoguePage page);
        void sendToServer(HiredBrewingOrderPayload payload);
    }
}
