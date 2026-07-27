package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * Persistent representation and vanilla/NeoForge attribute catalogue for the attribute filter.
 *
 * <p>The reference item shown in the editor is intentionally transient. Only the selected
 * attribute and its inversion are stored, so changing tags or recipes in a datapack immediately
 * changes what an existing filter accepts.</p>
 */
public final class VillagerAttributeFilterData {
    private static final String ROOT_TAG = VillagerRetaliation.MOD_ID + ":attribute_filter";
    private static final String TYPE_TAG = "Type";
    private static final String VALUE_TAG = "Value";
    private static final String INVERTED_TAG = "Inverted";

    private VillagerAttributeFilterData() {
    }

    public static Configuration read(ItemStack filter) {
        if (!VillagerRetaliationItems.isAttributeFilter(filter)) {
            return Configuration.EMPTY;
        }
        CompoundTag root = customRoot(filter);
        AttributeType type = AttributeType.byId(root.getString(TYPE_TAG));
        if (type == null) {
            return Configuration.EMPTY;
        }
        return new Configuration(new Attribute(type, root.getString(VALUE_TAG)), root.getBoolean(INVERTED_TAG));
    }

    public static boolean setSelected(ItemStack filter, Attribute attribute, boolean inverted) {
        if (!VillagerRetaliationItems.isAttributeFilter(filter) || attribute == null) {
            return false;
        }
        Configuration previous = read(filter);
        Configuration next = new Configuration(attribute, inverted);
        if (previous.equals(next)) {
            return false;
        }
        write(filter, next);
        return true;
    }

    public static boolean setInverted(ItemStack filter, boolean inverted) {
        Configuration configuration = read(filter);
        return configuration.attribute() != null
                && setSelected(filter, configuration.attribute(), inverted);
    }

    public static void clear(ItemStack filter) {
        write(filter, Configuration.EMPTY);
    }

    public static boolean isDefault(ItemStack filter) {
        return read(filter).attribute() == null;
    }

    public static boolean matches(Level level, ItemStack filter, ItemStack candidate) {
        if (!VillagerRetaliationItems.isAttributeFilter(filter)
                || candidate == null
                || candidate.isEmpty()) {
            return false;
        }
        Configuration configuration = read(filter);
        if (configuration.attribute() == null) {
            return false;
        }
        boolean applies = configuration.attribute().appliesTo(candidate, level);
        return configuration.inverted() ? !applies : applies;
    }

