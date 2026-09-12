# HGN Client builds

Published builds of the HGN Minecraft client patch layer. The HGN Launcher
downloads the build matching an instance's loader and Minecraft version when the
instance is created, so the launcher itself does not have to ship a jar for every
combination.

This repository holds build output only. The source lives in the `client/`
directory of the HGN Launcher repository.

## Layout

```
releases/<release-name>/manifest.json
releases/<release-name>/jars/hgn-client-<minecraft>-<loader>.jar
```

Each release-scoped `manifest.json` binds the exact release identity and targets:

```json
{
  "schema": 1,
  "clientVersion": "1.8.3-alpha.1",
  "releaseName": "ALPHA-V1.8.3",
  "builds": [
    {
      "minecraft": "1.21.4",
      "loader": "fabric",
      "file": "releases/ALPHA-V1.8.3/jars/hgn-client-1.21.4-fabric.jar",
      "size": 17243754,
      "sha256": "..."
    }
  ]
}
```

The launcher verifies the SHA-256 of every download and refuses to create an
instance it cannot patch.

## Coverage

The ALPHA-V1.8.3 client matrix covers Minecraft 1.21 through 1.21.11 on Fabric,
Forge, and NeoForge: 35 exact version/loader combinations. The launcher package
defines the supported targets; an individual server can still require a specific
Minecraft version. UI appearance is subject to owner feedback during Alpha.

Excluded targets:

- Forge publishes no build for 1.21.2.
- Minecraft 26.x is not supported by this release's compatibility adapters.

## Updating

From the launcher repository, after building the current targets:

```
node client/tools/collect-builds.mjs <path-to-this-repo>
```

Then commit and push the new release tree. Published manifests and jars are
immutable. Adding a Minecraft target requires a new launcher/client release
identity; never widen or overwrite an existing release tree. Historical root
artifacts remain for older launchers.

Launcher installers and checksum updater manifests are attached to the matching
[public Alpha release](https://github.com/HorizonGate-Minecraft-Plugins/hgn-client-builds/releases).
Downloads do not require a GitHub account or source-repository access.
