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
            TriggerRegistry.hardwareHandshake(player);
            TriggerRegistry.checkForNewCompletions(player, LoreSavedData.get(player.serverLevel()));
            syncAllLore(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncAllLore(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LoreSavedData data = LoreSavedData.get(player.serverLevel());
            TriggerRegistry.hardwareHandshake(player);
            TriggerRegistry.checkForNewCompletions(player, data);
            syncAllLore(player);
        }
    }

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

                // Biome check on tick
                player.level().getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> {
                    TriggerRegistry.fire(player, "biome", key.location());
                });

                // FIX #9: Re-fire item/wearing on tick so item conditions get picked up
                // without requiring a world restart
                for (net.minecraft.world.item.ItemStack stack : player.getInventory().items) {
                    if (!stack.isEmpty()) TriggerRegistry.fireItem(player, stack);
                }
                player.getArmorSlots().forEach(stack -> {
                    if (!stack.isEmpty()) TriggerRegistry.fireWearing(player, stack);
                });
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
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
                TriggerRegistry.fire(player, "machine", blockId);
            }
        }
    }
}
