package net.phoenix_archives.phoenix_archive.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class PhoenixNetwork {

    private static final String PROTOCOL_VERSION = "1";

    /** Registers every packet; hooked to the mod bus from {@code PhoenixArchive}. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(SyncLorePacket.TYPE, SyncLorePacket.STREAM_CODEC, SyncLorePacket::handle);
        registrar.playToClient(BulkSyncLorePacket.TYPE, BulkSyncLorePacket.STREAM_CODEC, BulkSyncLorePacket::handle);
        registrar.playToServer(BookmarkPacket.TYPE, BookmarkPacket.STREAM_CODEC, BookmarkPacket::handle);
    }
}
