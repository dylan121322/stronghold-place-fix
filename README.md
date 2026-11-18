# Stronghold Place Fix / Universal Place-Y Fix (Forge 1.20.1)

Server-side Forge mod that makes

```
/place structure <any structure> <x> <y> <z>
```

generate the structure around the requested position, in particular at the
requested **Y**.  Natural world generation is not affected.

**Version 1.1.0** — generalized from 1.0.0 (which only handled strongholds).
Tested on Minecraft **1.20.1** / Forge **47.1.39** with JDK 17.

---

## 1. Root cause (verified against the exact server jar)

Decompiling `server-1.20.1-20230612.114412-srg.jar`:

* `PlaceCommand#placeStructure` already passes the requested chunk
  (`new ChunkPos(pos)`) into `Structure#generate(...)`. **X/Z are honoured** for
  every structure (the old "ring placement moves the chunk" theory is not how
  `/place` works in 1.20.1).
* Y is never passed to generation.  Instead:
  * `StrongholdStructure` and `MineshaftStructure` bury themselves with
    `StructurePiecesBuilder#moveBelowSeaLevel(seaLevel, minY, random, 10)`,
    which moves the structure to a random deep Y.
  * Other structures use terrain-based Y (`getLowestY`, `getSurfaceY`, custom
    logic), so the requested Y is ignored too.
* Therefore the requested Y must be applied **after** generation, on the
  returned `StructureStart`.

## 2. Fix (1.1.0)

Three Mixins; no changed vanilla files on disk:

| Mixin | Target | Purpose |
|-------|--------|---------|
| `PlaceCommandMixin` | `PlaceCommand#placeStructure` (`m_214587_`) | Stores the requested `BlockPos` in a `ThreadLocal` for **any** structure. A `@Redirect` on the `Structure#generate` call applies the vertical shift to the returned `StructureStart`; this also catches modded structures that override generation (e.g. Twilight Forest's `AuroraPalaceStructure`). Clears the target on return and on the chunk-load validation failure path. |
| `StructurePiecesBuilderMixin` | `StructurePiecesBuilder#moveBelowSeaLevel` (`m_226965_`) | Generalised (no `instanceof`): while a place target is active, structures that use this method (stronghold, mineshaft) get their top aligned to the requested Y instead of a random depth. |
| `StructureStartAccessor` | `StructureStart` | Clears the cached bounding box after pieces are moved. |

Universal shift (`PlacementShifter`):

* Collects every piece of the `StructureStart`, including piece objects nested
  inside other pieces (e.g. ocean-monument rooms) via reflection.
* Moves every piece with `StructurePiece#move(0, delta, 0)`.  `PoolElementStructurePiece`
  and `TemplateStructurePiece` override `move` and also update their template
  position, so jigsaw structures move correctly.
* Clamps the result to the world build height.

### Anchor policy

The requested Y is placed on the **nearest edge** of the generated structure:

* requested Y above the natural structure → align the **top** to requested Y
  (buried structures: stronghold, mineshaft, ancient city, deep mod structures);
* requested Y below the natural structure → align the **bottom** to requested Y
  (surface structures: village, desert pyramid, …);
* the structure already spans requested Y → leave it unchanged (avoids pointless
  movement; e.g. ocean monument naturally spans 39..61 and Goety crypt 12..84).

The `moveBelowSeaLevel` hook always anchors the **top** (the historical behaviour
for strongholds/mineshafts).  Both rules are documented here because "top" is
not meaningful for surface structures: burying a village by anchoring its top
would be worse than anchoring its base.

## 3. Build

Requirements: **JDK 17** (e.g. `/opt/homebrew/opt/openjdk@17`) and network access
to MinecraftForge maven.

```bash
cd stronghold-place-fix
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew build
```

Output: `build/libs/strongholdplacefix-1.1.0.jar`

SHA-256: `587baf6297c9a21d6d4a27d198334a2a3f4a206c419af71050a02ac258638ff3`

`build.gradle` declares `MixinConfigs: strongholdplacefix.mixins.json` and
`compileOnly org.spongepowered:mixin:0.8.5`; the Mixin runtime is provided by
Forge.  Mixin annotations use SRG member names (no refmap, production SRG
runtime).

## 4. Install

Copy the jar into the server's `mods/` directory and restart.  Server-side only
(`displayTest = IGNORE_ALL_VERSION`); clients do not need it.

## 5. Test results (1.1.0, dedicated test instance, normal world seed 778)

All placements at **requested Y = 60** unless noted.  Block counts are blocks
changed inside the structure's bounding box (region-file diff before/after
placement); the requested-chunk column is the chunk containing the requested
x/z.

