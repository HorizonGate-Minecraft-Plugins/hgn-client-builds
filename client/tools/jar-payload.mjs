import { inflateRawSync, inflateSync } from 'node:zlib'

const MAX_ENTRY_BYTES = 64 * 1024 * 1024
const MAX_IMAGE_BYTES = 128 * 1024 * 1024
const PNG_SIGNATURE = Buffer.from('89504e470d0a1a0a', 'hex')
const REQUIRED_TEXTURES = [
	'assets/hgn-client/textures/gui/dashboard_welcome_city.png',
	'assets/hgn-client/textures/gui/hgn_gateway_reference_clean.png',
	'assets/hgn-client/textures/gui/hgn_gateway_twilight.png',
	'assets/hgn-client/textures/gui/servers/server-hub.png',
	'assets/hgn-client/textures/gui/theme_v2/text_field.png',
]
const REQUIRED_ENTRIES = {
	fabric: ['fabric.mod.json', 'online/horizongate/hgn/fabric/HgnFabric.class'],
	forge: ['META-INF/mods.toml', 'online/horizongate/hgn/forge/HgnForge.class'],
	neoforge: ['META-INF/neoforge.mods.toml', 'online/horizongate/hgn/neoforge/HgnNeoForge.class'],
}
const FORBIDDEN_PREFIXES = [
	'online/horizongate/hgn/runtime/',
	'assets/hgn_cosmetics/models/',
	'assets/hgn_cosmetics/textures/',
	'assets/hgn-client/font/rajdhani_medium.ttf',
]

function fail(jar, message) {
	throw new Error(`${jar}: ${message}`)
}

export function crc32(bytes) {
	let crc = 0xffffffff
	for (const byte of bytes) {
		crc ^= byte
		for (let bit = 0; bit < 8; bit++) {
			crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0)
		}
	}
	return (crc ^ 0xffffffff) >>> 0
}

function zipEntries(bytes, jar) {
	let end = -1
	for (let offset = bytes.length - 22; offset >= Math.max(0, bytes.length - 65557); offset--) {
		if (bytes.readUInt32LE(offset) === 0x06054b50
			&& offset + 22 + bytes.readUInt16LE(offset + 20) === bytes.length) {
			end = offset
			break
		}
	}
	if (end < 0 || bytes.readUInt16LE(end + 4) !== 0 || bytes.readUInt16LE(end + 6) !== 0
		|| bytes.readUInt16LE(end + 8) !== bytes.readUInt16LE(end + 10)) {
		fail(jar, 'invalid ZIP end record')
	}
	const count = bytes.readUInt16LE(end + 10)
	const size = bytes.readUInt32LE(end + 12)
	const start = bytes.readUInt32LE(end + 16)
	if (count === 0xffff || size === 0xffffffff || start === 0xffffffff
		|| start + size > end) fail(jar, 'unsupported or invalid ZIP central directory')
	const entries = new Map()
	let offset = start
	for (let index = 0; index < count; index++) {
		if (offset + 46 > start + size || bytes.readUInt32LE(offset) !== 0x02014b50) {
			fail(jar, 'invalid ZIP central directory entry')
		}
		const nameLength = bytes.readUInt16LE(offset + 28)
		const extraLength = bytes.readUInt16LE(offset + 30)
		const commentLength = bytes.readUInt16LE(offset + 32)
		const next = offset + 46 + nameLength + extraLength + commentLength
		if (next > start + size) fail(jar, 'truncated ZIP central directory entry')
		const name = bytes.toString('utf8', offset + 46, offset + 46 + nameLength)
		if (entries.has(name)) fail(jar, `duplicate jar entry ${name}`)
		entries.set(name, {
			name,
			flags: bytes.readUInt16LE(offset + 8),
			method: bytes.readUInt16LE(offset + 10),
			crc: bytes.readUInt32LE(offset + 16),
			compressed: bytes.readUInt32LE(offset + 20),
			uncompressed: bytes.readUInt32LE(offset + 24),
			local: bytes.readUInt32LE(offset + 42),
		})
		offset = next
	}
	if (offset !== start + size) fail(jar, 'invalid ZIP central directory size')
	return { entries, start }
}

function readEntry(bytes, zip, name, jar) {
	const entry = zip.entries.get(name)
	if (!entry) fail(jar, `missing required jar entry ${name}`)
	if (entry.flags & 1 || entry.compressed === 0xffffffff || entry.uncompressed === 0xffffffff
		|| entry.uncompressed > MAX_ENTRY_BYTES || ![0, 8].includes(entry.method)) {
		fail(jar, `unsupported jar entry ${name}`)
	}
	if (entry.local + 30 > zip.start || bytes.readUInt32LE(entry.local) !== 0x04034b50) {
		fail(jar, `invalid local ZIP header for ${name}`)
	}
	const nameLength = bytes.readUInt16LE(entry.local + 26)
	const extraLength = bytes.readUInt16LE(entry.local + 28)
	const dataStart = entry.local + 30 + nameLength + extraLength
	if (bytes.toString('utf8', entry.local + 30, entry.local + 30 + nameLength) !== name
		|| dataStart + entry.compressed > zip.start) fail(jar, `invalid ZIP payload for ${name}`)
	const compressed = bytes.subarray(dataStart, dataStart + entry.compressed)
	let data
	try {
		data = entry.method === 0 ? compressed
			: inflateRawSync(compressed, { maxOutputLength: MAX_ENTRY_BYTES })
	} catch (error) {
		fail(jar, `cannot decompress jar entry ${name}: ${error.message}`)
	}
	if (data.length !== entry.uncompressed || crc32(data) !== entry.crc) {
		fail(jar, `corrupt jar entry ${name}`)
	}
	return data
}

