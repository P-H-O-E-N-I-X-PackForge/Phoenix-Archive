package net.phoenix_archives.phoenix_archive.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenix_archives.phoenix_archive.client.ArchiveScreen;

import java.util.Arrays;

public record BulkSyncLorePacket(CompoundTag allData) implements CustomPacketPayload {

    public static final Type<BulkSyncLorePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_archive", "bulk_sync_lore"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, BulkSyncLorePacket> STREAM_CODEC = StreamCodec
            .of((buf, msg) -> encode(msg, buf), buf -> decode(buf));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(BulkSyncLorePacket msg, FriendlyByteBuf buffer) {
        buffer.writeNbt(msg.allData);
    }

    public static BulkSyncLorePacket decode(FriendlyByteBuf buffer) {
        return new BulkSyncLorePacket(buffer.readNbt());
    }

    public static void handle(BulkSyncLorePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;

            ArchiveScreen.CLIENT_LORE_CACHE = packet.allData().copy();

            mc.player.getPersistentData().put("PhoenixArchive", packet.allData().copy());

            if (mc.screen instanceof ArchiveScreen archive) {
                archive.init(mc, archive.width, archive.height);
            }
        });
    }

    private static String prettifyKey(String key) {
        String clean = key.replace("unlocked_", "");
        if (clean.isEmpty()) return "Unknown Entry";

        return Arrays.stream(clean.split("_"))
                .map(s -> s.isEmpty() ? "" : s.substring(0, 1).toUpperCase() + s.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(clean);
    }
}
