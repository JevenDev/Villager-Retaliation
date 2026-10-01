package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.SettlementWorkerPoolRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWorkerPoolSnapshotPayload;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import com.jvn.villagerretaliation.settlement.SettlementWorkerPool;
import com.jvn.villagerretaliation.settlement.SettlementWorkerPoolManagementService;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

final class SettlementWorkerPoolClientChecks {
    private final VillageAllegianceId town;
    private final UUID resident;
    private final Consumer<String> capture;
    private final UUID temporaryLeader = UUID.randomUUID();
    private SettlementWorkerPoolsScreen editor;
    private Map<UUID, Set<SettlementPermission>> delegates;
    private UUID area;
    private UUID pool;
    private String siteName;
    private int phase;
    private int ticks;
    private long nextServerTick;

    SettlementWorkerPoolClientChecks(VillageAllegianceId town, UUID resident, Consumer<String> capture) {
        this.town = town;
        this.resident = resident;
        this.capture = capture;
    }

    boolean tick(Minecraft minecraft) {
        if (++this.ticks < 5) return false;
        require(this.ticks < 400, "Worker pool client step " + this.phase + " did not complete: "
                + (this.editor == null || this.editor.snapshot() == null ? "" : this.editor.snapshot().feedback() + " / " + this.editor.snapshot().blocker()));
        if (server(minecraft, () -> minecraft.getSingleplayerServer().overworld().getGameTime()) < this.nextServerTick) return false;
        var screen = minecraft.screen;
        switch (this.phase) {
            case 0 -> {
                require(screen instanceof SettlementManagementScreen, "the pool flow starts from settlement management");
                server(minecraft, () -> {
                    var town = town(minecraft);
                    var level = minecraft.getSingleplayerServer().overworld();
                    var resident = (net.minecraft.world.entity.npc.Villager) level.getEntity(this.resident);
                    this.area = town.duties().get(this.resident);
                    this.siteName = town.workplace(this.area).name();
                    var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
                    require(SettlementWorkforceService.assign(level, player, this.town, resident, this.area), "the fixture starts with an explicit manual assignment");
                    require(pinned(minecraft), "the existing civic resident is manually pinned");
                    return true;
                });
                ((SettlementManagementScreen) screen).navigateTo(Page.SUMMARY, "");
                next();
            }
            case 1 -> {
                if (((SettlementManagementScreen) screen).snapshot().page() != Page.SUMMARY) return false;
                click(screen, "title");
                next();
            }
            case 2 -> {
                if (!(screen instanceof SettlementWorkerPoolsScreen pools) || pools.snapshot() == null || !button(screen, "new").active) return false;
                this.editor = pools;
                verifyRequests(pools.snapshot());
                bounds(screen);
                click(screen, "new");
                next();
            }
            case 3 -> {
                field(screen, "name").setValue("Authorized work crew");
                field(screen, "search").setValue(this.resident.toString());
                next();
            }
            case 4 -> {
                if (!this.editor.snapshot().query().equals(this.resident.toString()) || this.editor.snapshot().residents().size() != 1) return false;
                rows(screen).children().getFirst().mouseClicked(0, 0, 0);
                click(screen, "include");
                require(this.editor.configuration().residents().equals(java.util.List.of(this.resident)), "only the selected resident is enrolled in the draft");
                require(this.editor.snapshot().residents().getFirst().pinned(), "enrollment does not silently release the manual pin");
                this.capture.accept("pool-residents");
                click(screen, "worksites_count");
                next();
            }
            case 5 -> {
                choice(screen, this.siteName);
                var site = this.editor.snapshot().worksites().stream().filter(value -> value.id().equals(this.area)).findFirst().orElseThrow();
                require(field(screen, "minimum").getValue().equals("0") && field(screen, "maximum").getValue().equals(Integer.toString(site.desired())),
                        "the editor shows zero minimum and existing desired staffing before inclusion");
                click(screen, "include");
                field(screen, "maximum").setValue("3");
                field(screen, "minimum").setValue("4");
                require(!button(screen, "save").active && this.editor.configuration() == null, "inverted staffing limits cannot be submitted");
                field(screen, "minimum").setValue("1");
                next();
            }
            case 6 -> {
                if (!button(screen, "save").active) return false;
                bounds(screen);
                require(field(screen, "minimum").getValue().equals("1") && field(screen, "maximum").getValue().equals("3"),
                        "resizing retains the exact unsaved staffing limits");
                this.capture.accept("pool-limits");
                minecraft.options.guiScale().set(4);
                minecraft.resizeDisplay();
                next();
            }
            case 7 -> {
                bounds(screen);
                this.capture.accept("pool-editor-small");
                minecraft.options.guiScale().set(2);
                minecraft.resizeDisplay();
                if (!button(screen, "save").active) return false;
                click(screen, "save");
                next();
            }
            case 8 -> {
                var selected = this.editor.snapshot().selected();
                if (selected == null || selected.state() != SettlementWorkerPool.State.PAUSED || !button(screen, "activate").active) return false;
                this.pool = selected.id();
                require(server(minecraft, () -> pinned(minecraft)), "saving the paused draft retains the manual pin");
                click(screen, "activate");
                next();
            }
            case 9 -> {
                if (this.editor.snapshot().selected().state() != SettlementWorkerPool.State.ACTIVE) return false;
                click(screen, "residents_count");
                next();
            }
            case 10 -> {
                if (!this.editor.snapshot().query().equals(this.resident.toString())) return false;
                rows(screen).children().getFirst().mouseClicked(0, 0, 0);
                if (!button(screen, "return_control").active) return false;
                this.capture.accept("pool-active-pinned");
                click(screen, "return_control");
                next();
            }
            case 11 -> {
                if (!server(minecraft, () -> !pinned(minecraft))) return false;
                require(server(minecraft, () -> this.area.equals(town(minecraft).duties().get(this.resident))), "returning pool authority keeps the current duty");
                if (!button(screen, "history").active) return false;
                click(screen, "history");
                bounds(screen);
                this.capture.accept("pool-history");
                this.editor.onClose();
                next();
            }
            case 12 -> {
                if (!button(screen, "pause").active) return false;
                click(screen, "pause");
                next();
            }
            case 13 -> {
                if (this.editor.snapshot().selected() == null || this.editor.snapshot().selected().state() != SettlementWorkerPool.State.PAUSED
                        || !button(screen, "open").active) return false;
                click(screen, "open");
                next();
            }
            case 14 -> {
                if (this.editor.configuration() == null || !button(screen, "save").active) return false;
                field(screen, "name").setValue("Unsaved local crew");
                server(minecraft, () -> {
                    var town = town(minecraft);
                    var pool = town.workerPools().get(this.pool);
                    var config = pool.configuration();
                    var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
                    SettlementWorkerPoolManagementService.handle(player, new SettlementWorkerPoolRequestPayload(this.town.value(), UUID.randomUUID(),
                            this.pool, pool.revision(), "", 0, new SettlementWorkerPoolRequestPayload.Save(
                            new SettlementWorkerPool.Configuration("Server crew", config.residents(), config.worksites()), false)));
                    return true;
                });
                next();
            }
            case 15 -> {
                if (!server(minecraft, () -> town(minecraft).workerPools().get(this.pool).configuration().name().equals("Server crew"))
                        || button(screen, "save").active || this.editor.snapshot().selected().configuration().name().equals("Authorized work crew")) return false;
                require(field(screen, "name").getValue().equals("Unsaved local crew"), "a concurrent save preserves the local draft instead of silently replacing it");
                this.capture.accept("pool-stale");
                if (!button(screen, "reload").active) return false;
                click(screen, "reload");
                next();
            }
            case 16 -> { confirm(screen); next(); }
            case 17 -> {
                if (!field(screen, "name").getValue().equals("Server crew") || !button(screen, "save").active) return false;
                this.editor.onClose();
                next();
            }
            case 18 -> {
                if (!button(screen, "refresh").active) return false;
                server(minecraft, () -> {
                    var town = town(minecraft);
                    this.delegates = Map.copyOf(town.delegates());
                    long now = minecraft.getSingleplayerServer().overworld().getGameTime();
                    var offer = town.offerLeadership(town.leader(), this.temporaryLeader, now);
                    require(town.acceptLeadership(this.temporaryLeader, offer.id(), now), "a separate leader controls the permission fixture");
                    require(town.setPermissions(this.temporaryLeader, minecraft.player.getUUID(), Set.of(SettlementPermission.VIEW)), "the current player retains only VIEW");
                    return true;
                });
                click(screen, "refresh");
                next();
            }
            case 19 -> {
                if (this.editor.snapshot().editable() || !button(screen, "open").active) return false;
                require(!button(screen, "activate").active && !button(screen, "remove").active, "view permission cannot activate or remove the pool");
                click(screen, "open");
                next();
            }
            case 20 -> {
                if (this.editor.configuration() == null || !button(screen, "preview").active) return false;
                require(!button(screen, "save").active && !button(screen, "activate").active, "view-only inspection cannot save or authorize a draft");
                this.capture.accept("pool-view-only");
                server(minecraft, () -> {
                    var town = town(minecraft);
                    long now = minecraft.getSingleplayerServer().overworld().getGameTime();
                    var player = minecraft.player.getUUID();
                    var offer = town.offerLeadership(this.temporaryLeader, player, now);
                    require(town.acceptLeadership(player, offer.id(), now), "leadership returns to the fixture player");
                    this.delegates.forEach((delegate, permissions) -> require(town.setPermissions(player, delegate, permissions), "fixture permissions restore"));
                    return true;
                });
                this.editor.onClose();
                next();
            }
            case 21 -> {
                if (!button(screen, "remove").active) return false;
                click(screen, "remove");
                next();
            }
            case 22 -> { confirm(screen); next(); }
            case 23 -> {
                if (this.editor.snapshot().pools().stream().anyMatch(value -> value.id().equals(this.pool))) return false;
                require(server(minecraft, () -> {
                    var town = town(minecraft);
                    var level = minecraft.getSingleplayerServer().overworld();
                    var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
                    require(this.area.equals(town.duties().get(this.resident)), "pool removal preserves the native assignment");
                    return SettlementWorkforceService.assign(level, player, this.town,
                            (net.minecraft.world.entity.npc.Villager) level.getEntity(this.resident), this.area);
                }), "the fixture's original manual pin is restored");
                require(SettlementViewClient.connectedPanel() && minecraft.getCameraEntity() != minecraft.player, "pool navigation keeps the connected world camera");
                this.editor.onClose();
                return true;
            }
            default -> throw new IllegalStateException("Unknown worker pool client check " + this.phase);
        }
        return false;
    }

