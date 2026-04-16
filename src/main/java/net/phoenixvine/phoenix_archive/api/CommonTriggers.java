package net.phoenixvine.phoenix_archive.api;


import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "phoenix_archive")
public class CommonTriggers {

    // 1. ITEM PICKUP (Trigger logic when they find a specific item)
    @SubscribeEvent
    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String itemID = event.getStack().getItem().getDescriptionId(); // or use ForgeRegistries.ITEMS.getKey()
            TriggerRegistry.fire(player, "item", event.getStack().getItem().asItem().toString());
        }
    }

    // 2. CRAFTING (Trigger when they craft a specific machine or tool)
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TriggerRegistry.fire(player, "craft", event.getCrafting().getItem().toString());
        }
    }

    // 3. SLAYING MOBS (Trigger lore when a boss or specific mob is killed)
    @SubscribeEvent
    public static void onMobKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            String mobID = event.getEntity().getType().getDescriptionId();
            TriggerRegistry.fire(player, "kill", event.getEntity().getType().toString());
        }
    }
}
