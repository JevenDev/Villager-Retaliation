package com.jvn.villagerretaliation.compat.secondwind;

import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerSecondWindCompatGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillagerSecondWindCompatGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void adapterUsesVillagerTargetAfterReviverArgument(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ArmorStand reviverArgument = EntityType.ARMOR_STAND.create(level);
        Villager villager = EntityType.VILLAGER.create(level);
        helper.assertTrue(reviverArgument != null && villager != null, "test entities should be created");
        reviverArgument.moveTo(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter());
        villager.moveTo(helper.absolutePos(new BlockPos(2, 1, 1)).getCenter());
        level.addFreshEntity(reviverArgument);
        level.addFreshEntity(villager);

        VillagerDownedService.enterDowned(
                level,
                villager,
                new VillagerDeathProtectionResolver.ProtectionResult(true, List.of("secondwind:test")),
                7.0F);

        Object[] adapterArguments = {reviverArgument, villager};
        helper.assertTrue(Boolean.TRUE.equals(VillagerSecondWindCompat.dispatchAdapterCall(
                "canRevive", adapterArguments)), "the adapter should inspect the villager target, not the first living argument");
        Object healthOverride = VillagerSecondWindCompat.dispatchAdapterCall("reviveHealthOverride", adapterArguments);
        helper.assertTrue(healthOverride instanceof OptionalDouble health && health.isPresent()
                        && Math.abs(health.getAsDouble() - 7.0D) < 0.000001D,
                "the adapter should read the villager target's exact recovery health");
        helper.assertTrue(Boolean.TRUE.equals(VillagerSecondWindCompat.dispatchAdapterCall(
                "revive", adapterArguments)), "the adapter should recover the villager target");
        helper.assertFalse(VillagerDownedService.isDowned(villager), "the adapter revive should clear VR's downed state");
        helper.assertTrue(Math.abs(villager.getHealth() - 7.0F) < 0.000001F,
                "the adapter revive should restore the villager's exact recovery health");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void creativePlayerCompletesRealSecondWindReviveChannel(GameTestHelper helper) {
        if (!VillagerSecondWindCompat.isActive()) {
            helper.succeed();
            return;
        }

        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        helper.assertTrue(villager != null, "test villager should be created");
        villager.moveTo(helper.absolutePos(new BlockPos(2, 1, 1)).getCenter());
        level.addFreshEntity(villager);

        ServerPlayer reviver = helper.makeMockServerPlayerInLevel();
        reviver.setPos(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter());
        helper.assertTrue(reviver.isCreative(), "the regression requires a Creative reviver");

        VillagerDownedService.enterDowned(
                level,
                villager,
                new VillagerDeathProtectionResolver.ProtectionResult(true, List.of("secondwind:creative_channel_test")),
                7.0F);

        try {
            Class<?> service = Class.forName("com.jvn.secondwind.state.SecondWindEntityService");
            Method canPlayerRevive = service.getMethod("canPlayerRevive", ServerPlayer.class,
                    net.minecraft.world.entity.LivingEntity.class);
            Method refreshReviveChannel = service.getMethod("refreshReviveChannel", ServerPlayer.class,
                    net.minecraft.world.entity.LivingEntity.class);

            helper.assertTrue(Boolean.TRUE.equals(canPlayerRevive.invoke(null, reviver, villager)),
                    "Second Wind should accept a Creative player as a reviver");
            helper.onEachTick(() -> {
                if (!VillagerDownedService.isDowned(villager)) return;
                try {
                    helper.assertTrue(Boolean.TRUE.equals(refreshReviveChannel.invoke(null, reviver, villager)),
                            "the server should keep the Creative revive channel active");
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Could not refresh the real Second Wind revive channel", exception);
                }
            });
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access the real Second Wind entity service", exception);
        }

        helper.succeedWhen(() -> {
            helper.assertFalse(VillagerDownedService.isDowned(villager),
                    "the real Second Wind channel should recover the VR villager");
            helper.assertTrue(Math.abs(villager.getHealth() - 7.0F) < 0.000001F,
                    "the completed channel should preserve VR's recovery health");
        });
    }
}
