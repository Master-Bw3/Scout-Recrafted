package pm.c7.scout.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientNetworkHandlerMixin {

    @Inject(
        method = "onScreenHandlerSlotUpdate",
        at = @At("HEAD"),
        cancellable = true
    )
    private void scout$ignoreOnlyCreativeOutOfBoundsForCurrentHandler(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        // Este parche solo aplica si el jugador está viendo el inventario creativo.
        if (!(client.currentScreen instanceof CreativeInventoryScreen)) {
            return;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null) return;

        int slot = packet.getSlot();

        // Cursor slot: válido.
        if (slot == -1) return;

        // Muy importante:
        // solo ignorar el paquete si corresponde al handler actual visible.
        // Si no, no tocar nada.
        if (packet.getSyncId() != handler.syncId) {
            return;
        }

        // En creativo, al equipar/desequipar trinkets pueden llegar updates
        // fuera del rango del handler visible. Ignorarlos evita el protocol error.
        if (slot < 0 || slot >= handler.slots.size()) {
            ci.cancel();
        }
    }
}