package com.wynnphoton;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the installer against the real Photon release that the build downloads. */
class PhotonInstallerTest {
	private static final Path PHOTON_ZIP = Path.of(System.getProperty("wynnphoton.photonZip"));

	@TempDir
	Path game;
	Path shaderpacks;
	Path config;
	PatchBundle bundle;

	@BeforeEach
	void setUp() throws IOException {
		shaderpacks = Files.createDirectories(game.resolve("shaderpacks"));
		config = Files.createDirectories(game.resolve("config"));
		bundle = PatchBundle.loadFromClasspath();
	}

	private PhotonInstaller installer() {
		return new PhotonInstaller(shaderpacks, config, bundle, WynnPhotonConfig.DEFAULTS);
	}

	@Test
	void patchesTheZipAndSwitchesIrisToIt() throws IOException {
		Files.copy(PHOTON_ZIP, shaderpacks.resolve("photon_v1.4.zip"));
		Files.writeString(shaderpacks.resolve("photon_v1.4.zip.txt"), "SHADOW_QUALITY=2\n");
		Files.writeString(config.resolve("iris.properties"),
				"#Iris config\nenableShaders=true\nshaderPack=photon_v1.4.zip\nmaxShadowRenderDistance=32\n");

		assertEquals(PhotonInstaller.Status.INSTALLED, installer().run());

		Path output = shaderpacks.resolve(bundle.outputName());
		String blocks = Files.readString(output.resolve("shaders/block.properties"));
		assertTrue(blocks.contains("block.10079 = redstone_torch:lit=true redstone_wall_torch:lit=true"));
		assertFalse(blocks.contains("block.10038 = redstone_torch"));
		assertTrue(Files.readString(output.resolve("shaders/settings.glsl")).contains("\n#define COLORED_LIGHTS "));
		assertTrue(Files.exists(output.resolve("LICENSE")), "Photon's license travels with the pack");
		assertTrue(Files.exists(output.resolve(PhotonInstaller.MARKER_FILE)));

		Properties iris = irisProperties();
		assertEquals(bundle.outputName(), iris.getProperty("shaderPack"));
		assertEquals("32", iris.getProperty("maxShadowRenderDistance"));
		assertEquals("SHADOW_QUALITY=2\n", Files.readString(shaderpacks.resolve(bundle.outputName() + ".txt")));

		assertEquals(PhotonInstaller.Status.UP_TO_DATE, installer().run());
	}

	@Test
	void ignoresAnEditedCopyOfPhoton() throws IOException {
		Path edited = extract(shaderpacks.resolve("photon_v1.4_personal"));
		Path blocks = edited.resolve("shaders/block.properties");
		Files.writeString(blocks, Files.readString(blocks) + "\n# my edit\n");

		assertEquals(PhotonInstaller.Status.BASE_NOT_FOUND, installer().run());
		assertFalse(Files.exists(shaderpacks.resolve(bundle.outputName())));
		assertProblem("your Photon copy is edited", "'photon_v1.4_personal': an edited");
	}

	@Test
	void explainsAMissingPhotonAndClearsTheReportOnceInstalled() throws IOException {
		PhotonInstaller installer = installer();
		assertEquals(PhotonInstaller.Status.BASE_NOT_FOUND, installer.run());
		assertProblem("Photon v1.4 is missing", "Download photon_v1.4.zip from https://modrinth.com/");

		Files.copy(PHOTON_ZIP, shaderpacks.resolve("photon_v1.4.zip"));
		assertEquals(PhotonInstaller.Status.INSTALLED, installer.run());
		assertTrue(installer.report().isEmpty());
		assertFalse(Files.exists(shaderpacks.resolve(InstallReport.FILE_NAME)));
	}

	@Test
	void explainsANewerPhoton() throws IOException {
		Path newer = extract(shaderpacks.resolve("photon_v1.5"));
		Files.writeString(newer.resolve("shaders/block.properties"), "changed in 1.5\n");

		assertEquals(PhotonInstaller.Status.BASE_NOT_FOUND, installer().run());
		assertProblem("this Wynn-Photon needs Photon v1.4", "Photon 1.5, newer than");
	}

