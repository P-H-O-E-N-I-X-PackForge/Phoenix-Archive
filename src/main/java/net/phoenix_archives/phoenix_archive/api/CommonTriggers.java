package net.phoenix_archives.phoenix_archive.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "phoenix_archive")
public class CommonTriggers {

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            TriggerRegistry.fireItem(player, event.getOriginalStack());
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TriggerRegistry.fireItem(player, event.getCrafting());
        }
    }

    @SubscribeEvent
    public static void onMobKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {

            ResourceLocation mobKey = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
            if (mobKey != null) {
                TriggerRegistry.fire(player, "kill", mobKey);
            }
        }
    }
}
