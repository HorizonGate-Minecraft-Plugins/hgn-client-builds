package online.horizongate.hgn.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Non-shipping proof for an HGN-owned Java agent in a launcher-managed version.
 * It requires Fabric Loader 0.19.3 and a player-authorized Minecraft 1.21.4 install.
 * It transforms Fabric Knot in memory, but does not distribute or persist
 * modified Minecraft or Fabric binaries.
 */
public final class HgnRuntimeAgent {
	private static final String KNOT = "net/fabricmc/loader/impl/launch/knot/Knot";
	private static final String AGENT = "online/horizongate/hgn/runtime/HgnRuntimeAgent";
	private static final String MIXINS = "hgn-client.mixins.json";
	private static final AtomicBoolean INSTALLED = new AtomicBoolean();
	private static Path runtimeJar;

	private HgnRuntimeAgent() {
	}

	public static void premain(String arguments, Instrumentation instrumentation) throws Exception {
		if (arguments != null && !arguments.isBlank()) {
			throw new IllegalArgumentException("The HGN runtime agent does not accept arguments");
		}
		runtimeJar = Path.of(HgnRuntimeAgent.class.getProtectionDomain().getCodeSource()
			.getLocation().toURI()).toRealPath();
		verifyOwnArchive(runtimeJar);
		KnotTransformer transformer = new KnotTransformer();
		instrumentation.addTransformer(transformer);
		try {
			Class.forName(KNOT.replace('/', '.'), false, ClassLoader.getSystemClassLoader());
			if (!transformer.patched.get()) {
				throw new IllegalStateException("Pinned Fabric Knot class was already loaded or could not be patched");
			}
		} finally {
			instrumentation.removeTransformer(transformer);
		}
	}

	private static void verifyOwnArchive(Path jarPath) throws IOException {
		if (!Files.isRegularFile(jarPath) || !jarPath.getFileName().toString().endsWith(".jar")) {
			throw new IllegalStateException("The HGN runtime agent must launch from a managed JAR");
		}
		try (JarFile jar = new JarFile(jarPath.toFile())) {
			if (jar.getEntry("fabric.mod.json") != null || jar.getEntry("META-INF/mods.toml") != null ||
				jar.getEntry("META-INF/neoforge.mods.toml") != null ||
				jar.getEntry(MIXINS) == null ||
				jar.getEntry("online/horizongate/hgn/runtime/HgnRuntimeBootstrap.class") == null) {
				throw new IllegalStateException("The HGN runtime archive is incomplete or exposes mod registration");
			}
		}
	}

	public static void beforeTransform(Object knot) {
		if (!INSTALLED.compareAndSet(false, true)) {
			throw new IllegalStateException("The HGN runtime was attached more than once");
		}
		try {
			Class<?> loaderClass = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl");
			String version = (String) loaderClass.getField("VERSION").get(null);
			Object loader = loaderClass.getField("INSTANCE").get(null);
			Object provider = loaderClass.getMethod("getGameProvider").invoke(loader);
			String minecraft = (String) provider.getClass()
				.getMethod("getNormalizedGameVersion").invoke(provider);
			String side = knot.getClass().getMethod("getEnvironmentType").invoke(knot).toString();
			boolean legacyMod = (boolean) loaderClass.getMethod("isModLoaded", String.class)
				.invoke(loader, "hgn-client");
			if (!"0.19.3".equals(version) || !"1.21.4".equals(minecraft) ||
				!"CLIENT".equals(side) || legacyMod) {
				throw new IllegalStateException(
					"The HGN runtime requires Fabric Loader 0.19.3, Minecraft 1.21.4, and no legacy HGN mod");
			}
			knot.getClass().getMethod("addToClassPath", Path.class, String[].class)
				.invoke(knot, runtimeJar, new String[0]);
			try (InputStream config = (InputStream) knot.getClass()
				.getMethod("getResourceAsStream", String.class).invoke(knot, MIXINS)) {
				if (config == null) {
					throw new IllegalStateException("HGN mixin configuration is unavailable to the game loader");
				}
			}
			Class.forName("org.spongepowered.asm.mixin.Mixins")
				.getMethod("addConfiguration", String.class).invoke(null, MIXINS);
		} catch (ReflectiveOperationException | IOException failure) {
			throw new IllegalStateException("Could not attach the HGN runtime before game transforms", failure);
		}
	}

