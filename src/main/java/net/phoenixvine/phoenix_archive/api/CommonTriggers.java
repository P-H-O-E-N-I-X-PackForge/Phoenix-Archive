package net.phoenixvine.phoenix_archive.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "phoenix_archive")
public class CommonTriggers {

    @SubscribeEvent
    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TriggerRegistry.fireItem(player, event.getStack()); // use fireItem, not fire
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TriggerRegistry.fireItem(player, event.getCrafting()); // use fireItem, not fire
        }
    }

    @SubscribeEvent
    public static void onMobKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            // Use the registry key, not toString()
            ResourceLocation mobKey = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
            if (mobKey != null) {
                TriggerRegistry.fire(player, "kill", mobKey);
            }
        }
    }
}
