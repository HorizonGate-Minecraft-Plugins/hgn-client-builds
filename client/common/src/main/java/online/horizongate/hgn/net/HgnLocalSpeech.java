package online.horizongate.hgn.net;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import online.horizongate.hgn.HgnClient;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Optional local Windows speech; subtitles and Minecraft audio remain available without SAPI. */
final class HgnLocalSpeech {
	private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30,
		TimeUnit.SECONDS, new ArrayBlockingQueue<>(6), runnable -> {
			Thread thread = new Thread(runnable, "hgn-local-boss-speech");
			thread.setDaemon(true);
			return thread;
		}, new ThreadPoolExecutor.DiscardPolicy());
	private static final Map<String, byte[]> CACHE = new LinkedHashMap<>();
	private static final int MAX_CACHE_ENTRIES = 12;
	private static final int MAX_WAV_BYTES = 2_000_000;
	private static volatile long generation;

	private HgnLocalSpeech() {
	}

	static void speak(HgnBossEvents.Voice voice, Minecraft minecraft) {
		double distance = Math.sqrt(minecraft.player.distanceToSqr(
			voice.origin().x(), voice.origin().y(), voice.origin().z()));
		double gain = voice.volume() * Math.max(0, 1 - distance / voice.range());
		if (gain <= 0) return;
		double dx = voice.origin().x() - minecraft.player.getX();
		double dz = voice.origin().z() - minecraft.player.getZ();
		double yaw = Math.toRadians(minecraft.player.getYRot());
		float pan = (float) Math.clamp((dx * Math.cos(yaw) + dz * Math.sin(yaw))
			/ Math.max(1, distance), -1, 1);
		long started = generation;
		WORKER.execute(() -> {
			if (generation != started) return;
			try {
				byte[] wave = cachedWave(voice);
				if (wave == null || generation != started) {
					fallback(voice, gain, minecraft, started);
					return;
				}
				play(wave, voice.pitch(), gain, pan);
			} catch (Exception exception) {
				HgnClient.LOGGER.debug("Local boss speech unavailable; using Minecraft audio", exception);
				fallback(voice, gain, minecraft, started);
			}
		});
	}

	static void reset() {
		generation++;
		WORKER.getQueue().clear();
	}

	private static byte[] cachedWave(HgnBossEvents.Voice voice) {
		String key = voice.profile() + '\n' + voice.language() + '\n' + voice.speed() + '\n' + voice.text();
		synchronized (CACHE) {
			byte[] saved = CACHE.get(key);
			if (saved != null) return saved;
		}
		byte[] created = synthesize(voice);
		if (created != null) {
			synchronized (CACHE) {
				while (CACHE.size() >= MAX_CACHE_ENTRIES) CACHE.remove(CACHE.keySet().iterator().next());
				CACHE.put(key, created);
			}
		}
		return created;
	}

	private static byte[] synthesize(HgnBossEvents.Voice voice) {
		if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return null;
		Path wave = null;
		try {
			wave = Files.createTempFile("hgn-boss-voice-", ".wav");
			String encodedText = Base64.getEncoder().encodeToString(voice.text().getBytes(StandardCharsets.UTF_8));
			int rate = Math.clamp((int) Math.round((voice.speed() - 1) * 8
				+ presetRate(voice.profile()) + emotionRate(voice.emotion())), -8, 8);
			int voiceIndex = Math.floorMod(voice.profile().hashCode(), 2);
			if ("skeleton".equals(voice.profile())) voiceIndex = 0;
			if ("titan".equals(voice.profile())) voiceIndex = 1;
			String output = wave.toAbsolutePath().toString().replace("'", "''");
			String script = "Add-Type -AssemblyName System.Speech; "
				+ "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer; "
				+ "try {$vs=$s.GetInstalledVoices(); if($vs.Count -gt 0){$s.SelectVoice($vs["
				+ voiceIndex + " % $vs.Count].VoiceInfo.Name)}; $s.Rate=" + rate
				+ "; $s.SetOutputToWaveFile('" + output + "'); "
				+ "$t=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('" + encodedText + "')); "
				+ "$s.Speak($t)} finally {$s.Dispose()}";
			String encodedScript = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
			Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
				"-EncodedCommand", encodedScript).redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD).start();
			if (!process.waitFor(5, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return null;
			}
			if (process.exitValue() != 0 || Files.size(wave) > MAX_WAV_BYTES) return null;
			return Files.readAllBytes(wave);
		} catch (Exception exception) {
			return null;
		} finally {
			if (wave != null) {
				try { Files.deleteIfExists(wave); }
				catch (Exception ignored) { }
			}
		}
	}

	private static int presetRate(String profile) {
		return switch (profile) {
			case "skeleton" -> 2;
			case "zombie" -> -3;
			case "wither_skeleton", "wither skeleton" -> -4;
			case "titan" -> -6;
			case "dragon" -> -5;
			case "golden_boss", "golden boss" -> -1;
			default -> Math.floorMod(profile.hashCode(), 5) - 2;
		};
	}

	private static int emotionRate(String emotion) {
		return switch (emotion.toLowerCase()) {
			case "angry", "threatening", "excited" -> 1;
			case "sad", "ominous", "calm" -> -1;
			default -> 0;
		};
	}

	private static void play(byte[] wave, double pitch, double gain, float pan) throws Exception {
		try (AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wave))) {
			AudioFormat format = stream.getFormat();
			byte[] samples = stream.readAllBytes();
			AudioFormat shifted = new AudioFormat(format.getEncoding(),
				(float) (format.getSampleRate() * pitch), format.getSampleSizeInBits(),
				format.getChannels(), format.getFrameSize(), (float) (format.getFrameRate() * pitch),
				format.isBigEndian());
			try (Clip clip = AudioSystem.getClip()) {
				clip.open(shifted, samples, 0, samples.length);
				if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
					FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
					control.setValue(Math.max(control.getMinimum(), (float) (20 * Math.log10(Math.max(gain, 0.001)))));
				}
				if (clip.isControlSupported(FloatControl.Type.PAN)) {
					((FloatControl) clip.getControl(FloatControl.Type.PAN)).setValue(pan);
				}
				clip.start();
				long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
				while (clip.isRunning() && System.nanoTime() < timeout) Thread.sleep(20);
			}
		}
	}

	private static void fallback(HgnBossEvents.Voice voice, double gain, Minecraft minecraft, long started) {
		minecraft.execute(() -> {
			if (generation != started || minecraft.level == null) return;
			SoundEvent sound = switch (voice.profile()) {
				case "skeleton" -> SoundEvents.SKELETON_AMBIENT;
				case "zombie" -> SoundEvents.ZOMBIE_AMBIENT;
				case "wither_skeleton", "wither skeleton" -> SoundEvents.WITHER_SKELETON_AMBIENT;
				case "dragon" -> SoundEvents.ENDER_DRAGON_GROWL;
				default -> SoundEvents.WARDEN_ROAR;
			};
			minecraft.level.playLocalSound(voice.origin().x(), voice.origin().y(), voice.origin().z(),
				sound, SoundSource.HOSTILE, (float) gain, (float) voice.pitch(), false);
		});
	}
}
