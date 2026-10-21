import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = (name) =>
	readFileSync(new URL(`../../common/src/main/java/online/horizongate/hgn/screen/${name}`, import.meta.url), 'utf8')

const selection = read('HgnSelectWorldScreen.java')
const empty = read('HgnEmptyWorldScreen.java')
const dashboard = read('HgnDashboardScreen.java')
const settings = read('HgnClientSettingsScreen.java')
const multiplayer = read('HgnMultiplayerScreen.java')

assert.match(selection, /findLevelCandidates\(\)\.isEmpty\(\)/)
assert.match(selection, /return new HgnEmptyWorldScreen\(parent\)/)
assert.match(selection, /return new HgnSelectWorldScreen\(parent\)/)
assert.match(empty, /HgnCompat\.openFreshWorld\(minecraft, this\)/)
for (const generation of ['g1210', 'g1212', 'g1216', 'g1219', 'g12111']) {
	const compat = readFileSync(new URL(`../../common/src/compat/${generation}/java/online/horizongate/hgn/compat/HgnCompat.java`, import.meta.url), 'utf8')
	assert.match(compat, /void openFreshWorld\(Minecraft minecraft, Screen parent\)/)
	assert.match(compat, /CreateWorldScreen\.openFresh\(minecraft,/)
}
assert.match(empty, /minecraft\.setScreen\(HgnSelectWorldScreen\.open\(parent\)\)/)
assert.match(empty, /minecraft\.setScreen\(parent\)/)
assert.doesNotMatch(empty, /HgnWorldMenuSmokeFixture|Files\.create|Files\.write/)
for (const route of [dashboard, settings, multiplayer]) {
	assert.match(route, /minecraft\.setScreen\(HgnSelectWorldScreen\.open\(this\)\)/)
}
for (const route of [selection, empty]) {
	assert.match(route, /"PLAY", "SINGLEPLAYER", "MULTIPLAYER",\s*"SERVERS"/)
	assert.match(route, /case 2 -> minecraft\.setScreen\(new HgnMultiplayerScreen\(this\)\)/)
	assert.match(route, /case 3 -> minecraft\.setScreen\(new HgnSavedServersScreen\(this\)\)/)
}

console.log('Empty-world route: native creation, refresh, safe return and user-facing entries are wired without a fixture.')
