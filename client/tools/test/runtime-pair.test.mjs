import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { deflateRawSync } from 'node:zlib'
import { crc32 } from '../jar-payload.mjs'
import { artifactNames, createProof, inspectRuntimePair, verifyProof } from '../runtime-pair.mjs'

const version = '1.8.16-alpha.1'
const root = new URL('../../common/src/main/resources/assets/hgn-client/', import.meta.url)
const png = readFileSync(new URL('textures/gui/dashboard_welcome_city.png', root))
const gateway = readFileSync(new URL('textures/gui/hgn_gateway_reference_clean.png', root))
const twilight = readFileSync(new URL('textures/gui/hgn_gateway_twilight.png', root))
const certificate = readFileSync(new URL('security/hgn_alpha_ip_root.pem', root))
const classBytes = Buffer.from('cafebabe00000034', 'hex')
const ttf = Buffer.from('000100000000000000000000', 'hex')
const assetFiles = new Map([
	['assets/hgn-client/font/ui.json', Buffer.from(JSON.stringify({
		providers: [{ type: 'ttf', file: 'hgn-client:inter_regular.ttf' }],
	}))],
	['assets/hgn-client/font/inter_regular.ttf', ttf],
	['assets/hgn-client/font/heading.json', Buffer.from(JSON.stringify({
		providers: [{ type: 'ttf', file: 'hgn-client:rajdhani_semibold.ttf' }],
	}))],
	['assets/hgn-client/font/rajdhani_semibold.ttf', ttf],
	['assets/hgn-client/security/hgn_alpha_ip_root.pem', certificate],
	['assets/hgn-client/textures/gui/dashboard_welcome_city.png', png],
	['assets/hgn-client/textures/gui/hgn_gateway_reference_clean.png', gateway],
	['assets/hgn-client/textures/gui/hgn_gateway_twilight.png', twilight],
])

function zip(files, method = 0) {
	const local = []
	const central = []
	let offset = 0
	for (const [name, data] of files) {
		const filename = Buffer.from(name, 'utf8')
		const payload = method === 8 ? deflateRawSync(data) : data
		const header = Buffer.alloc(30)
		header.writeUInt32LE(0x04034b50, 0)
		header.writeUInt16LE(20, 4)
		header.writeUInt16LE(0x0800, 6)
		header.writeUInt16LE(method, 8)
		header.writeUInt32LE(crc32(data), 14)
		header.writeUInt32LE(payload.length, 18)
		header.writeUInt32LE(data.length, 22)
		header.writeUInt16LE(filename.length, 26)
		local.push(header, filename, payload)
		const directory = Buffer.alloc(46)
		directory.writeUInt32LE(0x02014b50, 0)
		directory.writeUInt16LE(20, 4)
		directory.writeUInt16LE(20, 6)
		directory.writeUInt16LE(0x0800, 8)
		directory.writeUInt16LE(method, 10)
		directory.writeUInt32LE(crc32(data), 16)
		directory.writeUInt32LE(payload.length, 20)
		directory.writeUInt32LE(data.length, 24)
		directory.writeUInt16LE(filename.length, 28)
		directory.writeUInt32LE(offset, 42)
		central.push(directory, filename)
		offset += header.length + filename.length + payload.length
	}
	const directoryBytes = Buffer.concat(central)
	const end = Buffer.alloc(22)
	end.writeUInt32LE(0x06054b50, 0)
	end.writeUInt16LE(files.size, 8)
	end.writeUInt16LE(files.size, 10)
	end.writeUInt32LE(directoryBytes.length, 12)
	end.writeUInt32LE(offset, 16)
	return Buffer.concat([...local, directoryBytes, end])
}

function fixtures(packFormat = 46) {
	const agent = new Map([
		['META-INF/MANIFEST.MF', Buffer.from('Manifest-Version: 1.0\r\nPremain-Class: online.horizongate.hgn.runtime.HgnRuntimeAgent\r\n')],
		['hgn-client.mixins.json', Buffer.from(JSON.stringify({
			package: 'online.horizongate.hgn.mixin', client: ['MinecraftMixin'],
		}))],
		['online/horizongate/hgn/HgnClient.class', classBytes],
		['online/horizongate/hgn/runtime/HgnRuntimeAgent.class', classBytes],
		['online/horizongate/hgn/runtime/HgnRuntimeBootstrap.class', classBytes],
		['online/horizongate/hgn/runtime/HgnRuntimeFabricPlatform.class', classBytes],
		['online/horizongate/hgn/mixin/MinecraftMixin.class', classBytes],
		...assetFiles,
	])
	const pack = new Map([
		['pack.mcmeta', Buffer.from(JSON.stringify({ pack: {
			pack_format: packFormat, description: 'HorizonGate Network Client test resources',
		} }))],
		...assetFiles,
	])
	return { agent, pack }
}

test('deterministic 1.21.4 Fabric pair creates a self-consistent proof', () => {
	const { agent, pack } = fixtures()
	const agentBytes = zip(agent)
	const packBytes = zip(pack)
	const proof = createProof(version, 'ALPHA-V1.8.16', agentBytes, packBytes)
	assert.deepEqual(Object.keys(proof), [
		'schema', 'clientVersion', 'releaseName', 'minecraft', 'loader', 'loaderVersion',
		'agent', 'resourcePack',
	])
	assert.equal(proof.agent.file, artifactNames(version).agent)
	assert.equal(proof.resourcePack.file, artifactNames(version).resourcePack)
	assert.match(proof.agent.sha256, /^[0-9a-f]{64}$/)
	assert.equal(verifyProof(proof, agentBytes, packBytes).assetCount, assetFiles.size)
})

