package pm.c7.scout.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.collect.Table;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import pm.c7.scout.item.BaseBagItem;
import pm.c7.scout.screen.BagSlot;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

    @Redirect(method = "copySharedSlots",
        at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/Table;put(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            remap = false))
    private Object scout$skipNullInventories_put(Table<Inventory, Integer, Integer> self,
                                                 Object inventory, Object index, Object size) {
        if (inventory == null) return null;
        return self.put((Inventory) inventory, (int) index, (int) size);
    }

    @Redirect(method = "copySharedSlots",
        at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/Table;get(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            remap = false))
    private Object scout$skipNullInventories_get(Table<Inventory, Integer, Integer> self,
                                                 Object inventory, Object index) {
        if (inventory == null) return null;
        return self.get(inventory, (int) index);
    }

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void scout$preventBagOnBagReplace(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        ScreenHandler self = (ScreenHandler) (Object) this;

        if (actionType != SlotActionType.PICKUP || button != 0) {
            return;
        }

        if (slotIndex < 0 || slotIndex >= self.slots.size()) {
            return;
        }

        Slot slot = self.slots.get(slotIndex);
        if (slot == null) {
            return;
        }

        ItemStack slotStack = slot.getStack();
        ItemStack cursorStack = self.getCursorStack();

        boolean slotHasBag = !slotStack.isEmpty() && slotStack.getItem() instanceof BaseBagItem;
        boolean cursorHasBag = !cursorStack.isEmpty() && cursorStack.getItem() instanceof BaseBagItem;

        if (!slotHasBag || !cursorHasBag) {
            return;
        }

        // No bloquear el inventario principal del jugador ni los slots internos del bag.
        // Solo evitar reemplazar bags equipadas / slots externos especiales con otra bag encima.
        if (slot instanceof BagSlot) {
            return;
        }

        if (slot.inventory == player.getInventory()) {
            return;
        }

        ci.cancel();
    }
}