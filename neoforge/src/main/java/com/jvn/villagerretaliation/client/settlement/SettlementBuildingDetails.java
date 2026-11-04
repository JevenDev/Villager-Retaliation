package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementBuildingPreviewPayload;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.List;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

record SettlementBuildingDetails(int width, int height, int depth, int beds, int workstations, int price,
                                 boolean affordable, int missingKinds, List<String> shortages) {
    static SettlementBuildingDetails from(SettlementBuildingPreviewPayload preview) {
        int beds = 0, stations = 0;
        for (var block : preview.blocks()) {
            if (block.state().getBlock() instanceof BedBlock && block.state().getValue(BedBlock.PART) == BedPart.FOOT) beds++;
            if (PoiTypes.forState(block.state()).filter(poi -> poi.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)).isPresent()
                    || block.state().is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)
                    || block.state().is(net.minecraft.world.level.block.Blocks.FURNACE)) stations++;
        }
        var missing = preview.materials().stream().filter(material -> material.available() < material.required()).toList();
        return new SettlementBuildingDetails(preview.blocks().isEmpty() ? 0 : preview.max().getX() - preview.min().getX() + 1,
                preview.max().getY() - preview.min().getY() + 1, preview.max().getZ() - preview.min().getZ() + 1,
                beds, stations, preview.price(), preview.funds().compareTo(CurrencyAmount.of(preview.price(), 1)) >= 0, missing.size(),
                missing.stream().limit(8).map(material -> material.label() + ": " + material.available() + "/" + material.required()).toList());
    }

    boolean available() { return this.width > 0; }
    String dimensions() { return this.width + " x " + this.height + " x " + this.depth; }
    String summary() { return !available() ? "Template unavailable" : dimensions() + " / beds " + this.beds + " / workstations " + this.workstations; }
    String supplySummary() { return !available() ? "Check the template and configured block limit" : "Fee " + this.price + (this.affordable ? "" : " (insufficient funds)")
            + (this.missingKinds == 0 ? " / materials available" : " / missing " + this.missingKinds + " material types"); }

    boolean matches(int purpose, int cost) {
        return available() && (purpose == 0 || purpose == 1 && this.beds > 0 || purpose == 2 && this.workstations > 0
                || purpose == 3 && this.beds == 0 && this.workstations == 0)
                && (cost == 0 || this.affordable && (cost == 1 || this.missingKinds == 0));
    }
}
