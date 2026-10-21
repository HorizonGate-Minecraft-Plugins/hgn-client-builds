import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const root = new URL('../../common/src/', import.meta.url)
const read = (path) => readFileSync(new URL(path, root), 'utf8')

test('the wide gateway hero uses the existing live player preview only after the FPS gate', () => {
	const dashboard = read('main/java/online/horizongate/hgn/screen/HgnDashboardScreen.java')
	const policy = read('main/java/online/horizongate/hgn/ui/HgnHeroPreviewPolicy.java')
	assert.match(dashboard, /if \(minecraft\.screen == this\) refreshHeroPreview\(\)/)
	assert.match(dashboard, /heroPreviewPolicy\.shouldRender\(compactLayout == null,/)
	assert.match(dashboard, /if \(!visible\) \{[\s\S]*?return;[\s\S]*?HgnCompat\.heroWidget/)
	assert.match(policy, /fps < STOP_FPS[\s\S]*?slowSamples >= SLOW_SAMPLES_TO_STOP\) stoppedForScreen = true/)
	const cosmetics = read('main/java/online/horizongate/hgn/cosmetics/HgnCosmetics.java')
	assert.match(cosmetics, /equippedPreviewRenderPlan\(\)[\s\S]*?current\.profile\(\)\.equipped\(\), null,/)
	for (const generation of ['g1210', 'g1212', 'g1216', 'g1219', 'g12111']) {
		const compat = read(`compat/${generation}/java/online/horizongate/hgn/compat/HgnCompat.java`)
		const widget = read(`compat/${generation}/java/online/horizongate/hgn/compat/HgnCosmeticPreviewWidget.java`)
		assert.match(compat, /heroWidget\([\s\S]*?new HgnCosmeticPreviewWidget\(/)
		assert.match(compat, /heroWidget\([\s\S]*?widget\.active = false/)
		assert.match(widget, /equippedOnly[\s\S]*?HgnCosmetics\.equippedPreviewRenderPlan\(\)/)
	}
	const preview = read('compat/g1212/java/online/horizongate/hgn/compat/HgnCosmeticPreviewWidget.java')
	assert.match(preview, /this\.rotationY = initialYaw/)
	assert.match(preview, /\* sizeMultiplier/)
})

test('the foreground model stays between navigation, featured action, and profile at common sizes', () => {
	const layout = read('main/java/online/horizongate/hgn/ui/HgnDashboardLayout.java')
	const shell = read('main/java/online/horizongate/hgn/ui/HgnReferenceShellLayout.java')
	const findRect = (source, name) => {
		const body = source.match(new RegExp(`public HgnRect ${name}\\(\\) \\{([\\s\\S]*?)\\n\\t\\}`))?.[1]
		assert.ok(body, `missing ${name} geometry`)
		const values = body.match(/return (?:canvas\.)?rect\((\d+), (\d+), (\d+), (\d+)\)/)?.slice(1).map(Number)
		assert.equal(values?.length, 4, `unexpected ${name} geometry`)
		return values
	}
	const player = findRect(layout, 'gatewayForegroundModel')
	const rail = findRect(shell, 'rail')
	const featured = findRect(shell, 'featuredNetwork')
	const profile = findRect(shell, 'profileColumn')
	const referenceWidth = Number(layout.match(/WIDTH = (\d+)/)?.[1])
	const referenceHeight = Number(layout.match(/HEIGHT = (\d+)/)?.[1])
	for (const [width, height] of [[640, 360], [836, 471], [1280, 720], [1672, 941], [1920, 1080]]) {
		const scale = Math.min(width / referenceWidth, height / referenceHeight)
		const offsetX = Math.round((width - referenceWidth * scale) / 2)
		const offsetY = Math.round((height - referenceHeight * scale) / 2)
		const bounds = ([x, y, w, h]) => ({
			x: offsetX + Math.round(x * scale),
			y: offsetY + Math.round(y * scale),
			right: offsetX + Math.round((x + w) * scale),
			bottom: offsetY + Math.round((y + h) * scale),
		})
		const avatar = bounds(player)
		assert.ok(bounds(rail).right < avatar.x, `${width}x${height}: avatar hit navigation`)
		assert.ok(avatar.bottom <= bounds(featured).y, `${width}x${height}: avatar hit featured action`)
		assert.ok(avatar.right < bounds(profile).x, `${width}x${height}: avatar hit profile`)
		assert.ok(avatar.y >= 0 && avatar.bottom <= height, `${width}x${height}: avatar left viewport`)
	}
})