| Structure | Requested pos | Final structure Y (bbox) | Requested chunk (blocks / y) | Whole structure (blocks) | Notes |
|---|---|---|---|---|---|
| `minecraft:stronghold` | -193, 60, -97 | 28..60 | (-13,-7): **903** / 48..60 | **24101**, 42 chunks | regression, top-anchored |
| `minecraft:mineshaft` | 3384, 60, 3384 | 36..60 | (211,211): **67** / 50..60 | **1276**, 24 chunks | top-anchored via `moveBelowSeaLevel` |
| `minecraft:village_plains` | -193, 60, 703 | 60..82 | (-13,43): **126** / 67..73 | **3743**, 34 chunks | bottom-anchored (surface) |
| `minecraft:ancient_city` | -193, 60, 1103 | 30..60 | (-13,68): **1923** / 30..60 | **46787**, 133 chunks | top-anchored |
| `minecraft:desert_pyramid` | -1128, 60, 7192 | 60..74 | (-71,449): 未测 | 未测（1.1.0） | placement succeeded; block count not collected (probe already placed). 1.0.0 measurement: 1430 blocks y60..74, requested chunk 653 y62..74 |
| `minecraft:monument` (ocean monument) | 1912, 60, 2904 | 39..61 | (119,181): 未测 | 未测（1.1.0） | already spans 60, no movement by design. 1.0.0 measurement: 45499 blocks y39..61, requested chunk 3136 y39..60 |
| `twilightforest:aurora_palace` | 3000, 60, 2000 | 60..192 | (187,125): **4433** / 60..192 | **45726**, 10 chunks | mod structure that overrides generation; covered by the redirect |
| `goety:crypt` | 2000, 60, 2000 | 12..84 | (125,125): **2084** / 41..84 | **38115**, 48 chunks | mod structure; already spans 60, unchanged by design |

Additional 1.1.0 checks:

* `twilightforest:aurora_palace` and `goety:crypt` prove the generic path works
  for modded `Structure` subclasses (including custom generation overrides).
* Natural stronghold generation is untouched: the `ThreadLocal` target is only
  set by `/place structure`, and every natural-generation hook is gated on it.
  (1.0.0 regression on the same world: natural stronghold at -1600/1568 had
  7863 blocks at y -29..-1, i.e. random deep, with no forcing log.  The 1.1.0
  changes only widen the same `PlaceCommand`-scoped mechanism.)

## 6. Known limitations / notes

* **Desert pyramid and ocean monument block counts are not re-measured with
  1.1.0** because the probe placed them before the before-snapshot could be
  taken.  Their Y placement *is* confirmed by the 1.1.0 bounding-box logs
  (desert 60..74, monument 39..61); 1.0.0 counts are given for reference.
* Structures that naturally already span the requested Y are intentionally
  **not moved** (ocean monument, Goety crypt).  If an exact top/base alignment
  is required for those, the anchor policy would need to change.
* `minecraft:ocean_monument` is not the registry id; the vanilla id is
  `minecraft:monument`.
* Requested Y is clamped to the world height; if the structure is taller than
  the available space the clamp wins.
* As with vanilla `/place structure`, all chunks of the structure's bounding box
  must be loaded; otherwise the command reports `That position is not loaded`.
* Custom mod structures that neither use `StructurePiece#move`-aware pieces nor
  keep their pieces in fields reachable by reflection could still be missed.
  Twilight Forest and Goety were verified; no crash was observed.
* The jar uses SRG names in Mixin annotations and no refmap, so it is built for
  the production server (SRG runtime), not a dev workspace using official names.

## 7. Files

```
stronghold-place-fix/
├── build.gradle, gradle.properties, gradlew, gradle/
├── README.md
├── evidence/nbt-scans.txt
└── src/main/
    ├── java/dev/boqing/strongholdplacefix/
    │   ├── StrongholdPlaceFix.java
    │   ├── ForcedPlacement.java
    │   ├── PlacementShifter.java
    │   └── mixin/
    │       ├── PlaceCommandMixin.java
    │       ├── StructurePiecesBuilderMixin.java
    │       └── StructureStartAccessor.java
    └── resources/
        ├── META-INF/mods.toml
        ├── pack.mcmeta
        └── strongholdplacefix.mixins.json
```
