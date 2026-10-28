package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen.DialogueOption;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionScreen.DialoguePage;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.network.HiredBuilderOrderPayload;
import java.util.LinkedHashSet;
import java.util.Set;

final class BuilderScreenController {
    private static final Runnable NO_ACTION = () -> {};
    private final Host host;
    private String selectedBuilderCategory;
    private BuilderStructureCatalog.Entry selectedBuilderStructure;

    BuilderScreenController(Host host) { this.host = host; }

    void addBuilderCategoryOptions() {
        Set<String> categories = new LinkedHashSet<>();
        for (BuilderStructureCatalog.Entry entry : BuilderStructureCatalog.entries()) {
            categories.add(entry.category());
        }
        if (categories.isEmpty()) {
            host.option(DialogueOption.enabled(host.translate("recruit.builder_no_structures"), NO_ACTION));
        }
        for (String category : categories) {
            host.option(DialogueOption.enabled(
                    host.translate("recruit.builder_category", category),
                    () -> openBuilderStructureCategoryPage(category)));
        }
        addOption("recruit.nevermind", host::openReturnPage);
    }

    void addBuilderStructureOptions() {
        if (this.selectedBuilderCategory == null || this.selectedBuilderCategory.isBlank()) {
            openBuilderStructuresPage();
            return;
        }
        int added = 0;
        for (BuilderStructureCatalog.Entry entry : BuilderStructureCatalog.entries()) {
            if (!entry.category().equals(this.selectedBuilderCategory)) {
                continue;
            }
            host.option(DialogueOption.enabled(entry.label(), () -> {
                this.selectedBuilderStructure = entry;
                requestBuilderOrder(HiredBuilderOrderPayload.Action.PREVIEW, entry);
                host.openPage(DialoguePage.BUILDER_CONFIRM);
            }));
            added++;
        }
        if (added == 0) {
            host.option(DialogueOption.enabled(host.translate("recruit.builder_no_category_structures"), NO_ACTION));
        }
        addOption("recruit.nevermind", this::openBuilderStructuresPage);
    }

    void addBuilderConfirmOptions() {
        if (this.selectedBuilderStructure == null) {
            openBuilderStructuresPage();
            return;
        }
        host.option(DialogueOption.enabled(host.translate("recruit.builder_selected", this.selectedBuilderStructure.menuLabel()), NO_ACTION));
        addOption("recruit.builder_confirm", () -> requestBuilderOrder(HiredBuilderOrderPayload.Action.CONFIRM, this.selectedBuilderStructure));
        addOption("recruit.builder_pick_another", this::openBuilderStructuresPage);
        addOption("recruit.nevermind", host::openReturnPage);
    }

    void openBuilderStructuresPage() {
        this.selectedBuilderCategory = null;
        this.selectedBuilderStructure = null;
        host.openPage(DialoguePage.BUILDER_STRUCTURES);
    }

    private void openBuilderStructureCategoryPage(String category) {
        this.selectedBuilderCategory = category;
        this.selectedBuilderStructure = null;
        host.openPage(DialoguePage.BUILDER_STRUCTURE_CATEGORY);
    }

    private void requestBuilderOrder(HiredBuilderOrderPayload.Action action, BuilderStructureCatalog.Entry entry) {
        host.sendToServer(new HiredBuilderOrderPayload(host.entityId(), action, entry.id()));
        if (action == HiredBuilderOrderPayload.Action.CONFIRM) {
            host.openWorkPage();
        }
    }

    boolean hasSelectedCategory() {
        return this.selectedBuilderCategory != null && !this.selectedBuilderCategory.isBlank();
    }

    private void addOption(String key, Runnable action) {
        host.option(DialogueOption.enabled(host.translate(key), action));
    }

    interface Host {
        int entityId();
        String translate(String key, Object... arguments);
        void option(DialogueOption option);
        void openPage(DialoguePage target);
        void openReturnPage();
        void openWorkPage();
        void sendToServer(HiredBuilderOrderPayload payload);
    }
}
