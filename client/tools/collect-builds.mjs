/**
 * Collects the per-target client jars into a publishable tree.
 *
 * The launcher downloads these at instance creation rather than bundling them,
 * so this produces the layout served from the public builds repository:
 *
 *   releases/<release-name>/manifest.json
 *   releases/<release-name>/jars/hgn-client-<minecraft>-<loader>.jar
 *
 * Usage: node client/tools/collect-builds.mjs <output-dir>
 */
import { createHash } from 'node:crypto'
import {
	copyFileSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, renameSync,
	writeFileSync,
} from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { assertClientJarPayload } from './jar-payload.mjs'

const clientDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const outDir = resolve(process.argv[2] ?? join(clientDir, 'build', 'publish'))
const loaders = ['fabric', 'forge', 'neoforge']
// Release builds can be staged off the project drive. When set, this directory
// contains one <loader>/libs folder per Gradle target; normal local builds keep
// using client/<loader>/build/libs.
const buildRoot = process.env.HGN_BUILD_LIBS_ROOT
  ? resolve(process.env.HGN_BUILD_LIBS_ROOT)
  : null

// The same source the Gradle build stamps into the jars, so the two cannot drift.
const launcherPackage = join(clientDir, '..', 'apps', 'app-frontend', 'package.json')
const launcherMetadata = JSON.parse(readFileSync(launcherPackage, 'utf8'))
const clientVersion = launcherMetadata.version
const releaseName = launcherMetadata.hgnReleaseName
const releaseChannel = launcherMetadata.hgnReleaseChannel
const supportedMinecraftVersions = launcherMetadata.hgnClientMinecraftVersions
const unavailableTargets = new Set(launcherMetadata.hgnClientUnavailableTargets ?? [])
if (typeof clientVersion !== 'string' || clientVersion.length === 0) {
	throw new Error('launcher package must define a non-empty machine version')
}
if (typeof releaseName !== 'string' || releaseName.length === 0) {
	throw new Error('launcher package must define a non-empty hgnReleaseName')
}
if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(releaseName)) {
	throw new Error('launcher hgnReleaseName is not safe for a release directory')
}
const releaseRoot = join(outDir, 'releases', releaseName)
const alphaVersion = clientVersion.match(/^(\d+\.\d+\.\d+)-alpha\.1$/)
const betaVersion = clientVersion.match(/^(\d+\.\d+\.\d+)-beta\.(0|[1-9]\d*)$/)
const stableVersion = clientVersion.match(/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/)
if (clientVersion.includes('-alpha') && !alphaVersion) {
	throw new Error(`HGN Alpha machine version ${clientVersion} must use X.Y.Z-alpha.1`)
}
if (clientVersion.includes('-beta') && !betaVersion) {
	throw new Error(`HGN Beta machine version ${clientVersion} must use X.Y.Z-beta.N`)
}
if (!Array.isArray(supportedMinecraftVersions) || supportedMinecraftVersions.length === 0
	|| supportedMinecraftVersions.some((version) =>
		typeof version !== 'string' || !/^[0-9][0-9A-Za-z._+-]*$/.test(version))
	|| new Set(supportedMinecraftVersions).size !== supportedMinecraftVersions.length) {
	throw new Error('launcher hgnClientMinecraftVersions must contain unique safe versions')
}
for (const target of unavailableTargets) {
	const [minecraft, loader, extra] = String(target).split(':')
	if (extra !== undefined || !supportedMinecraftVersions.includes(minecraft)
		|| !loaders.includes(loader)) {
		throw new Error(`launcher contains invalid unavailable client target ${target}`)
	}
}
const expectedTargets = supportedMinecraftVersions.flatMap((minecraft) =>
	loaders
		.map((loader) => `${minecraft}:${loader}`)
		.filter((target) => !unavailableTargets.has(target)),
)
if (alphaVersion) {
	const expectedReleaseName = `ALPHA-V${alphaVersion[1]}`
	if (releaseName !== expectedReleaseName) {
		throw new Error(
			`HGN release name ${releaseName} does not match ${clientVersion}; expected ${expectedReleaseName}`,
		)
	}
	if (releaseChannel !== 'alpha') {
		throw new Error(`HGN release channel ${releaseChannel} does not match ${clientVersion}; expected alpha`)
	}
}
if (betaVersion) {
	const expectedReleaseName = `v${clientVersion}`
	if (releaseName !== expectedReleaseName) {
		throw new Error(
			`HGN release name ${releaseName} does not match ${clientVersion}; expected ${expectedReleaseName}`,
		)
	}
	if (releaseChannel !== 'beta') {
		throw new Error(`HGN release channel ${releaseChannel} does not match ${clientVersion}; expected beta`)
	}
}
if (stableVersion) {
	const expectedReleaseName = `v${clientVersion}`
	if (releaseName !== expectedReleaseName || releaseChannel !== 'release') {
		throw new Error(
			`HGN stable release ${releaseName} (${releaseChannel}) must be ${expectedReleaseName} (release)`,
		)
	}
}
function stageArtifact(source, destination) {
	// The published tree must not share writable file data with Gradle output.
	copyFileSync(source, destination)
}

