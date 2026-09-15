package net.phoenix_archives.phoenix_archive.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix_archives.phoenix_archive.client.ArchiveScreen;

import java.util.Arrays;
import java.util.function.Supplier;

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

            ArchiveScreen.CLIENT_LORE_CACHE = packet.allData().copy();

            mc.player.getPersistentData().put("PhoenixArchive", packet.allData().copy());

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
