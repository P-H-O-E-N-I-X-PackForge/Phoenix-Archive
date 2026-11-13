package net.phoenix_archives.phoenix_archive.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;

public final class ArchiveText {

    private ArchiveText() {}

    public static String str(String key, Object... args) {
        return I18n.get(key, args);
    }

    public static String label(String key) {
        return Language.getInstance().getOrDefault(key);
    }
}
