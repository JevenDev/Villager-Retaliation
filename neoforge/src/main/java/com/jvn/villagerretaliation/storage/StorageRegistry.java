package com.jvn.villagerretaliation.storage;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.capabilities.*;

public final class StorageRegistry {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,VillagerRetaliation.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,VillagerRetaliation.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,VillagerRetaliation.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,VillagerRetaliation.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES=DeferredRegister.create(Registries.RECIPE_SERIALIZER,VillagerRetaliation.MOD_ID);
    public static final DeferredHolder<Block,StorageBlock> VAULT=block("vault",0);
    public static final DeferredHolder<Block,StorageBlock> CONTROLLER=block("vault_controller",1);
    public static final DeferredHolder<Block,StorageBlock> SAFE=block("safe",2);
    public static final DeferredHolder<Item,Item> KEY=ITEMS.register("storage_key",()->new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item,Item> MOLD=ITEMS.register("key_mold",()->new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item,Item> CAST=ITEMS.register("key_cast",()->new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<StorageBlockEntity>> ENTITY=ENTITIES.register("bank_storage",()->BlockEntityType.Builder.of(StorageBlockEntity::new,VAULT.get(),CONTROLLER.get(),SAFE.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<StorageMenu>> MENU=MENUS.register("bank_storage",()->IMenuTypeExtension.create(StorageMenu::new));
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<KeyCopyRecipe>> COPYING=RECIPES.register("key_copying",()->new SimpleCraftingRecipeSerializer<>(KeyCopyRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<KeySmeltingRecipe>> SMELTING=RECIPES.register("key_smelting",()->new SimpleCookingSerializer<>(KeySmeltingRecipe::new,200));
    private static DeferredHolder<Block,StorageBlock> block(String name,int kind) {
        var block=BLOCKS.register(name,()->{
            var properties=BlockBehaviour.Properties.of().strength(50,3600000).requiresCorrectToolForDrops().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
            return new StorageBlock(properties.noOcclusion(),kind);
        });
        ITEMS.register(name,()->new BlockItem(block.get(),new Item.Properties().stacksTo(1)));
        return block;
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); RECIPES.register(bus);
        bus.addListener(StorageRegistry::capabilities);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,ENTITY.get(),(bank,side)->bank.automation());
    }
}