test('method-8 DEFLATE archives verify without a Gradle build', () => {
	const { agent, pack } = fixtures()
	const agentBytes = zip(agent, 8)
	const packBytes = zip(pack, 8)
	const proof = createProof(version, 'ALPHA-V1.8.16', agentBytes, packBytes)
	assert.equal(verifyProof(proof, agentBytes, packBytes).assetCount, assetFiles.size)
})

test('changed archive bytes invalidate the SHA-256 proof', () => {
	const { agent, pack } = fixtures()
	const agentBytes = zip(agent)
	const packBytes = zip(pack)
	const proof = createProof(version, 'ALPHA-V1.8.16', agentBytes, packBytes)
	const changed = Buffer.from(packBytes)
	changed[changed.length - 1] = 1
	assert.throws(() => verifyProof(proof, agentBytes, changed), /resourcePack does not match/)
})

test('release identity and artifact names stay pinned to the client version', () => {
	assert.throws(() => artifactNames('1.8.16-alpha.2'), /supported safe release identifier/)
	assert.throws(() => artifactNames('1.8.16-beta.01'), /supported safe release identifier/)
	const { agent, pack } = fixtures()
	const agentBytes = zip(agent)
	const packBytes = zip(pack)
	assert.throws(() => createProof(version, 'BETA-V1.8.16', agentBytes, packBytes), /releaseName is invalid/)
	const proof = createProof(version, 'ALPHA-V1.8.16', agentBytes, packBytes)
	proof.agent.file = 'another-agent.jar'
	assert.throws(() => verifyProof(proof, agentBytes, packBytes), /agent does not match/)
})

test('wrong resource pack format is rejected', () => {
	const { agent, pack } = fixtures(47)
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /pack_format 46/)
})

test('damaged PNGs and resource-pack mod metadata are rejected', () => {
	const { agent, pack } = fixtures()
	const damaged = Buffer.from(png)
	damaged[damaged.length - 20] ^= 1
	pack.set('assets/hgn-client/textures/gui/dashboard_welcome_city.png', damaged)
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /corrupt PNG|cannot decode PNG/)
	pack.set('assets/hgn-client/textures/gui/dashboard_welcome_city.png', png)
	pack.set('fabric.mod.json', Buffer.from('{}'))
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /unapproved entry fabric.mod.json/)
})

test('mod metadata and outside-namespace classes are rejected', () => {
	const { agent, pack } = fixtures()
	agent.set('fabric.mod.json', Buffer.from('{}'))
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /unapproved entry fabric.mod.json/)
	agent.delete('fabric.mod.json')
	agent.set('net/minecraft/client/Minecraft.class', classBytes)
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /unapproved entry net\/minecraft/)
})

test('missing, altered and source-mismatched assets are rejected', () => {
	const { agent, pack } = fixtures()
	pack.delete('assets/hgn-client/textures/gui/hgn_gateway_reference_clean.png')
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /missing asset|different HGN assets/)
	pack.set('assets/hgn-client/textures/gui/hgn_gateway_reference_clean.png', gateway)
	pack.delete('assets/hgn-client/font/inter_regular.ttf')
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /missing asset/)
	pack.set('assets/hgn-client/font/inter_regular.ttf', Buffer.from('000100000000000000000001', 'hex'))
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /different HGN assets/)
	pack.set('assets/hgn-client/font/inter_regular.ttf', ttf)
	pack.delete('assets/hgn-client/font/heading.json')
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /missing asset/)
	pack.set('assets/hgn-client/font/heading.json', assetFiles.get('assets/hgn-client/font/heading.json'))
	pack.delete('assets/hgn-client/font/rajdhani_semibold.ttf')
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /missing asset/)
	pack.set('assets/hgn-client/font/rajdhani_semibold.ttf', ttf)
	const source = new Map([...assetFiles].map(([name, data]) => [name, '0'.repeat(64)]))
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack), source), /different HGN assets/)
})

test('heading font definition and TrueType bytes must be valid', () => {
	const { agent, pack } = fixtures()
	const heading = 'assets/hgn-client/font/heading.json'
	const font = 'assets/hgn-client/font/rajdhani_semibold.ttf'
	pack.set(heading, Buffer.from('{'))
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /invalid JSON/)
	pack.set(heading, Buffer.from(JSON.stringify({
		providers: [{ type: 'ttf', file: 'hgn-client:other.ttf' }],
	})))
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /heading font does not reference/)
	pack.set(heading, assetFiles.get(heading))
	pack.set(font, Buffer.from('not a TrueType font'))
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /invalid font/)
})

test('CRC errors, unsafe entry names and nested archives are rejected', () => {
	const { agent, pack } = fixtures()
	const agentBytes = zip(agent)
	const firstDirectory = agentBytes.lastIndexOf(Buffer.from('PK\x01\x02', 'binary'))
	const corrupt = Buffer.from(agentBytes)
	corrupt.writeUInt32LE(0, firstDirectory + 16)
	assert.throws(() => inspectRuntimePair(corrupt, zip(pack)), /corrupt data/)
	agent.set('../escape.class', classBytes)
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /unsafe ZIP entry name/)
	agent.delete('../escape.class')
	agent.set('online/horizongate/hgn/nested.jar', Buffer.from('PK'))
	assert.throws(() => inspectRuntimePair(zip(agent), zip(pack)), /unapproved entry/)
})
