package net.phoenix.phoenix_archive.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.phoenix_archive.client.ArchiveScreen;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * @param allData This getter allows the handle method to access the data
 */
public record BulkSyncLorePacket(CompoundTag allData) {

    public static void encode(BulkSyncLorePacket msg, FriendlyByteBuf buffer) {
        buffer.writeNbt(msg.allData);
    }

    public static BulkSyncLorePacket decode(FriendlyByteBuf buffer) {
        return new BulkSyncLorePacket(buffer.readNbt());
    }

    public static void handle(BulkSyncLorePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // 1. Get the local cache (The old "Truth")
            CompoundTag oldData = mc.player.getPersistentData().getCompound("PhoenixArchive");

            // 2. Get the new "Truth" from the packet using the field or getter
            CompoundTag newData = packet.allData();

            // 3. Compare to see what's new for the Toast
            for (String key : newData.getAllKeys()) {
                // If it was false (or didn't exist) and is now true
                if (key.startsWith("unlocked_") && !oldData.getBoolean(key)) {
                    mc.getToasts().addToast(new SystemToast(
                            SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                            Component.literal("§6> ARCHIVE_DECRYPTED"),
                            Component.literal("§f" + prettifyKey(key))
                    ));
                }
            }

            // 4. Overwrite the local cache with the absolute Truth from the server
            mc.player.getPersistentData().put("PhoenixArchive", newData.copy());

            // 5. REFRESH SCREEN: If the player has the Archive open, make it update live
            if (mc.screen instanceof ArchiveScreen archive) {
                archive.init(mc, archive.width, archive.height);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static String prettifyKey(String key) {
        String clean = key.replace("unlocked_", "");
        if (clean.isEmpty()) return "Unknown Entry";

        return Arrays.stream(clean.split("_"))
                .map(s -> s.isEmpty() ? "" : s.substring(0, 1).toUpperCase() + s.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(clean);
    }
}