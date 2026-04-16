package net.phoenixvine.phoenix_archive.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.phoenix_archive.client.ArchiveScreen;

import java.util.function.Supplier;

public class SyncLorePacket {
    private final String hardwareId;
    private final boolean state;
    private final boolean playSound; // New field

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
                // ... (Existing NBT Storage Logic) ...
                CompoundTag forgeData = mc.player.getPersistentData();
                CompoundTag phoenixData = forgeData.getCompound("PhoenixArchive");
                phoenixData.putBoolean(msg.hardwareId, msg.state);
                forgeData.put("PhoenixArchive", phoenixData);

                // 1. Play Narration if the packet requests it
                if (msg.playSound && msg.state) {
                    // Logic to find the sound associated with this ID
                    // This could trigger a custom sound event from your sounds.json
                }

                // 2. Refresh UI if open
                if (mc.screen instanceof ArchiveScreen archive) {
                    archive.init(mc, archive.width, archive.height);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}