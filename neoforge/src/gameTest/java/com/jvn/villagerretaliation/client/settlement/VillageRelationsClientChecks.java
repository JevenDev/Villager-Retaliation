package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.client.exploration.ExploredMapScreen;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyApi;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import com.jvn.villagerretaliation.diplomacy.VillageRelation;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

final class VillageRelationsClientChecks {
    private final VillageAllegianceId town;
    private final SettlementManagementScreen management;
    private final Consumer<String> capture;
    private CompletableFuture<Void> preparation;
    private VillageRelationsScreen relations;
    private ExploredMapScreen map;
    private int phase, ticks;
    private UUID journey;
    private net.minecraft.core.BlockPos mapCenter;
    private int observationCursor;
    private SettlementExchangeClientChecks exchangeChecks;

    VillageRelationsClientChecks(VillageAllegianceId town, SettlementManagementScreen management, Consumer<String> capture) {
        this.town = town; this.management = management; this.capture = capture;
    }

    boolean tick(Minecraft minecraft) {
        require(++ticks < 400, "relations client check timed out at " + phase);
        switch (phase) {
            case 0 -> {
                if (preparation == null) preparation = minecraft.getSingleplayerServer().submit(() -> {
                    var level = minecraft.getSingleplayerServer().overworld();
                    var registry = VillageAllegianceRegistrySavedData.get(level);
                    var center = registry.canonicalRecord(town).orElseThrow().center();
                    mapCenter = center;
                    var oakvale = registry.create(level.getGameTime(), level.dimension().location(), center.offset(128, 0, 0), "");
                    var stoneford = registry.create(level.getGameTime(), level.dimension().location(), center.offset(256, 0, 64), "");
                    var hidden = registry.create(level.getGameTime(), level.dimension().location(), center.offset(384, 0, 128), "");
                    registry.rename(oakvale, "Oakvale"); registry.rename(stoneford, "Stoneford"); registry.rename(hidden, "Hidden Harbor");
                    VillageDiplomacyApi.set(level, town, oakvale, VillageRelation.NEUTRAL);
                    VillageDiplomacyApi.set(level, town, stoneford, VillageRelation.NEUTRAL);
                    VillageDiplomacyApi.set(level, oakvale, hidden, VillageRelation.NEUTRAL);
                    VillageDiplomacyService.standingEvent(level, town, oakvale, UUID.randomUUID(), 1, 45, "agreement");
                    VillageDiplomacyService.standingEvent(level, town, stoneford, UUID.randomUUID(), 1, -35, "interference");
                    journey = com.jvn.villagerretaliation.settlement.SettlementCourierClientFixture.seed(level, town, oakvale);
                });
                if (!preparation.isDone()) return false;
                preparation.join();
                management.navigate(Page.RELATIONS, 0, "");
                next();
            }
            case 1 -> {
                if (!(minecraft.screen instanceof VillageRelationsScreen screen) || screen.snapshot() == null) return false;
                relations = screen;
                require(screen.snapshot().entries().size() == 2, "only known village contacts appear");
                require(screen.snapshot().entries().stream().noneMatch(entry -> entry.name().contains("Hidden")), "hidden names absent");
                require(screen.snapshot().entries().stream().anyMatch(entry -> entry.standing() > 0)
                        && screen.snapshot().entries().stream().anyMatch(entry -> entry.standing() < 0), "bar fixture spans neutral");
                next();
            }
            case 2 -> {
                if (ticks < 5) return false;
                bounds(minecraft.screen);
                capture.accept("village-relations");
                minecraft.options.guiScale().set(3); minecraft.resizeDisplay();
                next();
            }
            case 3 -> {
                if (ticks < 5) return false;
                bounds(minecraft.screen); capture.accept("village-relations-small");
                minecraft.options.guiScale().set(2); minecraft.resizeDisplay();
                var rows = relations.children().stream().filter(ObjectSelectionList.class::isInstance)
                        .map(child -> (ObjectSelectionList<?>) child).findFirst().orElseThrow();
                rows.children().getFirst().mouseClicked(0, 0, 0);
                button(relations, "show_map").onPress();
                next();
            }
            case 4 -> {
                if (!(minecraft.screen instanceof ExploredMapScreen current) || ticks < 25) return false;
                map = current;
                if (ticks == 25) map.mouseClicked(map.width / 2.0 - 68 * 2.5, map.height / 2.0, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                if (ticks < 50) return false;
                capture.accept("village-relations-map");
                require(map.mouseClicked(map.width / 2.0, map.height / 2.0, GLFW.GLFW_MOUSE_BUTTON_RIGHT), "known marker opens context popup");
                next();
            }
            case 5 -> {
                if (ticks < 5) return false;
                capture.accept("village-relations-popup");
                map.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                next();
            }
            case 6 -> {
                if (!(minecraft.screen instanceof VillageRelationsScreen screen) || screen.snapshot() == null) return false;
                require(screen.snapshot().entries().size() == 1, "other village reveals only its mutually known relation");
                require(screen.snapshot().entries().stream().noneMatch(entry -> entry.name().contains("Hidden")), "popup view keeps hidden contact private");
                screen.onClose(); require(minecraft.screen == map, "return keeps the same map and selection");
                map.onClose();
                next();
            }
            case 7 -> {
                if (minecraft.screen == map) return false;
                require(minecraft.screen == relations, "map returns to original relations screen");
                aidButton(relations, "title").onPress(); next();
            }
            case 8 -> {
                if (!(minecraft.screen instanceof SettlementAidScreen screen) || screen.snapshot() == null) return false;
                require(screen.snapshot().authorized(), "settlement leader can manage food aid");
                require(!screen.snapshot().policy().automatic(), "player settlement starts with manual outgoing aid");
                bounds(screen); capture.accept("settlement-food-aid"); aidButton(screen, "policy").onPress(); next();
            }
            case 9 -> {
                if (ticks < 5) return false;
                var screen = (SettlementAidScreen) minecraft.screen; bounds(screen); capture.accept("settlement-aid-policy");
                screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                        .map(net.minecraft.client.gui.components.EditBox.class::cast)
                        .filter(box -> box.getMessage().equals(SettlementAidScreen.text("maximum_items"))).findFirst().orElseThrow().setValue("64");
                aidButton(screen, "save").onPress(); next();
            }
            case 10 -> {
                var screen = (SettlementAidScreen) minecraft.screen;
                if (screen.snapshot().policy().maximumItems() != 64) return false;
                minecraft.options.guiScale().set(3); minecraft.resizeDisplay(); next();
            }
            case 11 -> {
                if (ticks < 5 || !observeRoute()) return false;
                bounds(minecraft.screen); capture.accept("settlement-aid-policy-small");
                minecraft.options.guiScale().set(2); minecraft.resizeDisplay();
                minecraft.screen.onClose(); require(minecraft.screen == relations, "aid returns to relations");
                relations.onClose(); require(minecraft.screen == management, "relations returns to settlement management");
                minecraft.setScreen(new com.jvn.villagerretaliation.client.exploration.SettlementRoutesScreen(management)); next();
            }
            case 12 -> {
                if (ticks < 30) return false;
                bounds(minecraft.screen); capture.accept("settlement-routes");
                var choose = minecraft.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.getMessage().getString().equals("Choose saved route")).findFirst().orElseThrow();
                require(choose.active, "route server response reached the screen"); choose.onPress();
                require(minecraft.screen instanceof SettlementTradePickerScreen, "routes use a searchable picker");
                var choices = minecraft.screen.children().stream().filter(ObjectSelectionList.class::isInstance)
                        .map(child -> (ObjectSelectionList<?>) child).findFirst().orElseThrow();
                require(!choices.children().isEmpty(), "saved route is listed");
                choices.children().getFirst().mouseClicked(0, 0, 0);
                labeled(minecraft.screen, "Choose").onPress();
                labeled(minecraft.screen, "Roads").onPress(); next();
            }
            case 13 -> {
                if (ticks < 30) return false;
                bounds(minecraft.screen); capture.accept("settlement-route-sections");
                minecraft.options.guiScale().set(3); minecraft.resizeDisplay(); next();
            }
            case 14 -> {
                if (ticks < 5) return false;
                bounds(minecraft.screen); capture.accept("settlement-routes-small");
                minecraft.screen.mouseScrolled(minecraft.screen.width - 20, 100, 0, -10); next();
            }
            case 15 -> {
                if (ticks < 5) return false;
                bounds(minecraft.screen); capture.accept("settlement-route-sections-small");
                minecraft.options.guiScale().set(2); minecraft.resizeDisplay();
                minecraft.setScreen(new com.jvn.villagerretaliation.client.exploration.SettlementRoutesScreen(management, journey)); next();
            }
            case 16 -> {
                if (ticks < 30) return false;
                bounds(minecraft.screen); capture.accept("settlement-caravan");
                labeled(minecraft.screen, "Crew").onPress(); next();
            }
            case 17 -> {
                if (ticks < 5) return false;
                bounds(minecraft.screen); capture.accept("settlement-caravan-crew");
                labeled(minecraft.screen, "Delivery").onPress(); next();
            }
            case 18 -> {
                if (ticks < 5) return false;
                bounds(minecraft.screen); capture.accept("settlement-caravan-delivery");
                minecraft.screen.onClose(); require(minecraft.screen == management, "caravan screen returns to management");
                minecraft.setScreen(new SettlementExchangeScreen(management, town.value(), null));
                exchangeChecks = new SettlementExchangeClientChecks(capture); next();
            }
            case 19 -> {
                return exchangeChecks.tick(minecraft);
            }
        }
        return false;
    }

