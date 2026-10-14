package net.phoenix_archives.phoenix_archive.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenix_archives.phoenix_archive.PhoenixArchive;
import net.phoenix_archives.phoenix_archive.common.LoreSavedData;
import net.phoenix_archives.phoenix_archive.network.BulkSyncLorePacket;

@EventBusSubscriber(modid = PhoenixArchive.MOD_ID)
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
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new BulkSyncLorePacket(masterTag.copy()));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide && event.getEntity().tickCount % 40 == 0) {
            if (event.getEntity() instanceof ServerPlayer player) {
                LoreSavedData data = LoreSavedData.get(player.serverLevel());
                TriggerRegistry.checkForNewCompletions(player, data);

                player.level().getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> {
                    TriggerRegistry.fire(player, "biome", key.location());
                });

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
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
            if (blockId != null) {
                TriggerRegistry.fire(player, "machine", blockId);
            }
        }
    }
}