	@Test
	void explainsABrokenZip() throws IOException {
		byte[] zip = Files.readAllBytes(PHOTON_ZIP);
		Files.write(shaderpacks.resolve("photon_v1.4.zip"), java.util.Arrays.copyOf(zip, zip.length / 3));

		assertEquals(PhotonInstaller.Status.BASE_NOT_FOUND, installer().run());
		assertProblem("the Photon zip is broken", "half-downloaded");
	}

	@Test
	void explainsAnExtraFolder() throws IOException {
		extract(shaderpacks.resolve("photon_v1.4").resolve("photon_v1.4"));

		assertEquals(PhotonInstaller.Status.BASE_NOT_FOUND, installer().run());
		assertProblem("Photon was unzipped into an extra folder", "'photon_v1.4': a folder");
	}

	@Test
	void buildsThePackWhenPhotonAppearsWhileTheGameRuns() throws Exception {
		Files.writeString(config.resolve("iris.properties"), "shaderPack=photon_v1.4.zip\n");
		PhotonInstaller installer = installer();
		assertEquals(PhotonInstaller.Status.BASE_NOT_FOUND, installer.run());

		java.util.concurrent.CountDownLatch installed = new java.util.concurrent.CountDownLatch(1);
		Thread watcher = new ShaderpacksWatcher(installer, 100, installed::countDown).start();
		try {
			Path partial = shaderpacks.resolve("photon_v1.4.zip.part");
			Files.copy(PHOTON_ZIP, partial);
			Files.move(partial, shaderpacks.resolve("photon_v1.4.zip"));

			assertTrue(installed.await(30, java.util.concurrent.TimeUnit.SECONDS), "watcher built the pack");
			assertTrue(Files.exists(shaderpacks.resolve(bundle.outputName()).resolve(PhotonInstaller.MARKER_FILE)));
			assertTrue(installer.selectedInIris());
			assertFalse(Files.exists(shaderpacks.resolve(InstallReport.FILE_NAME)));
			watcher.join(5000);
			assertFalse(watcher.isAlive(), "watcher stops after installing");
		} finally {
			watcher.interrupt();
		}
	}

	@Test
	void carriesSettingsFromTheNewestOldPackEvenWhenIrisUsesAnotherPack() throws IOException {
		Files.copy(PHOTON_ZIP, shaderpacks.resolve("photon_v1.4.zip"));
		for (String version : new String[]{"0.0.1", "0.0.2"}) {
			Path old = Files.createDirectories(shaderpacks.resolve(bundle.baseName() + " + WynnPhoton_" + version));
			Files.writeString(old.resolve(PhotonInstaller.MARKER_FILE), "patchSetHash=old\n");
			Path settings = shaderpacks.resolve(old.getFileName() + ".txt");
			Files.writeString(settings, "BLOOM_INTENSITY=" + version + "\n");
			Files.setLastModifiedTime(settings, java.nio.file.attribute.FileTime.fromMillis(version.equals("0.0.2") ? 2_000_000 : 1_000_000));
		}
		Files.writeString(config.resolve("iris.properties"), "shaderPack=SomethingElse.zip\n");

		assertEquals(PhotonInstaller.Status.INSTALLED, installer().run());

		assertEquals("BLOOM_INTENSITY=0.0.2\n", Files.readString(shaderpacks.resolve(bundle.outputName() + ".txt")));
		assertEquals("SomethingElse.zip", irisProperties().getProperty("shaderPack"));
	}

