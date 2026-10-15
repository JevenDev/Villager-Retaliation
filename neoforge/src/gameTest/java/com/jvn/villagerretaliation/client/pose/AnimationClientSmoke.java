package com.jvn.villagerretaliation.client.pose;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel;
import com.jvn.villagerretaliation.client.villager.VillagerDownedClientCache;
import com.jvn.villagerretaliation.client.villager.VillagerStudyClientCache;
import com.jvn.villagerretaliation.client.villager.VillagerWorkAnimationClientCache;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedPose;
import com.jvn.villagerretaliation.interaction.work.HiredWorkAnimation;
import com.jvn.villagerretaliation.network.VillagerDownedStatePayload;
import com.jvn.villagerretaliation.network.VillagerStudyStatePayload;
import com.jvn.villagerretaliation.network.VillagerWorkAnimationPayload;
import com.jvn.villagerretaliation.repair.SmithRepairAnimationState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class AnimationClientSmoke {
    private static final String OUTPUT = "villagerretaliation.animationClientSmoke";
    private static final List<String> RESULTS = new ArrayList<>();
    private static CompletableFuture<Void> reload;
    private static int mode;
    private static int page;
    private static int frames;
    private static long started;
    private static boolean finished;
    private static boolean sawOpenEyes;
    private static boolean sawClosedEyes;
    private static List<Scenario> scenarios;

    private AnimationClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty(OUTPUT) == null || finished) return;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setWindowActive(true);
        minecraft.options.pauseOnLostFocus = false;
        if (started == 0) started = Util.getMillis();
        require(Util.getMillis() - started < 240000, "animation client timed out");
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        if (reload != null) {
            if (!reload.isDone()) return;
            reload.join();
            reload = null;
            scenarios = null;
        }
        if (minecraft.getOverlay() != null) return;
        if (!(minecraft.screen instanceof MatrixScreen)) {
            scenarios = scenarios();
            page = 0;
            frames = 0;
            minecraft.setScreen(new MatrixScreen());
        }
        if (frames < (page == (scenarios.size() - 1) / 6 ? 120 : 12)) return;
        Screenshot.grab(Path.of(System.getProperty(OUTPUT)).toFile(), "mode-" + mode + "-page-" + page + ".png",
                minecraft.getMainRenderTarget(), ignored -> { });
        if (++page * 6 < scenarios.size()) {
            frames = 0;
            return;
        }
        RESULTS.add("Passed mode " + mode + " with " + scenarios.size() + " animation scenarios");
        if (++mode == 3) {
            require(sawOpenEyes && sawClosedEyes, "Fresh Animations eyes visibly open and close on the bridge");
            RESULTS.add("Verified Fresh Animations and bridge skin sources, facial geometry and blinking");
            try {
                Files.writeString(Path.of(System.getProperty(OUTPUT), "result.txt"),
                        "Animation client checks passed\n" + String.join("\n", RESULTS));
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            finished = true;
            minecraft.stop();
            return;
        }
        minecraft.setScreen(null);
        minecraft.getResourcePackRepository().reload();
        var selected = new ArrayList<>(minecraft.getResourcePackRepository().getSelectedIds());
        selected.removeIf(id -> id.equals("file/fresh.zip") || id.equals("file/bridge.zip"));
        selected.add("file/fresh.zip");
        if (mode == 2) selected.add("file/bridge.zip");
        minecraft.getResourcePackRepository().setSelected(selected);
        reload = minecraft.reloadResourcePacks();
    }

    private static List<Scenario> scenarios() {
        List<Scenario> cases = new ArrayList<>();
        add(cases, "idle", VillagerArmPose.NONE, v -> { });
        add(cases, "walking", VillagerArmPose.NONE, v -> v.walkAnimation.update(0.6F, 1));
        add(cases, "scared running", VillagerArmPose.NONE, v -> v.walkAnimation.update(1.0F, 1));
        add(cases, "hurt", VillagerArmPose.NONE, v -> v.hurtTime = 8);
        add(cases, "baby idle", VillagerArmPose.NONE, v -> v.setBaby(true));
        add(cases, "sleeping", VillagerArmPose.NONE, v -> v.startSleeping(new BlockPos(0, 100, 0)));
        add(cases, "sword idle", VillagerArmPose.HOLDING_ITEM, v -> hand(v, Items.IRON_SWORD));
        add(cases, "punch recovery", VillagerArmPose.MELEE_WEAPON, v -> attack(v));
        add(cases, "sword recovery", VillagerArmPose.MELEE_WEAPON, v -> { hand(v, Items.IRON_SWORD); attack(v); });
        add(cases, "left offhand swing", VillagerArmPose.MELEE_WEAPON, v -> {
            v.mainArm = HumanoidArm.LEFT;
            v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_AXE));
            v.swingingArm = InteractionHand.OFF_HAND;
            attack(v);
        });
        add(cases, "water empty", VillagerArmPose.SWIMMING, v -> v.water = true);
        add(cases, "water sword", VillagerArmPose.SWIMMING, v -> { v.water = true; hand(v, Items.IRON_SWORD); });
        add(cases, "water attack", VillagerArmPose.MELEE_WEAPON, v -> { v.water = true; hand(v, Items.IRON_SWORD); attack(v); });
        add(cases, "lava empty", VillagerArmPose.SWIMMING, v -> v.lava = true);
        add(cases, "lava attack", VillagerArmPose.MELEE_WEAPON, v -> { v.lava = true; attack(v); });
        add(cases, "lava sword", VillagerArmPose.SWIMMING, v -> { v.lava = true; hand(v, Items.IRON_SWORD); });
        add(cases, "wading sword", VillagerArmPose.HOLDING_ITEM, v -> { v.water = true; v.setOnGround(true); hand(v, Items.IRON_SWORD); });
        add(cases, "baby swimming", VillagerArmPose.SWIMMING, v -> { v.water = true; v.setBaby(true); hand(v, Items.STICK); });
        add(cases, "bow release", VillagerArmPose.HOLDING_ITEM, v -> { hand(v, Items.BOW); attack(v); });
        add(cases, "crossbow shot", VillagerArmPose.CROSSBOW_HOLD, v -> { hand(v, Items.CROSSBOW); attack(v); });
        add(cases, "sleeping sword", VillagerArmPose.HOLDING_ITEM, v -> { hand(v, Items.IRON_SWORD); v.startSleeping(new BlockPos(0, 100, 0)); });
        add(cases, "bow draw", VillagerArmPose.BOW_AND_ARROW, v -> use(v, Items.BOW, InteractionHand.MAIN_HAND));
        add(cases, "water bow", VillagerArmPose.BOW_AND_ARROW, v -> { v.water = true; use(v, Items.BOW, InteractionHand.MAIN_HAND); });
        add(cases, "shield lowered", VillagerArmPose.SHIELD_LOWERED, v -> {
            hand(v, Items.IRON_SWORD);
            v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            v.setAggressive(true);
        });
        add(cases, "crossbow charge", VillagerArmPose.CROSSBOW_CHARGE, v -> use(v, Items.CROSSBOW, InteractionHand.MAIN_HAND));
        add(cases, "crossbow hold", VillagerArmPose.CROSSBOW_HOLD, v -> {
            hand(v, Items.CROSSBOW);
            v.getMainHandItem().set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));
        });
        add(cases, "shield block", VillagerArmPose.SHIELD_BLOCK, v -> { hand(v, Items.IRON_SWORD); use(v, Items.SHIELD, InteractionHand.OFF_HAND); });
        add(cases, "eating main", VillagerArmPose.CONSUMING_ITEM, v -> use(v, Items.BREAD, InteractionHand.MAIN_HAND));
        add(cases, "drinking offhand", VillagerArmPose.CONSUMING_ITEM, v -> use(v, Items.MILK_BUCKET, InteractionHand.OFF_HAND));
        add(cases, "potion throw", VillagerArmPose.THROWING_ITEM, v -> { hand(v, Items.SPLASH_POTION); attack(v); });
        add(cases, "armored attack", VillagerArmPose.MELEE_WEAPON, v -> {
            hand(v, Items.IRON_SWORD); attack(v);
            v.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            v.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        });
        add(cases, "work item", VillagerArmPose.WORK_ITEM_USE, v -> { }, v ->
                VillagerWorkAnimationClientCache.accept(new VillagerWorkAnimationPayload(v.getId(), HiredWorkAnimation.USE_ITEM, 40, new ItemStack(Items.WHEAT_SEEDS))));
        add(cases, "study", VillagerArmPose.NONE, v -> { }, v ->
                VillagerStudyClientCache.accept(new VillagerStudyStatePayload(v.getId(), v.getUUID(), true, "farming", 20, 1200, false, 0)));
        for (VillagerDownedPose pose : VillagerDownedPose.values()) {
            add(cases, "downed " + pose.name(), VillagerArmPose.NONE, v -> { }, v ->
                    VillagerDownedClientCache.accept(new VillagerDownedStatePayload(v.getId(), true, pose.id())));
        }
        add(cases, "smith repair", VillagerArmPose.NONE, v ->
                ((SmithRepairAnimationState) (Villager) v).villagerretaliation$repairAnimation(new BlockPos(0, 100, 1),
                        new ItemStack(Items.IRON_SWORD), v.level().getGameTime() - 20));
        add(cases, "riding sword", VillagerArmPose.HOLDING_ITEM, v -> {
            hand(v, Items.IRON_SWORD);
            v.water = true;
            v.startRiding(EntityType.PIG.create(v.level()), true);
        });
        add(cases, "face animation", VillagerArmPose.HOLDING_ITEM, v -> hand(v, Items.STICK));
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(Minecraft.getInstance().level);
        trader.attackAnim = trader.oAttackAnim = 0.35F;
        require(DefaultWanderingTraderPoseProvider.INSTANCE.getArmPose(trader, 0.35F) == VillagerArmPose.MELEE_WEAPON,
                "trader attack recovery keeps independent arms");
        return cases;
    }

    private static void add(List<Scenario> cases, String name, VillagerArmPose expected, Consumer<FluidVillager> setup) {
        add(cases, name, expected, setup, v -> { });
    }

    private static void add(List<Scenario> cases, String name, VillagerArmPose expected,
            Consumer<FluidVillager> setup, Consumer<FluidVillager> beforeRender) {
        FluidVillager entity = new FluidVillager();
        entity.setUUID(java.util.UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        entity.setPos(0, 100, 0);
        entity.yHeadRot = entity.yHeadRotO = 25;
        entity.setXRot(20);
        entity.xRotO = 20;
        entity.tickCount = 100;
        setup.accept(entity);
        cases.add(new Scenario(name, entity, expected, beforeRender));
    }

    private static void hand(Villager entity, net.minecraft.world.item.Item item) {
        entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
    }

    private static void attack(Villager entity) {
        entity.attackAnim = entity.oAttackAnim = 0.35F;
    }

    private static void use(FluidVillager entity, net.minecraft.world.item.Item item, InteractionHand hand) {
        entity.setItemInHand(hand, new ItemStack(item));
        entity.beginItemUse(hand);
    }

    private static void verify(Scenario scenario) {
        FluidVillager entity = scenario.entity();
        var renderer = (net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?>)
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer.getModel() instanceof VillagerRetaliationVillagerModel<?> actual)) {
            require(mode > 0 && scenario.entity().getMainHandItem().isEmpty()
                    && scenario.expected() != VillagerArmPose.MELEE_WEAPON,
                    scenario.name() + " uses a side-arm model when required");
            require(renderer.getModel() instanceof com.jvn.villagerretaliation.client.model.VanillaVillagerModelAdapter<?>,
                    scenario.name() + " retains the native base-pack model");
            require(Minecraft.getInstance().getResourceManager().getResource(
                    com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets.VANILLA_VILLAGER_SKIN).orElseThrow()
                    .sourcePackId().equals("file/fresh.zip"), "native villager skin comes from Fresh Animations");
            return;
        }
        var expected = new VillagerRetaliationVillagerModel<Villager>(
                VillagerRetaliationVillagerModel.createBodyLayer().bakeRoot(), DefaultVillagerPoseProvider.INSTANCE);
        expected.riding = actual.riding;
        expected.young = actual.young;
        expected.attackTime = actual.attackTime;
        float speed = entity.isPassenger() ? 0 : Math.min(1, entity.walkAnimation.speed(1));
        float swing = entity.isPassenger() ? 0 : entity.walkAnimation.position(1) * (entity.isBaby() ? 3 : 1);
        expected.setupAnim(entity, swing, speed, entity.tickCount + 1, 25, 20);
        for (String arm : List.of("RightArm", "LeftArm")) {
            compareRotations(actual.root().getChild(arm), expected.root().getChild(arm), scenario.name() + " " + arm);
        }
        if (mode == 2) {
            require(Minecraft.getInstance().getResourceManager().getResource(
                    com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets.COMBAT_VILLAGER_SKIN).orElseThrow()
                    .sourcePackId().equals("file/bridge.zip"), "combat villager skin comes from the bridge");
            require(actual.root().getAllParts().anyMatch(part -> part.getClass().getName().contains("EMF")), "bridge model is decorated by EMF");
            require(actual.getHead().getAllParts().count() > 10, "bridge face geometry is loaded");
            if (scenario.name().equals("sword idle")) {
                require(Math.abs(actual.root().getChild("body").y) > 0.01F, "Fresh Animations body motion executes on the bridge");
            }
            if (scenario.name().equals("sleeping sword")) {
                ModelPart eye = findPart(actual.root(), "r_eye_white");
                require(eye != null && eye.yScale < 0.02F, "equipped sleeping villagers close their FA eyes");
            }
            if (scenario.name().equals("face animation")) {
                ModelPart eye = findPart(actual.root(), "r_eye_white");
                require(eye != null, "FA eye plane is present");
                if (eye.yScale > 0.2F) sawOpenEyes = true;
                if (eye.yScale < 0.02F && !sawClosedEyes) {
                    sawClosedEyes = true;
                    Screenshot.grab(Path.of(System.getProperty(OUTPUT)).toFile(), "bridge-eyes-closed.png",
                            Minecraft.getInstance().getMainRenderTarget(), ignored -> { });
                }
            }
            float marker = expected.root().x;
            if (marker == 0.02F) close(actual.root().getChild("body").yRot, expected.root().getChild("body").yRot, scenario.name() + " body swing");
            if (marker == 0.03F) compareRotations(actual.getHead(), expected.getHead(), scenario.name() + " aim");
            if (marker == 0.01F || marker == 0.04F || marker == 0.05F || entity.isPassenger() || VillagerDownedClientCache.isDowned(entity)) {
                for (String part : List.of("head", "body", "RightArm", "LeftArm", "RightLeg", "LeftLeg")) {
                    ModelPart a = actual.root().getChild(part), b = expected.root().getChild(part);
                    compareRotations(a, b, scenario.name() + " " + part);
                    close(a.x, b.x, scenario.name() + " " + part + " x");
                    close(a.y, b.y, scenario.name() + " " + part + " y");
                    close(a.z, b.z, scenario.name() + " " + part + " z");
                }
            }
        }
    }

    private static ModelPart findPart(ModelPart root, String name) {
        for (var entry : ((com.jvn.villagerretaliation.mixin.client.ModelPartAccessor) (Object) root)
                .villagerretaliation$children().entrySet()) {
            if (entry.getKey().contains(name)) return entry.getValue();
            ModelPart found = findPart(entry.getValue(), name);
            if (found != null) return found;
        }
        return null;
    }

    private static void compareRotations(ModelPart a, ModelPart b, String message) {
        close(a.xRot, b.xRot, message + " pitch");
        close(a.yRot, b.yRot, message + " yaw");
        close(a.zRot, b.zRot, message + " roll");
    }

    private static void close(float actual, float expected, String message) {
        require(Float.isFinite(actual) && Math.abs(actual - expected) < 0.005F,
                message + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Scenario(String name, FluidVillager entity, VillagerArmPose expected, Consumer<FluidVillager> beforeRender) { }

    private static final class FluidVillager extends Villager {
        private boolean water, lava;
        private HumanoidArm mainArm = HumanoidArm.RIGHT;
        private FluidVillager() { super(EntityType.VILLAGER, Minecraft.getInstance().level); }
        @Override public boolean isInWater() { return water; }
        @Override public boolean isInLava() { return lava; }
        @Override public HumanoidArm getMainArm() { return mainArm; }
        private void beginItemUse(InteractionHand hand) {
            startUsingItem(hand);
            setLivingEntityFlag(1, true);
            setLivingEntityFlag(2, hand == InteractionHand.OFF_HAND);
            useItemRemaining -= 10;
        }
    }

    private static final class MatrixScreen extends Screen {
        private MatrixScreen() { super(Component.literal("Animation regression matrix")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (finished) return;
            graphics.fill(0, 0, width, height, 0xFF25282D);
            graphics.drawString(font, "Mode " + mode + ": " + List.of("VR default", "Fresh Animations base", "Fresh Animations + bridge").get(mode), 16, 12, 0xFFFFFF);
            for (int index = page * 6; index < Math.min(scenarios.size(), (page + 1) * 6); index++) {
                Scenario scenario = scenarios.get(index);
                scenario.entity().tickCount = 100 + frames * (scenario.name().equals("face animation") ? 6 : 1);
                scenario.beforeRender().accept(scenario.entity());
                require(DefaultVillagerPoseProvider.INSTANCE.getArmPose(scenario.entity(), scenario.entity().attackAnim) == scenario.expected(),
                        scenario.name() + " selects " + scenario.expected());
                int column = (index % 6) % 3, row = (index % 6) / 3;
                int x = width * (column * 2 + 1) / 6, y = 55 + row * (height - 55) / 2;
                graphics.drawCenteredString(font, scenario.name(), x, y, 0xFFFFFF);
                InventoryScreen.renderEntityInInventory(graphics, x, y + (height - 55) / 2 - 16, 65,
                        new Vector3f(), new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.PI + 0.25F), null, scenario.entity());
                verify(scenario);
            }
            if (Minecraft.getInstance().getOverlay() == null) frames++;
        }
    }
}
