package pm.c7.scout.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;
import pm.c7.scout.Scout;
import pm.c7.scout.ScoutPlayerScreenHandler;
import pm.c7.scout.item.BaseBagItem;
import pm.c7.scout.screen.BagSlot;

@Mixin(value = PlayerScreenHandler.class, priority = 950)
public abstract class PlayerScreenHandlerMixin extends ScreenHandler implements ScoutPlayerScreenHandler {

    protected PlayerScreenHandlerMixin() {
        super(null, 0);
    }

    @Unique public final DefaultedList<BagSlot> satchelSlots = DefaultedList.of();
    @Unique public final DefaultedList<BagSlot> leftPouchSlots = DefaultedList.of();
    @Unique public final DefaultedList<BagSlot> rightPouchSlots = DefaultedList.of();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void scout$addSlots(PlayerInventory inventory, boolean onServer, PlayerEntity owner, CallbackInfo ci) {
        int x = 8, y = 168;
        for (int i = 0; i < Scout.MAX_SATCHEL_SLOTS; i++) {
            if (i % 9 == 0) x = 8;
            BagSlot slot = new BagSlot(i, x, y);
            satchelSlots.add(slot);
            this.addSlot(slot);
            x += 18;
            if ((i + 1) % 9 == 0) y += 18;
        }

        x = 8;
        y = 66;
        for (int i = 0; i < Scout.MAX_POUCH_SLOTS; i++) {
            if (i % 3 == 0) {
                x -= 18;
                y += 54;
            }
            BagSlot slot = new BagSlot(i, x, y);
            leftPouchSlots.add(slot);
            this.addSlot(slot);
            y -= 18;
        }

        x = 152;
        y = 66;
        for (int i = 0; i < Scout.MAX_POUCH_SLOTS; i++) {
            if (i % 3 == 0) {
                x += 18;
                y += 54;
            }
            BagSlot slot = new BagSlot(i, x, y);
            rightPouchSlots.add(slot);
            this.addSlot(slot);
            y -= 18;
        }
    }

    @Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
    private void scout$quickMoveToBag(PlayerEntity player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (player.getAbilities().creativeMode) return;
        if (index < 0 || index >= this.slots.size()) return;

        Slot clickedSlot = this.slots.get(index);
        if (clickedSlot instanceof BagSlot) return;
        if (!clickedSlot.hasStack()) return;

        ItemStack clickedStack = clickedSlot.getStack();

        if (clickedStack.getItem() instanceof BaseBagItem) return;
        if (clickedStack.getItem() instanceof ArmorItem) return;

        if (clickedSlot.inventory != player.getInventory()) return;

        boolean inserted = scout$tryInsert(satchelSlots, clickedSlot)
                || scout$tryInsert(leftPouchSlots, clickedSlot)
                || scout$tryInsert(rightPouchSlots, clickedSlot);

        if (inserted) {
            if (clickedSlot.getStack().isEmpty()) {
                clickedSlot.setStack(ItemStack.EMPTY);
            }
            clickedSlot.markDirty();
            this.sendContentUpdates();
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Unique
    private boolean scout$tryInsert(DefaultedList<BagSlot> bagSlots, Slot source) {
        if (bagSlots.isEmpty()) return false;

        ItemStack srcStack = source.getStack();
        if (srcStack.isEmpty()) return true;

        for (BagSlot bag : bagSlots) {
            if (!bag.isEnabled()) continue;

            ItemStack existing = bag.getStack();
            if (existing.isEmpty()) continue;
            if (!ItemStack.areItemsAndComponentsEqual(existing, srcStack)) continue;

            int max = Math.min(bag.getMaxItemCount(existing), srcStack.getMaxCount());
            int space = max - existing.getCount();
            if (space <= 0) continue;

            int move = Math.min(space, srcStack.getCount());
            existing.increment(move);
            bag.setStack(existing.copy());
            srcStack.decrement(move);

            if (srcStack.isEmpty()) return true;
        }

        for (BagSlot bag : bagSlots) {
            if (!bag.isEnabled()) continue;
            if (!bag.getStack().isEmpty()) continue;
            if (!bag.canInsert(srcStack)) continue;

            int move = Math.min(Math.min(bag.getMaxItemCount(srcStack), srcStack.getMaxCount()), srcStack.getCount());
            bag.setStack(srcStack.copyWithCount(move));
            srcStack.decrement(move);

            if (srcStack.isEmpty()) return true;
        }

        return srcStack.isEmpty();
    }

    @Override
    public final DefaultedList<BagSlot> scout$getSatchelSlots() {
        return satchelSlots;
    }

    @Override
    public final DefaultedList<BagSlot> scout$getLeftPouchSlots() {
        return leftPouchSlots;
    }

    @Override
    public final DefaultedList<BagSlot> scout$getRightPouchSlots() {
        return rightPouchSlots;
    }
}