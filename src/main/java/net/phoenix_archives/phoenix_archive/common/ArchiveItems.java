package net.phoenix_archives.phoenix_archive.common;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ArchiveItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("phoenix_archive");

    public static final DeferredHolder<Item, Item> LORE_TABLET = ITEMS.register("lore_tablet",
            LoreTabletItem::new);
}
