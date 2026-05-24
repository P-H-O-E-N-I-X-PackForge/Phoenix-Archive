package net.phoenixvine.phoenix_archive.api;

/**
 * Serialised representation of a category.
 * {@code parentId} is null for top-level categories.
 * Child IDs must start with their parent's ID followed by a dot,
 * e.g. parent "RESEARCH" → child "RESEARCH.BIOLOGY".
 */
public record CategoryDefinition(
                                 String id,
                                 String description,
                                 int weight,
                                 String parentId   // null → top-level category
) {

    /** Convenience constructor for top-level categories (no parent). */
    public CategoryDefinition(String id, String description, int weight) {
        this(id, description, weight, null);
    }
}
