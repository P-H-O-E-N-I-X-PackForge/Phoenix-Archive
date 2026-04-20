package net.phoenixvine.phoenix_archive.proxy;

import net.minecraft.client.Minecraft;
import net.phoenixvine.phoenix_archive.client.ArchiveScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientProxy implements IProxy {
    @Override
    public void init() {
        // No-op for now, but we can add client-specific setup here later
    }

    public void openArchiveScreen() {
        Minecraft.getInstance().setScreen(new ArchiveScreen());
    }
}
