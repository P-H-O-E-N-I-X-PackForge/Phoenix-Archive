package net.phoenix_archives.phoenix_archive.client.rich;

import net.phoenix_archives.phoenix_archive.api.ConditionNode;
import net.phoenixvine.wiki.client.rich.RichBlock;
import net.phoenixvine.wiki.client.rich.RichSpan;

import java.util.List;

/**
 * Archive's own {@code :::if condition} / {@code :::else} / {@code :::} block, tied to Archive's
 * own {@link ConditionNode}/condition system -- this stays local rather than moving into
 * phoenix_wiki, which has no condition system of its own and shouldn't need one. Resolved (thrown
 * away in favor of whichever branch applies) by {@code ArchiveScreen#resolveConditionals} before
 * render -- the renderer never sees this type.
 */
public record ArchiveConditionalSection(ConditionNode condition, List<RichBlock> thenChildren,
                                        List<RichBlock> elseChildren)
        implements RichBlock {

    @Override
    public List<RichSpan> spans() {
        return List.of();
    }
}
