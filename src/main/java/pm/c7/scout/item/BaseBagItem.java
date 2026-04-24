package pm.c7.scout.item;

import java.util.List;
import java.util.Optional;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import pm.c7.scout.Scout;
import pm.c7.scout.ScoutPlayerScreenHandler;
import pm.c7.scout.ScoutUtil;
import pm.c7.scout.ScoutUtil.EquippedBagRef;
import pm.c7.scout.client.BaseBagItemClient;
import pm.c7.scout.screen.BagSlot;

public class BaseBagItem extends TrinketItem {

    private static final String ITEMS_KEY = "Items";

    private final int slots;
    private final BagType type;

    public BaseBagItem(Settings settings, int slots, BagType type) {
        super(settings);

        if (type == BagType.SATCHEL && slots > Scout.MAX_SATCHEL_SLOTS) {
            throw new IllegalArgumentException("Satchel has too many slots.");
        }

        if (type == BagType.POUCH && slots > Scout.MAX_POUCH_SLOTS) {
            throw new IllegalArgumentException("Pouch has too many slots.");
        }

        this.slots = slots;
        this.type = type;
    }

    public int getSlotCount() {
        return this.slots;
    }

    public BagType getType() {
        return this.type;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(
                Text.translatable(
                        "tooltip.scout.slots",
                        Text.literal(String.valueOf(this.slots)).formatted(Formatting.BLUE)
                ).formatted(Formatting.GRAY)
        );
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient() && TrinketItem.equipItem(user, stack)) {
            return TypedActionResult.consume(stack);
        }

