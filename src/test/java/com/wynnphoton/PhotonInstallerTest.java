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
