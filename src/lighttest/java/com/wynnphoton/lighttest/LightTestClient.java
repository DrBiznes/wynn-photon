package com.wynnphoton.lighttest;

import com.mojang.blaze3d.platform.NativeImage;
import com.wynnphoton.lighttest.LightTestCases.OptionSet;
import com.wynnphoton.lighttest.LightTestCases.Spot;
import com.wynnphoton.lighttest.LightTestCases.Time;
import com.wynnphoton.lighttest.LightTestCases.World;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dev-only: photographs the spots of a case file under every option set, with nobody at the keyboard, then closes the
 * game. It serves {@code lightTest}, {@code skyTest} and {@code mapTest}. It is compiled into the {@code lighttest}
 * source set, which only those runs load, and does nothing unless {@code -Dwynnphoton.lightTest=<case file>} is set.
 *
 * <p>It runs on its own thread and hands each step to the client or server thread. For every option set it writes
 * the shader pack's option file and reloads Iris; for every time and spot it sets the time and weather, teleports the
 * player (in spectator mode, with the HUD hidden), waits for the image to settle and saves the frame. A spot can aim
 * at the sun or the moon, set the field of view and give the ground around it a biome.</p>
 *
 * <p>In the map world ({@code world map}) Voxy's ingestion must be off, so the empty chunks around the player never
 * overwrite the copied Wynncraft LODs. {@code runMapTest} switches it off in Voxy's config file before the game starts
 * (Voxy's config must not be touched from here that early: read before Voxy has started, it loads disabled), and the
 * harness stops at once if it finds it on.</p>
 */
public final class LightTestClient implements ClientModInitializer {
	private static final String CASES_PROPERTY = "wynnphoton.lightTest";
	/** The last block the gallery datapack places for the light test: the world is ready once it is there. */
	private static final BlockPos LAST_BLOCK = new BlockPos(5, -59, 70);
	/** Inside the sealed torch room. */
	private static final BlockPos SEALED_ROOM = new BlockPos(86, -59, 49);
	private static final Pattern SUN_PATH_ROTATION =
			Pattern.compile("const\\s+float\\s+sunPathRotation\\s*=\\s*(-?[0-9.]+)");

	private Minecraft mc;
	private Path outDir;

	@Override
	public void onInitializeClient() {
		String casesFile = System.getProperty(CASES_PROPERTY);
		if (casesFile == null || casesFile.isBlank()) {
			return;
		}
		outDir = Path.of(System.getProperty(CASES_PROPERTY + ".out", "light-test"));
		LightTestCases cases;
		try {
			Files.createDirectories(outDir);
			Files.deleteIfExists(outDir.resolve("done.txt"));
			Files.deleteIfExists(outDir.resolve("error.txt"));
			cases = LightTestCases.read(Path.of(casesFile));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		Thread thread = new Thread(() -> run(cases), "wynn-photon-light-test");
		thread.setDaemon(true);
		thread.start();
	}

	private void run(LightTestCases cases) {
		try {
			runCases(cases);
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

		// Wait for the game and the world, and for the gallery if this is the test world
		waitUntil("the client to start", 300, () -> (mc = Minecraft.getInstance()) != null);
		waitUntil("the test world to open", 600,
				() -> onClient(() -> mc.player != null && mc.level != null && mc.getSingleplayerServer() != null));
		IntegratedServer server = mc.getSingleplayerServer();
		if (cases.world == World.MAP && onClient(LightTestClient::voxyIngestEnabled)) {
			throw new IllegalStateException("Voxy's ingestion is on in the map world. Run it with gradlew mapTest, "
					+ "which switches it off, so the empty chunks never overwrite the Wynncraft LODs.");
		}
		if (cases.world == World.GALLERY) {
			waitUntil("the gallery datapack to build the light test spots", 180,
					() -> server.submit(() -> server.overworld().getBlockState(LAST_BLOCK).is(Blocks.AIR)
							&& server.overworld().getBlockState(LAST_BLOCK.above(2)).is(Blocks.STONE)).join());
			// The gallery is rebuilt on every load and the light engine catches up afterwards: wait until the sealed
			// torch room has lost its sky light, then give the client time to receive the light and rebuild its chunks
			waitUntil("the light engine to catch up", 180,
					() -> server.submit(() -> server.overworld().getBrightness(LightLayer.SKY, SEALED_ROOM) == 0).join());
		}
		Thread.sleep(Long.getLong(CASES_PROPERTY + ".startMillis", 8000));

		String player = onClient(() -> mc.player.getScoreboardName());
		String home = onClient(() -> String.format(Locale.ROOT, "%.2f %.2f %.2f %.1f %.1f",
				mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot()));
		int fovBefore = onClient(() -> mc.options.fov().get());
		int renderDistanceBefore = onClient(() -> mc.options.renderDistance().get());
		onClient(() -> {
			mc.options.pauseOnLostFocus = false;
			mc.options.hideGui = true;
			if (cases.renderDistance > 0) {
				mc.options.renderDistance().set(cases.renderDistance);
			}
			if (mc.screen != null) {
				log("Closing screen " + mc.screen.getClass().getName());
				mc.setScreen(null);
			}
			return null;
		});
		command(server, "gamemode spectator " + player);

		String packName = (String) irisCall("getCurrentPackName");
		Path shaderpacks = (Path) irisCall("getShaderpacksDirectory");
		Path optionsFile = shaderpacks.resolve(packName + ".txt");
		float defaultSunPathRotation = sunPathRotation(shaderpacks.resolve(packName));
		Path optionsBackup = outDir.resolve("options-before-test.txt");
		boolean hadOptions = Files.exists(optionsFile);
		if (hadOptions) {
			Files.copy(optionsFile, optionsBackup, StandardCopyOption.REPLACE_EXISTING);
		}
		log("Shader pack: " + packName + ", " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
				+ ", world " + cases.world.name().toLowerCase(Locale.ROOT));

		StringBuilder manifest = new StringBuilder("spot,time,set,options,file\n");
		// -Pframes=<n>: after each shot, the time per frame over five runs of n frames, with the frame rate uncapped
		int timedFrames = Integer.getInteger(CASES_PROPERTY + ".frames", 0);
		StringBuilder frameTimes = new StringBuilder("file,ms_per_frame_1,ms_per_frame_2,ms_per_frame_3,"
				+ "ms_per_frame_4,ms_per_frame_5\n");
		int total = 0;
		for (OptionSet set : cases.sets) {
			for (Time time : cases.times) {
				for (Spot spot : cases.spots) {
					total += spot.appliesTo(time, set) ? 1 : 0;
				}
			}
		}
		int n = 0;
		String weather = null;
		try {
			for (OptionSet set : cases.sets) {
				boolean reloaded = false;
				float sunPathRotation = set.options().containsKey("sunPathRotation")
						? Float.parseFloat(set.options().get("sunPathRotation")) : defaultSunPathRotation;
				for (Time time : cases.times) {
					boolean timeSet = false;
					for (Spot spot : cases.spots) {
						if (!spot.appliesTo(time, set)) {
							continue;
						}
						n++;
						String file = LightTestCases.shotName(spot, time, set);
						if (filter != null && !filter.matcher(file).find()) {
							continue;
						}
						if (!reloaded) {
							writeOptions(optionsFile, set);
							onClient(() -> irisCall("reload"));
							// Voxy rebuilds its renderer while Iris reloads and then picks its non-shader pipeline
							// ("using pipeline 'NormalRenderPipeline'" in the log), which draws the LODs and the sky
							// wrong. Reloading the chunks once the new Iris pipeline exists (like F3+A) puts it back.
							waitFrames(20, 2000);
							onClient(() -> {
								mc.levelRenderer.allChanged();
								return null;
							});
							waitFrames(settleFrames, reloadMillis);
							reloaded = true;
						}
						long settle = spot.settleMillis(settleMillis);
						if (!timeSet) {
							command(server, "time set " + time.ticks());
							daylightCycle(server, time.flows());
							if (!time.weather().equals(weather)) {
								// Rain and thunder fade in and out over a few seconds
								command(server, "weather " + time.weather());
								if (weather != null) {
									settle = Math.max(settle, 8000);
								}
								weather = time.weather();
							}
							timeSet = true;
						}
						int fov = spot.fov() > 0 ? spot.fov() : fovBefore;
						onClient(() -> {
							mc.options.fov().set(fov);
							return null;
						});
						command(server, String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f", player,
								spot.x(), spot.y(), spot.z()));
						if (spot.biome() != null) {
							// The chunks must be loaded first. 39 x 20 x 39 blocks stays under fillbiome's volume limit.
							waitFrames(20, 1500);
							int x = (int) Math.floor(spot.x());
							int z = (int) Math.floor(spot.z());
							command(server, String.format(Locale.ROOT, "fillbiome %d -64 %d %d -45 %d %s",
									x - 19, z - 19, x + 19, z + 19, spot.biome()));
							settle = Math.max(settle, 6000);
						} else {
							waitFrames(5, 200);
						}
						teleport(server, player, spot, look(spot, sunPathRotation));
						waitFrames(settleFrames, settle);
						if (spot.aimed()) {
							// The sky moves a little while the image settles: aim again just before the shot
							teleport(server, player, spot, look(spot, sunPathRotation));
							waitFrames(10, 300);
						}
						screenshot(outDir.resolve(file));
						manifest.append(String.join(",", spot.name(), time.name(), set.name(),
								String.join(" ", set.options().entrySet().stream()
										.map(e -> e.getKey() + "=" + e.getValue()).toList()), file)).append('\n');
						log(n + "/" + total + " " + file);
						if (timedFrames > 0) {
							frameTimes.append(file).append(',').append(timeFrames(timedFrames)).append('\n');
						}
					}
				}
			}
			Files.writeString(outDir.resolve("shots.csv"), manifest.toString(), StandardCharsets.UTF_8);
			if (timedFrames > 0) {
				Files.writeString(outDir.resolve("frames.csv"), frameTimes.toString(), StandardCharsets.UTF_8);
			}
		} finally {
			// Put the pack's options, the view, the player, the weather and the time back as they were
			onClient(() -> {
				mc.options.fov().set(fovBefore);
				mc.options.renderDistance().set(renderDistanceBefore);
				return null;
			});
			if (hadOptions) {
				Files.move(optionsBackup, optionsFile, StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.deleteIfExists(optionsFile);
			}
			daylightCycle(server, false);
			command(server, "time set noon");
			command(server, "weather clear");
			command(server, "gamemode creative " + player);
			command(server, "tp " + player + " " + home);
			waitFrames(5, 500);
		}
	}

	/** Fixed-point numbers: the command parser takes no exponents, and a yaw can come out as -2.4E-14. */
	private static void teleport(IntegratedServer server, String player, Spot spot, float[] look) {
		command(server, String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f %.3f %.3f", player,
				spot.x(), spot.y(), spot.z(), look[0], look[1]));
	}

	/** The spot's yaw and pitch: fixed, or looking at the sun or the moon plus the spot's pitch. */
	private float[] look(Spot spot, float sunPathRotation) {
		return spot.aimed() ? aim(spot.aim(), sunPathRotation, spot.pitch()) : new float[] {spot.yaw(), spot.pitch()};
	}

	/**
	 * The yaw and pitch that look at the sun or the moon, plus {@code pitchOffset}. Iris turns the sky like OptiFine:
	 * a body's direction is Ry(-90) Rz(sunPathRotation) Rx(angle) applied to straight up, where the angle is the
	 * game's own sun or moon angle.
	 */
	private float[] aim(String body, float sunPathRotation, float pitchOffset) {
		float degrees = onClient(() -> mc.level.environmentAttributes().getValue(
				body.equals("sun") ? EnvironmentAttributes.SUN_ANGLE : EnvironmentAttributes.MOON_ANGLE,
				mc.player.position()));
		double angle = Math.toRadians(degrees);
		double tilt = Math.toRadians(sunPathRotation);
		double x = -Math.sin(angle);
		double y = Math.cos(angle) * Math.cos(tilt);
		double z = -Math.cos(angle) * Math.sin(tilt);
		float yaw = (float) Math.toDegrees(Math.atan2(-x, z));
		float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, y))));
		log(String.format(Locale.ROOT, "%s angle %.1f, direction (%.3f, %.3f, %.3f), yaw %.1f, pitch %.1f",
				body, degrees, x, y, z, yaw, pitch));
		return new float[] {yaw, Math.max(-90, Math.min(90, pitch + pitchOffset))};
	}

	/** The pack's default {@code sunPathRotation}, from its settings.glsl. */
	private static float sunPathRotation(Path packDir) throws IOException {
		Path settings = packDir.resolve("shaders").resolve("settings.glsl");
		if (Files.isRegularFile(settings)) {
			Matcher m = SUN_PATH_ROTATION.matcher(Files.readString(settings, StandardCharsets.UTF_8));
			if (m.find()) {
				return Float.parseFloat(m.group(1));
			}
		}
		log("No sunPathRotation in " + settings + ", taking 0");
		return 0;
	}

	/** Voxy's ingestion switch. Voxy is only on the runtime classpath, so it is read by name. */
	private static boolean voxyIngestEnabled() {
		try {
			Object config = Class.forName("me.cortex.voxy.client.config.VoxyConfig").getField("CONFIG").get(null);
			return config.getClass().getField("ingestEnabled").getBoolean(config);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not read Voxy's config", e);
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

	/** The test world keeps its time still. The game rule was renamed in 1.21.11; the other name does nothing. */
	private static void daylightCycle(IntegratedServer server, boolean on) {
		command(server, "gamerule advance_time " + on);
		command(server, "gamerule doDaylightCycle " + on);
	}

	/**
	 * The milliseconds per frame of five runs of {@code frames} frames, comma separated, with vsync and the frame
	 * rate limits off while it measures. One round trip to the client thread is one frame.
	 */
	private String timeFrames(int frames) throws InterruptedException {
		boolean vsync = onClient(() -> mc.options.enableVsync().get());
		int limit = onClient(() -> mc.options.framerateLimit().get());
		InactivityFpsLimit inactivity = onClient(() -> mc.options.inactivityFpsLimit().get());
		onClient(() -> {
			mc.options.enableVsync().set(false);
			mc.options.framerateLimit().set(260);
			mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
			return null;
		});
		try {
			waitFrames(60, 1000);
			java.util.List<String> runs = new java.util.ArrayList<>();
			for (int run = 0; run < 5; run++) {
				long start = System.nanoTime();
				for (int i = 0; i < frames; i++) {
					mc.submit(() -> {}).join();
				}
				runs.add(String.format(Locale.ROOT, "%.3f", (System.nanoTime() - start) / 1e6 / frames));
			}
			log("ms per frame: " + runs);
			return String.join(",", runs);
		} finally {
			onClient(() -> {
				mc.options.enableVsync().set(vsync);
				mc.options.framerateLimit().set(limit);
				mc.options.inactivityFpsLimit().set(inactivity);
				return null;
			});
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
