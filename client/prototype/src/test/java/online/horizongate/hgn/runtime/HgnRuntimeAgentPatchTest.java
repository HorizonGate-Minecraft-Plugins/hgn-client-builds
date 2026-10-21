package online.horizongate.hgn.runtime;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Standalone bytecode-shape check against the pinned Fabric Loader archive. */
public final class HgnRuntimeAgentPatchTest {
	private HgnRuntimeAgentPatchTest() {
	}

	public static void main(String[] arguments) throws Exception {
		if (arguments.length != 1) {
			throw new IllegalArgumentException("Pass the official Fabric Loader 0.19.3 JAR");
		}
		byte[] original;
		try (JarFile loader = new JarFile(Path.of(arguments[0]).toFile())) {
			if (loader.getEntry("fabric.mod.json") == null) {
				throw new AssertionError("The input is not a Fabric Loader archive");
			}
			var knot = loader.getEntry("net/fabricmc/loader/impl/launch/knot/Knot.class");
			if (knot == null) throw new AssertionError("Fabric Knot is missing");
			try (var stream = loader.getInputStream(knot)) {
				original = stream.readAllBytes();
			}
		}
		byte[] patched = HgnRuntimeAgent.patchKnot(original);
		if (patched == null || HgnRuntimeAgent.patchKnot(patched) != null) {
			throw new AssertionError("Knot hook is unavailable or accepted a double patch");
		}
		List<String> init = calls(patched, "init");
		List<String> launch = calls(patched, "launch");
		ordered(init,
			"net/fabricmc/loader/impl/launch/FabricMixinBootstrap.init",
			"online/horizongate/hgn/runtime/HgnRuntimeAgent.beforeTransform",
			"net/fabricmc/loader/impl/launch/FabricLauncherBase.finishMixinBootstrapping");
		ordered(launch,
			"net/fabricmc/loader/impl/launch/knot/Knot.init",
			"online/horizongate/hgn/runtime/HgnRuntimeAgent.afterKnotInit",
			"net/fabricmc/loader/impl/game/GameProvider.launch");
		System.out.println("Pinned Fabric Knot has one early mixin hook and one runtime entrypoint hook");
	}

	private static List<String> calls(byte[] bytes, String methodName) {
		List<String> calls = new ArrayList<>();
		new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor,
				String signature, String[] exceptions) {
				if (!name.equals(methodName)) return null;
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String name,
						String descriptor, boolean isInterface) {
						calls.add(owner + "." + name);
					}
				};
			}
		}, 0);
		return calls;
	}

	private static void ordered(List<String> calls, String first, String second, String third) {
		int firstIndex = calls.indexOf(first);
		int secondIndex = calls.indexOf(second);
		int thirdIndex = calls.indexOf(third);
		if (firstIndex < 0 || secondIndex <= firstIndex || thirdIndex <= secondIndex ||
			calls.lastIndexOf(second) != secondIndex) {
			throw new AssertionError("Expected exactly one HGN hook between " + first + " and " + third);
		}
	}
}