    public static List<Attribute> availableAttributes(ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Attribute> attributes = new LinkedHashSet<>();
        addIf(attributes, AttributeType.PLACEABLE, stack.getItem() instanceof BlockItem);
        addIf(attributes, AttributeType.CONSUMABLE, stack.has(DataComponents.FOOD));
        addIf(attributes, AttributeType.FLUID_CONTAINER,
                stack.getCapability(Capabilities.FluidHandler.ITEM) != null);
        addIf(attributes, AttributeType.ENCHANTED, stack.isEnchanted());
        addIf(attributes, AttributeType.MAX_ENCHANTED, isMaxEnchanted(stack));
        addIf(attributes, AttributeType.RENAMED, stack.has(DataComponents.CUSTOM_NAME));
        addIf(attributes, AttributeType.DAMAGED, stack.isDamaged());
        addIf(attributes, AttributeType.BADLY_DAMAGED,
                stack.isDamaged() && (float) stack.getDamageValue() / stack.getMaxDamage() > 0.75F);
        addIf(attributes, AttributeType.NOT_STACKABLE, !stack.isStackable());
        addIf(attributes, AttributeType.EQUIPPABLE, isEquippable(stack));
        addIf(attributes, AttributeType.FURNACE_FUEL, AbstractFurnaceBlockEntity.isFuel(stack));
        addIf(attributes, AttributeType.SMELTABLE, hasRecipe(level, stack, RecipeType.SMELTING));
        addIf(attributes, AttributeType.SMOKABLE, hasRecipe(level, stack, RecipeType.SMOKING));
        addIf(attributes, AttributeType.BLASTABLE, hasRecipe(level, stack, RecipeType.BLASTING));
        addIf(attributes, AttributeType.COMPOSTABLE, ComposterBlock.getValue(stack) > 0.0F);

        stack.getTags().forEach(tag ->
                attributes.add(new Attribute(AttributeType.IN_TAG, tag.location().toString())));
        addCreativeGroups(attributes, stack, level);

        String creatorModId = stack.getItem().getCreatorModId(stack);
        if (creatorModId != null && !creatorModId.isBlank()) {
            attributes.add(new Attribute(AttributeType.ADDED_BY, creatorModId));
        }
        for (Holder<Enchantment> enchantment : EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet()) {
            enchantment.unwrapKey().ifPresent(key ->
                    attributes.add(new Attribute(AttributeType.HAS_ENCHANT, key.location().toString())));
        }
        for (DyeColor color : matchingColors(stack)) {
            attributes.add(new Attribute(AttributeType.HAS_COLOR, color.getName()));
        }
        for (Fluid fluid : containedFluids(stack)) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            if (fluidId != null) {
                attributes.add(new Attribute(AttributeType.HAS_FLUID, fluidId.toString()));
            }
        }
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            attributes.add(new Attribute(
                    AttributeType.HAS_NAME,
                    stack.getOrDefault(DataComponents.CUSTOM_NAME, Component.empty()).getString()));
        }
        if (stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            var book = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            attributes.add(new Attribute(AttributeType.BOOK_AUTHOR, book.author()));
            attributes.add(new Attribute(AttributeType.BOOK_COPY, Integer.toString(book.generation())));
        }
        ShulkerLevel shulkerLevel = ShulkerLevel.of(stack);
        if (shulkerLevel != null) {
            attributes.add(new Attribute(AttributeType.SHULKER_FILL_LEVEL, shulkerLevel.id));
        }
        return List.copyOf(attributes);
    }

    public static List<Component> tooltip(ItemStack filter) {
        Configuration configuration = read(filter);
        if (configuration.attribute() == null) {
            return List.of(
                    Component.translatable("item.villagerretaliation.attribute_filter.empty")
                            .withStyle(ChatFormatting.DARK_GRAY),
                    Component.translatable("item.villagerretaliation.attribute_filter.controls")
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
        return List.of(
                Component.translatable("item.villagerretaliation.attribute_filter.mode")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.translatable(configuration.inverted()
                                        ? "item.villagerretaliation.attribute_filter.mode.exclude"
                                        : "item.villagerretaliation.attribute_filter.mode.match")
                                .withStyle(ChatFormatting.GOLD)),
                Component.literal("- ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(configuration.attribute().display().copy().withStyle(ChatFormatting.GRAY)),
                Component.translatable("item.villagerretaliation.attribute_filter.controls")
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static CompoundTag customRoot(ItemStack filter) {
        var customData = filter.getOrDefault(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        return customData.isEmpty() ? new CompoundTag() : customData.copyTag().getCompound(ROOT_TAG);
    }

    private static void write(ItemStack filter, Configuration configuration) {
        if (!VillagerRetaliationItems.isAttributeFilter(filter)) {
            return;
        }
        var existing = filter.getOrDefault(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        CompoundTag customData = existing.isEmpty() ? new CompoundTag() : existing.copyTag();
        if (configuration.attribute() == null) {
            customData.remove(ROOT_TAG);
        } else {
            CompoundTag root = new CompoundTag();
            root.putString(TYPE_TAG, configuration.attribute().type().id);
            if (!configuration.attribute().value().isEmpty()) {
                root.putString(VALUE_TAG, configuration.attribute().value());
            }
            if (configuration.inverted()) {
                root.putBoolean(INVERTED_TAG, true);
            }
            customData.put(ROOT_TAG, root);
        }
        if (customData.isEmpty()) {
            filter.remove(DataComponents.CUSTOM_DATA);
        } else {
            filter.set(DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(customData));
        }
    }

    private static void addIf(Set<Attribute> attributes, AttributeType type, boolean condition) {
        if (condition) {
            attributes.add(new Attribute(type, ""));
        }
    }

    private static boolean isEquippable(ItemStack stack) {
        Equipable equipable = Equipable.get(stack);
        return equipable != null && equipable.getEquipmentSlot().getType() != EquipmentSlot.Type.HAND;
    }

    private static boolean isMaxEnchanted(ItemStack stack) {
        for (var entry : stack.getTagEnchantments().entrySet()) {
            if (entry.getIntValue() >= entry.getKey().value().getMaxLevel()) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean hasRecipe(Level level, ItemStack stack, RecipeType<?> type) {
        return level != null && level.getRecipeManager()
                .getRecipeFor((RecipeType) type, new SingleRecipeInput(stack.copy()), level)
                .isPresent();
    }

    private static void addCreativeGroups(Set<Attribute> attributes, ItemStack stack, Level level) {
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
                continue;
            }
            ensureCreativeTabBuilt(tab, level);
            if (!tabContains(tab, stack)) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id != null) {
                attributes.add(new Attribute(AttributeType.IN_ITEM_GROUP, id.toString()));
            }
        }
    }

    private static void ensureCreativeTabBuilt(CreativeModeTab tab, Level level) {
        if (level == null
                || !tab.getDisplayItems().isEmpty()
                || !tab.getSearchTabDisplayItems().isEmpty()) {
            return;
        }
        try {
            tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                    level.enabledFeatures(), false, level.registryAccess()));
        } catch (RuntimeException | LinkageError ignored) {
            // A third-party creative tab must not make a filter unusable.
        }
    }

    private static boolean tabContains(CreativeModeTab tab, ItemStack stack) {
        return tab.contains(stack) || tab.contains(new ItemStack(stack.getItem()));
    }

    private static Collection<DyeColor> matchingColors(ItemStack stack) {
        LinkedHashSet<DyeColor> colors = new LinkedHashSet<>();
        DyeColor direct = DyeColor.getColor(stack);
        if (direct != null) {
            colors.add(direct);
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        for (DyeColor color : DyeColor.values()) {
            if (path.startsWith(color.getName() + "_")) {
                colors.add(color);
            }
        }
        return colors;
    }

    private static List<Fluid> containedFluids(ItemStack stack) {
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return List.of();
        }
        List<Fluid> fluids = new ArrayList<>();
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            if (!handler.getFluidInTank(tank).isEmpty()) {
                fluids.add(handler.getFluidInTank(tank).getFluid());
            }
        }
        return fluids;
    }

    public record Configuration(Attribute attribute, boolean inverted) {
        private static final Configuration EMPTY = new Configuration(null, false);
    }

    public record Attribute(AttributeType type, String value) {
        public Attribute {
            Objects.requireNonNull(type, "type");
            value = value == null ? "" : value;
        }

        public boolean appliesTo(ItemStack stack, Level level) {
            return type.appliesTo(stack, level, value);
        }

        public Component display() {
            String key = "item.villagerretaliation.attribute_filter.attribute." + type.id;
            return type.hasValue
                    ? Component.translatable(key, type.displayValue(value))
                    : Component.translatable(key);
        }
    }

    public enum AttributeType {
        PLACEABLE("placeable"),
        CONSUMABLE("consumable"),
        FLUID_CONTAINER("fluid_container"),
        ENCHANTED("enchanted"),
        MAX_ENCHANTED("max_enchanted"),
        RENAMED("renamed"),
        DAMAGED("damaged"),
        BADLY_DAMAGED("badly_damaged"),
        NOT_STACKABLE("not_stackable"),
        EQUIPPABLE("equippable"),
        FURNACE_FUEL("furnace_fuel"),
        SMELTABLE("smeltable"),
        SMOKABLE("smokable"),
        BLASTABLE("blastable"),
        COMPOSTABLE("compostable"),
        IN_TAG("in_tag", true),
        IN_ITEM_GROUP("in_item_group", true),
        ADDED_BY("added_by", true),
        HAS_ENCHANT("has_enchant", true),
        HAS_COLOR("has_color", true),
        HAS_FLUID("has_fluid", true),
        HAS_NAME("has_name", true),
        BOOK_AUTHOR("book_author", true),
        BOOK_COPY("book_copy", true),
        SHULKER_FILL_LEVEL("shulker_fill_level", true);

        private final String id;
        private final boolean hasValue;

        AttributeType(String id) {
            this(id, false);
        }

        AttributeType(String id, boolean hasValue) {
            this.id = id;
            this.hasValue = hasValue;
        }

        public String id() {
            return id;
        }

        public static AttributeType byId(String id) {
            for (AttributeType type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            return null;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private boolean appliesTo(ItemStack stack, Level level, String value) {
            return switch (this) {
                case PLACEABLE -> stack.getItem() instanceof BlockItem;
                case CONSUMABLE -> stack.has(DataComponents.FOOD);
                case FLUID_CONTAINER -> stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
                case ENCHANTED -> stack.isEnchanted();
                case MAX_ENCHANTED -> isMaxEnchanted(stack);
                case RENAMED -> stack.has(DataComponents.CUSTOM_NAME);
                case DAMAGED -> stack.isDamaged();
                case BADLY_DAMAGED -> stack.isDamaged()
                        && (float) stack.getDamageValue() / stack.getMaxDamage() > 0.75F;
                case NOT_STACKABLE -> !stack.isStackable();
                case EQUIPPABLE -> isEquippable(stack);
                case FURNACE_FUEL -> AbstractFurnaceBlockEntity.isFuel(stack);
                case SMELTABLE -> hasRecipe(level, stack, RecipeType.SMELTING);
                case SMOKABLE -> hasRecipe(level, stack, RecipeType.SMOKING);
                case BLASTABLE -> hasRecipe(level, stack, RecipeType.BLASTING);
                case COMPOSTABLE -> ComposterBlock.getValue(stack) > 0.0F;
                case IN_TAG -> {
                    ResourceLocation id = ResourceLocation.tryParse(value);
                    yield id != null && stack.getTags().anyMatch(tag -> tag.location().equals(id));
                }
                case IN_ITEM_GROUP -> {
                    ResourceLocation id = ResourceLocation.tryParse(value);
                    CreativeModeTab tab = id == null ? null : BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
                    if (tab != null) {
                        ensureCreativeTabBuilt(tab, level);
                    }
                    yield tab != null && tabContains(tab, stack);
                }
                case ADDED_BY -> value.equals(stack.getItem().getCreatorModId(stack));
                case HAS_ENCHANT -> EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream()
                        .map(Holder::unwrapKey)
                        .flatMap(java.util.Optional::stream)
                        .anyMatch(key -> key.location().toString().equals(value));
                case HAS_COLOR -> matchingColors(stack).stream()
                        .anyMatch(color -> color.getName().equals(value));
                case HAS_FLUID -> containedFluids(stack).stream()
                        .anyMatch(fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString().equals(value));
                case HAS_NAME -> stack.has(DataComponents.CUSTOM_NAME)
                        && stack.getOrDefault(DataComponents.CUSTOM_NAME, Component.empty()).getString().equals(value);
                case BOOK_AUTHOR -> stack.has(DataComponents.WRITTEN_BOOK_CONTENT)
                        && stack.get(DataComponents.WRITTEN_BOOK_CONTENT).author().equals(value);
                case BOOK_COPY -> stack.has(DataComponents.WRITTEN_BOOK_CONTENT)
                        && Integer.toString(stack.get(DataComponents.WRITTEN_BOOK_CONTENT).generation()).equals(value);
                case SHULKER_FILL_LEVEL -> {
                    ShulkerLevel levelValue = ShulkerLevel.of(stack);
                    yield levelValue != null && levelValue.id.equals(value);
                }
            };
        }

        private Object displayValue(String value) {
            return switch (this) {
                case IN_TAG -> "#" + value;
                case IN_ITEM_GROUP -> {
                    ResourceLocation id = ResourceLocation.tryParse(value);
                    CreativeModeTab tab = id == null ? null : BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
                    yield tab == null ? value : tab.getDisplayName();
                }
                case ADDED_BY -> ModList.get().getModContainerById(value)
                        .map(container -> container.getModInfo().getDisplayName())
                        .orElse(value);
                case HAS_ENCHANT -> {
                    ResourceLocation id = ResourceLocation.tryParse(value);
                    yield id == null ? value : Component.translatable(
                            "enchantment." + id.getNamespace() + "." + id.getPath());
                }
                case HAS_COLOR -> Component.translatable("color.minecraft." + value);
                case HAS_FLUID -> {
                    ResourceLocation id = ResourceLocation.tryParse(value);
                    Fluid fluid = id == null ? null : BuiltInRegistries.FLUID.get(id);
                    yield fluid == null ? value : fluid.getFluidType().getDescription();
                }
                case BOOK_COPY -> Component.translatable(
                        "item.villagerretaliation.attribute_filter.book_copy." + value);
                case SHULKER_FILL_LEVEL -> Component.translatable(
                        "item.villagerretaliation.attribute_filter.shulker." + value);
                default -> value;
            };
        }
    }

    private enum ShulkerLevel {
        EMPTY("empty"),
        PARTIAL("partial"),
        FULL("full");

        private final String id;

        ShulkerLevel(String id) {
            this.id = id;
        }

        private static ShulkerLevel of(ItemStack stack) {
            if (!(Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock)
                    || stack.has(DataComponents.CONTAINER_LOOT)) {
                return null;
            }
            ItemContainerContents contents =
                    stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            if (contents == ItemContainerContents.EMPTY || contents.getSlots() == 0) {
                return EMPTY;
            }
            NonNullList<ItemStack> inventory = NonNullList.withSize(27, ItemStack.EMPTY);
            contents.copyInto(inventory);
            boolean full = inventory.stream()
                    .allMatch(item -> !item.isEmpty() && item.getCount() >= item.getMaxStackSize());
            return full ? FULL : PARTIAL;
        }
    }
}
