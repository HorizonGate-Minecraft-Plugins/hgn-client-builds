import { createHash, X509Certificate } from 'node:crypto'
import { readFileSync, readdirSync, renameSync, statSync, writeFileSync } from 'node:fs'
import { basename, dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { inflateRawSync } from 'node:zlib'
import { checkPng, crc32 } from './jar-payload.mjs'

const MINECRAFT = '1.21.4'
const LOADER = 'fabric'
const LOADER_VERSION = '0.19.3'
const PACK_FORMAT = 46
const MAX_ARCHIVE_BYTES = 512 * 1024 * 1024
const MAX_ENTRY_BYTES = 128 * 1024 * 1024
const MAX_TOTAL_BYTES = 768 * 1024 * 1024
const ASSET_ROOT = 'assets/hgn-client/'
const CLASS_ROOT = 'online/horizongate/hgn/'
const ASSET_SUFFIXES = ['.json', '.mcmeta', '.pem', '.png', '.ttf', '.txt']
const REQUIRED_CLASSES = [
	'online/horizongate/hgn/HgnClient.class',
	'online/horizongate/hgn/runtime/HgnRuntimeAgent.class',
	'online/horizongate/hgn/runtime/HgnRuntimeBootstrap.class',
	'online/horizongate/hgn/runtime/HgnRuntimeFabricPlatform.class',
]
const REQUIRED_ASSETS = [
	'assets/hgn-client/font/ui.json',
	'assets/hgn-client/font/inter_regular.ttf',
	'assets/hgn-client/font/heading.json',
	'assets/hgn-client/font/rajdhani_semibold.ttf',
	'assets/hgn-client/security/hgn_alpha_ip_root.pem',
	'assets/hgn-client/textures/gui/dashboard_welcome_city.png',
	'assets/hgn-client/textures/gui/hgn_gateway_reference_clean.png',
	'assets/hgn-client/textures/gui/hgn_gateway_twilight.png',
]
const UTF8 = new TextDecoder('utf-8', { fatal: true })

function fail(message) {
	throw new Error(`HGN runtime pair: ${message}`)
}

function sha256(bytes) {
	return createHash('sha256').update(bytes).digest('hex')
}

export function artifactNames(clientVersion) {
	if (typeof clientVersion !== 'string'
		|| !/^\d+\.\d+\.\d+(?:-alpha\.1|-beta\.(?:0|[1-9]\d*))?$/.test(clientVersion)) {
		fail('clientVersion is not a supported safe release identifier')
	}
	const stem = `${MINECRAFT}-${LOADER}-${LOADER_VERSION}-${clientVersion}`
	return {
		agent: `hgn-client-runtime-${stem}.jar`,
		resourcePack: `hgn-client-resources-${stem}.zip`,
		proof: `hgn-client-runtime-proof-${stem}.json`,
	}
}

function validReleaseName(clientVersion, releaseName) {
	if (typeof releaseName !== 'string' || !releaseName.trim()
		|| releaseName.length > 128 || /[\x00-\x1f]/.test(releaseName)) return false
	const alpha = /^(\d+\.\d+\.\d+)-alpha\.1$/.exec(clientVersion)
	if (alpha) return releaseName === `ALPHA-V${alpha[1]}`
	if (clientVersion.includes('-beta.')) return releaseName === `v${clientVersion}`
	return true
}

function safeName(name) {
	if (!name || name.includes('\\') || name.includes('\0') || name.startsWith('/')
		|| /^[A-Za-z]:/.test(name) || name.split('/').some((part, index, parts) =>
			part === '.' || part === '..' || (!part && index !== parts.length - 1))) {
		fail(`unsafe ZIP entry name ${JSON.stringify(name)}`)
	}
}

function parseZip(bytes, label) {
	if (!Buffer.isBuffer(bytes) || bytes.length < 22 || bytes.length > MAX_ARCHIVE_BYTES) {
		fail(`${label} has an invalid archive size`)
	}
	let end = -1
	for (let at = bytes.length - 22; at >= Math.max(0, bytes.length - 65557); at--) {
		if (bytes.readUInt32LE(at) === 0x06054b50 && at + 22 + bytes.readUInt16LE(at + 20) === bytes.length) {
			end = at
			break
		}
	}
	if (end < 0 || bytes.readUInt16LE(end + 20) !== 0
		|| bytes.readUInt16LE(end + 4) !== 0 || bytes.readUInt16LE(end + 6) !== 0
		|| bytes.readUInt16LE(end + 8) !== bytes.readUInt16LE(end + 10)) {
		fail(`${label} has an invalid or multi-disk ZIP end record`)
	}
	const count = bytes.readUInt16LE(end + 10)
	const directorySize = bytes.readUInt32LE(end + 12)
	const directoryStart = bytes.readUInt32LE(end + 16)
	if (!count || count === 0xffff || directorySize === 0xffffffff
		|| directoryStart === 0xffffffff || directoryStart + directorySize !== end) {
		fail(`${label} has an unsupported ZIP directory`)
	}
	const entries = new Map()
	let offset = directoryStart
	let total = 0
	for (let index = 0; index < count; index++) {
		if (offset + 46 > end || bytes.readUInt32LE(offset) !== 0x02014b50) {
			fail(`${label} has a corrupt ZIP directory entry`)
		}
		const nameLength = bytes.readUInt16LE(offset + 28)
		const extraLength = bytes.readUInt16LE(offset + 30)
		const commentLength = bytes.readUInt16LE(offset + 32)
		const next = offset + 46 + nameLength + extraLength + commentLength
		if (next > end || commentLength !== 0) fail(`${label} has a truncated or commented ZIP entry`)
		let name
		try {
			name = UTF8.decode(bytes.subarray(offset + 46, offset + 46 + nameLength))
		} catch {
			fail(`${label} has a non-UTF-8 ZIP entry name`)
		}
		safeName(name)
		if (entries.has(name)) fail(`${label} has duplicate ZIP entry ${name}`)
		const entry = {
			name,
			flags: bytes.readUInt16LE(offset + 8),
			method: bytes.readUInt16LE(offset + 10),
			crc: bytes.readUInt32LE(offset + 16),
			compressed: bytes.readUInt32LE(offset + 20),
			uncompressed: bytes.readUInt32LE(offset + 24),
			local: bytes.readUInt32LE(offset + 42),
		}
		const unixMode = bytes.readUInt32LE(offset + 38) >>> 16
		if (entry.flags & ~(0x0008 | 0x0800) || ![0, 8].includes(entry.method)
			|| entry.compressed === 0xffffffff || entry.uncompressed === 0xffffffff
			|| entry.uncompressed > MAX_ENTRY_BYTES || entry.local >= directoryStart
			|| (unixMode & 0xf000) === 0xa000) {
			fail(`${label} has an unsupported ZIP entry ${name}`)
		}
		total += entry.uncompressed
		if (total > MAX_TOTAL_BYTES) fail(`${label} exceeds its expanded size limit`)
		entries.set(name, entry)
		offset = next
	}
	if (offset !== end) fail(`${label} has an invalid ZIP directory length`)
	function read(name) {
		const entry = entries.get(name)
		if (!entry) fail(`${label} is missing ${name}`)
		if (entry.local + 30 > directoryStart || bytes.readUInt32LE(entry.local) !== 0x04034b50) {
			fail(`${label} has an invalid local header for ${name}`)
		}
		const localNameLength = bytes.readUInt16LE(entry.local + 26)
		const extraLength = bytes.readUInt16LE(entry.local + 28)
		const dataStart = entry.local + 30 + localNameLength + extraLength
		let localName
		try {
			localName = UTF8.decode(bytes.subarray(entry.local + 30, entry.local + 30 + localNameLength))
		} catch {
			fail(`${label} has a non-UTF-8 local name for ${name}`)
		}
		if (localName !== name || bytes.readUInt16LE(entry.local + 6) !== entry.flags
			|| bytes.readUInt16LE(entry.local + 8) !== entry.method
			|| dataStart + entry.compressed > directoryStart) {
			fail(`${label} has an invalid ZIP payload for ${name}`)
		}
		let data
		try {
			const compressed = bytes.subarray(dataStart, dataStart + entry.compressed)
			data = entry.method === 0 ? compressed
				: inflateRawSync(compressed, { maxOutputLength: MAX_ENTRY_BYTES })
		} catch {
			fail(`${label} cannot decompress ${name}`)
		}
		if (data.length !== entry.uncompressed || crc32(data) !== entry.crc) {
			fail(`${label} has corrupt data in ${name}`)
		}
		return data
	}
	return { entries, read }
}

function assetDigest(name, data, label) {
	if (!ASSET_SUFFIXES.some((suffix) => name.endsWith(suffix))) {
		fail(`${label} has a disallowed asset ${name}`)
	}
	if (name.endsWith('.png')) {
		checkPng(data, label, name)
	} else if (name.endsWith('.ttf')) {
		if (data.length < 12 || data.readUInt32BE(0) !== 0x00010000) {
			fail(`${label} has an invalid font ${name}`)
		}
	} else {
		let content
		try {
			content = UTF8.decode(data)
		} catch {
			fail(`${label} has non-UTF-8 text in ${name}`)
		}
		if (content.includes('\0')) fail(`${label} has NUL text in ${name}`)
		if (name.endsWith('.json') || name.endsWith('.mcmeta')) {
			try {
				JSON.parse(content)
			} catch {
				fail(`${label} has invalid JSON in ${name}`)
			}
		}
		if (name.endsWith('.pem')) {
			try {
				new X509Certificate(content)
			} catch {
				fail(`${label} has an invalid certificate in ${name}`)
			}
		}
	}
	return sha256(data)
}

function inspectAgent(bytes) {
	const zip = parseZip(bytes, 'agent')
	const assets = new Map()
	for (const name of zip.entries.keys()) {
		const data = zip.read(name)
		if (name.endsWith('/')) {
			if (data.length) fail(`agent has nonempty directory ${name}`)
			continue
		}
		if (!data.length) fail(`agent has empty file ${name}`)
		if (name.startsWith(CLASS_ROOT) && name.endsWith('.class')) {
			if (data.length < 8 || data.readUInt32BE(0) !== 0xcafebabe) {
				fail(`agent has invalid class ${name}`)
			}
		} else if (name.startsWith(ASSET_ROOT)) {
			assets.set(name, assetDigest(name, data, 'agent'))
		} else if (name !== 'META-INF/MANIFEST.MF' && name !== 'hgn-client.mixins.json') {
			fail(`agent contains unapproved entry ${name}`)
		}
	}
	for (const name of REQUIRED_CLASSES) zip.read(name)
	for (const name of REQUIRED_ASSETS) {
		if (!assets.has(name)) fail(`agent is missing asset ${name}`)
	}
	const manifest = UTF8.decode(zip.read('META-INF/MANIFEST.MF'))
	if (!/^Premain-Class: online\.horizongate\.hgn\.runtime\.HgnRuntimeAgent\r?$/m.test(manifest)) {
		fail('agent manifest has no pinned Premain-Class')
	}
	let mixins
	try {
		mixins = JSON.parse(UTF8.decode(zip.read('hgn-client.mixins.json')))
	} catch {
		fail('agent mixin configuration is invalid JSON')
	}
	if (mixins?.package !== 'online.horizongate.hgn.mixin'
		|| !Array.isArray(mixins.client) || !mixins.client.length
		|| mixins.client.some((name) => typeof name !== 'string'
			|| !zip.entries.has(`online/horizongate/hgn/mixin/${name}.class`))) {
		fail('agent is missing a declared client mixin')
	}
	return assets
}

function inspectPack(bytes) {
	const zip = parseZip(bytes, 'resource pack')
	const assets = new Map()
	for (const name of zip.entries.keys()) {
		const data = zip.read(name)
		if (name.endsWith('/')) {
			if (data.length) fail(`resource pack has nonempty directory ${name}`)
			continue
		}
		if (!data.length) fail(`resource pack has empty file ${name}`)
		if (name.startsWith(ASSET_ROOT)) {
			assets.set(name, assetDigest(name, data, 'resource pack'))
		} else if (name !== 'pack.mcmeta') {
			fail(`resource pack contains unapproved entry ${name}`)
		}
	}
	let metadata
	try {
		metadata = JSON.parse(UTF8.decode(zip.read('pack.mcmeta')))
	} catch {
		fail('resource pack has invalid pack.mcmeta JSON')
	}
	if (metadata?.pack?.pack_format !== PACK_FORMAT
		|| typeof metadata.pack.description !== 'string' || !metadata.pack.description.trim()) {
		fail(`resource pack must declare pack_format ${PACK_FORMAT}`)
	}
	for (const name of REQUIRED_ASSETS) {
		if (!assets.has(name)) fail(`resource pack is missing asset ${name}`)
	}
	let font
	try {
		font = JSON.parse(UTF8.decode(zip.read('assets/hgn-client/font/ui.json')))
	} catch {
		fail('resource pack has invalid HGN UI font JSON')
	}
	if (!Array.isArray(font?.providers) || !font.providers.some((provider) =>
		provider?.type === 'ttf' && provider.file === 'hgn-client:inter_regular.ttf')) {
		fail('resource pack UI font does not reference its bundled TrueType font')
	}
	let heading
	try {
		heading = JSON.parse(UTF8.decode(zip.read('assets/hgn-client/font/heading.json')))
	} catch {
		fail('resource pack has invalid HGN heading font JSON')
	}
	if (!Array.isArray(heading?.providers) || !heading.providers.some((provider) =>
		provider?.type === 'ttf' && provider.file === 'hgn-client:rajdhani_semibold.ttf')) {
		fail('resource pack heading font does not reference its bundled TrueType font')
	}
	return assets
}

function sameAssets(agentAssets, packAssets) {
	if (agentAssets.size !== packAssets.size || [...agentAssets].some(([name, hash]) =>
		packAssets.get(name) !== hash)) {
		fail('agent and resource pack contain different HGN assets')
	}
}

export function inspectRuntimePair(agentBytes, packBytes, sourceAssets) {
	const agentAssets = inspectAgent(agentBytes)
	const packAssets = inspectPack(packBytes)
	sameAssets(agentAssets, packAssets)
	if (sourceAssets !== undefined) sameAssets(sourceAssets, packAssets)
	return { assetCount: packAssets.size }
}

export function sourceAssetDigests(root) {
	const assets = new Map()
	function visit(directory) {
		for (const entry of readdirSync(directory, { withFileTypes: true })) {
			const file = join(directory, entry.name)
			if (entry.isDirectory()) visit(file)
			else if (entry.isFile()) {
				const name = ASSET_ROOT + relative(root, file).split(sep).join('/')
				assets.set(name, assetDigest(name, readFileSync(file), 'source tree'))
			} else fail(`source asset is not a regular file: ${file}`)
		}
	}
	visit(root)
	if (!assets.size) fail('source asset tree is empty')
	return assets
}

export function createProof(clientVersion, releaseName, agentBytes, packBytes, sourceAssets) {
	artifactNames(clientVersion)
	if (!validReleaseName(clientVersion, releaseName)) {
		fail('releaseName is invalid')
	}
	inspectRuntimePair(agentBytes, packBytes, sourceAssets)
	const names = artifactNames(clientVersion)
	return {
		schema: 1,
		clientVersion,
		releaseName,
		minecraft: MINECRAFT,
		loader: LOADER,
		loaderVersion: LOADER_VERSION,
		agent: { file: names.agent, size: agentBytes.length, sha256: sha256(agentBytes) },
		resourcePack: { file: names.resourcePack, size: packBytes.length, sha256: sha256(packBytes) },
	}
}

export function verifyProof(proof, agentBytes, packBytes) {
	if (!proof || typeof proof !== 'object' || Array.isArray(proof)
		|| Object.keys(proof).sort().join(',') !== 'agent,clientVersion,loader,loaderVersion,minecraft,releaseName,resourcePack,schema'
		|| proof.schema !== 1 || proof.minecraft !== MINECRAFT
		|| proof.loader !== LOADER || proof.loaderVersion !== LOADER_VERSION) {
		fail('proof schema or pinned target is invalid')
	}
	const names = artifactNames(proof.clientVersion)
	if (!validReleaseName(proof.clientVersion, proof.releaseName)) {
		fail('proof releaseName is invalid')
	}
	for (const [field, expected, bytes] of [
		['agent', names.agent, agentBytes],
		['resourcePack', names.resourcePack, packBytes],
	]) {
		const item = proof[field]
		if (!item || typeof item !== 'object' || Array.isArray(item)
			|| Object.keys(item).sort().join(',') !== 'file,sha256,size'
			|| item.file !== expected || !Number.isSafeInteger(item.size) || item.size !== bytes.length
			|| typeof item.sha256 !== 'string' || !/^[0-9a-f]{64}$/.test(item.sha256)
			|| item.sha256 !== sha256(bytes)) {
			fail(`${field} does not match its pinned name, size and SHA-256`)
		}
	}
	return inspectRuntimePair(agentBytes, packBytes)
}

function readArchive(file) {
	if (statSync(file).size > MAX_ARCHIVE_BYTES) fail(`${file} exceeds the archive size limit`)
	return readFileSync(file)
}

function run(args) {
	if (args[0] === 'create' && args.length === 7) {
		const [, agentPath, packPath, proofPath, clientVersion, releaseName, sourceRoot] = args
		const names = artifactNames(clientVersion)
		const folder = dirname(resolve(proofPath))
		if (dirname(resolve(agentPath)) !== folder || dirname(resolve(packPath)) !== folder
			|| basename(agentPath) !== names.agent || basename(packPath) !== names.resourcePack
			|| basename(proofPath) !== names.proof) {
			fail('artifacts must use pinned filenames in one directory')
		}
		const proof = createProof(clientVersion, releaseName,
			readArchive(agentPath), readArchive(packPath), sourceAssetDigests(resolve(sourceRoot)))
		const temporary = `${proofPath}.tmp`
		writeFileSync(temporary, `${JSON.stringify(proof, null, '\t')}\n`, { flag: 'w' })
		renameSync(temporary, proofPath)
		console.log(`Created non-shipping HGN runtime proof: ${proofPath}`)
		return
	}
	if (args[0] === 'verify' && args.length === 2) {
		const proofPath = resolve(args[1])
		let proof
		try {
			proof = JSON.parse(UTF8.decode(readFileSync(proofPath)))
		} catch {
			fail('proof is missing or invalid JSON')
		}
		const names = artifactNames(proof.clientVersion)
		if (basename(proofPath) !== names.proof) fail('proof filename is not pinned')
		verifyProof(proof, readArchive(join(dirname(proofPath), names.agent)),
			readArchive(join(dirname(proofPath), names.resourcePack)))
		console.log(`Verified self-consistent HGN runtime proof: ${proofPath}`)
		return
	}
	fail('usage: node runtime-pair.mjs create AGENT PACK PROOF CLIENT_VERSION RELEASE_NAME SOURCE_ASSET_ROOT | verify PROOF')
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	try {
		run(process.argv.slice(2))
	} catch (error) {
		console.error(error.message)
		process.exitCode = 1
	}
}
