package pm.c7.scout.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import pm.c7.scout.item.BagTooltipData;
import pm.c7.scout.item.BaseBagItem;

import java.util.Optional;

import static pm.c7.scout.item.BaseBagItem.refreshAllSlots;

public class BaseBagItemClient {
    public static void scout$queueRefresh(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> {
                if (client.player != null) {
                    refreshAllSlots(client.player, client.player.getRegistryManager());
                }
            });
        }
    }
}
