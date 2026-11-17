package com.wynnphoton;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import static com.wynnphoton.WynnPhotonPreLaunch.LOGGER;

/**
 * Makes a running Iris load the pack named in iris.properties, as pressing R does. All by reflection,
 * so the mod still loads no Minecraft or Iris classes at launch and works without Iris.
 *
 * <p>{@code Iris.reload()} re-reads iris.properties before loading the pack, and must run on the
 * client thread, so the call is handed to Minecraft's own task queue.
 */
final class IrisReloader {
	private IrisReloader() {
	}

	static void reloadOnClientThread() {
		try {
			Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
			Method reload = iris.getMethod("reload");
			if (iris.getMethod("getIrisConfig").invoke(null) == null) {
				return; // Iris hasn't started yet; it reads iris.properties itself.
			}
			Executor client = minecraft();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				try {
					reload.invoke(null);
					LOGGER.info("Iris reloaded with the new Wynn-Photon pack");
				} catch (ReflectiveOperationException e) {
					LOGGER.warn("Could not reload Iris; press R or restart to use the new pack: {}", e.toString());
				}
			});
		} catch (ClassNotFoundException e) {
			// No Iris: nothing to reload.
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Could not reload Iris; press R or restart to use the new pack: {}", e.toString());
		}
	}

	/** Minecraft.getInstance(), looked up through Fabric's mappings so it works in dev and in production. */
	private static Executor minecraft() throws ReflectiveOperationException {
		MappingResolver mappings = FabricLoader.getInstance().getMappingResolver();
		String className = mappings.mapClassName("intermediary", "net.minecraft.class_310");
		String getInstance = mappings.mapMethodName("intermediary", "net.minecraft.class_310", "method_1551",
				"()Lnet/minecraft/class_310;");
		Class<?> minecraft = Class.forName(className);
		return (Executor) minecraft.getMethod(getInstance).invoke(null);
	}
}