    private boolean observeRoute() {
        var repository = com.jvn.villagerretaliation.client.exploration.ExploredMapClient.repository();
        require(repository != null, "explored map repository is ready");
        int tileX = com.jvn.villagerretaliation.exploration.MapSnapshot.tile(mapCenter.getX());
        int tileZ = com.jvn.villagerretaliation.exploration.MapSnapshot.tile(mapCenter.getZ());
        repository.tick();
        while (observationCursor < 57) {
            int x = tileX - 1 + observationCursor % 19, z = tileZ - 1 + observationCursor / 19;
            if (repository.get(x, z) == null) return false;
            var columns = new java.util.ArrayList<com.jvn.villagerretaliation.exploration.MapSnapshot.Column>();
            for (int i = 0; i < 64; i++) columns.add(new com.jvn.villagerretaliation.exploration.MapSnapshot.Column(i, mapCenter.getY() - 1,
                    java.util.List.of(new com.jvn.villagerretaliation.exploration.MapSnapshot.Block(mapCenter.getY() - 1,
                            "minecraft:grass_block", 0x80ad52, 0x3f76e4, 2)), true));
            if (!repository.put(new com.jvn.villagerretaliation.exploration.MapSnapshot(x, z, System.currentTimeMillis(), columns, java.util.List.of()))) return false;
            observationCursor++;
        }
        return true;
    }

    private static Button labeled(Screen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private void next() { phase++; ticks = 0; }

    private static Button button(Screen screen, String key) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().equals(VillageRelationsScreen.text(key))).findFirst().orElseThrow();
    }

    private static Button aidButton(Screen screen, String key) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().equals(SettlementAidScreen.text(key))).findFirst().orElseThrow();
    }

    private static void bounds(Screen screen) {
        for (var child : screen.children()) if (child instanceof Button button && button.visible) {
            require(button.getX() >= 0 && button.getY() >= 0 && button.getRight() <= screen.width && button.getBottom() <= screen.height,
                    "relations controls fit the viewport");
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