    private com.jvn.villagerretaliation.settlement.SettlementRecord town(Minecraft minecraft) {
        return SettlementSavedData.get(minecraft.getSingleplayerServer().overworld()).get(this.town).orElseThrow();
    }

    private boolean pinned(Minecraft minecraft) {
        var level = minecraft.getSingleplayerServer().overworld();
        var data = SettlementSavedData.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
        var saved = data.getList("Settlements", net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(net.minecraft.nbt.CompoundTag.class::cast)
                .filter(town -> town.getUUID("Id").equals(this.town.value())).findFirst().orElseThrow();
        return saved.getList("ManualDuties", net.minecraft.nbt.Tag.TAG_INT_ARRAY).stream()
                .map(net.minecraft.nbt.NbtUtils::loadUUID).anyMatch(this.resident::equals);
    }

    private void next() {
        this.phase++;
        this.ticks = 0;
        var minecraft = Minecraft.getInstance();
        this.nextServerTick = server(minecraft, () -> minecraft.getSingleplayerServer().overworld().getGameTime() + 5L);
        com.mojang.logging.LogUtils.getLogger().info("Worker pool client check {}", this.phase);
    }

    private static <T> T server(Minecraft minecraft, java.util.function.Supplier<T> action) {
        return minecraft.getSingleplayerServer().submit(action).join();
    }

    private static Button button(Screen screen, String key) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(value -> value.visible)
                .filter(value -> value.getMessage().getContents() instanceof TranslatableContents content
                        && content.getKey().equals("villagerretaliation.gui.settlement.pool." + key)).findFirst().orElseThrow();
    }

