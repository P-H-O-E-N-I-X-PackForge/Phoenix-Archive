package net.phoenixvine.phoenix_archive.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CategoryRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record CategoryMeta(String id, String description, int weight) {}

    private static final Map<String, CategoryMeta> META = new HashMap<>();

    static {
        register("GENERAL", "Standard data packets.", 100);
        register("RESEARCH", "Scientific observations.", 10);
    }

    public static void register(String id, String desc, int weight) {
        if (id == null || id.isEmpty()) return;
        String upperId = id.toUpperCase();
        META.put(upperId, new CategoryMeta(upperId, desc, weight));
    }

    // FIX #11: Allow removing a category from the registry
    public static void unregister(String id) {
        if (id == null) return;
        META.remove(id.toUpperCase());
    }

    public static Set<String> getRegisteredIds() {
        return META.keySet();
    }

    public static int getWeight(String id) {
        return META.containsKey(id.toUpperCase()) ? META.get(id.toUpperCase()).weight() : 500;
    }

    public static String getDescription(String id) {
        return META.containsKey(id.toUpperCase()) ? META.get(id.toUpperCase()).description() : "";
    }

    public static void loadFromDisk() {
        File dir = new File("config/phoenix_archive/categories");
        if (!dir.exists()) dir.mkdirs();

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                CategoryMeta meta = GSON.fromJson(reader, CategoryMeta.class);
                if (meta != null && meta.id() != null) {
                    register(meta.id(), meta.description(), meta.weight());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}