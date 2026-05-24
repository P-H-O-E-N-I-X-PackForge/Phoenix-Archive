package net.phoenixvine.phoenix_archive.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PhoenixNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("phoenix_archive", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void init() {
        // ID 0: Single hardware unlock (used for the initial "ding" and toast)
        CHANNEL.registerMessage(0, SyncLorePacket.class, SyncLorePacket::encode, SyncLorePacket::decode,
                SyncLorePacket::handle);

        // ID 1: Bulk synchronization (used for dimension hops, login, and respawn)
        CHANNEL.registerMessage(1, BulkSyncLorePacket.class, BulkSyncLorePacket::encode, BulkSyncLorePacket::decode,
                BulkSyncLorePacket::handle);

        // ID 2: Client → Server bookmark toggle (persisted in PhoenixArchive NBT, synced back via BulkSyncLorePacket)
        CHANNEL.registerMessage(2, BookmarkPacket.class, BookmarkPacket::encode, BookmarkPacket::decode,
                BookmarkPacket::handle);
    }
}
