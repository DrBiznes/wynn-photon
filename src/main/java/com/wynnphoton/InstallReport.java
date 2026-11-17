package com.wynnphoton;

import java.util.ArrayList;
import java.util.List;

/**
 * A player-facing explanation of why the Wynn-Photon pack could not be built: what was found, what
 * is needed and how to fix it. Logged, and written to {@link #FILE_NAME} in the shaderpacks folder.
 */
public record InstallReport(String title, List<String> found, String needed, List<String> steps) {
	/** The "_0" sorts it to the top of the shaderpacks folder. */
	public static final String FILE_NAME = "_0WYNN_PHOTON_ERRORS.txt";
	static final String PHOTON_VERSIONS_PAGE = "https://modrinth.com/shader/photon-shader/versions";
	/** The mod's Modrinth page. */
	static final String MOD_PAGE = "https://modrinth.com/mod/wynnphoton";
	static final String ISSUES_PAGE = "https://github.com/DrBiznes/wynn-photon/issues";

	public InstallReport {
		found = List.copyOf(found);
		steps = List.copyOf(steps);
	}

	public String render() {
		StringBuilder text = new StringBuilder();
		text.append("=== Wynn-Photon: ").append(title).append(" ===\n");
		for (int i = 0; i < found.size(); i++) {
			text.append(i == 0 ? "Found:  " : "        ").append(found.get(i)).append('\n');
		}
		text.append("Needed: ").append(needed).append('\n');
		text.append("How to fix it:\n");
		for (int i = 0; i < steps.size(); i++) {
			text.append("  ").append(i + 1).append(". ").append(steps.get(i)).append('\n');
		}
		return text.toString();
	}

	/** Steps to add an unmodified Photon zip, after the case-specific ones. */
	static List<String> addPhotonSteps(PatchBundle bundle, List<String> first) {
		List<String> steps = new ArrayList<>(first);
		steps.add("Download " + bundle.baseName() + ".zip from " + PHOTON_VERSIONS_PAGE);
		steps.add("Put the zip in the shaderpacks folder as it is. Don't unzip, rename or edit it.");
		steps.add("Restart Minecraft. Wynn-Photon builds '" + bundle.outputName() + "' and selects it.");
		return steps;
	}

	static InstallReport unexpected(PatchBundle bundle, Throwable error) {
		String version = bundle == null ? "" : " " + bundle.modVersion();
		return new InstallReport(
				"the patched pack could not be built",
				List.of(error.toString()),
				"Wynn-Photon" + version + " to finish patching",
				List.of("Restart Minecraft once. A file locked by another program can cause this.",
						"If it happens again, report it at " + ISSUES_PAGE + " with logs/latest.log attached."));
	}
}
