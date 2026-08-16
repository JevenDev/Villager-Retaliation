package com.jvn.villagerretaliation.item;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class BannerHelmetGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private BannerHelmetGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void rightClickAttachesAndRestoresCompleteBanner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "BannerHelmetTest"));
        SimpleContainer container = new SimpleContainer(27);
        ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), container);

        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        CompoundTag unrelatedData = new CompoundTag();
        unrelatedData.putBoolean("UnrelatedData", true);
        helmet.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelatedData));
        container.setItem(0, helmet);

        ItemStack banner = new ItemStack(Items.RED_BANNER, 2);
        banner.set(DataComponents.CUSTOM_NAME, Component.literal("Company Standard"));
        menu.setCarried(banner);
        menu.clicked(0, 1, ClickType.PICKUP, player);

        helper.assertTrue(container.getItem(0).is(Items.DIAMOND_HELMET),
                "attaching a banner must not replace the helmet");
        helper.assertValueEqual(menu.getCarried().getCount(), 1,
                "attaching must consume exactly one banner");
        ItemStack storedBanner = BannerHelmetData.getAttachedBanner(helmet, level.registryAccess()).orElseThrow();
        helper.assertTrue(storedBanner.is(Items.RED_BANNER), "the banner base color must survive attachment");
        helper.assertValueEqual(storedBanner.getHoverName().getString(), "Company Standard",
                "the complete banner components must survive attachment");
        helper.assertTrue(helmet.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains("UnrelatedData"),
                "attaching must preserve unrelated helmet custom data");

        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 1, ClickType.PICKUP, player);

        helper.assertFalse(BannerHelmetData.hasAttachedBanner(helmet),
                "right-clicking with an empty cursor must remove the attached banner");
        helper.assertTrue(menu.getCarried().is(Items.RED_BANNER), "removal must put the banner on the cursor");
        helper.assertValueEqual(menu.getCarried().getHoverName().getString(), "Company Standard",
                "removal must restore the original banner components");
        helper.assertTrue(helmet.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains("UnrelatedData"),
                "removal must preserve unrelated helmet custom data");
        helper.succeed();
    }
}
