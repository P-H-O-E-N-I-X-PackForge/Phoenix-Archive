package net.phoenix.phoenix_archive.api;

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
import net.phoenix.phoenix_archive.PhoenixArchive;
import net.phoenix.phoenix_archive.common.LoreSavedData;
import net.phoenix.phoenix_archive.network.BulkSyncLorePacket;
import net.phoenix.phoenix_archive.network.PhoenixNetwork;

@Mod.EventBusSubscriber(modid = PhoenixArchive.MOD_ID)
public class ServerEvents {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LoreSavedData data = LoreSavedData.get(player.serverLevel());

            // 1. Calculate the truth first (unlocks base lore/existing progress)
            TriggerRegistry.checkForNewCompletions(player, data);

            // 2. Fire environmental triggers for the current spot
            TriggerRegistry.fire(player, "dimension", player.level().dimension().location());
            player.level().getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> {
                TriggerRegistry.fire(player, "biome", key.location());
            });

            // 3. Sync the final result to the client
            syncAllLore(player);

            PhoenixArchive.LOGGER.info("§6[SYSTEM] §fArchive handshake complete for {}", player.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Dimension travel destroys the client-side local cache.
            // We must push the Master Ledger data to the new entity immediately.
            syncAllLore(player);

            // Now record the 'Metadata' that they visited this dimension
            TriggerRegistry.fire(player, "dimension", event.getTo().location());
        }
    }

    // Ensure this method is called to "tether" the Ledger to the Player
    public static void syncAllLore(ServerPlayer player) {
        LoreSavedData data = LoreSavedData.get(player.getServer().overworld());
        CompoundTag masterTag = data.getRawDataForPlayer(player.getUUID());

        // CRITICAL: Put the data into the player's actual NBT so the GUI sees it
        player.getPersistentData().put("PhoenixArchive", masterTag.copy());

        // Push to client
        PhoenixNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new BulkSyncLorePacket(masterTag.copy()));
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // This fires when returning from the End or Respawning
        if (event.getEntity() instanceof ServerPlayer newPlayer) {
            // Ignore what the 'original' entity had.
            // Force the new entity to sync with the Master Ledger.
            syncAllLore(newPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isServer() && event.phase == TickEvent.Phase.END && event.player.tickCount % 40 == 0) {
            if (event.player instanceof ServerPlayer player) {
                player.level().getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> {
                    TriggerRegistry.fire(player, "biome", key.location());
                });
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock());
            if (blockId != null) {
                TriggerRegistry.fire(player, "gt_machine", blockId);
            }
        }
    }

}