	@Test
	void postsAToastForInstallsUpdatesAndErrorsButNotWhenUpToDate() throws IOException {
		Notices.resetForTest();
		PhotonInstaller installer = installer();
		installer.run();
		installer.run();
		Files.copy(PHOTON_ZIP, shaderpacks.resolve("photon_v1.4.zip"));
		Path old = Files.createDirectories(shaderpacks.resolve(bundle.baseName() + " + WynnPhoton_0.0.1"));
		Files.writeString(old.resolve(PhotonInstaller.MARKER_FILE), "patchSetHash=old\nmodVersion=0.0.1\n");
		installer.run();
		installer.run();

		var notices = Notices.pendingForTest();
		assertEquals(2, notices.size(), notices.toString());
		assertEquals(Notices.Kind.ERROR, notices.get(0).kind());
		assertTrue(notices.get(0).message().startsWith("Photon v1.4 is missing. See shaderpacks/_0WYNN_PHOTON_ERRORS.txt"));
		assertEquals("Wynn-Photon updated", notices.get(1).title());
		assertTrue(notices.get(1).message().startsWith("Updated from 0.0.1 to " + bundle.modVersion()), notices.get(1).message());
		Notices.resetForTest();
	}

	@Test
	void comparesVersions() {
		assertEquals("1.4", PhotonInstaller.version("photon_v1.4.zip"));
		assertEquals(null, PhotonInstaller.version("Photon"));
		assertTrue(PhotonInstaller.compareVersions("1.4.1", "1.4") > 0);
		assertTrue(PhotonInstaller.compareVersions("1.10", "1.4") > 0);
		assertEquals(0, PhotonInstaller.compareVersions("1.4.0", "1.4"));
	}

	/** The report's title and some of its text, both in the installer and in the file. */
	private void assertProblem(String title, String text) throws IOException {
		String file = Files.readString(shaderpacks.resolve(InstallReport.FILE_NAME));
		assertTrue(file.startsWith("=== Wynn-Photon: " + title + " ==="), file);
		assertTrue(file.contains(text), file);
	}

	@Test
	void usesAnUnzippedPhotonAndReplacesAnOlderWynnPhotonPack() throws IOException {
		extract(shaderpacks.resolve("Photon"));
		Path old = Files.createDirectories(shaderpacks.resolve(bundle.baseName() + " + WynnPhoton_0.0.1"));
		Files.writeString(old.resolve(PhotonInstaller.MARKER_FILE), "patchSetHash=old\n");
		Files.writeString(shaderpacks.resolve(old.getFileName() + ".txt"), "BLOOM_INTENSITY=0.5\n");
		Files.writeString(config.resolve("iris.properties"), "shaderPack=" + IrisConfig.escape(old.getFileName().toString()) + "\n");

		assertEquals(PhotonInstaller.Status.INSTALLED, installer().run());

		assertFalse(Files.exists(old), "older Wynn-Photon pack is deleted");
		assertEquals(bundle.outputName(), irisProperties().getProperty("shaderPack"));
		assertEquals("BLOOM_INTENSITY=0.5\n", Files.readString(shaderpacks.resolve(bundle.outputName() + ".txt")));
	}

	@Test
	void leavesAFolderItDidNotCreateAlone() throws IOException {
		Files.copy(PHOTON_ZIP, shaderpacks.resolve("photon_v1.4.zip"));
		Path squatter = Files.createDirectories(shaderpacks.resolve(bundle.outputName()));
		Files.writeString(squatter.resolve("notes.txt"), "mine");

		assertEquals(PhotonInstaller.Status.NAME_TAKEN, installer().run());
		assertEquals("mine", Files.readString(squatter.resolve("notes.txt")));
		assertProblem("the pack name is taken", "which Wynn-Photon did not create");
	}

	private Properties irisProperties() throws IOException {
		Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(config.resolve("iris.properties"))) {
			properties.load(in);
		}
		return properties;
	}

	private static Path extract(Path destination) throws IOException {
		try (ZipFile zip = new ZipFile(PHOTON_ZIP.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}
				Path target = destination.resolve(entry.getName());
				Files.createDirectories(target.getParent());
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, target);
				}
			}
		}
		return destination;
	}
}
