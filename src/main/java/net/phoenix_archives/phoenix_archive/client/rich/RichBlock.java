package net.phoenix_archives.phoenix_archive.client.rich;

import net.minecraft.resources.ResourceLocation;
import net.phoenix_archives.phoenix_archive.api.ConditionNode;

import java.util.List;

public sealed interface RichBlock {

    List<RichSpan> spans();

    record Heading(int level, List<RichSpan> spans) implements RichBlock {}

    record Paragraph(List<RichSpan> spans) implements RichBlock {}

    record ListItem(String marker, int indent, List<RichSpan> spans) implements RichBlock {}

    record Checklist(String checkKey, boolean checkedDefault, int indent, List<RichSpan> spans) implements RichBlock {}

    record Quote(List<RichSpan> spans) implements RichBlock {}

    record CodeBlock(String lang, String code) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Callout(String type, String title, List<RichBlock> children) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Details(String expandKey, String title, List<RichBlock> children) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record CollapsibleSection(int level, List<RichSpan> headingSpans, String collapseKey,
                              List<RichBlock> children)
            implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return headingSpans;
        }
    }

    record ScaleDirective(float multiplier) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Table(List<List<RichSpan>> header, List<List<List<RichSpan>>> rows) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Rule() implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Blank() implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record ConditionalSection(ConditionNode condition, List<RichBlock> thenChildren, List<RichBlock> elseChildren)
            implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record HotspotImage(ResourceLocation image, int width, int height, List<Hotspot> hotspots)
            implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Hotspot(int x, int y, String tooltip) {}
}