    private static void click(Screen screen, String key) {
        var button = button(screen, key);
        require(button.active, "pool control is available: " + key);
        button.onPress();
    }

    private static void confirm(Screen screen) {
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(value -> value.getMessage().getString().equals(Component.translatable("gui.yes").getString())).findFirst().orElseThrow().onPress();
    }

    private static EditBox field(Screen screen, String key) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(value -> value.getMessage().getString().equals(SettlementWorkerPoolClient.text(key).getString())).findFirst().orElseThrow();
    }

    private static ObjectSelectionList<?> rows(Screen screen) {
        return screen.children().stream().filter(ObjectSelectionList.class::isInstance).map(value -> (ObjectSelectionList<?>) value).findFirst().orElseThrow();
    }

    private static void choice(Screen screen, String name) {
        rows(screen).children().stream().filter(value -> value.getNarration().getString().contains(name)).findFirst().orElseThrow().mouseClicked(0, 0, 0);
    }

    private static void bounds(Screen screen) {
        SettlementPolicyClientChecks.bounds(screen);
        var minecraft = Minecraft.getInstance();
        int width = screen.width, height = screen.height;
        for (var size : java.util.List.of(new int[]{320, 240}, new int[]{426, 240}, new int[]{width, height})) {
            screen.resize(minecraft, size[0], size[1]);
            for (var child : screen.children()) if (child instanceof Button button && button.visible)
                require(minecraft.font.width(button.getMessage()) <= button.getWidth() - 4,
                        "worker pool label fits: " + button.getMessage().getString() + " in " + button.getWidth());
        }
    }

    private static void verifyRequests(SettlementWorkerPoolSnapshotPayload snapshot) {
        var state = new SettlementWorkerPoolClientState(snapshot.town());
        var old = state.request(null, -1, "", 0, new SettlementWorkerPoolRequestPayload.View(), 0);
        var current = state.request(null, -1, "", 0, new SettlementWorkerPoolRequestPayload.View(), 1);
        require(!state.accept(reply(snapshot, old.request())) && state.snapshot() == null, "superseded pool responses cannot replace current state");
        require(state.accept(reply(snapshot, current.request())) && state.editable(), "the matching pool response publishes current authority");
        var mutation = state.request(UUID.randomUUID(), 1, "", 0, new SettlementWorkerPoolRequestPayload.Change(SettlementWorkerPoolRequestPayload.Action.PAUSE), 10);
        require(mutation != null && state.busy() && state.request(null, -1, "", 0, new SettlementWorkerPoolRequestPayload.View(), 11) == null,
                "a pool mutation cannot be superseded by a catalogue refresh");
        require(!state.expire(10_009) && state.expire(10_010) && !state.accept(reply(snapshot, mutation.request())),
                "timed-out pool changes ignore late responses and retain the last confirmed state");
    }

    private static SettlementWorkerPoolSnapshotPayload reply(SettlementWorkerPoolSnapshotPayload source, UUID request) {
        return new SettlementWorkerPoolSnapshotPayload(source.dimension(), source.town(), request, source.revision(), source.gameTime(), false, source.editable(),
                "", "", source.pools(), source.selected(), source.query(), source.page(), source.more(), source.residents(), source.worksites(), source.history());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
