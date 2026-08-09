# HGN Client builds

Published builds of the HGN Minecraft client patch layer. The HGN Launcher
downloads the build matching an instance's loader and Minecraft version when the
instance is created, so the launcher itself does not have to ship a jar for every
combination.

This repository holds build output only. The source lives in the `client/`
directory of the HGN Launcher repository.

## Layout

```
manifest.json                        every available build, with checksums
jars/hgn-client-<minecraft>-<loader>.jar
```

`manifest.json`:

```json
{
  "schema": 1,
  "clientVersion": "1.0.0",
  "builds": [
    {
      "minecraft": "1.21.4",
      "loader": "fabric",
      "file": "jars/hgn-client-1.21.4-fabric.jar",
      "size": 403451,
      "sha256": "..."
    }
  ]
}
```

The launcher verifies the SHA-256 of every download and refuses to create an
instance it cannot patch.

## Coverage

The current Alpha publishes Minecraft 1.21.4 only, on Fabric, Forge, and
NeoForge. This keeps testing on one game API while the new UI and supplied-model
cosmetic renderer stabilize. Stable publication restores the Minecraft 1.21
through 1.21.11 loader matrix (35 builds).

Two gaps are upstream, not missing work:

- Forge publishes no build for 1.21.2.
- Minecraft 26.x cannot be built yet: Mojang stopped obfuscating and stopped
  publishing mappings at 26.1, and the current modding toolchain has no path for
  an already-deobfuscated game.

## Updating

From the launcher repository, after building the current targets:

```
node client/tools/collect-builds.mjs <path-to-this-repo>
```

Then commit and push. Adding a Minecraft version needs no launcher release.
