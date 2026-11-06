package com.japaneselearning.vocabulary.dto.content;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Schema v1. All containers hold inline text only; blocks cannot contain blocks.
 */
public record LessonContentDocument(List<Block> blocks) {
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Heading.class, name = "heading"),
            @JsonSubTypes.Type(value = Paragraph.class, name = "paragraph"),
            @JsonSubTypes.Type(value = ListBlock.class, name = "list"),
            @JsonSubTypes.Type(value = Table.class, name = "table"),
            @JsonSubTypes.Type(value = Example.class, name = "example"),
            @JsonSubTypes.Type(value = Callout.class, name = "callout"),
            @JsonSubTypes.Type(value = Quote.class, name = "quote"),
            @JsonSubTypes.Type(value = Divider.class, name = "divider")
    })
    @Schema(name = "LessonContentBlock")
    public sealed interface Block permits Heading, Paragraph, ListBlock, Table, Example, Callout, Quote, Divider {
        String id();
    }

    @Schema(name = "LessonContentInline")
    public record Inline(
            String text,
            List<String> marks
    ) {
    }

    @Schema(name = "LessonContentHeading")
    public record Heading(
            String id,
            Integer level,
            List<Inline> inlines
    ) implements Block {
    }

    @Schema(name = "LessonContentParagraph")
    public record Paragraph(
            String id,
            List<Inline> inlines
    ) implements Block {
    }

    @Schema(name = "LessonContentList")
    public record ListBlock(
            String id,
            Boolean ordered,
            List<List<Inline>> items
    ) implements Block {
    }

    @Schema(name = "LessonContentTable")
    public record Table(
            String id,
            List<List<Inline>>
            headers,
            List<List<List<Inline>>> rows
    ) implements Block {
    }

    @Schema(name = "LessonContentTranslations")
    public record Translations(
            String en,
            String vi
    ) {
    }

    @Schema(name = "LessonContentExample")
    public record Example(
            String id,
            String japanese,
            String reading,
            Translations translations
    ) implements Block {
    }

    @Schema(name = "LessonContentCallout")
    public record Callout(
            String id,
            String variant,
            List<Inline> inlines
    ) implements Block {
    }

    @Schema(name = "LessonContentQuote")
    public record Quote(
            String id,
            List<Inline> inlines
    ) implements Block {
    }

    @Schema(name = "LessonContentDivider")
    public record Divider(
            String id
    ) implements Block {
    }
}
