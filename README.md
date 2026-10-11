# Comprendium

A visual, interactive reference atlas for **Minecraft 26.3** on **Fabric**. The first chart is villager trades, presented in balanced rows you can freely pan and zoom. The Wandering Trader gets a separate multi-column panel showing buying, common, and uncommon pools. More charts (brewing, mining, mechanics) are intended to be added later.

## Goals

- Native Minecraft GUI, true 3D villager previews, workstation and trade item renders: not a static PNG.
- Professions arranged across multiple rows, with Novice through Master trades and a dedicated wide Wandering Trader section.\n- GUI-only 3D entities are projected into viewport coordinates so they remain attached to their cards while panning and zooming.\n- Enchantment glint previews for output items enchanted by trade modifiers.
- Compact item palettes for equivalent color variants, banners, dyes, boats and other matching trade families, with no trade data discarded.\n- A larger workstation model above a 3×3 crafting recipe loaded from the installed Minecraft and client mod recipe resources. The recipe grid shows a real example ingredient for tag-based inputs. The glow represents a potentially enchanted result, not a particular randomized enchantment.\n- Additional costs and conditional/modifier markers.
- Dynamically discover profession and trade-set definitions on the active server, including singleplayer integrated servers, server datapacks, and compatible Fabric mods.
- A **universal** JAR: optional installation on both the client and Fabric server shares the server's current catalog; without a server installation, the client loads the vanilla reference directly from the installed Minecraft 26.3 game JAR; unknown server-side custom trade definitions cannot be reconstructed.
- Trade definitions are possible offers, **not** hidden offers of specific villagers. Randomized prices, probabilities and item components may require extra inspection to interpret correctly.
- Lightweight, versioned v2 server-to-client snapshot format, validated on receipt.

## Install / controls

1. Install Minecraft **26.3**, Fabric Loader, Fabric API, and Comprendium on your client.
2. Open a world; press **J** to open the atlas (can be rebound in Controls).
3. Drag to pan, scroll to zoom around the pointer, ESC to close.
4. In singleplayer, the integrated server supplies active definitions automatically.
5. On dedicated Fabric servers, optionally install the exact same JAR + Fabric API to synchronize authoritative trade definitions to connected Comprendium clients. Players without the mod do not receive custom packets.

## Development

Java 25, Gradle 9.7.1+, Fabric Loom 1.18, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3.

```sh
gradle build
```

CI compiles and uploads JARs. Java classes in `src/main` must not reference client-only Minecraft types; client rendering lives in `src/client`.

### Data limitations

- Servers without Comprendium do not send their server-side trade registries. On these servers, Comprendium renders a vanilla chart extracted from the local game JAR, which cannot reflect custom server-only datapacks or plugins.
- Mods changing trades using arbitrary Java logic may not expose enumerable static definitions.
- The exported data is **reference metadata**, not live `MerchantOffer` instances. Random ranges and enchantment-related conditions cannot always be displayed as a single exact item.
- A dedicated server cannot directly render or inspect client-only entity models; that is handled client-side.

## License

MIT.

### Workstation recipes

The GUI discovers Java Edition shaped crafting recipes in the installed Minecraft and Fabric mod resource packs (`data/<namespace>/recipe/*.json`). Ingredients from item tags display one valid local example, not the only permissible ingredient. This is client-side recipe discovery, not a new network protocol: server-exclusive datapack recipes remain unavailable to clients unless they are also present in a locally installed pack or mod. No recipe image or ingredient list is hardcoded.

### Cartographer variants

Biome-dependent banner trades and village maps are grouped when their costs, modifier flags and trade conditions match. The compact palette retains each individual offer and shows **BIOME-DEPENDENT OPTIONS** to avoid implying every offer is available from a single villager. Maps that cost both emeralds and a compass display both input icons. Different prices or conditions remain separate.

### Rotating crafting ingredients

When a recipe uses an item tag such as `#minecraft:logs` or `#minecraft:planks`, Comprendium cycles valid item variants roughly once each second (oak, birch, dark oak, etc.). Each occurrence of the same tag uses the same variant on a given frame. The original crafting recipe is not changed. Only item-tag alternatives present in local game/mod data can be displayed; the mod cannot infer server-only datapacks without synchronization.

## UI shell (0.3.0)

The top bar is now the shared guide navigation shell: **Trading** opens the current villager atlas, and **Next guide +** opens an empty future-guide canvas. Switching tabs retains the atlas pan and zoom. The header no longer displays profession counts or zoom, which are in the bottom status bar instead. Workstation previews respond to hover with the real Minecraft item tooltip. The atlas uses rounded cards, rows, and crafting slots, with the Modern GUI 26.3 font/rendering primitives.

### Building with Modern GUI

The universal Comprendium JAR **bundles** [Modern GUI](https://github.com/Fayberr/modern-gui) version `1.0.14+26.3` (pinned source commit `785e5c0f7b5d95c334955af1372674a588d9a362`) as a nested client-only Fabric mod, so users do not need to install another JAR. CI builds that version and publishes it into Maven Local before building Comprendium.

For local builds, first checkout the pinned Modern GUI `fabric/26.3` revision and run `gradle publishToMavenLocal` with Java 25, then build Comprendium normally. The bundled Modern GUI library is LGPL-3.0-or-later; its source and license are available upstream and in its nested JAR. Comprendium's own source remains MIT licensed.

Modern GUI is client-only. Comprendium's networking and server code do not reference GUI APIs; the universal JAR still runs on a dedicated server.