        return TypedActionResult.pass(stack);
    }

    public Inventory getInventory(ItemStack stack) {
        return createInventory(stack, null);
    }

    public Inventory getInventory(EquippedBagRef ref) {
        return createInventory(ref.stack(), ref);
    }

    private Inventory createInventory(ItemStack stack, EquippedBagRef ref) {
        SimpleInventory inventory = new SimpleInventory(this.slots) {
            @Override
            public void markDirty() {
                ItemStack targetStack = stack;

                if (ref != null && ref.inventory() != null) {
                    try {
                        int liveIndex = ref.slotIndex();
                        if (liveIndex >= 0 && liveIndex < ref.inventory().size()) {
                            ItemStack liveStack = ref.inventory().getStack(liveIndex);
                            if (!liveStack.isEmpty()) {
                                targetStack = liveStack;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }

                scout$writeInventoryToStack(targetStack, this);

                if (ref != null && ref.inventory() != null) {
                    try {
                        ref.inventory().markDirty();
                    } catch (Throwable ignored) {
                    }
                }

                super.markDirty();
            }
        };

        NbtComponent customData = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound compound = customData.copyNbt();

        if (compound.contains(ITEMS_KEY, NbtElement.LIST_TYPE)) {
            NbtList items = compound.getList(ITEMS_KEY, NbtElement.COMPOUND_TYPE);
            ScoutUtil.inventoryFromTag(items, inventory);
        }

        return inventory;
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        DefaultedList<ItemStack> stacks = DefaultedList.of();
        Inventory inventory = getInventory(stack);

        for (int i = 0; i < this.slots; i++) {
            stacks.add(inventory.getStack(i));
        }

        if (stacks.stream().allMatch(ItemStack::isEmpty)) {
            return Optional.empty();
        }

        return Optional.of(new BagTooltipData(stacks, this.slots));
    }

    @Override
    public void onEquip(ItemStack stack, SlotReference slotRef, LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            scout$queueRefresh(player);
        }
    }

    @Override
    public void onUnequip(ItemStack stack, SlotReference slotRef, LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            scout$flushCurrentOpenBags(player);
            scout$queueRefresh(player);
        }
    }

    @Override
    public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        return entity instanceof PlayerEntity;
    }

    private static void scout$queueRefresh(PlayerEntity player) {
        if (player.getWorld().isClient()) {
            BaseBagItemClient.scout$queueRefresh(player);
        } else if (player.getServer() != null) {
            player.getServer().execute(() -> refreshAllSlots(player, player.getRegistryManager()));
        }
    }

    private static void scout$writeInventoryToStack(ItemStack stack, Inventory inventory) {
        if (stack == null || stack.isEmpty() || inventory == null) return;

        SimpleInventory copy = new SimpleInventory(inventory.size());
        for (int i = 0; i < inventory.size(); i++) {
            copy.setStack(i, inventory.getStack(i).copy());
        }

        NbtComponent existing = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound compound = existing.copyNbt();
        compound.put(ITEMS_KEY, ScoutUtil.inventoryToTag(copy));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(compound));
    }

    private static void scout$flushSlots(DefaultedList<BagSlot> slots) {
        for (BagSlot slot : slots) {
            if (slot.getBackingInventory() != null) {
                slot.markDirty();
            }
        }
    }

    private static void scout$sanitizeSlots(DefaultedList<BagSlot> slots) {
        for (BagSlot slot : slots) {
            Inventory inv = slot.getBackingInventory();
            if (inv == null) {
                slot.setEnabled(false);
                continue;
            }

            if (slot.getStack().isEmpty() && !slot.isEnabled()) {
                slot.setInventory(null);
                slot.setEnabled(false);
            }
        }
    }

    private static void scout$flushCurrentOpenBags(PlayerEntity player) {
        if (player.playerScreenHandler instanceof ScoutPlayerScreenHandler handler) {
            scout$flushSlots(handler.scout$getSatchelSlots());
            scout$flushSlots(handler.scout$getLeftPouchSlots());
            scout$flushSlots(handler.scout$getRightPouchSlots());
        }

        if (player.currentScreenHandler != player.playerScreenHandler
                && player.currentScreenHandler instanceof ScoutPlayerScreenHandler handler) {
            scout$flushSlots(handler.scout$getSatchelSlots());
            scout$flushSlots(handler.scout$getLeftPouchSlots());
            scout$flushSlots(handler.scout$getRightPouchSlots());
        }
    }

    private static void scout$clearSlots(DefaultedList<BagSlot> slots) {
        for (BagSlot slot : slots) {
            slot.setInventory(null);
            slot.setEnabled(false);
        }
    }

    private static void scout$bindSlots(DefaultedList<BagSlot> slots, EquippedBagRef ref, RegistryWrapper.WrapperLookup registries) {
        if (slots == null || slots.isEmpty()) {
            return;
        }

        if (ref == null || ref.stack().isEmpty() || !(ref.stack().getItem() instanceof BaseBagItem item)) {
            return;
        }

        Inventory inv = item.getInventory(ref);
        int max = Math.min(slots.size(), item.getSlotCount());

        for (int i = 0; i < max; i++) {
            slots.get(i).setInventory(inv);
            slots.get(i).setEnabled(true);
        }

        for (int i = max; i < slots.size(); i++) {
            slots.get(i).setInventory(null);
            slots.get(i).setEnabled(false);
        }
    }

    private static void scout$refreshHandler(ScreenHandler rawHandler, PlayerEntity player, RegistryWrapper.WrapperLookup registries) {
        if (!(rawHandler instanceof ScoutPlayerScreenHandler handler)) {
            return;
        }

        DefaultedList<BagSlot> satchelSlots = handler.scout$getSatchelSlots();
        DefaultedList<BagSlot> leftPouchSlots = handler.scout$getLeftPouchSlots();
        DefaultedList<BagSlot> rightPouchSlots = handler.scout$getRightPouchSlots();

        if ((satchelSlots == null || satchelSlots.isEmpty())
                && (leftPouchSlots == null || leftPouchSlots.isEmpty())
                && (rightPouchSlots == null || rightPouchSlots.isEmpty())) {
            return;
        }

        scout$flushSlots(satchelSlots);
        scout$flushSlots(leftPouchSlots);
        scout$flushSlots(rightPouchSlots);

        EquippedBagRef satchelRef = ScoutUtil.findBagRef(player, BagType.SATCHEL, false);
        EquippedBagRef leftPouchRef = ScoutUtil.findBagRef(player, BagType.POUCH, false);
        EquippedBagRef rightPouchRef = ScoutUtil.findBagRef(player, BagType.POUCH, true);

        scout$clearSlots(satchelSlots);
        scout$clearSlots(leftPouchSlots);
        scout$clearSlots(rightPouchSlots);

        scout$bindSlots(satchelSlots, satchelRef, registries);
        scout$bindSlots(leftPouchSlots, leftPouchRef, registries);
        scout$bindSlots(rightPouchSlots, rightPouchRef, registries);

        scout$sanitizeSlots(satchelSlots);
        scout$sanitizeSlots(leftPouchSlots);
        scout$sanitizeSlots(rightPouchSlots);
    }

    public static void refreshAllSlots(PlayerEntity player, RegistryWrapper.WrapperLookup registries) {
        scout$refreshHandler(player.playerScreenHandler, player, registries);

        if (player.currentScreenHandler != player.playerScreenHandler) {
            scout$refreshHandler(player.currentScreenHandler, player, registries);
        }
    }

    public enum BagType {
        SATCHEL,
        POUCH
    }
}