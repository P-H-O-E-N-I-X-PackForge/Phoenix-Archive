# Lore Entry Markdown Reference

This documents the text formatting available in a lore entry's `content` and `lockedContent`
fields (whether authored via the in-game editor, a `config/phoenix_archive/lore/*.json` file, a
datapack `assets/<mod>/phoenix_lore/*.json` entry, or a Java-registered entry via
`ArchiveRegisterEntriesEvent`). It's parsed by `ArchiveMarkdownParser` and drawn by
`ArchiveRichTextRenderer` (`src/main/java/net/phoenixvine/phoenix_archive/client/rich/`).

This dialect is forked from `phoenix_wiki`'s own markdown (see that mod's docs for the shared
baseline) with two Archive-only extensions: **`:::if`** (living/conditional content) and
**`:::hotspots`** (illustrated pages). Both are called out below.

Legacy Minecraft formatting (`&c`, `&#RRGGBB`) is converted to `§`-codes on the *entry* before this
parser ever sees it (`LoreEntry.processHex`), so both `&`-codes and everything in this doc work
together in the same text.

---

## Contents

1. [Block-level syntax](#block-level-syntax)
2. [Container blocks](#container-blocks)
3. [Archive extension: if (living content)](#archive-extension-if-living-content)
4. [Archive extension: hotspots (illustrated pages)](#archive-extension-hotspots-illustrated-pages)
5. [Inline syntax](#inline-syntax)
6. [Condition keys](#condition-keys)
7. [Known limitations](#known-limitations)

---

## Block-level syntax

| Syntax | Result |
|---|---|
| `# Heading` … `###### Heading` | Heading, levels 1–6 (levels 3+ all render the same size). Every heading becomes a **collapsible section** wrapping everything until the next heading of equal-or-higher level. |
| Plain text | A paragraph. Consecutive non-blank lines join into one paragraph (single line breaks don't force a new line — leave a blank line between paragraphs). |
| `- item`, `* item`, `+ item` | Bullet list item. Indent by 2 spaces per nesting level (`  - nested`). |
| `1. item` | Numbered list item (no nesting support). |
| `- [ ] task` / `- [x] task` | Checklist item — must be inside a bullet item. `[x]`/`[X]` = initial checked state; clickable in-game. |
| `---`, `***`, `___` (3+ chars, alone on a line) | Horizontal rule. |
| ` ```lang ` … ` ``` ` | Code block. `lang` enables syntax highlighting for `java`, `js`/`javascript`, `ts`/`typescript`, `kotlin`, `json` (any other/no language: plain monospace, still boxed). |
| `> quoted text` | Blockquote. Consecutive `>` lines join into one quote. |
| GFM-style table (`\| a \| b \|` header + `\|---\|---\|` separator + rows) | Table. |
| `[^note]: definition text` (own line) | Defines footnote `note`, referenced elsewhere as `[^note]`. Definition lines are stripped before the rest of the page is parsed, so put them anywhere. |
| `{scale:1.5}` (alone on its own line) | Scales the rest of the block by that factor. |

---

## Container blocks

General form:

```
:::type Optional Title
content, any of the block syntax above, or an Archive extension below
:::
```

| `type` | Result |
|---|---|
| `warning` / `warn` | Amber callout box, ⚠ icon. |
| `danger` / `error` | Red callout box, ⛔ icon. |
| `tip` / `success` | Highlight-colored callout box, 💡 icon. |
| `note` / `info` | Bright callout box, ℹ icon. |
| anything else | Generic callout box, ● icon, in the terminal accent color. |
| `spoiler` / `details` | Collapsible "▸ Title" section — click the title to expand/collapse. |
| `if` | **Archive extension** — see below. |
| `hotspots` | **Archive extension** — see below. |

A callout's title is whatever follows the type on the opening line (`:::warning Careful!`); if
omitted, it defaults to the capitalized type name.

---

## Archive extension: if (living content)

```
:::if <condition_key>:<condition_value>
This paragraph only shows while the condition holds.
:::
```

Example:

```
:::if dimension:minecraft:the_nether
You've been to the Nether. The following makes more sense now.
:::

:::if chronicles_quest:phoenix_chronicles:main/defeat_warden
Congratulations on the Warden. Here's what really happened...
:::
```

The condition uses the same `key:value` pairs as an entry's own unlock **conditions** (see
[Condition keys](#condition-keys) below) — but unlike those, an `:::if` block is checked **live, on
the client, every render**, not once at unlock time and not persisted. That makes it useful for two
different things at once:

- **Progressive redaction** — gate an individual paragraph instead of the whole entry. An entry can
  be fully unlocked and still show/hide sections as the reader's state changes (which biome they're
  standing in right now, whether a quest is done *right now*, etc.).
- **Living content** — because it's re-checked every frame off already-synced client state
  (`CLIENT_LORE_CACHE`, the same signals `TriggerRegistry` maintains), a page can visibly change while
  you're looking at it if the underlying condition flips (e.g. change dimension while the Archive is
  open).

`:::if` blocks can nest inside headings, callouts, `:::spoiler`/`:::details`, and each other.

If the condition's value is empty/missing, the block never shows. There's no "else" — write two
`:::if` blocks with complementary conditions if you need that.

---

## Archive extension: hotspots (illustrated pages)

```
:::hotspots <image_id>,<width>,<height>
@<x>,<y> Tooltip text for this marker
@<x>,<y> Another marker's tooltip
:::
```

Example:

```
:::hotspots phoenix_archive:textures/gui/ruins_map.png,180,120
@20,15 The old watchtower — abandoned after the Sundering
@140,90 Entrance to the lower vault
:::
```

Draws `image_id` (a plain texture, `namespace:path`, sized `width`×`height`) as a block, then a
small marker at each `@x,y` pixel offset **into the image** (top-left origin). Hovering a marker
shows its tooltip text.

This is **opt-in** — nothing renders this way unless you deliberately write the syntax. It's meant
for "interactive parchment page" style entries (a map, a diagram, a scene) rather than every entry.

Lines inside the block that don't start with `@` are ignored, so you can leave yourself comments.
A malformed header (bad image id, non-numeric width/height) drops the whole block rather than
breaking the page.

---

## Inline syntax

| Syntax | Result |
|---|---|
| `**bold**` | **Bold** |
| `*italic*` | *Italic* |
| `~~strike~~` | ~~Strikethrough~~ |
| `==highlight==` | Highlighted background |
| `` `code` `` | Inline code, visually distinct |
| `<kbd>Key</kbd>` | Rendered as a keycap |
| `{#RRGGBB}...{reset}` | Sets text color to `#RRGGBB` until the next `{reset}` (or end of block) |
| `{scale:1.4}...{reset}` | Scales subsequent inline text by that factor until reset |
| `[label](https://url)` or `[label](wiki:page)` | A styled, clickable link — `https://` opens the system browser, `wiki:` opens the page in PhoenixWiki |
| `[label](tip:tooltip text)` | Text with a hover tooltip |
| `[^note]` | Footnote reference — hover shows the matching `[^note]: ...` definition, if one exists elsewhere in the entry |
| `[img:namespace:path.png,w,h]` or `[label](img:namespace:path.png,w,h)` | Inline image, defaults to 48×48 if size omitted |
| `[item:namespace:item_id]` or `[item:namespace:item_id\|tooltip text]` | An inline item icon, optionally with a custom hover tooltip (defaults to none) |
| `"quoted text"` | Automatically converted to smart “curly” quotes (skipped inside `` `code` ``) |

---

## Condition keys

These are the `key:value` pairs usable both in an entry's own **Conditions** (set via `EDIT_LOGIC`
in the editor — gates the whole entry, server-authoritative, persisted) and in an `:::if` block
(client-only, live, per-paragraph):

| Key | Value | Meaning |
|---|---|---|
| `dimension` | e.g. `minecraft:the_nether` | Player has been in this dimension |
| `biome` | e.g. `minecraft:crimson_forest` | Player has been in this biome |
| `machine` | a block id | Player has placed this block |
| `item` | an item id | Player has held this item |
| `wearing` | an item id | Player has worn this item |
| `suit_event` | mod-defined | A custom signal fired via `TriggerRegistry.fire`/`ArchiveAPI.fireTrigger` |
| `chronicles_quest` | a Chronicles quest id (`namespace:path`) | That Phoenix Chronicles quest is completed (requires Chronicles installed) |
| *(anything else)* | anything | A custom signal — fire it yourself with `TriggerRegistry.fire(player, key, value)` |

The entry's own `questId` field (a legacy FTB Quests numeric id, set via the quest picker's `[FTB]`
entries) is a **separate** mechanism, not a `key:value` condition — it can't be referenced from
`:::if`.

---

## Known limitations

Everything below now works in-game — links, collapsible sections, checklists, code-copy, and
standalone `{scale:N}` are all wired up in `ArchiveScreen`/`ArchiveRichTextRenderer`:

- **Links are clickable.** `[label](https://...)` opens the system browser; `[label](wiki:...)`
  opens the matching page in PhoenixWiki.
- **`:::spoiler`/`:::details` blocks expand and collapse in-game** — click the `▸`/`▾ Title` line to
  toggle.
- **Checklists are interactive** — click a `[x]`/`[ ]` item to toggle it; `[x]`/`[X]` in the source
  just sets the initial state.
- **Code blocks' copy button copies** the block's contents to the clipboard.
- **Hover tooltips work**: `[label](tip:...)`, `[^footnote]` references, and `:::hotspots` markers
  all show their tooltip on hover.
- **Standalone `{scale:N}` on its own line works**, same as the inline form (`{scale:N}...{reset}`).

There are no known gaps in the format at this time.
