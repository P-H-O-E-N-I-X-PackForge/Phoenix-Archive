package net.phoenixvine.phoenix_archive.common;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.phoenixvine.phoenix_archive.PhoenixArchive;
import net.phoenixvine.phoenix_archive.client.ClientHelper;
import net.phoenixvine.phoenix_archive.config.ArchiveConfigs;

import java.util.List;

import javax.annotation.Nullable;

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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // ... your existing tooltip code, but remove the Minecraft.getInstance() call
        // Replace the username lookup with just "GUEST" or guard it differently
        int seed = Math.abs(stack.hashCode());

        String[] headers = {
                "§8// §aPHOENIX_ARCHIVE_INTERFACE_v4.2",
                "§2[ §aSYSTEM_READY §2] §7Uplink Stable",
                "§b⚡ §3ROOT_UPLINK_ACTIVE",
                "§f$ §7sudo get_archives --force"
        };
        tooltip.add(Component.literal(headers[seed % headers.length]));

        PhoenixArchive.PROXY.appendPlayerTooltip(tooltip);

        String tabletName = ArchiveConfigs.INSTANCE.general.loreTabletTooltipName;
        tooltip.add(Component.literal("§6> §7Click to access §f" + tabletName));

        String[] flavorText = {
                "§e\"Reclaim what is lost, remember who you are.\"",
                "§e\"Was our power worth the loss of the world?\"",
                "§e\"Are you still.... Yourself?\"",
                "§e\"The code remembers. The flesh forgets.\"",
                "§e\"The stars are silent, but the data screams.\"",
                "§e\"Identity is the only thing the void cannot digitize.\""
        };

        int footerIndex = (seed / headers.length) % flavorText.length;
        tooltip.add(Component.empty());
        tooltip.add(Component.literal(flavorText[footerIndex]));

        String hexId = Integer.toHexString(seed).toUpperCase();
        tooltip.add(Component.literal("§8ID: " + hexId + " // VER: " + (seed % 9)));
    }
}
