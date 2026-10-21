import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const common = new URL('../../common/', import.meta.url)
const read = (path) => readFileSync(new URL(path, common), 'utf8')

test('the in-game heading face selects the bundled Rajdhani TrueType font', () => {
	const definition = JSON.parse(read('src/main/resources/assets/hgn-client/font/heading.json'))
	assert.ok(definition.providers.some((provider) => provider.type === 'ttf'
		&& provider.file === 'hgn-client:rajdhani_semibold.ttf'))
	for (const generation of ['g1210', 'g1212', 'g1216', 'g1219', 'g12111']) {
		const compat = read(`src/compat/${generation}/java/online/horizongate/hgn/compat/HgnCompat.java`)
		assert.match(compat, /HEADING_FONT[^;]+"hgn-client", "heading"/s)
		assert.match(compat, /Component headingText\(String text\)/)
	}
})

test('dashboard headings use the in-game heading face', () => {
	const dashboard = read('src/main/java/online/horizongate/hgn/screen/HgnDashboardScreen.java')
	assert.match(dashboard, /fittedHeading\(graphics, font, "HORIZONGATE"/)
	assert.match(dashboard, /heading\(graphics, font, "C L I E N T"/)
	assert.match(dashboard, /fittedHeading\(graphics, font, "HORIZONGATE NETWORK"/)
})
