import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const fabricBuild = readFileSync(new URL('../../fabric/build.gradle', import.meta.url), 'utf8')

test('prototype sources are remapped only for explicit runtime proof tasks', () => {
	const condition = fabricBuild.indexOf('if (prototypeTaskRequested) {')
	const jarTask = fabricBuild.indexOf("tasks.register('hgnRuntimePrototypeJar', Jar)")
	assert.ok(condition >= 0 && jarTask > condition)
	const scopedSources = fabricBuild.slice(condition, jarTask)
	assert.match(scopedSources,
		/sourceSets\.main\.java\.srcDir rootProject\.file\('prototype\/src\/main\/java'\)/)
	assert.match(scopedSources, /compileOnly 'org\.ow2\.asm:asm:9\.10\.1'/)
	assert.doesNotMatch(fabricBuild, /sourceSets\.create\('runtimePrototype'\)/)
	assert.match(fabricBuild, /leaf in \['hgnRuntimePrototypeJar', 'hgnRuntimeResourcePack',/)
	assert.doesNotMatch(fabricBuild, /leaf in \[[^\]]*'build'|leaf in \[[^\]]*'remapJar'/)
})

test('agent archive takes runtime classes from Loom remapJar, not named dev output', () => {
	const start = fabricBuild.indexOf("tasks.register('hgnRuntimePrototypeJar', Jar)")
	const end = fabricBuild.indexOf("tasks.register('hgnRuntimeResourcePack', Zip)")
	assert.ok(start >= 0 && end > start)
	const jarTask = fabricBuild.slice(start, end)
	assert.match(jarTask, /dependsOn tasks\.named\('remapJar'\)/)
	assert.match(jarTask,
		/from\(\{ zipTree\(tasks\.named\('remapJar'\)\.get\(\)\.archiveFile\.get\(\)\.asFile\) \}\)/)
	assert.doesNotMatch(jarTask, /sourceSets\.runtimePrototype|sourceSets\.main\.output/)
	assert.match(fabricBuild,
		/names\.contains\('online\/horizongate\/hgn\/runtime\/HgnRuntimeFabricPlatform\.class'\)/)
})

test('runtime pair proof checks the exact pinned Fabric Knot before accepting a pair', () => {
	assert.match(fabricBuild, /leaf in \[[^\]]*'verifyHgnRuntimeKnot'/)
	assert.match(fabricBuild, /rootProject\.loader_version != '0\.19\.3'/)
	assert.match(fabricBuild, /tasks\.register\('verifyHgnRuntimeKnot', JavaExec\)/)
	assert.match(fabricBuild, /pinnedLoader\.transitive = false/)
	const proof = fabricBuild.slice(fabricBuild.indexOf("tasks.register('proveHgnRuntimePair', Exec)"))
	assert.match(proof, /dependsOn tasks\.named\('verifyHgnRuntimePrototype'\), tasks\.named\('verifyHgnRuntimeKnot'\)/)
})
