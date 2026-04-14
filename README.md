# Phoenix Archive
## A Quest and Lore System for Minecraft

Phoenix Archive is a Minecraft mod that adds a flexible and powerful quest and lore system, designed to be easily integrated with other mods, especially GregTech.

<hr>

## Features
- **Custom Lore Entries:** Create custom lore entries with titles, categories, content, and icons.
- **Quest Integration:** Link lore entries to FTB Quests, requiring players to complete quests to unlock lore.
- **Custom Conditions:** Define custom conditions for unlocking lore, such as reaching a specific dimension, biome, or crafting a specific machine.
- **In-Game Editor:** A built-in editor allows you to create and edit lore entries directly in the game.
- **Client-Side Unlock Logic:** All unlock logic is handled on the client, ensuring a smooth and responsive experience.

<hr>

## How to Use
1.  **Create Lore Entries:** Use the in-game editor (`/phoenix_archive edit`) to create new lore entries.
2.  **Define Conditions:** Set the conditions for unlocking each entry, such as a quest ID, dimension, biome, or machine.
3.  **Save and Reload:** Save your changes and reload the game to see your new lore entries in the Phoenix Archive.

<hr>

## For Developers
This mod is built on the GregTech Modern addon template and can be easily extended and customized. The code is designed to be modular and easy to understand, with a clear separation between server-side and client-side logic.

### Key Classes
-   `LoreDataLoader`: Loads all lore entries from JSON files.
-   `TriggerRegistry`: Fires events when custom conditions are met.
-   `ArchiveScreen`: The main GUI for displaying lore entries.
-   `QuestHelper`: Provides helper methods for interacting with the FTB Quests API.

<hr>

This template comes packaged with [Spotless](https://github.com/diffplug/spotless)!

### 1. What is Spotless?
- Spotless keeps your code neatly formatted. It's essentially a grammar check for your code!
### 2. Can I choose not to use Spotless?
- Yes! Spotless is completely optional and will not affect your project by default
### 3. How do I run Spotless?
- You can run Spotless anytime by:
  - Running the `spotlessApply` task from the Gradle tab in IntelliJ
  - Installing the [Spotless Gradle plugin for IntelliJ](https://plugins.jetbrains.com/plugin/18321-spotless-gradle)
  - Typing in `gradlew.bat :spotlessApply` if you're on Windows
  - Typing in `bash gradlew :spotlessApply` if you're on Linux
### 4. So how do I check if Spotless has been applied to my code?
- Running `spotlessApply` will format all files for you automatically! If you want GitHub to check each commit for if Spotless has been run, you can add [this](https://github.com/Frontiers-PackForge/CosmicCore/blob/main-1.20.1-forge/.github/workflows/spotless.yml) and [this](https://github.com/Frontiers-PackForge/CosmicCore/blob/main-1.20.1-forge/.github/actions/build_setup/action.yml) to your project
