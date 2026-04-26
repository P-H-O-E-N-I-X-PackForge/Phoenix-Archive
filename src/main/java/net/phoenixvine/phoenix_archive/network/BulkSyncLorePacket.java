package net.phoenixvine.phoenix_archive.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.phoenix_archive.client.ArchiveScreen;

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

            // 1. Total Cache Replacement
            // Don't loop or filter. If the server says this is the data, this is the data.
            ArchiveScreen.CLIENT_LORE_CACHE = packet.allData().copy();

            // 2. Update Persistent Data for display
            mc.player.getPersistentData().put("PhoenixArchive", packet.allData().copy());

            // 3. Immediate UI Refresh
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
