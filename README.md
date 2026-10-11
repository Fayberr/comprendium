# Comprendium

A visual, interactive reference atlas for **Minecraft 26.3** on **Fabric**. The first chart is villager trades, shown as a single large canvas you can freely pan and zoom. More charts (brewing, mining, mechanics) are intended to be added later.

## Goals

- Native Minecraft GUI, true 3D villager previews, workstation and trade item renders: not a static PNG.
- Profession columns with Novice through Master trades, including additional costs and conditional/modifier markers.
- Dynamically discover profession and trade-set definitions on the active server, including singleplayer integrated servers, server datapacks, and compatible Fabric mods.
- A **universal** JAR: optional installation on both the client and Fabric server shares the server's current catalog; without a server installation the client UI still opens, but unknown server-side trade definitions cannot be reconstructed.
- Trade definitions are possible offers, **not** hidden offers of specific villagers. Randomized prices, probabilities and item components may require extra inspection to interpret correctly.
- Lightweight, versioned server-to-client snapshot format, validated on receipt.

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

- Unmodified servers do not send their server-side registries just because the client has this mod. They require an installed Comprendium for authoritative synchronization.
- Mods changing trades using arbitrary Java logic may not expose enumerable static definitions.
- The exported data is **reference metadata**, not live `MerchantOffer` instances. Random ranges and enchantment-related conditions cannot always be displayed as a single exact item.
- A dedicated server cannot directly render or inspect client-only entity models; that is handled client-side.

## License

MIT.
