package net.phoenix_archives.phoenix_archive.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenix_archives.phoenix_archive.client.ArchiveScreen;

public class SyncLorePacket implements CustomPacketPayload {

    public static final Type<SyncLorePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_archive", "sync_lore"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SyncLorePacket> STREAM_CODEC = StreamCodec
            .of((buf, msg) -> encode(msg, buf), buf -> decode(buf));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final String hardwareId;
    private final boolean state;
    private final boolean playSound;

    public SyncLorePacket(String hardwareId, boolean state, boolean playSound) {
        this.hardwareId = hardwareId;
        this.state = state;
        this.playSound = playSound;
    }

    public static void encode(SyncLorePacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.hardwareId);
        buffer.writeBoolean(msg.state);
        buffer.writeBoolean(msg.playSound);
    }

    public static SyncLorePacket decode(FriendlyByteBuf buffer) {
        return new SyncLorePacket(buffer.readUtf(), buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(SyncLorePacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {

                CompoundTag forgeData = mc.player.getPersistentData();
                CompoundTag phoenixData = forgeData.getCompound("PhoenixArchive");
                phoenixData.putBoolean(msg.hardwareId, msg.state);
                forgeData.put("PhoenixArchive", phoenixData);

                if (msg.playSound && msg.state) {

                }

                if (mc.screen instanceof ArchiveScreen archive) {
                    archive.init(mc, archive.width, archive.height);
                }
            }
        });
    }
}
