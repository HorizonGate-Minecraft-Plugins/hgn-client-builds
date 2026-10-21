import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const root = new URL('../../common/src/main/java/online/horizongate/hgn/', import.meta.url)
const read = (path) => readFileSync(new URL(path, root), 'utf8')

test('empty installed Mods hero uses real state and the existing exact-version catalog', () => {
	const screen = read('screen/HgnInstalledModsScreen.java')
	const index = read('screen/HgnInstalledModsIndex.java')
	const layout = read('ui/HgnInstalledModsLayout.java')
	assert.match(screen, /layout\.showsEmptyDiscoveryHero\(\) && snapshot\.problem\(\)\.isEmpty\(\)/)
	assert.match(screen, /&& snapshot\.jars\(\)\.isEmpty\(\)/)
	assert.match(screen, /if \(showsEmptyDiscoveryHero\(\)\) \{[\s\S]*?DISCOVER MODS[\s\S]*?new HgnModCatalogScreen\(this\)/)
	assert.match(screen, /if \(showsEmptyDiscoveryHero\(\)\) \{\s*renderEmptyDiscoveryHero\(graphics\);\s*return;/)
	assert.match(screen, /HgnSurface\.GATEWAY_SCENE/)
	assert.match(screen, /"MOD FOLDER UNAVAILABLE"[\s\S]*?"NO MATCHING MODS"/)
	assert.match(index, /if \(Files\.notExists\(mods\)\) \{\s*return new Snapshot\(mods, List\.of\(\), ""\);/)
	assert.match(index, /if \(!Files\.exists\(mods\)\) \{\s*return new Snapshot\(mods, List\.of\(\), "Could not access/)
	assert.match(layout, /list\.width\(\) >= 420 && list\.height\(\) >= 150/)
	assert.match(layout, /list\.width\(\) >= 300 && list\.height\(\) >= 175/)
	assert.match(screen, /if \(layout\.emptyDiscoveryStacked\(\)\) \{[\s\S]*?textureRegionStretch/)
})
