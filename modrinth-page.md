# Comprendium - Modrinth page copy

Source of truth for https://modrinth.com/mod/comprendium.

## description

An interactive villager trade atlas with 3D previews, workstation recipes and optional server synchronization.

## body

# Comprendium

A reference atlas you can open inside Minecraft. The current guide covers villager trades and the Wandering Trader, with item previews and workstation crafting recipes.

Press **J** to open it. Drag to pan, scroll to zoom and press Escape to close. The key can be changed in Controls.

## Features

- Villager professions arranged in rows, with trades from Novice through Master.
- A separate Wandering Trader panel for buying, common and uncommon trade pools.
- 3D villager, workstation and item previews, with item tooltips.
- Workstation crafting recipes from your installed game and client mod resources.
- Compact palettes for matching trade variants, while retaining the individual offers.
- Optional server synchronization for active profession and trade definitions.

## Installation

Requires Minecraft 26.3, Fabric Loader and Fabric API. Install Comprendium on your client. Modern GUI is bundled, so a separate download is not needed.

In singleplayer, the integrated server supplies the active trade definitions. On a dedicated Fabric server, you can optionally install the same jar and Fabric API to share the server's trade catalog. Players without Comprendium can still join.

Without a server installation, the client displays the vanilla reference from its own Minecraft game files.

## Notes

- The atlas shows possible trade definitions, not the hidden offers of a particular villager. Random prices, enchantments and other conditions can vary.
- Server-only datapacks and plugin trades cannot be reconstructed without compatible synchronization. Mods that change trades through arbitrary code may not provide readable definitions.
- Crafting recipes come from locally installed resources. Tag ingredients cycle through valid local examples.
- Trading is the available guide. Other guide tabs are reserved for future content.

Comprendium is MIT licensed. The bundled [Modern GUI](https://github.com/Fayberr/modern-gui) library is LGPL-3.0-or-later.
