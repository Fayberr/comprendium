# Comprendium

A visual, interactive reference atlas for **Minecraft 26.3** on **Fabric**. The first chart is villager trades, presented in balanced rows you can freely pan and zoom. The Wandering Trader gets a separate multi-column panel showing buying, common, and uncommon pools. More charts (brewing, mining, mechanics) are intended to be added later.

## Goals

- Native Minecraft GUI, true 3D villager previews, workstation and trade item renders: not a static PNG.
- Professions arranged across multiple rows, with Novice through Master trades and a dedicated wide Wandering Trader section.\n- GUI-only 3D entities are projected into viewport coordinates so they remain attached to their cards while panning and zooming.\n- Enchantment glint previews for output items enchanted by trade modifiers. The glow represents a potentially enchanted result, not a particular randomized enchantment.\n- Additional costs and conditional/modifier markers.
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
