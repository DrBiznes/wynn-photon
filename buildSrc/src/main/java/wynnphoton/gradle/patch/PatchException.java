package wynnphoton.gradle.patch;

import java.io.IOException;

/** A patch could not be parsed or does not fit the file it is being applied to. */
public class PatchException extends IOException {
	public PatchException(String message) {
		super(message);
	}
}
