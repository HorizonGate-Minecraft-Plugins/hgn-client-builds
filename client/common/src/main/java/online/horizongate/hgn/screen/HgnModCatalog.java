package online.horizongate.hgn.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Read-only Modrinth catalogue for the currently running Minecraft version and loader. */
public final class HgnModCatalog {
	private static final URI MODRINTH_API = URI.create("https://api.modrinth.com/v2/");
	private static final String USER_AGENT =
		"HorizonGateClient/1 (https://github.com/HorizonGate-Minecraft-Plugins/HGN-MC-Launcher)";
	private static final Pattern PROJECT_ID = Pattern.compile("[A-Za-z0-9]{1,64}");
	private static final Pattern GAME_VERSION = Pattern.compile("[0-9]{1,2}\\.[0-9]{1,2}(?:\\.[0-9]{1,2})?");
	private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
	private final HttpClient client;
	private final URI api;

	public HgnModCatalog() {
		this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NEVER).build(), MODRINTH_API);
	}

	/** Package-private loopback endpoint keeps the parser testable without a live API dependency. */
	HgnModCatalog(HttpClient client, URI api) {
		this.client = Objects.requireNonNull(client, "client");
		this.api = Objects.requireNonNull(api, "api");
		boolean production = api.equals(MODRINTH_API);
		boolean localTest = "http".equals(api.getScheme()) && api.getHost() != null
			&& (api.getHost().equals("localhost") || api.getHost().equals("127.0.0.1"));
		if ((!production && !localTest) || !api.getPath().endsWith("/")) {
			throw new IllegalArgumentException("Catalogue endpoint must be Modrinth or local test loopback");
		}
	}

	public SearchResult search(String query, String minecraft, String loader, int offset)
		throws IOException, InterruptedException {
		validateTarget(minecraft, loader);
		String trimmed = Objects.requireNonNull(query, "query").trim();
		if (trimmed.length() > 80 || offset < 0 || offset > 5_000) {
			throw new IllegalArgumentException("Invalid catalogue search request");
		}
		JsonArray facets = new JsonArray();
		for (String facet : List.of("project_type:mod", "categories:" + loader,
			"versions:" + minecraft)) {
			JsonArray group = new JsonArray();
			group.add(facet);
			facets.add(group);
		}
		URI request = api.resolve("search?query=" + encode(trimmed) + "&facets="
			+ encode(facets.toString()) + "&limit=20&offset=" + offset);
		JsonObject body = fetchObject(request);
		JsonElement hitsValue = body.get("hits");
		if (hitsValue == null || !hitsValue.isJsonArray()) {
			throw new IOException("Mod catalogue returned an invalid hits list");
		}
		JsonArray hits = hitsValue.getAsJsonArray();
		List<Project> projects = new ArrayList<>();
		for (JsonElement hit : hits) {
			if (!hit.isJsonObject()) continue;
			JsonObject value = hit.getAsJsonObject();
			if (!"mod".equals(string(value, "project_type"))) continue;
			String id = string(value, "project_id");
			String title = string(value, "title");
			if (id == null || !PROJECT_ID.matcher(id).matches() || title == null || title.isBlank()) continue;
			projects.add(new Project(id, title, orEmpty(string(value, "description")),
				orEmpty(string(value, "author"))));
		}
		return new SearchResult(List.copyOf(projects), integer(body, "total_hits", projects.size()));
	}

	public List<Release> releases(String projectId, String minecraft, String loader)
		throws IOException, InterruptedException {
		validateTarget(minecraft, loader);
		if (projectId == null || !PROJECT_ID.matcher(projectId).matches()) {
			throw new IllegalArgumentException("Invalid Modrinth project ID");
		}
		String filters = "loaders=" + encode("[\"" + loader + "\"]")
			+ "&game_versions=" + encode("[\"" + minecraft + "\"]")
			+ "&include_changelog=false";
		JsonArray versions = fetchArray(api.resolve("project/" + projectId + "/version?" + filters));
		List<Release> releases = new ArrayList<>();
		for (JsonElement entry : versions) {
			if (!entry.isJsonObject()) continue;
			JsonObject value = entry.getAsJsonObject();
			String id = string(value, "id");
			String number = string(value, "version_number");
			String type = string(value, "version_type");
			if (id == null || !PROJECT_ID.matcher(id).matches() || number == null
				|| !projectId.equals(string(value, "project_id"))
				|| !"listed".equals(string(value, "status"))
				|| !List.of("release", "beta", "alpha").contains(type)
				|| !contains(array(value, "game_versions"), minecraft)
				|| !contains(array(value, "loaders"), loader)) continue;
			releases.add(new Release(id, projectId, number,
				orEmpty(string(value, "name")), type,
				orEmpty(string(value, "date_published"))));
		}
		return List.copyOf(releases);
	}

	private JsonObject fetchObject(URI uri) throws IOException, InterruptedException {
		JsonElement value = fetch(uri);
		if (!value.isJsonObject()) throw new IOException("Mod catalogue returned an invalid object");
		return value.getAsJsonObject();
	}

	private JsonArray fetchArray(URI uri) throws IOException, InterruptedException {
		JsonElement value = fetch(uri);
		if (!value.isJsonArray()) throw new IOException("Mod catalogue returned an invalid list");
		return value.getAsJsonArray();
	}

	private JsonElement fetch(URI uri) throws IOException, InterruptedException {
		if (!uri.getScheme().equals(api.getScheme()) || !uri.getHost().equals(api.getHost())
			|| uri.getPort() != api.getPort()) throw new IOException("Unexpected catalogue origin");
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12))
			.header("Accept", "application/json").header("User-Agent", USER_AGENT).GET().build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream stream = response.body()) {
			if (response.statusCode() != 200) {
				throw new IOException("Mod catalogue returned HTTP " + response.statusCode());
			}
			byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
			if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("Mod catalogue response is too large");
			try {
				return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
			} catch (RuntimeException error) {
				throw new IOException("Mod catalogue returned invalid JSON", error);
			}
		}
	}

	private static void validateTarget(String minecraft, String loader) {
		if (minecraft == null || !GAME_VERSION.matcher(minecraft).matches()
			|| loader == null || !List.of("fabric", "forge", "neoforge").contains(loader)) {
			throw new IllegalArgumentException("Unsupported Minecraft version or loader");
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String string(JsonObject value, String field) {
		JsonElement element = value.get(field);
		if (element == null || !element.isJsonPrimitive()
			|| !element.getAsJsonPrimitive().isString()) return null;
		return element.getAsString();
	}

	private static int integer(JsonObject value, String field, int fallback) {
		JsonElement element = value.get(field);
		if (element == null || !element.isJsonPrimitive()) return fallback;
		try {
			return Math.max(0, element.getAsInt());
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static JsonArray array(JsonObject value, String field) {
		JsonElement element = value.get(field);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	private static boolean contains(JsonArray values, String expected) {
		for (JsonElement value : values) {
			if (value.isJsonPrimitive() && expected.equals(value.getAsString())) return true;
		}
		return false;
	}

	public record Project(String id, String title, String description, String author) {
	}

	public record SearchResult(List<Project> projects, int totalHits) {
	}

	public record Release(String id, String projectId, String versionNumber,
		String name, String channel, String publishedAt) {
		public String label() {
			return versionNumber + " (" + channel.toUpperCase(Locale.ROOT) + ")";
		}
	}
}
