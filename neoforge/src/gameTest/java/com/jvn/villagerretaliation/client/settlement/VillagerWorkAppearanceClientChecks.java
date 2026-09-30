package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.model.BaseVillagerModel;
import com.jvn.villagerretaliation.client.model.HumanoidCompatVillagerModel;
import com.jvn.villagerretaliation.client.renderer.VillagerRetaliationVillagerRenderer;
import com.jvn.villagerretaliation.client.renderer.layer.VillagerRetaliationProfessionLayer;
import com.jvn.villagerretaliation.config.VillagerRenderMode;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.VillagerWorkAppearance;
import com.jvn.villagerretaliation.interaction.VillagerWorkAppearanceHolder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class VillagerWorkAppearanceClientChecks {
    private VillagerWorkAppearanceClientChecks() { }

    static void verify() {
        var minecraft = Minecraft.getInstance();
        var villager = EntityType.VILLAGER.create(minecraft.level);
        villager.setVillagerData(villager.getVillagerData().setType(VillagerType.SNOW).setProfession(VillagerProfession.TOOLSMITH).setLevel(5));
        var identity = villager.getVillagerData();
        var renderer = (VillagerRetaliationVillagerRenderer) minecraft.getEntityRenderDispatcher().getRenderer(villager);
        var previous = VillagerRetaliationConfig.VILLAGER_RENDER_MODE.get();
        try {
            for (var mode : VillagerRenderMode.values()) {
                VillagerRetaliationConfig.VILLAGER_RENDER_MODE.set(mode);
                for (var role : HiredVillagerRole.values()) {
                    ((VillagerWorkAppearanceHolder) villager).villagerretaliation$assignedWorkRole(role);
                    var trace = render(renderer, villager);
                    var profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(VillagerWorkAppearance.profession(role)).getPath();
                    require(trace.layers().stream().anyMatch(layer -> layer.contains("/profession/" + profession + ".png")),
                            mode + " renders the existing " + role + " outfit");
                    require(trace.layers().stream().noneMatch(layer -> layer.contains("/profession_level/")), "assignment outfits have no trading badges");
                    require(trace.layers().stream().anyMatch(layer -> layer.contains("/type/snow.png")), "assignment outfits retain biome layers");
                    require(villager.getVillagerData().equals(identity), "rendering leaves the actual profession and level intact");
                    if (role == HiredVillagerRole.FARMING) require(!trace.biomeHat(), "the mapped farmer hat hides the biome hat");
                    if (role == HiredVillagerRole.CRAFTSMAN) require(trace.biomeHat(), "a hatless work outfit preserves the biome hat");
                }
                ((VillagerWorkAppearanceHolder) villager).villagerretaliation$assignedWorkRole(null);
                require(render(renderer, villager).layers().stream().noneMatch(layer -> layer.contains("/profession/")), "unassigned toolsmiths wear only biome clothing");
                ((VillagerWorkAppearanceHolder) villager).villagerretaliation$assignedWorkRole(HiredVillagerRole.FARMING);
                villager.setAge(-24_000);
                require(render(renderer, villager).layers().stream().noneMatch(layer -> layer.contains("/profession/")), "babies wear only biome clothing");
                villager.setAge(0);
                villager.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                ((VillagerWorkAppearanceHolder) villager).villagerretaliation$assignedWorkRole(null);
                var neutralArmor = render(renderer, villager).layers().stream().filter(layer -> layer.contains("/models/armor/")).toList();
                ((VillagerWorkAppearanceHolder) villager).villagerretaliation$assignedWorkRole(HiredVillagerRole.FARMING);
                var armored = render(renderer, villager);
                require(armored.layers().stream().filter(layer -> layer.contains("/models/armor/")).toList().equals(neutralArmor),
                        "job clothing preserves the mode's existing armor rendering");
                if (mode != VillagerRenderMode.PACK_NATIVE) require(neutralArmor.stream().anyMatch(layer -> layer.contains("/models/armor/iron_layer_1.png")),
                        mode + " renders the equipped chestplate");
                require(armored.layers().stream().anyMatch(layer -> layer.contains("/profession/farmer.png")), "equipped armor preserves the work outfit");
                villager.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                verifyVisibility(renderer, villager);
            }
        } finally {
            VillagerRetaliationConfig.VILLAGER_RENDER_MODE.set(previous);
        }
    }

    private static Trace render(VillagerRetaliationVillagerRenderer renderer, Villager villager) {
        var layers = new ArrayList<String>();
        boolean[] biomeHat = {true};
        try (var memory = new ByteBufferBuilder(65_536)) {
            var buffer = new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            renderer.render(villager, 0, 0, new PoseStack(), type -> {
                String layer = type.toString();
                layers.add(layer);
                if (layer.contains("/type/snow.png")) biomeHat[0] = hatVisible(renderer.getModel());
                return buffer;
            }, 0xF000F0);
            try (var mesh = buffer.build()) {
                require(mesh != null && mesh.drawState().vertexCount() > 0, "the actual villager renderer emits geometry");
            }
        }
        return new Trace(List.copyOf(layers), biomeHat[0]);
    }

    private static boolean hatVisible(BaseVillagerModel<Villager> model) {
        return model instanceof HumanoidCompatVillagerModel<?> ? model.getHead().getChild("helmet").visible : model.getHead().visible;
    }

    private static void verifyVisibility(VillagerRetaliationVillagerRenderer renderer, Villager villager) {
        var model = renderer.getModel();
        var parts = model.getHead().getAllParts().toList();
        boolean[] original = new boolean[parts.size()];
        boolean[] before = new boolean[parts.size()];
        for (int index = 0; index < before.length; index++) {
            original[index] = parts.get(index).visible;
            before[index] = index % 2 == 0;
            parts.get(index).visible = before[index];
        }
        var layer = new VillagerRetaliationProfessionLayer<>(renderer, Minecraft.getInstance().getResourceManager(), "villager");
        try (var memory = new ByteBufferBuilder(16_384)) {
            var buffer = new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            layer.render(new PoseStack(), type -> buffer, 0xF000F0, villager, 0, 0, 0, 0, 0, 0);
            try (var mesh = buffer.build()) {
                require(mesh != null, "the clothing layer emits geometry");
            }
        }
        for (int index = 0; index < before.length; index++) {
            require(parts.get(index).visible == before[index], "the clothing pass restores every original head-part visibility");
            parts.get(index).visible = original[index];
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Trace(List<String> layers, boolean biomeHat) { }
}
