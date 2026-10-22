package net.phoenix_archives.phoenix_archive.client.rich;

import net.phoenix_archives.phoenix_archive.api.ConditionNode;
import net.phoenixvine.wiki.client.rich.RichBlock;
import net.phoenixvine.wiki.client.rich.RichSpan;

import java.util.List;

public record ArchiveConditionalSection(ConditionNode condition, List<RichBlock> thenChildren,
                                        List<RichBlock> elseChildren)
        implements RichBlock {

    @Override
    public List<RichSpan> spans() {
        return List.of();
    }
}
