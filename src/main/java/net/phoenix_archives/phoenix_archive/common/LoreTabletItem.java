package net.phoenix_archives.phoenix_archive.common;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.phoenix_archives.phoenix_archive.PhoenixArchive;
import net.phoenix_archives.phoenix_archive.client.ClientHelper;
import net.phoenix_archives.phoenix_archive.config.ArchiveConfigs;

import java.util.List;

public class LoreTabletItem extends Item {

    public LoreTabletItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ClientHelper.openArchiveScreen();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        int seed = Math.abs(stack.hashCode());

        String[] headers = {
                "phoenix_archive.lore_tablet.header_0",
                "phoenix_archive.lore_tablet.header_1",
                "phoenix_archive.lore_tablet.header_2",
                "phoenix_archive.lore_tablet.header_3"
        };
        tooltip.add(ArchiveLang.tr(headers[seed % headers.length]));

        PhoenixArchive.PROXY.appendPlayerTooltip(tooltip);

        String tabletName = ArchiveConfigs.INSTANCE.general.loreTabletTooltipName;
        tooltip.add(ArchiveLang.tr("phoenix_archive.lore_tablet_item.click_to_access", tabletName));

        String[] flavorText = {
                "phoenix_archive.lore_tablet.flavor_0",
                "phoenix_archive.lore_tablet.flavor_1",
                "phoenix_archive.lore_tablet.flavor_2",
                "phoenix_archive.lore_tablet.flavor_3",
                "phoenix_archive.lore_tablet.flavor_4",
                "phoenix_archive.lore_tablet.flavor_5"
        };

        int footerIndex = (seed / headers.length) % flavorText.length;
        tooltip.add(Component.empty());
        tooltip.add(ArchiveLang.tr(flavorText[footerIndex]));

        String hexId = Integer.toHexString(seed).toUpperCase();
        tooltip.add(ArchiveLang.tr("phoenix_archive.lore_tablet_item.id_ver", hexId, (seed % 9)));
    }
}
