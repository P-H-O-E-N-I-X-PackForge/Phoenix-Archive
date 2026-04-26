package net.phoenixvine.phoenix_archive.proxy;

import net.minecraft.network.chat.Component;

import java.util.List;

public interface IProxy {

    void init();

    void openArchiveScreen();

    void appendPlayerTooltip(List<Component> tooltip);
}
