# TheSift — Phase 1 (Foundation)

A Paper plugin foundation for a Sift-inspired dimension (Minecraft 26.3, Paper, Java 25, Gradle).

**Status: written but never compiled or run.** The authoring session had no network access, so Gradle could not
download the Paper API. Expect compile errors on the first local build. Nothing here has been tested.

## Phase 1 report

**Completed (as source code)**
- Gradle project: `io.papermc.paper:paper-api:26.3.build.+`, Java 25 toolchain, `paper-plugin.yml` with `api-version: '26.3'`.
- Service architecture (`ServiceRegistry`): ordered start, reverse-ordered stop, typed lookup.
- Config snapshot (`SiftConfig`) and commented `config.yml`.
- SQLite database service (single DB thread, async API; schema for players and death-recovery records).
- Async, atomic `state.yml` persistence.
- Datapack installer and Sift world lookup (restart required after first install).
- Tide cycle Flow -> Thrive -> Endure: configurable, persisted, Endure warning.
- Resource-pack delivery: URL, SHA-1, required/optional, per-player status tracking, version bump = re-download.
- Commands: `/sift help | info | enter | exit | resourcepack | tide [set <tide>]`.

**Not implemented (everything else in the spec)**
Custom biomes and terrain (the dimension is a placeholder: vanilla overworld noise, fixed cherry grove biome),
blocks, items, Ichor, Charoite, Siftite, mobs and AI, structures, portal puzzle, rifts, Mini Rift recovery, Monarch,
advancements, weather, sleep rules, sounds, the resource pack itself (no textures/models exist yet), automated tests.
Spec commands `locate`, `give`, `structures`, `weather`, `spawn`, `reload`, `world`, `debug` are deliberately not registered yet.

**Compilation:** not attempted. **Testing:** none.

## Build

```
gradle wrapper        # once, if you have no wrapper
./gradlew build
```
Output: `build/libs/TheSift-0.1.0.jar` into `plugins/`.

## Build without installing anything (GitHub Actions)

1. Create a GitHub repository and upload the contents of this folder (including the hidden `.github` folder).
2. Open the repo's **Actions** tab. The "Build TheSift" workflow runs on every push (or press **Run workflow**).
3. When it finishes, open the run and download the **TheSift-jar** artifact (a zip containing the JAR).
4. If the run fails, open the failed step's log. The errors are the Java compile errors to fix.

The build is still Gradle; GitHub just runs it for you. The workflow assumes `actions/setup-java` can provide
Java 25 (Temurin). If that step fails, check which distributions offer Java 25 and change `distribution`.

## First start

1. Start once. The plugin copies its datapack to `<level-name>/datapacks/the_sift_dimension/`.
2. Restart the server (datapack dimensions load with the world).
3. `/sift enter` teleports to the Sift's spawn (placeholder until portals/rifts exist).

## Resource-pack hosting

The plugin cannot host files. Upload a pack to a public HTTPS host, set `resourcepack.url` and `resourcepack.sha1`
in `config.yml`, set `enabled: true`, restart. Bump `resourcepack.version` after every re-upload.
There is no pack in this milestone to upload.

## Unverified items to check first

- `Bukkit.getWorld(NamespacedKey)` returning a datapack dimension; `World#getKey()` usage in `/sift info`.
- `pack.mcmeta` `pack_format` is a placeholder `0`; set the value 26.3 requires.
- `dimension_type` JSON fields are 1.21-era; compare with the 26.3 vanilla file (generate with the server data generator).
- `Player#setResourcePack(UUID, String, byte[], Component, boolean)` and `PlayerResourcePackStatusEvent#getID()`.
- SQLite JDBC driver present on Paper's classpath; otherwise declare `org.xerial:sqlite-jdbc` as a plugin library.
- `Commands#register(LiteralCommandNode, String)` and Brigadier import paths.

## Research notes and design decisions

- Spec says 11 biomes; the Modrinth page text I could retrieve says 10. The 2.0.0 changelog was not retrievable. Confirm before naming biomes.
- Tide names Flow/Thrive/Endure come from the Minecraft Dungeons II Sift; cycle order and durations are my own design.
- Art direction: all assets must be original. Producing finished textures, models, animations and audio is a large separate effort and cannot be done credibly as placeholder output.

## Next phase

Phase 2 (world generation): custom biomes via datapack (biome JSON, noise settings, features), then terrain tuning.
Requires the 26.3 vanilla data dump to match field names exactly.
