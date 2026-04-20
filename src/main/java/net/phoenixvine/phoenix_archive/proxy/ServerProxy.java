package net.phoenixvine.phoenix_archive.proxy;

public class ServerProxy implements IProxy {
    @Override
    public void init() {
        // Server-specific setup
    }

    @Override
    public void openArchiveScreen() {
        // No-op on the server
    }
}
