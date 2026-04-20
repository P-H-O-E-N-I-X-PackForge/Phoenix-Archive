package net.phoenixvine.phoenix_archive.common;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.phoenixvine.phoenix_archive.client.ArchiveScreen;
import net.phoenixvine.phoenix_archive.config.ArchiveConfigs;

import javax.annotation.Nullable;
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
            // This safely opens the screen on the client
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::openArchiveGui);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    private void openArchiveGui() {
        Minecraft.getInstance().setScreen(new ArchiveScreen());
    }
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // We use the stack's hash to create a stable "random" seed
        int seed = Math.abs(stack.hashCode());

        // Header - Cyberpunk Archive Style
        String[] headers = {
                "§8// §aPHOENIX_ARCHIVE_INTERFACE_v4.2",
                "§2[ §aSYSTEM_READY §2] §7Uplink Stable",
                "§b⚡ §3ROOT_UPLINK_ACTIVE",
                "§f$ §7sudo get_archives --force"
        };
        tooltip.add(Component.literal(headers[seed % headers.length]));

        // Identity Section
        // On the server, we don't know the player, so "GUEST" is a perfect fallback.
        String user = "GUEST";
        if (level != null && level.isClientSide()) {
            user = Minecraft.getInstance().player != null ?
                    Minecraft.getInstance().player.getScoreboardName() : "USER";
        }

        tooltip.add(Component.literal("§8[ §7AUTH_AS: §b" + user.toUpperCase() + " §8]"));

        // Main Action Lore
        String tabletName = ArchiveConfigs.INSTANCE.general.loreTabletTooltipName;
        tooltip.add(Component.literal("§6> §7Click to access §f" + tabletName));

        // Deep Lore - Cryptic & Cybernetic
        String[] flavorText = {
                "§e\"Reclaim what is lost, remember who you are.\"",
                "§e\"Was our power worth the loss of the world?\"",
                "§e\"Are you still.... Yourself?\"",
                "§e\"The code remembers. The flesh forgets.\"",
                "§e\"The stars are silent, but the data screams.\"",
                "§e\"Identity is the only thing the void cannot digitize.\""
        };

        // Use a different math operation so the header and footer aren't linked
        int footerIndex = (seed / headers.length) % flavorText.length;
        tooltip.add(Component.empty()); // Spacer line
        tooltip.add(Component.literal(flavorText[footerIndex]));

        // System Metadata
        String hexId = Integer.toHexString(seed).toUpperCase();
        tooltip.add(Component.literal("§8ID: " + hexId + " // VER: " + (seed % 9)));
    }
}