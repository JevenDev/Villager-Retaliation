package com.jvn.villagerretaliation.compat;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class VillagerRetaliationMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".TradeEmiOverlayMixin"))
            return FMLLoader.getLoadingModList().getModFileById("emi") != null;
        if (mixinClassName.endsWith(".TradeJeiOverlayMixin"))
            return FMLLoader.getLoadingModList().getModFileById("jei") != null;
        if (mixinClassName.contains(".DialogueSodiumCamera"))
            return supportsSodiumDialogueCamera();
        // sodium replaces setupRender and already centers terrain on the active camera
        return !mixinClassName.equals("com.jvn.villagerretaliation.mixin.client.SettlementLevelRendererMixin")
                || FMLLoader.getLoadingModList().getModFileById("sodium") == null;
    }

    public static boolean supportsSodiumDialogueCamera() {
        var sodium = FMLLoader.getLoadingModList().getModFileById("sodium");
        if (sodium == null) return false;
        return sodium.getMods().stream().filter(mod -> mod.getModId().equals("sodium")).anyMatch(mod -> {
            var version = mod.getVersion().toString();
            return version.equals("0.8.13") || version.startsWith("0.8.13+");
        });
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
