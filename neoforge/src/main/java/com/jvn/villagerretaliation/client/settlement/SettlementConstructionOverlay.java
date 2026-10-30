package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.item.ConstructionBlueprintPreviewRenderer;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Project;
import com.jvn.villagerretaliation.network.SettlementProjectPreviewPayload;
import com.jvn.villagerretaliation.network.SettlementProjectPreviewRequestPayload;
import com.jvn.villagerretaliation.settlement.SettlementProject;
import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

final class SettlementConstructionOverlay {
    private record Cached(ConstructionBlueprintItem.PreviewData ghost, long received) { }
    private final Map<UUID, Cached> cache = new LinkedHashMap<>();
    private List<Project> visible = List.of();
    private UUID pending;
    private int sequence;
    private long sentAt;

    static boolean unfinished(Project project) {
        return project.status() == SettlementProject.Status.ACTIVE || project.status() == SettlementProject.Status.PENDING;
    }

    void tick(UUID session, Collection<Project> projects, SettlementViewWindow window, UUID selected, double x, double z, boolean enabled) {
        if (!enabled) { clear(); return; }
        this.visible = projects.stream().filter(project -> project.farm() == null).filter(SettlementConstructionOverlay::unfinished)
                .filter(project -> window.chunks().stream().anyMatch(chunk -> chunk.getMaxBlockX() >= project.min().getX()
                        && chunk.getMinBlockX() <= project.max().getX() && chunk.getMaxBlockZ() >= project.min().getZ() && chunk.getMinBlockZ() <= project.max().getZ()))
                .sorted(Comparator.comparing((Project project) -> !project.id().equals(selected)).thenComparingDouble(project ->
                        Math.pow(project.origin().getX() - x, 2) + Math.pow(project.origin().getZ() - z, 2))).limit(4).toList();
        this.cache.keySet().removeIf(id -> this.visible.stream().noneMatch(project -> project.id().equals(id)));
        long now = Util.getMillis();
        if (this.pending != null && (now - this.sentAt >= 3000 || this.visible.stream().noneMatch(project -> project.id().equals(this.pending)))) this.pending = null;
        if (this.pending != null || now - this.sentAt < 350) return;
        for (var project : this.visible) {
            var cached = this.cache.get(project.id());
            if (cached != null && now - cached.received() < 10000) continue;
            this.pending = project.id();
            this.sentAt = now;
            this.sequence = this.sequence == Integer.MAX_VALUE ? 1 : this.sequence + 1;
            PacketDistributor.sendToServer(new SettlementProjectPreviewRequestPayload(session, this.pending, this.sequence));
            return;
        }
    }

    void accept(SettlementProjectPreviewPayload payload) {
        if (!payload.project().equals(this.pending) || payload.requestId() != this.sequence) return;
        this.pending = null;
        var project = this.visible.stream().filter(row -> row.id().equals(payload.project())).findFirst().orElse(null);
        var level = Minecraft.getInstance().level;
        if (project == null || level == null) return;
        var preview = payload.preview();
        ConstructionBlueprintItem.PreviewData ghost = null;
        if (preview != null && preview.origin().equals(project.origin()) && preview.min().equals(project.min()) && preview.max().equals(project.max())) {
            var blocks = preview.blocks().stream().map(block -> new ConstructionBlueprintItem.PreviewBlock(block.local(), block.state())).toList();
            ghost = new ConstructionBlueprintItem.PreviewData(project.id(), project.builder(), level.dimension(), preview.structure(), project.name(),
                    preview.origin(), preview.rotation(), preview.min().subtract(preview.origin()), preview.max().subtract(preview.origin()),
                    blocks.size(), 0, 0, "", "", "", false, false, false, false, blocks);
        }
        this.cache.put(project.id(), new Cached(ghost, Util.getMillis()));
    }

    void render(RenderLevelStageEvent event) {
        for (var cached : this.cache.values()) if (cached.ghost() != null) ConstructionBlueprintPreviewRenderer.renderPreview(event, cached.ghost(), false);
    }

    String status() {
        long loaded = this.cache.values().stream().filter(cached -> cached.ghost() != null).count();
        long unavailable = this.cache.values().stream().filter(cached -> cached.ghost() == null).count();
        return "Structure previews: " + loaded + "/" + this.visible.size() + " nearby projects"
                + (unavailable == 0 ? "" : " (" + unavailable + " unavailable)");
    }

    void clear() { this.cache.clear(); this.visible = List.of(); this.pending = null; }
}