function readBuildIdentity(data, jar) {
	const invalid = () => fail(jar, 'invalid HGN build identity class')
	let offset = 0
	const u1 = () => {
		if (offset + 1 > data.length) invalid()
		return data[offset++]
	}
	const u2 = () => {
		if (offset + 2 > data.length) invalid()
		const value = data.readUInt16BE(offset)
		offset += 2
		return value
	}
	const u4 = () => {
		if (offset + 4 > data.length) invalid()
		const value = data.readUInt32BE(offset)
		offset += 4
		return value
	}
	const skip = (length) => {
		if (offset + length > data.length) invalid()
		offset += length
	}
	if (u4() !== 0xcafebabe) invalid()
	skip(4)
	const constants = [null]
	const count = u2()
	for (let index = 1; index < count; index++) {
		const tag = u1()
		if (tag === 1) {
			const length = u2()
			if (offset + length > data.length) invalid()
			constants[index] = { tag, value: data.toString('utf8', offset, offset + length) }
			skip(length)
		} else if (tag === 8) {
			constants[index] = { tag, value: u2() }
		} else if ([7, 16, 19, 20].includes(tag)) {
			skip(2)
		} else if ([3, 4, 9, 10, 11, 12, 17, 18].includes(tag)) {
			skip(4)
		} else if (tag === 15) {
			skip(3)
		} else if (tag === 5 || tag === 6) {
			skip(8)
			index++
		} else {
			invalid()
		}
	}
	const utf8 = (index) => constants[index]?.tag === 1 ? constants[index].value : invalid()
	const string = (index) => constants[index]?.tag === 8
		? utf8(constants[index].value) : invalid()
	skip(6)
	const interfaces = u2()
	skip(interfaces * 2)
	const fields = u2()
	const identity = new Map()
	for (let index = 0; index < fields; index++) {
		const access = u2()
		const name = utf8(u2())
		const descriptor = utf8(u2())
		const attributes = u2()
		for (let attribute = 0; attribute < attributes; attribute++) {
			const attributeName = utf8(u2())
			const length = u4()
			if (attributeName === 'ConstantValue' && descriptor === 'Ljava/lang/String;'
				&& (access & 0x0019) === 0x0019 && length === 2) {
				if (identity.has(name)) invalid()
				identity.set(name, string(u2()))
			} else {
				skip(length)
			}
		}
	}
	return identity
}

export function checkPng(data, jar, name) {
	if (data.length < 45 || !data.subarray(0, 8).equals(PNG_SIGNATURE)) {
		fail(jar, `invalid PNG texture ${name}`)
	}
	let offset = 8
	let width = 0
	let height = 0
	let bitDepth = 0
	let colorType = 0
	let interlace = 0
	let ended = false
	const imageData = []
	while (offset + 12 <= data.length) {
		const length = data.readUInt32BE(offset)
		const next = offset + 12 + length
		if (next > data.length) fail(jar, `truncated PNG texture ${name}`)
		const type = data.toString('ascii', offset + 4, offset + 8)
		const chunk = data.subarray(offset + 4, offset + 8 + length)
		if (crc32(chunk) !== data.readUInt32BE(offset + 8 + length)) {
			fail(jar, `corrupt PNG texture ${name}`)
		}
		if (offset === 8) {
			if (type !== 'IHDR' || length !== 13) fail(jar, `invalid PNG header ${name}`)
			width = data.readUInt32BE(offset + 8)
			height = data.readUInt32BE(offset + 12)
			bitDepth = data[offset + 16]
			colorType = data[offset + 17]
			interlace = data[offset + 20]
			if (width === 0 || height === 0 || width * height > 16_777_216
				|| data[offset + 18] !== 0 || data[offset + 19] !== 0 || interlace > 1) {
				fail(jar, `invalid PNG dimensions or format ${name}`)
			}
		}
		if (type === 'IDAT') imageData.push(data.subarray(offset + 8, offset + 8 + length))
		if (type === 'IEND') {
			if (length !== 0 || next !== data.length) fail(jar, `invalid PNG ending ${name}`)
			ended = true
			break
		}
		offset = next
	}
	if (!ended || imageData.length === 0) fail(jar, `incomplete PNG texture ${name}`)
	let pixels
	try {
		pixels = inflateSync(Buffer.concat(imageData), { maxOutputLength: MAX_IMAGE_BYTES })
	} catch (error) {
		fail(jar, `cannot decode PNG texture ${name}: ${error.message}`)
	}
	const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colorType]
	if (!channels || ![1, 2, 4, 8, 16].includes(bitDepth) || pixels.length === 0) {
		fail(jar, `invalid PNG pixels ${name}`)
	}
	if (interlace === 0 && pixels.length !== (Math.ceil(width * channels * bitDepth / 8) + 1) * height) {
		fail(jar, `invalid PNG pixel length ${name}`)
	}
	if (name.endsWith('/server-hub.png') && (width !== 1254 || height !== 1254)) {
		fail(jar, `unexpected HGN Hub texture dimensions ${width}x${height}`)
	}
	if (name.endsWith('/hgn_gateway_reference_clean.png') && (width !== 1672 || height !== 941)) {
		fail(jar, `unexpected HGN gateway scene dimensions ${width}x${height}`)
	}
}