const builds = []
for (const loader of loaders) {
	const libs = buildRoot
		? join(buildRoot, loader, 'libs')
		: join(clientDir, loader, 'build', 'libs')
	let entries
	try {
		entries = readdirSync(libs)
	} catch {
		continue
	}
	for (const entry of entries) {
		if (!entry.endsWith('.jar') || entry.endsWith('-sources.jar') || entry.endsWith('-dev.jar')) {
			continue
		}
		// hgn-client-<minecraft>-<loader>-<clientVersion>+<minecraft>.jar
		const match = entry.match(/^hgn-client-(.+)-([a-z]+)-(.+)\+(.+)\.jar$/)
		if (!match || match[2] !== loader) continue

		const minecraft = match[1]
		if (!supportedMinecraftVersions.includes(minecraft)
			|| unavailableTargets.has(`${minecraft}:${loader}`)) continue
		// A jar left over from an earlier client version would otherwise be
		// collected alongside the current one, giving two entries per target.
		if (match[3] !== clientVersion) continue
		if (match[4] !== minecraft) {
			throw new Error(`${entry} has mismatched Minecraft version suffix +${match[4]}`)
		}
		const name = `hgn-client-${minecraft}-${loader}.jar`
		const bytes = readFileSync(join(libs, entry))
		assertClientJarPayload(bytes, loader, minecraft, entry, clientVersion, releaseName)
		builds.push({
			minecraft,
			loader,
			file: `releases/${releaseName}/jars/${name}`,
			name,
			size: bytes.length,
			sha256: createHash('sha256').update(bytes).digest('hex'),
			source: join(libs, entry),
		})
	}
}

builds.sort((a, b) => a.minecraft.localeCompare(b.minecraft) || a.loader.localeCompare(b.loader))

const uniqueTargets = new Set(builds.map((build) => `${build.minecraft}:${build.loader}`))
if (uniqueTargets.size !== builds.length) {
	throw new Error('collected duplicate Minecraft/loader targets')
}
const missingTargets = expectedTargets.filter((target) => !uniqueTargets.has(target))
const unexpectedTargets = [...uniqueTargets].filter((target) => !expectedTargets.includes(target))
if (builds.length !== expectedTargets.length || missingTargets.length > 0
	|| unexpectedTargets.length > 0) {
	throw new Error(
		`expected exactly ${expectedTargets.length} client builds; collected ${builds.length}, ` +
			`missing ${missingTargets.join(', ') || 'none'}, ` +
			`unexpected ${unexpectedTargets.join(', ') || 'none'}`,
	)
}

const manifestBuilds = builds.map(({ source: _source, name: _name, ...build }) => build)
const manifestText =
	JSON.stringify({ schema: 1, clientVersion, releaseName, builds: manifestBuilds }, null, '\t') + '\n'

// A release directory is immutable once created. An identical collection is
// idempotent; a byte or manifest mismatch fails instead of rewriting artifacts
// already consumed by an older launcher.
if (existsSync(releaseRoot)) {
	const manifestPath = join(releaseRoot, 'manifest.json')
	if (!existsSync(manifestPath) || readFileSync(manifestPath, 'utf8') !== manifestText) {
		throw new Error(`refusing to replace existing client release ${releaseName}`)
	}
	for (const build of builds) {
		const publishedPath = join(releaseRoot, 'jars', build.name)
		if (!existsSync(publishedPath)) {
			throw new Error(`existing client release ${releaseName} is missing ${build.name}`)
		}
		const published = readFileSync(publishedPath)
		if (published.length !== build.size
			|| createHash('sha256').update(published).digest('hex') !== build.sha256) {
			throw new Error(`refusing to replace mismatched ${releaseName}/${build.name}`)
		}
	}
} else {
	mkdirSync(dirname(releaseRoot), { recursive: true })
	const stagingRoot = mkdtempSync(join(dirname(releaseRoot), `.staging-${releaseName}-`))
	mkdirSync(join(stagingRoot, 'jars'), { recursive: true })
	for (const build of builds) {
		const stagedPath = join(stagingRoot, 'jars', build.name)
		stageArtifact(build.source, stagedPath)
		const staged = readFileSync(stagedPath)
		if (staged.length !== build.size
			|| createHash('sha256').update(staged).digest('hex') !== build.sha256) {
			throw new Error(`staged client build changed while copying ${build.name}`)
		}
	}
	writeFileSync(join(stagingRoot, 'manifest.json'), manifestText)
	if (existsSync(releaseRoot)) {
		throw new Error(`refusing to replace existing client release ${releaseName}`)
	}
	// Same-volume rename publishes the fully validated directory as one unit.
	renameSync(stagingRoot, releaseRoot)
}

console.log(
	`collected ${builds.length} exact client-matrix builds for ${releaseName} (${clientVersion}) into ${releaseRoot}`,
)