	public static void afterKnotInit(ClassLoader gameLoader) {
		if (!INSTALLED.get()) {
			throw new IllegalStateException("HGN mixins were not registered before game startup");
		}
		try {
			Class<?> bootstrap = Class.forName(
				"online.horizongate.hgn.runtime.HgnRuntimeBootstrap", true, gameLoader);
			if (bootstrap.getClassLoader() != gameLoader) {
				throw new IllegalStateException("HGN bootstrap did not load in the game class loader");
			}
			bootstrap.getMethod("initializeFabric").invoke(null);
		} catch (InvocationTargetException failure) {
			throw new IllegalStateException("HGN runtime initialization failed", failure.getCause());
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("HGN runtime entrypoint is unavailable", failure);
		}
	}

	private static final class KnotTransformer implements ClassFileTransformer {
		private final AtomicBoolean patched = new AtomicBoolean();

		@Override
		public byte[] transform(ClassLoader loader, String className, Class<?> redefining,
			ProtectionDomain domain, byte[] source) {
			if (!KNOT.equals(className)) return null;
			byte[] result = patchKnot(source);
			patched.set(result != null);
			return result;
		}
	}

	static byte[] patchKnot(byte[] source) {
		ClassReader reader = new ClassReader(source);
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		int[] hooks = new int[2];
		AtomicBoolean alreadyPatched = new AtomicBoolean();
		reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor,
					String signature, String[] exceptions) {
					MethodVisitor delegate = super.visitMethod(access, name, descriptor,
						signature, exceptions);
					if (!(name.equals("init") || name.equals("launch"))) return delegate;
					if (name.equals("init") && descriptor.equals("([Ljava/lang/String;)Ljava/lang/ClassLoader;")) {
						return new MethodVisitor(Opcodes.ASM9, delegate) {
							@Override
							public void visitMethodInsn(int opcode, String owner, String method,
								String callDescriptor, boolean isInterface) {
								if (owner.equals(AGENT)) alreadyPatched.set(true);
								if (opcode == Opcodes.INVOKESTATIC &&
									owner.equals("net/fabricmc/loader/impl/launch/FabricLauncherBase") &&
									method.equals("finishMixinBootstrapping") && callDescriptor.equals("()V")) {
									super.visitVarInsn(Opcodes.ALOAD, 0);
									super.visitMethodInsn(Opcodes.INVOKESTATIC, AGENT, "beforeTransform",
										"(Ljava/lang/Object;)V", false);
									hooks[0]++;
								}
								super.visitMethodInsn(opcode, owner, method, callDescriptor, isInterface);
							}
						};
					}
					if (name.equals("launch") && descriptor.equals(
						"([Ljava/lang/String;Lnet/fabricmc/api/EnvType;)V")) {
						return new MethodVisitor(Opcodes.ASM9, delegate) {
							@Override
							public void visitMethodInsn(int opcode, String owner, String method,
								String callDescriptor, boolean isInterface) {
								if (owner.equals(AGENT)) alreadyPatched.set(true);
								super.visitMethodInsn(opcode, owner, method, callDescriptor, isInterface);
								if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(KNOT) &&
									method.equals("init") &&
									callDescriptor.equals("([Ljava/lang/String;)Ljava/lang/ClassLoader;")) {
									super.visitInsn(Opcodes.DUP);
									super.visitMethodInsn(Opcodes.INVOKESTATIC, AGENT, "afterKnotInit",
										"(Ljava/lang/ClassLoader;)V", false);
									hooks[1]++;
								}
							}
						};
					}
					return delegate;
				}
		}, 0);
		if (alreadyPatched.get() || hooks[0] != 1 || hooks[1] != 1) {
			return null;
		}
		return writer.toByteArray();
	}
}