export function assertClientJarPayload(bytes, loader, minecraft, jar, clientVersion, releaseName) {
	const zip = zipEntries(bytes, jar)
	for (const name of zip.entries.keys()) {
		if (FORBIDDEN_PREFIXES.some((prefix) => name.startsWith(prefix))) {
			fail(jar, `contains forbidden public jar entry ${name}`)
		}
	}
	for (const name of ['online/horizongate/hgn/HgnBuild.class', ...REQUIRED_ENTRIES[loader]]) {
		const data = readEntry(bytes, zip, name, jar)
		if (name.endsWith('.class') && (data.length < 8 || data.readUInt32BE(0) !== 0xcafebabe)) {
			fail(jar, `invalid class entry ${name}`)
		}
	}
	const identity = readBuildIdentity(
		readEntry(bytes, zip, 'online/horizongate/hgn/HgnBuild.class', jar), jar,
	)
	for (const [field, expected] of Object.entries({
		CLIENT_VERSION: clientVersion,
		RELEASE_NAME: releaseName,
		MINECRAFT_VERSION: minecraft,
		LOADER: loader,
	})) {
		if (identity.get(field) !== expected) {
			fail(jar, `HGN build identity ${field} does not match ${expected}`)
		}
	}
	if (loader === 'fabric') {
		const modules = ['fabric-api-base', 'fabric-networking-api-v1', 'fabric-resource-loader-v0']
		if (['1.21.9', '1.21.10', '1.21.11'].includes(minecraft)) {
			modules.push('fabric-resource-loader-v1')
		}
		for (const module of modules) {
			const name = [...zip.entries.keys()].find((path) => path.startsWith(`META-INF/jars/${module}-`))
			if (!name) fail(jar, `missing required jar entry META-INF/jars/${module}-`)
			const nested = readEntry(bytes, zip, name, jar)
			if (nested.length < 4 || nested.readUInt32LE(0) !== 0x04034b50) {
				fail(jar, `invalid nested Fabric module ${name}`)
			}
		}
	}
	const fontDefinition = readEntry(bytes, zip, 'assets/hgn-client/font/ui.json', jar)
	let font
	try {
		font = JSON.parse(fontDefinition.toString('utf8'))
	} catch {
		fail(jar, 'invalid HGN UI font definition')
	}
	if (!font || !Array.isArray(font.providers) || !font.providers.some((provider) =>
		provider?.type === 'ttf' && provider.file === 'hgn-client:inter_regular.ttf')) {
		fail(jar, 'HGN UI font definition does not reference its bundled TrueType font')
	}
	const ttf = readEntry(bytes, zip, 'assets/hgn-client/font/inter_regular.ttf', jar)
	if (ttf.length < 12 || ttf.readUInt32BE(0) !== 0x00010000) {
		fail(jar, 'invalid HGN TrueType font signature')
	}
	const headingDefinition = readEntry(bytes, zip, 'assets/hgn-client/font/heading.json', jar)
	let heading
	try {
		heading = JSON.parse(headingDefinition.toString('utf8'))
	} catch {
		fail(jar, 'invalid HGN heading font definition')
	}
	if (!heading || !Array.isArray(heading.providers) || !heading.providers.some((provider) =>
		provider?.type === 'ttf' && provider.file === 'hgn-client:rajdhani_semibold.ttf')) {
		fail(jar, 'HGN heading font definition does not reference its bundled TrueType font')
	}
	const headingTtf = readEntry(bytes, zip, 'assets/hgn-client/font/rajdhani_semibold.ttf', jar)
	if (headingTtf.length < 12 || headingTtf.readUInt32BE(0) !== 0x00010000) {
		fail(jar, 'invalid HGN heading TrueType font signature')
	}
	for (const name of REQUIRED_TEXTURES) {
		readEntry(bytes, zip, name, jar)
	}
	for (const name of zip.entries.keys()) {
		if (name.startsWith('assets/hgn-client/textures/gui/') && name.endsWith('.png')) {
			checkPng(readEntry(bytes, zip, name, jar), jar, name)
		}
	}
}
