package net.phoenix_archives.phoenix_archive.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenix_archives.phoenix_archive.api.ServerEvents;
import net.phoenix_archives.phoenix_archive.common.LoreSavedData;

public class BookmarkPacket implements CustomPacketPayload {

    public static final Type<BookmarkPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_archive", "bookmark"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, BookmarkPacket> STREAM_CODEC = StreamCodec
            .of((buf, msg) -> encode(msg, buf), buf -> decode(buf));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final String entryId;
    private final boolean bookmarked;

    public BookmarkPacket(String entryId, boolean bookmarked) {
        this.entryId = entryId;
        this.bookmarked = bookmarked;
    }

    public static void encode(BookmarkPacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.entryId);
        buffer.writeBoolean(msg.bookmarked);
    }

    public static BookmarkPacket decode(FriendlyByteBuf buffer) {
        return new BookmarkPacket(buffer.readUtf(), buffer.readBoolean());
    }

    public static void handle(BookmarkPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player == null) return;

            String key = "bookmark:" + msg.entryId;
            LoreSavedData data = LoreSavedData.get(player.getServer().overworld());

            if (msg.bookmarked) {
                data.unlock(player.getUUID(), key);
            } else {
                data.relock(player.getUUID(), key);
            }

            ServerEvents.syncAllLore(player);
        });
    }
}
