package com.wynnphoton.lighttest;

import com.wynnphoton.lighttest.LightTestCases.Compare;
import com.wynnphoton.lighttest.LightTestCases.OptionSet;
import com.wynnphoton.lighttest.LightTestCases.Spot;
import com.wynnphoton.lighttest.LightTestCases.Time;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the light test's screenshots into {@code report.html} (tables and a contact sheet) and
 * {@code luminance.csv}. Usage: {@code LightTestReport <case file> <output dir>}. Plain Java, no Minecraft.
 *
 * <p>Brightness is the mean luminance of the picture as displayed: each pixel's sRGB value is decoded to linear
 * light and weighted with the Rec. 709 luminance weights. It is shown as a percentage of white.</p>
 */
public final class LightTestReport {
	public static void main(String[] args) throws IOException {
		LightTestCases cases = LightTestCases.read(Path.of(args[0]));
		Path dir = Path.of(args[1]);
		if (Files.exists(dir.resolve("error.txt"))) {
			throw new IOException("The light test failed:\n" + Files.readString(dir.resolve("error.txt")));
		}
		if (!Files.exists(dir.resolve("done.txt"))) {
			throw new IOException("The light test did not finish (no done.txt in " + dir + "). See run/logs/latest.log.");
		}

		Map<String, Double> luminance = new HashMap<>();
		StringBuilder csv = new StringBuilder("spot,time,set,options,mean_luminance_percent\n");
		for (Spot spot : cases.spots) {
			for (Time time : cases.times) {
				for (OptionSet set : cases.sets) {
					String file = LightTestCases.shotName(spot, time, set);
					if (!Files.exists(dir.resolve(file))) {
						continue;
					}
					double value = meanLuminance(dir.resolve(file));
					luminance.put(file, value);
					csv.append(String.join(",", spot.name(), time.name(), set.name(), options(set),
							String.format(Locale.ROOT, "%.4f", value))).append('\n');
				}
			}
		}
		Files.writeString(dir.resolve("luminance.csv"), csv.toString(), StandardCharsets.UTF_8);

		StringBuilder html = new StringBuilder("""
				<!doctype html>
				<html lang="en"><head><meta charset="utf-8"><title>Wynn-Photon light test</title>
				<style>
				body { font: 14px/1.4 system-ui, sans-serif; background: #181818; color: #ddd; margin: 24px; }
				h1, h2, h3 { color: #fff; } h2 { margin-top: 40px; }
				table { border-collapse: collapse; margin: 8px 0 24px; }
				th, td { border: 1px solid #444; padding: 3px 8px; text-align: right; white-space: nowrap; }
				th { background: #2a2a2a; } td:first-child, th:first-child { text-align: left; }
				.up { background: #1f4d2b; } .down { background: #5a2323; } .same { color: #888; }
				.shots { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 20px; }
				figure { margin: 0; width: 240px; } figure img { width: 100%; display: block; }
				figcaption { font-size: 12px; } code { color: #9cf; }
				</style></head><body>
				<h1>Wynn-Photon light test</h1>
				<p>Brightness is the mean luminance of the displayed picture, as a percentage of white (sRGB decoded to
				linear light, Rec. 709 weights). A ratio of 2.00 is twice the light on screen, after tone mapping.
				Ratios within about the measurement noise (see the noise table) mean no change.</p>
				""");

		html.append("<h2>Option sets</h2><table><tr><th>Set</th><th>Shader options (everything else is the pack default)</th></tr>");
		for (OptionSet set : cases.sets) {
			html.append("<tr><td>").append(set.name()).append("</td><td style='text-align:left'><code>")
					.append(options(set)).append("</code></td></tr>");
		}
		html.append("</table>");

		html.append("<h2>Mean luminance, % of white</h2><table><tr><th>Spot, time</th>");
		for (OptionSet set : cases.sets) {
			html.append("<th>").append(set.name()).append("</th>");
		}
		html.append("</tr>");
		for (Spot spot : cases.spots) {
			for (Time time : cases.times) {
				html.append("<tr><td>").append(spot.name()).append(", ").append(time.name()).append("</td>");
				for (OptionSet set : cases.sets) {
					Double value = luminance.get(LightTestCases.shotName(spot, time, set));
					html.append("<td>").append(value == null ? "" : String.format(Locale.ROOT, "%.2f", value)).append("</td>");
				}
				html.append("</tr>");
			}
		}
		html.append("</table>");

		html.append("<h2>Comparisons</h2><p>Each cell: luminance of B divided by luminance of A. Green is at least 5% "
				+ "brighter, red at least 5% darker.</p><table><tr><th>Spot, time</th>");
		for (Compare c : cases.compares) {
			html.append("<th title='").append(c.title()).append("'>").append(c.b()).append("<br>/ ").append(c.a()).append("</th>");
		}
		html.append("</tr>");
		StringBuilder ratios = new StringBuilder("spot,time,a,b,luminance_a,luminance_b,ratio\n");
		for (Spot spot : cases.spots) {
			for (Time time : cases.times) {
				html.append("<tr><td>").append(spot.name()).append(", ").append(time.name()).append("</td>");
				for (Compare c : cases.compares) {
					Double a = luminance.get(spot.name() + "__" + time.name() + "__" + c.a() + ".png");
					Double b = luminance.get(spot.name() + "__" + time.name() + "__" + c.b() + ".png");
					if (a == null || b == null) {
						html.append("<td></td>");
						continue;
					}
					double ratio = b / Math.max(a, 1e-6);
					String cls = ratio >= 1.05 ? "up" : ratio <= 1 / 1.05 ? "down" : "same";
					html.append("<td class='").append(cls).append("'>").append(String.format(Locale.ROOT, "%.2f", ratio)).append("</td>");
					ratios.append(String.format(Locale.ROOT, "%s,%s,%s,%s,%.4f,%.4f,%.4f%n", spot.name(), time.name(),
							c.a(), c.b(), a, b, ratio));
				}
				html.append("</tr>");
			}
		}
		html.append("</table><ul>");
		for (Compare c : cases.compares) {
			html.append("<li><b>").append(c.b()).append(" / ").append(c.a()).append("</b>: ").append(c.title()).append("</li>");
		}
		html.append("</ul>");
		Files.writeString(dir.resolve("ratios.csv"), ratios.toString(), StandardCharsets.UTF_8);

		html.append("<h2>Shots</h2><p>Click a picture for the full size.</p>");
		for (Spot spot : cases.spots) {
			for (Time time : cases.times) {
				html.append("<h3>").append(spot.name()).append(", ").append(time.name()).append("</h3><div class='shots'>");
				for (OptionSet set : cases.sets) {
					String file = LightTestCases.shotName(spot, time, set);
					Double value = luminance.get(file);
					if (value == null) {
						continue;
					}
					html.append("<figure><a href='").append(file).append("'><img loading='lazy' src='").append(file)
							.append("'></a><figcaption>").append(set.name()).append(": ")
							.append(String.format(Locale.ROOT, "%.2f", value)).append("</figcaption></figure>");
				}
				html.append("</div>");
			}
		}
		html.append("</body></html>\n");
		Files.writeString(dir.resolve("report.html"), html.toString(), StandardCharsets.UTF_8);
		System.out.println("Light test report: " + dir.resolve("report.html").toAbsolutePath().toUri());
	}

	private static String options(OptionSet set) {
		return String.join(" ", set.options().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
	}

	private static final double[] SRGB_TO_LINEAR = new double[256];

	static {
		for (int i = 0; i < 256; i++) {
			double c = i / 255.0;
			SRGB_TO_LINEAR[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
		}
	}

	/** Mean linear luminance of the picture, 0 to 100. */
	static double meanLuminance(Path file) throws IOException {
		BufferedImage image = ImageIO.read(file.toFile());
		int w = image.getWidth();
		int h = image.getHeight();
		int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
		double sum = 0;
		for (int rgb : pixels) {
			sum += 0.2126 * SRGB_TO_LINEAR[(rgb >> 16) & 255] + 0.7152 * SRGB_TO_LINEAR[(rgb >> 8) & 255]
					+ 0.0722 * SRGB_TO_LINEAR[rgb & 255];
		}
		return 100.0 * sum / pixels.length;
	}
}
