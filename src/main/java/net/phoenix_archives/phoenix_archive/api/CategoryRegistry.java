package net.phoenix_archives.phoenix_archive.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.util.*;

public class CategoryRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record CategoryMeta(String id, String description, int weight, String parentId) {

        public CategoryMeta(String id, String description, int weight) {
            this(id, description, weight, null);
        }
    }

    private static final Map<String, CategoryMeta> META = new HashMap<>();

    static {
        register("GENERAL", "Standard data packets.", 100, null);
        register("RESEARCH", "Scientific observations.", 10, null);
    }

    public static void register(String id, String desc, int weight, String parentId) {
        if (id == null || id.isEmpty()) return;
        String upperId = id.toUpperCase();
        String upperParentId = (parentId == null || parentId.isBlank()) ? null : parentId.toUpperCase();
        META.put(upperId, new CategoryMeta(upperId, desc, weight, upperParentId));
    }

    public static void register(String id, String desc, int weight) {
        register(id, desc, weight, null);
    }

    public static void unregister(String id) {
        if (id == null) return;
        META.remove(id.toUpperCase());
    }

    public static Set<String> getRegisteredIds() {
        return META.keySet();
    }

    public static int getWeight(String id) {
        CategoryMeta m = META.get(upper(id));
        return m != null ? m.weight() : 500;
    }

    public static String getDescription(String id) {
        CategoryMeta m = META.get(upper(id));
        return m != null ? m.description() : "";
    }

    public static String getParentId(String id) {
        CategoryMeta m = META.get(upper(id));
        return m != null ? m.parentId() : null;
    }

    public static int getDepth(String id) {
        int depth = 0;
        String current = upper(id);
        while (true) {
            CategoryMeta m = META.get(current);
            if (m == null || m.parentId() == null) break;
            current = m.parentId();
            depth++;
            if (depth > 20) break;
        }
        return depth;
    }

    public static List<String> getChildren(String parentId) {
        String upperParent = parentId == null ? null : parentId.toUpperCase();
        return META.values().stream()
                .filter(m -> Objects.equals(m.parentId(), upperParent))
                .sorted(Comparator.comparingInt(CategoryMeta::weight).thenComparing(CategoryMeta::id))
                .map(CategoryMeta::id)
                .toList();
    }

    public static void loadFromDisk() {
        File dir = new File("config/phoenix_archive/categories");
        if (!dir.exists()) dir.mkdirs();

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {

                CategoryDefinition def = GSON.fromJson(reader, CategoryDefinition.class);
                if (def != null && def.id() != null) {
                    register(def.id(), def.description(), def.weight(), def.parentId());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static String upper(String id) {
        return id == null ? null : id.toUpperCase();
    }
}
