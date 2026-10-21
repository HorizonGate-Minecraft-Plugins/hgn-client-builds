import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const dashboard = readFileSync(new URL(
	'../../common/src/main/java/online/horizongate/hgn/screen/HgnDashboardScreen.java',
	import.meta.url,
), 'utf8')
const officialServers = readFileSync(new URL(
	'../../common/src/main/java/online/horizongate/hgn/HgnOfficialServers.java',
	import.meta.url,
), 'utf8')
const multiplayer = readFileSync(new URL(
	'../../common/src/main/java/online/horizongate/hgn/screen/HgnMultiplayerScreen.java',
	import.meta.url,
), 'utf8')

test('dashboard Play and featured Join share the official Hub connection', () => {
	assert.match(dashboard, /command\(0, this::joinOfficialHub\)/)
	assert.match(dashboard,
		/Component\.literal\("JOIN HORIZONGATE NETWORK"\), true,\s*ignored -> joinOfficialHub\(\)/)
	const route = dashboard.match(/private void joinOfficialHub\(\) \{([\s\S]*?)\n\t\}/)?.[1]
	assert.ok(route, 'shared official Hub route is missing')
	assert.match(route, /HgnOfficialServers\.HUB/)
	assert.match(route,
		/if \(hub\.playableNow\(\)\) HgnCompat\.connect\(this, minecraft, hub\.name\(\), hub\.address\(\)\)/)
	assert.match(route,
		/else HgnCompat\.openUrl\("hgn:\/\/play\/" \+ hub\.id\(\) \+ "\/" \+ hub\.requiredVersion\(\)\)/)
})

test('official Hub identity is canonical and server browsing stays separate', () => {
	assert.match(officialServers,
		/public static final Server HUB = new Server\(\s*"hub",\s*HgnClient\.HUB_NAME,\s*HgnClient\.HUB_ADDRESS/)
	assert.match(officialServers, /ALL = List\.of\(HUB, VINKSMP\)/)
	assert.match(dashboard,
		/command\(3,\s*\(\) -> minecraft\.setScreen\(new HgnMultiplayerScreen\(this\)\)\)/)
	assert.match(dashboard,
		/browseServersButton = target\(servers\[index\], "Browse servers",\s*\(\) -> minecraft\.setScreen\(new HgnMultiplayerScreen\(this\)\)\)/)
})

test('in-game official servers keep the shared destination rail and real server actions', () => {
	assert.match(multiplayer, /new HgnReferenceShellLayout\(layout\)/)
	assert.match(multiplayer,
		/for \(int index = 0; index < HgnReferenceShellLayout\.DESTINATIONS; index\+\+\)/)
	for (const action of ['OFFICIAL', 'OVERVIEW', 'SAVED', 'LAN', 'DIRECT', 'REFRESH', 'MENU']) {
		assert.match(multiplayer, new RegExp(`wideAction\\(\\d, "${action}"`))
	}
	assert.match(multiplayer, /wideAction\(2, "SAVED", false, this::openSaved\)/)
	assert.match(multiplayer, /wideAction\(3, "LAN", false, this::openLan\)/)
	assert.match(multiplayer, /wideAction\(4, "DIRECT", false, this::directConnect\)/)
	assert.match(multiplayer, /wideAction\(6, "MENU", false, this::onClose\)/)
	assert.match(multiplayer, /HgnOfficialServers\.ALL\.get\(selected\)/)
	assert.match(multiplayer, /if \(server\.playableNow\(\)\) HgnCompat\.connect/)
	assert.match(multiplayer, /HgnServerPopulation\.fromMinecraftPing\(true,/)
	const playRoute = multiplayer.match(/private void joinOfficialHub\(\) \{([\s\S]*?)\n\t\}/)?.[1]
	assert.ok(playRoute, 'official Servers Play must use the canonical Hub')
	assert.match(playRoute, /HgnOfficialServers\.HUB/)
	assert.match(playRoute, /hgn:\/\/play\//)
})
