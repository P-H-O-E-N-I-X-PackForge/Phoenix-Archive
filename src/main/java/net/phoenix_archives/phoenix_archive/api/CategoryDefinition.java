package net.phoenix_archives.phoenix_archive.api;

public record CategoryDefinition(
                                 String id,
                                 String description,
                                 int weight,
                                 String parentId) {

    public CategoryDefinition(String id, String description, int weight) {
        this(id, description, weight, null);
    }
}
