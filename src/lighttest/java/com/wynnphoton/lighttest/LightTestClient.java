package com.wynnphoton.lighttest;

import com.mojang.blaze3d.platform.NativeImage;
import com.wynnphoton.lighttest.LightTestCases.OptionSet;
import com.wynnphoton.lighttest.LightTestCases.Spot;
import com.wynnphoton.lighttest.LightTestCases.Time;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Dev-only: photographs the lighting test spots under every option set, with nobody at the keyboard, then closes the
 * game. It is compiled into the {@code lighttest} source set, which only the {@code lightTest} run loads, and does
 * nothing unless {@code -Dwynnphoton.lightTest=<case file>} is set.
 *
 * <p>It runs on its own thread and hands each step to the client or server thread. For every option set it writes
 * the shader pack's option file and reloads Iris; for every time and spot it sets the time, teleports the player
 * (in spectator mode, with the HUD hidden), waits for the image to settle and saves the frame.</p>
 */
public final class LightTestClient implements ClientModInitializer {
	private static final String CASES_PROPERTY = "wynnphoton.lightTest";
	/** The last block the gallery datapack places for the light test: the world is ready once it is there. */
	private static final BlockPos LAST_BLOCK = new BlockPos(5, -59, 70);
	/** Inside the sealed torch room. */
	private static final BlockPos SEALED_ROOM = new BlockPos(86, -59, 49);

	private Minecraft mc;
	private Path outDir;

	@Override
	public void onInitializeClient() {
		String casesFile = System.getProperty(CASES_PROPERTY);
		if (casesFile == null || casesFile.isBlank()) {
			return;
		}
		Thread thread = new Thread(() -> run(Path.of(casesFile)), "wynn-photon-light-test");
		thread.setDaemon(true);
		thread.start();
	}

	private void run(Path casesFile) {
		outDir = Path.of(System.getProperty(CASES_PROPERTY + ".out", "light-test"));
		try {
			Files.createDirectories(outDir);
			Files.deleteIfExists(outDir.resolve("done.txt"));
			Files.deleteIfExists(outDir.resolve("error.txt"));
			runCases(LightTestCases.read(casesFile));
			Files.writeString(outDir.resolve("done.txt"), "ok\n");
		} catch (Throwable t) {
			StringWriter trace = new StringWriter();
			t.printStackTrace(new PrintWriter(trace));
			log("FAILED: " + trace);
			try {
				Files.writeString(outDir.resolve("error.txt"), trace.toString());
			} catch (IOException ignored) {
				// The log has it
			}
		} finally {
			if (mc != null) {
				mc.execute(mc::stop);
			}
		}
	}

