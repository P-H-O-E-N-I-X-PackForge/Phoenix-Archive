package net.phoenix_archives.phoenix_archive.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix_archives.phoenix_archive.client.ArchiveScreen;

import java.util.function.Supplier;

public class SyncLorePacket {

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

    public static void handle(SyncLorePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
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
        ctx.get().setPacketHandled(true);
    }
}
