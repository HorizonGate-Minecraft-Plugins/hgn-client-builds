import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const root = new URL('../../common/src/main/java/online/horizongate/hgn/', import.meta.url)
const layout = readFileSync(new URL('ui/HgnWorldSelectionLayout.java', root), 'utf8')
const screen = readFileSync(new URL('screen/HgnSelectWorldScreen.java', root), 'utf8')
const geometry = readFileSync(new URL('HgnWorldSelectionLayoutTest.java', import.meta.url), 'utf8')

test('six native world actions leave a selected-save rail without replacing the gateway banner', () => {
	assert.match(layout, /boolean constrainedActions = contentHeight < 330/)
	assert.match(layout, /int buttonGap = constrainedActions \? 2 : 4/)
	assert.match(layout, /int maxButtonHeight = constrainedActions \? 20 : 24/)
	assert.match(layout, /boolean footerFits = !constrainedActions/)
	assert.match(screen, /if \(layout\.selectedWorldInHero\(\) && selected != null\) renderSelectedWorld/)
	assert.match(screen, /else renderHero\(graphics\)/)
	assert.match(geometry, /resolve\(640, 360, 6\)\.showsSelectedWorld\(\)/)
	assert.match(geometry, /!HgnWorldSelectionLayout\.resolve\(640, 360, 6\)\.selectedWorldInHero\(\)/)
	assert.match(geometry, /resolve\(640, 360, 7\)\.selectedWorldInHero\(\)/)
})
