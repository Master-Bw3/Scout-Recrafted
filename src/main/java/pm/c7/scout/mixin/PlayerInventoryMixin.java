package pm.c7.scout.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import pm.c7.scout.ScoutPlayerScreenHandler;
import pm.c7.scout.item.BaseBagItem;
import pm.c7.scout.screen.BagSlot;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {

    @Shadow public PlayerEntity player;

    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void scout$insertIntoBags(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;
        if (player.getWorld().isClient()) return;
        if (!(player.playerScreenHandler instanceof ScoutPlayerScreenHandler handler)) return;
        if (stack.getItem() instanceof BaseBagItem) return;

        scout$tryStack(handler.scout$getSatchelSlots(), stack);
        scout$tryStack(handler.scout$getLeftPouchSlots(), stack);
        scout$tryStack(handler.scout$getRightPouchSlots(), stack);

        if (stack.isEmpty()) {
            cir.setReturnValue(true);
        }
    }

    private static boolean scout$tryStack(DefaultedList<BagSlot> slots, ItemStack stack) {
        for (BagSlot bag : slots) {
            if (!bag.isEnabled()) continue;

            ItemStack existing = bag.getStack();
            if (existing.isEmpty()) continue;
            if (!ItemStack.areItemsAndComponentsEqual(existing, stack)) continue;

            int max = Math.min(bag.getMaxItemCount(), stack.getMaxCount());
            int space = max - existing.getCount();
            if (space <= 0) continue;

            int move = Math.min(space, stack.getCount());

            ItemStack newStack = existing.copy();
            newStack.increment(move);

            bag.setStack(newStack);
            bag.markDirty();

            stack.decrement(move);

            if (stack.isEmpty()) return true;
        }

        return false;
    }
}