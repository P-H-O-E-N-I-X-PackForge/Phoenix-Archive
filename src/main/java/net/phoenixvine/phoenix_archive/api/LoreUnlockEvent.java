package net.phoenixvine.phoenix_archive.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class LoreUnlockEvent extends Event {

    public final ServerPlayer player;
    public final ResourceLocation entryId;

    public LoreUnlockEvent(ServerPlayer player, ResourceLocation entryId) {
        this.player = player;
        this.entryId = entryId;
    }
}
