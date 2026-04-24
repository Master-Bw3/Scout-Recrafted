package pm.c7.scout;

import java.util.Optional;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import pm.c7.scout.item.BaseBagItem;
import pm.c7.scout.item.BaseBagItem.BagType;

public class ScoutUtil {
    public static final Identifier SLOT_TEXTURE = Identifier.of("scout", "textures/gui/slots.png");

    public static ItemStack findBagItem(PlayerEntity player, BaseBagItem.BagType type, boolean right) {
        Optional<TrinketComponent> optional = TrinketsApi.getTrinketComponent(player);
        if (optional.isEmpty()) return ItemStack.EMPTY;

        TrinketComponent component = optional.get();
        int pouchCount = 0;

        for (Pair<SlotReference, ItemStack> pair : component.getAllEquipped()) {
            ItemStack stack = pair.getRight();

            if (!(stack.getItem() instanceof BaseBagItem bag)) continue;
            if (bag.getType() != type) continue;

            if (type == BagType.POUCH) {
                if (pouchCount == (right ? 1 : 0)) {
                    return stack;
                }
                pouchCount++;
            } else {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static int scout$getSlotIndex(SlotReference slotRef) {
        try {
            return slotRef.index();
        } catch (Throwable ignored) {
            return -1;
        }
    }


    /**
     * Serialize a SimpleInventory to an NbtList.
     * Uses RegistryWrapper.WrapperLookup so item components are encoded correctly.
     */
    public static NbtList inventoryToTag(SimpleInventory inventory) {
        NbtList tag = new NbtList();

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                NbtCompound stackTag = new NbtCompound();
                stackTag.putInt("Slot", i);
                stackTag.put("Stack", ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).result().orElse(new NbtCompound()));
                tag.add(stackTag);
            }
        }

        return tag;
    }

    private static Inventory scout$getSlotInventory(SlotReference slotRef) {
        try {
            return slotRef.inventory();
        } catch (Throwable ignored) {
            return null;
        }
    }


    public record EquippedBagRef(ItemStack stack, Inventory inventory, int slotIndex) {}


    public static EquippedBagRef findBagRef(PlayerEntity player, BaseBagItem.BagType type, boolean right) {
        Optional<TrinketComponent> optional = TrinketsApi.getTrinketComponent(player);
        if (optional.isEmpty()) return null;

        TrinketComponent component = optional.get();
        int pouchCount = 0;

        for (Pair<SlotReference, ItemStack> pair : component.getAllEquipped()) {
            SlotReference slotRef = pair.getLeft();
            Inventory inventory = scout$getSlotInventory(slotRef);
            int index = scout$getSlotIndex(slotRef);

            if (inventory == null || index < 0 || index >= inventory.size()) continue;

            ItemStack stack = inventory.getStack(index);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof BaseBagItem bag)) continue;
            if (bag.getType() != type) continue;

            if (type == BagType.POUCH) {
                if (pouchCount == (right ? 1 : 0)) {
                    return new EquippedBagRef(stack, inventory, index);
                }
                pouchCount++;
            } else {
                return new EquippedBagRef(stack, inventory, index);
            }
        }

        return null;
    }


    /**
     * Deserialize an NbtList back into a SimpleInventory.
     */
    public static void inventoryFromTag(NbtList tag, SimpleInventory inventory) {
        inventory.clear();

        tag.forEach(element -> {
            NbtCompound stackTag = (NbtCompound) element;
            int slot = stackTag.getInt("Slot");
            if (stackTag.contains("Stack", NbtElement.COMPOUND_TYPE)) {
                ItemStack.CODEC.parse(NbtOps.INSTANCE, stackTag.getCompound("Stack")).resultOrPartial((x) -> {})
                        .ifPresent(stack -> inventory.setStack(slot, stack));
            }
        });
    }
}