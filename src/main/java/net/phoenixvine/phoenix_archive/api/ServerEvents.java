package net.phoenixvine.phoenix_archive.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phoenix_archive.PhoenixArchive;
import net.phoenixvine.phoenix_archive.common.LoreSavedData;
import net.phoenixvine.phoenix_archive.network.BulkSyncLorePacket;
import net.phoenixvine.phoenix_archive.network.PhoenixNetwork;

@Mod.EventBusSubscriber(modid = PhoenixArchive.MOD_ID)
public class ServerEvents {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 1. Initial Handshake (Dimension/Biome)
            TriggerRegistry.hardwareHandshake(player);

            // 2. This will now catch all "Condition-less" entries and unlock them
            TriggerRegistry.checkForNewCompletions(player, LoreSavedData.get(player.serverLevel()));

            // 3. Sync to Client
            syncAllLore(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // This fires when coming back from the End (since it counts as a respawn)
        if (event.getEntity() instanceof ServerPlayer player) {
            syncAllLore(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Use the anchored data
            LoreSavedData data = LoreSavedData.get(player.serverLevel());

            // 1. Tell the server what the NEW environment is
            TriggerRegistry.hardwareHandshake(player);

            // 2. Force a calculation check
            TriggerRegistry.checkForNewCompletions(player, data);

            // 3. FULL SYNC to update the client's CLIENT_LORE_CACHE
            syncAllLore(player);
        }
    }

    // Ensure this method is called to "tether" the Ledger to the Player
    public static void syncAllLore(ServerPlayer player) {
        LoreSavedData data = LoreSavedData.get(player.getServer().overworld());
        CompoundTag masterTag = data.getRawDataForPlayer(player.getUUID());

        player.getPersistentData().put("PhoenixArchive", masterTag.copy());
        PhoenixNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new BulkSyncLorePacket(masterTag.copy()));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isServer() && event.phase == TickEvent.Phase.END && event.player.tickCount % 40 == 0) {
            if (event.player instanceof ServerPlayer player) {
                LoreSavedData data = LoreSavedData.get(player.serverLevel());
                TriggerRegistry.checkForNewCompletions(player, data);

                player.level().getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> {
                    TriggerRegistry.fire(player, "biome", key.location());
                });
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // isWasDeath() = true for death, false for End return
        // For End return, onDimensionChange handles everything
        if (!event.isWasDeath()) return;

        if (event.getEntity() instanceof ServerPlayer newPlayer) {
            syncAllLore(newPlayer);
        }
    }



    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock());
            if (blockId != null) {
                // Changed from "gt_machine" to "machine" to match your Lore JSON
                TriggerRegistry.fire(player, "machine", blockId);
            }
        }
    }




}