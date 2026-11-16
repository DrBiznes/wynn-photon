package wynnphoton.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Downloads a file and refuses it unless its SHA-512 matches. */
public abstract class DownloadFileTask extends DefaultTask {
	@Input
	public abstract Property<String> getUrl();

	@Input
	public abstract Property<String> getSha512();

	@OutputFile
	public abstract RegularFileProperty getOutputFile();

	@TaskAction
	public void download() throws Exception {
		Path output = getOutputFile().get().getAsFile().toPath();
		String expected = getSha512().get().toLowerCase();
		if (Files.isRegularFile(output) && sha512(Files.readAllBytes(output)).equals(expected)) {
			return;
		}

		getLogger().lifecycle("Downloading {}", getUrl().get());
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(getUrl().get()))
				.header("User-Agent", "wynn-photon-build")
				.build();
		HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200) {
			throw new GradleException("Download failed with HTTP " + response.statusCode() + ": " + getUrl().get());
		}
		String actual = sha512(response.body());
		if (!actual.equals(expected)) {
			throw new GradleException("Downloaded file has SHA-512 " + actual + " but " + expected + " was expected: " + getUrl().get());
		}
		Files.createDirectories(output.getParent());
		Files.write(output, response.body());
	}

	private static String sha512(byte[] data) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(data));
	}
}
