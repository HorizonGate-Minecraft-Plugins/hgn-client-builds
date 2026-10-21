import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const screens = new URL('../../common/src/main/java/online/horizongate/hgn/screen/', import.meta.url)
const read = (name) => readFileSync(new URL(name, screens), 'utf8')

test('client settings uses the in-game heading face in wide and compact layouts', () => {
	const settings = read('HgnClientSettingsScreen.java')
	assert.match(settings, /fittedHeading\(graphics, font,\s*layout\.compact\(\)/)
	assert.match(settings, /fittedHeading\(graphics, font, section\.title\(\)/)
	for (const title of ['HORIZONGATE', 'C L I E N T', 'SETTINGS', 'SYSTEM INFO', 'QUICK ACTIONS']) {
		assert.ok(settings.includes(`refHeading(graphics, "${title}"`), `${title} uses the heading face`)
	}
	assert.match(settings, /HgnTypography\.fitted\(graphics, font, entryTitle\(entry\)/)
})

test('native video settings keeps its control labels separate from in-game chrome headings', () => {
	const video = read('HgnVideoSettingsScreen.java')
	assert.match(video, /fittedHeading\(graphics, font, "HGN \/ VIDEO"/)
	assert.match(video, /fittedHeading\(graphics, font, "DISPLAY"/)
	for (const title of ['HORIZONGATE', 'C L I E N T', 'SETTINGS', 'GRAPHICS OPTIONS', 'VIDEO OPTIONS', 'NATIVE SAVE']) {
		assert.ok(video.includes(`referenceHeading(graphics, canvas, "${title}"`), `${title} uses the heading face`)
	}
	assert.match(video, /for \(AbstractWidget control : visibleControls\) control\.render\(/)
})
