package net.phoenix.phoenix_archive.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.phoenix.phoenix_archive.client.ArchiveScreen;

import java.util.function.Supplier;

public class SyncLorePacket {
    private final String hardwareId;
    private final boolean state;

    public SyncLorePacket(String hardwareId, boolean state) {
        this.hardwareId = hardwareId;
        this.state = state;
    }

    public static void encode(SyncLorePacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.hardwareId);
        buffer.writeBoolean(msg.state);
    }

    public static SyncLorePacket decode(FriendlyByteBuf buffer) {
        return new SyncLorePacket(buffer.readUtf(), buffer.readBoolean());
    }

    public static void handle(SyncLorePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // 1. Get the ROOT persistent data
                CompoundTag forgeData = mc.player.getPersistentData();

                // 2. Get or Create our sub-tag
                CompoundTag phoenixData = forgeData.getCompound("PhoenixArchive");
                phoenixData.putBoolean(msg.hardwareId, msg.state);

                // 3. CRITICAL: Put it back into the root tag to mark it as changed
                forgeData.put("PhoenixArchive", phoenixData);

                // 4. Instant UI Refresh
                if (mc.screen instanceof ArchiveScreen archive) {
                    archive.init(mc, archive.width, archive.height);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}