# HGN Client 1.8.17 Alpha — QA source

This is the corresponding client source snapshot for the experimental Minecraft
1.21.4 / Fabric Loader 0.19.3 managed-runtime QA pair in this release tree.
It is **not** a stable release, a complete game, or proof of a full
launcher-started PC or Android launch. Minecraft and Fabric are obtained separately.

- Source commit: `982970592baed70611ecaebd303c49b70b32170e`
- Exact `client/` tree: `d4696864bc855adf970591e55423717db996c34a`
- Source ZIP: `hgn-client-source-1.8.17-alpha.1-9829705.zip`
- ZIP size: `22,755,481` bytes
- ZIP SHA-256: `eecbcf8f7da93eb43158bf88702d9cf99b6420c99ac17d5ade0f647fc530b76b`

The ZIP was made with `git archive` from that exact commit, including `client/`,
the repository `README.md` and `COPYING.md`, and the existing full GPLv3 text at
`apps/app/LICENSE`. There is no root `LICENSE` file in that source commit.
The added license path contains the license document only, not launcher code.
Keep the bundled copyright and attribution notices when redistributing.

To build the pair from the extracted ZIP with JDK 21 and Node.js 22:

```text
cd client
./gradlew :fabric:proveHgnRuntimePair -Pmc=1.21.4 -Ploader=fabric
```

The Gradle project uses the matching `mod_version` and `hgn_release_name` in
`client/gradle.properties` when the launcher package metadata is absent. The
proof task writes the agent JAR, resource-pack ZIP, and their JSON digest to
`client/fabric/build/libs/`. Their published hashes must be checked against
the adjacent QA proof before use.
