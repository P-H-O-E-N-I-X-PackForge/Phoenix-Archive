package net.phoenixvine.phoenix_archive.proxy;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phoenix_archive.client.ArchiveScreen;
import net.phoenixvine.phoenix_archive.client.ClientHelper;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientProxy implements IProxy {

    @Override
    public void init() {
        // No-op for now, but we can add client-specific setup here later
    }

    @Override
    public void appendPlayerTooltip(List<Component> tooltip) {
        ClientHelper.appendPlayerTooltip(tooltip);
    }

    public void openArchiveScreen() {
        Minecraft.getInstance().setScreen(new ArchiveScreen());
    }
}
