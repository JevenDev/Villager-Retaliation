package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.MapLighting;
import com.jvn.villagerretaliation.exploration.MapSnapshot;
import com.jvn.villagerretaliation.exploration.MapView;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

public final class ExploredMapRenderer implements AutoCloseable {
    private final MapTileView tiles = new MapTileView();

    public void render(GuiGraphics graphics, MapView view, MapLighting lighting) {
        var repository = ExploredMapClient.repository();
        if (repository == null) {
            graphics.fill(0, 0, view.width(), view.height(), lighting.background());
            return;
        }
        var cache = ExploredMapClient.tiles();
        boolean ready = this.tiles.update(repository, cache, view);
        MapFogRenderer.render(graphics, view, ready ? this.tiles : null, cache, lighting);
    }

    public List<MapSnapshot.Marker> markers() { return this.tiles.markers(); }

    @Override
    public void close() { this.tiles.close(); }
}