	private void runCases(LightTestCases cases) throws Exception {
		int settleFrames = Integer.getInteger(CASES_PROPERTY + ".settleFrames", 150);
		long settleMillis = Long.getLong(CASES_PROPERTY + ".settleMillis", 3000);
		long reloadMillis = Long.getLong(CASES_PROPERTY + ".reloadMillis", 8000);
		String only = System.getProperty(CASES_PROPERTY + ".only", "");
		Pattern filter = only.isBlank() ? null : Pattern.compile(only);

		// Wait for the game, the world and the gallery
		waitUntil("the client to start", 300, () -> (mc = Minecraft.getInstance()) != null);
		waitUntil("the test world to open", 600,
				() -> onClient(() -> mc.player != null && mc.level != null && mc.getSingleplayerServer() != null));
		IntegratedServer server = mc.getSingleplayerServer();
		waitUntil("the gallery datapack to build the light test spots", 180,
				() -> server.submit(() -> server.overworld().getBlockState(LAST_BLOCK).is(Blocks.AIR)
						&& server.overworld().getBlockState(LAST_BLOCK.above(2)).is(Blocks.STONE)).join());
		// The gallery is rebuilt on every load and the light engine catches up afterwards: wait until the sealed torch
		// room has lost its sky light, then give the client time to receive the light and rebuild its chunks
		waitUntil("the light engine to catch up", 180,
				() -> server.submit(() -> server.overworld().getBrightness(LightLayer.SKY, SEALED_ROOM) == 0).join());
		Thread.sleep(Long.getLong(CASES_PROPERTY + ".startMillis", 8000));

		String player = onClient(() -> mc.player.getScoreboardName());
		String home = onClient(() -> String.format(Locale.ROOT, "%.2f %.2f %.2f %.1f %.1f",
				mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot()));
		onClient(() -> {
			mc.options.pauseOnLostFocus = false;
			mc.options.hideGui = true;
			if (mc.screen != null) {
				log("Closing screen " + mc.screen.getClass().getName());
				mc.setScreen(null);
			}
			return null;
		});
		command(server, "weather clear");
		command(server, "gamemode spectator " + player);

		String packName = (String) irisCall("getCurrentPackName");
		Path optionsFile = ((Path) irisCall("getShaderpacksDirectory")).resolve(packName + ".txt");
		Path optionsBackup = outDir.resolve("options-before-test.txt");
		boolean hadOptions = Files.exists(optionsFile);
		if (hadOptions) {
			Files.copy(optionsFile, optionsBackup, StandardCopyOption.REPLACE_EXISTING);
		}
		log("Shader pack: " + packName + ", " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());

		StringBuilder manifest = new StringBuilder("spot,time,set,options,file\n");
		int total = cases.sets.size() * cases.times.size() * cases.spots.size();
		int n = 0;
		try {
			for (OptionSet set : cases.sets) {
				boolean reloaded = false;
				for (Time time : cases.times) {
					boolean timeSet = false;
					for (Spot spot : cases.spots) {
						n++;
						String file = LightTestCases.shotName(spot, time, set);
						if (filter != null && !filter.matcher(file).find()) {
							continue;
						}
						if (!reloaded) {
							writeOptions(optionsFile, set);
							onClient(() -> irisCall("reload"));
							waitFrames(settleFrames, reloadMillis);
							reloaded = true;
						}
						if (!timeSet) {
							command(server, "time set " + time.ticks());
							timeSet = true;
						}
						command(server, String.format(Locale.ROOT, "tp %s %s %s %s %s %s", player,
								spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch()));
						waitFrames(settleFrames, settleMillis);
						screenshot(outDir.resolve(file));
						manifest.append(String.join(",", spot.name(), time.name(), set.name(),
								String.join(" ", set.options().entrySet().stream()
										.map(e -> e.getKey() + "=" + e.getValue()).toList()), file)).append('\n');
						log(n + "/" + total + " " + file);
					}
				}
			}
			Files.writeString(outDir.resolve("shots.csv"), manifest.toString(), StandardCharsets.UTF_8);
		} finally {
			// Put the pack's options, the player and the time back as they were
			if (hadOptions) {
				Files.move(optionsBackup, optionsFile, StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.deleteIfExists(optionsFile);
			}
			command(server, "time set noon");
			command(server, "gamemode creative " + player);
			command(server, "tp " + player + " " + home);
			waitFrames(5, 500);
		}
	}

	/** The option file Iris reads on reload: only the options that differ from the pack's defaults. */
	private static void writeOptions(Path optionsFile, OptionSet set) throws IOException {
		StringBuilder text = new StringBuilder("#Written by the Wynn-Photon light test (gradlew lightTest)\n");
		set.options().forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
		Files.writeString(optionsFile, text.toString(), StandardCharsets.UTF_8);
	}

	/** Iris is only on the runtime classpath, so it is called by name. */
	private static Object irisCall(String method) {
		try {
			return Class.forName("net.irisshaders.iris.Iris").getMethod(method).invoke(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Iris." + method + "() failed", e);
		}
	}

	private void screenshot(Path file) throws Exception {
		CompletableFuture<Void> saved = new CompletableFuture<>();
		mc.execute(() -> {
			try {
				Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> save(image, file, saved));
			} catch (Throwable t) {
				saved.completeExceptionally(t);
			}
		});
		saved.get(60, TimeUnit.SECONDS);
	}

	private static void save(NativeImage image, Path file, CompletableFuture<Void> saved) {
		try (image) {
			image.writeToFile(file);
			saved.complete(null);
		} catch (Throwable t) {
			saved.completeExceptionally(t);
		}
	}

	private static void command(IntegratedServer server, String command) {
		server.submit(() -> server.getCommands().performPrefixedCommand(
				server.createCommandSourceStack().withSuppressedOutput(), command)).join();
	}

	private <T> T onClient(Supplier<T> task) {
		return mc.submit(task).join();
	}

	/** The client runs its queued tasks once per frame, so one round trip is one frame. */
	private void waitFrames(int frames, long atLeastMillis) throws InterruptedException {
		long end = System.currentTimeMillis() + atLeastMillis;
		for (int i = 0; i < frames; i++) {
			mc.submit(() -> {}).join();
			Thread.sleep(1);
		}
		long left = end - System.currentTimeMillis();
		if (left > 0) {
			Thread.sleep(left);
		}
	}

	private static void waitUntil(String what, int seconds, Supplier<Boolean> ready) throws InterruptedException {
		long end = System.currentTimeMillis() + seconds * 1000L;
		while (true) {
			try {
				if (ready.get()) {
					return;
				}
			} catch (RuntimeException e) {
				// Not there yet
			}
			if (System.currentTimeMillis() > end) {
				throw new IllegalStateException("Gave up after " + seconds + " s waiting for " + what);
			}
			Thread.sleep(500);
		}
	}

	private static void log(String message) {
		System.out.println("[light-test] " + message);
	}
}
