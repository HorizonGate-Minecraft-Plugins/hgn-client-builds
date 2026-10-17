# HGN Client 1.8.17 Alpha — current-source QA snapshot

This directory contains the corresponding GPL client source for the
experimental Minecraft 1.21.4 / Fabric Loader 0.19.3 managed-runtime pair in
`../runtime/`. It is a review-branch snapshot, not a release or a complete
Minecraft game. Minecraft and Fabric are obtained separately.

- Source repository: `HorizonGate-Minecraft-Plugins/HGN-MC-Launcher`
- Exact source commit: `d5c386adec69f8f026e85f8af2f8fa9afcb58c20`
- Exact `client/` tree: `3506fb842ce410dc369ba1e5aaa066ebc1ab4bf1`
- Source ZIP: `hgn-client-source-1.8.17-alpha.1-d5c386a.zip`
- Source ZIP SHA-256: `491a9f737bf981475a1e520b5c1396be9907838d3c2554c54c87d8df7002cf2a`
- Agent SHA-256: `7cd2261c55d6099fa8ed90867e0bccc1aa382587770883c1acc54f47ef74a93a`
- Resource-pack SHA-256: `4650ab4a5b9fa808ad48e3722e3563e709f47a0c84cee0afebfdba9ada680b04`
- Proof JSON SHA-256: `11aacfddc98ee263287e02897ecb3f97b223108a4e5b8ae23d8408c02ce0221d5`

The source ZIP was created with `git archive` from the exact commit. It
contains `client/`, the repository `README.md` and `COPYING.md`, and the full
GPLv3 text at `apps/app/LICENSE`. Keep copyright and attribution notices when
redistributing.

With JDK 21 and Node.js 22, reproduce the pair from the extracted ZIP:

```text
cd client
./gradlew :fabric:proveHgnRuntimePair -Pmc=1.21.4
node tools/runtime-pair.mjs verify fabric/build/libs/hgn-client-runtime-proof-1.21.4-fabric-0.19.3-1.8.17-alpha.1.json
```

The local source build passed 12 Gradle tasks, independent proof verification,
and 13 focused runtime-pair/wiring tests. The runtime has **not** passed a
launcher-installed paid-account game launch, God’s Eye/server acceptance, or
release approval. Do not merge this pair to `main` or tag it as a release on
the strength of these checks alone.
