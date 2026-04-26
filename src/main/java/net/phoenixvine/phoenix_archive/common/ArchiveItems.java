package net.phoenixvine.phoenix_archive.common;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ArchiveItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            "phoenix_archive");

    public static final RegistryObject<Item> LORE_TABLET = ITEMS.register("lore_tablet",
            LoreTabletItem::new);
}
