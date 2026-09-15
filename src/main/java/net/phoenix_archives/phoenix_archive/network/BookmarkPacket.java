package net.phoenix_archives.phoenix_archive.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix_archives.phoenix_archive.api.ServerEvents;
import net.phoenix_archives.phoenix_archive.common.LoreSavedData;

import java.util.function.Supplier;

public class BookmarkPacket {

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

    public static void handle(BookmarkPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
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
        ctx.get().setPacketHandled(true);
    }
}
