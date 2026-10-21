import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const screen = readFileSync(new URL('../../common/src/main/java/online/horizongate/hgn/screen/HgnDashboardScreen.java', import.meta.url), 'utf8')

test('dashboard Quick Status shows measured FPS rather than a mockup number', () => {
	assert.match(screen, /renderStatusCell\(graphics, 1, HgnIcon\.PERFORMANCE, "FRAMERATE",\s*minecraft\.getFps\(\) > 0 \? minecraft\.getFps\(\) \+ " FPS" : "MEASURING"/)
	assert.doesNotMatch(screen, /346 FPS|24 ms/)
})
