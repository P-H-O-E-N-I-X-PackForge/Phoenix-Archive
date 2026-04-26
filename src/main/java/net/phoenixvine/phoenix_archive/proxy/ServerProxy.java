package net.phoenixvine.phoenix_archive.proxy;

import net.minecraft.network.chat.Component;

import java.util.List;

public class ServerProxy implements IProxy {

    @Override
    public void init() {
        // Server-specific setup
    }

    @Override
    public void openArchiveScreen() {
        // No-op on the server
    }

    @Override
    public void appendPlayerTooltip(List<Component> tooltip) {}
}
