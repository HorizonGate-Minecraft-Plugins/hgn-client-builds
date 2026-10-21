package online.horizongate.hgn.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises Modrinth catalogue requests and parsing against a local HTTP server. */
public final class HgnModCatalogTest {
	public static void main(String[] args) throws Exception {
		searchEncodesFiltersAndIgnoresInvalidHits();
		releasesKeepOnlyCompatibleListedVersions();
		invalidInputNeverReachesTheServer();
		httpAndJsonFailuresAreReported();
		System.out.println("Mod catalogue: loopback request, filtering and failure checks passed.");
	}

	private static void searchEncodesFiltersAndIgnoresInvalidHits() throws Exception {
		try (Fixture fixture = new Fixture()) {
			fixture.respond(200, """
				{"total_hits":7,"hits":[
					{"project_type":"mod","project_id":"Valid1","title":"Useful map",
					 "description":"A map","author":"Cartographer"},
					{"project_type":"mod","project_id":"Valid2","title":"Another mod"},
					{"project_type":"plugin","project_id":"Plugin","title":"Server plugin"},
					{"project_type":"mod","project_id":"bad-id","title":"Invalid ID"},
					{"project_type":"mod","project_id":"Blank","title":"   "},
					{"project_type":"mod","title":"Missing ID"},
					42
				]}
				""");
			HgnModCatalog catalog = fixture.catalog();
			HgnModCatalog.SearchResult result = catalog.search("  Maps & More  ", "1.21.4", "fabric", 40);
			check(result.totalHits() == 7, "Search lost the API total");
			check(result.projects().size() == 2, "Search accepted an invalid or non-mod hit");
			check(result.projects().get(0).equals(new HgnModCatalog.Project(
				"Valid1", "Useful map", "A map", "Cartographer")), "Search changed a valid project");
			check(result.projects().get(1).equals(new HgnModCatalog.Project(
				"Valid2", "Another mod", "", "")), "Optional project fields need empty defaults");

			Captured request = fixture.captured();
			check(request.uri().getPath().equals("/v2/search"), "Search used the wrong endpoint");
			check(request.method().equals("GET"), "Catalogue request must use GET");
			check(request.accept().equals("application/json"), "Catalogue request lacks JSON Accept header");
			check(request.userAgent().startsWith("HorizonGateClient/"), "Catalogue request lacks client User-Agent");
			Map<String, String> parameters = parameters(request.uri());
			check(parameters.keySet().equals(Set.of("query", "facets", "limit", "offset")),
				"Search query parameters changed");
			check(parameters.get("query").equals("Maps & More"), "Search term was not trimmed and encoded");
			check(parameters.get("limit").equals("20") && parameters.get("offset").equals("40"),
				"Search pagination changed");
			JsonArray facets = JsonParser.parseString(parameters.get("facets")).getAsJsonArray();
			check(facets.size() == 3
				&& facet(facets, 0).equals("project_type:mod")
				&& facet(facets, 1).equals("categories:fabric")
				&& facet(facets, 2).equals("versions:1.21.4"),
				"Search must intersect mod, loader and game-version facets");

			fixture.respond(200, """
				{"total_hits":"unknown","hits":[{"project_type":"mod",
				 "project_id":"Valid3","title":"Fallback"}]}
				""");
			check(catalog.search("", "1.21.4", "fabric", 0).totalHits() == 1,
				"Malformed total should fall back to the accepted hit count");
			fixture.respond(200, "{\"total_hits\":-4,\"hits\":[]}");
			check(catalog.search("", "1.21.4", "fabric", 0).totalHits() == 0,
				"Negative total should be clamped to zero");
		}
	}

	private static void releasesKeepOnlyCompatibleListedVersions() throws Exception {
		try (Fixture fixture = new Fixture()) {
			fixture.respond(200, """
				[
				 {"id":"Release1","project_id":"Project1","version_number":"1.2.0",
				  "name":"Stable","version_type":"release","status":"listed",
				  "date_published":"2026-10-01","game_versions":["1.21.4"],"loaders":["fabric"]},
				 {"id":"Beta1","project_id":"Project1","version_number":"1.3.0-b1",
				  "version_type":"beta","status":"listed",
				  "game_versions":["1.21.4"],"loaders":["fabric"]},
				 {"id":"OtherProject","project_id":"Project2","version_number":"2",
				  "version_type":"release","status":"listed","game_versions":["1.21.4"],"loaders":["fabric"]},
				 {"id":"Hidden","project_id":"Project1","version_number":"2",
				  "version_type":"release","status":"unlisted","game_versions":["1.21.4"],"loaders":["fabric"]},
				 {"id":"OtherGame","project_id":"Project1","version_number":"2",
				  "version_type":"release","status":"listed","game_versions":["1.21.5"],"loaders":["fabric"]},
				 {"id":"OtherLoader","project_id":"Project1","version_number":"2",
				  "version_type":"release","status":"listed","game_versions":["1.21.4"],"loaders":["forge"]},
				 {"id":"UnknownChannel","project_id":"Project1","version_number":"2",
				  "version_type":"snapshot","status":"listed","game_versions":["1.21.4"],"loaders":["fabric"]},
				 {"id":"bad-id","project_id":"Project1","version_number":"2",
				  "version_type":"release","status":"listed","game_versions":["1.21.4"],"loaders":["fabric"]},
				 42
				]
				""");
			List<HgnModCatalog.Release> releases = fixture.catalog().releases("Project1", "1.21.4", "fabric");
			check(releases.size() == 2, "Release list accepted an incompatible or unlisted version");
			check(releases.get(0).equals(new HgnModCatalog.Release(
				"Release1", "Project1", "1.2.0", "Stable", "release", "2026-10-01")),
				"Listed release fields changed");
			check(releases.get(0).label().equals("1.2.0 (RELEASE)"), "Release label changed");
			check(releases.get(1).name().isEmpty() && releases.get(1).publishedAt().isEmpty()
				&& releases.get(1).label().equals("1.3.0-b1 (BETA)"),
				"Optional release fields or beta label changed");

			Captured request = fixture.captured();
			check(request.uri().getPath().equals("/v2/project/Project1/version"),
				"Releases used the wrong endpoint");
			Map<String, String> parameters = parameters(request.uri());
			check(parameters.keySet().equals(Set.of("loaders", "game_versions", "include_changelog")),
				"Release query parameters changed");
			check(JsonParser.parseString(parameters.get("loaders")).getAsJsonArray().get(0)
				.getAsString().equals("fabric"), "Release loader filter changed");
			check(JsonParser.parseString(parameters.get("game_versions")).getAsJsonArray().get(0)
				.getAsString().equals("1.21.4"), "Release game-version filter changed");
			check(parameters.get("include_changelog").equals("false"), "Release request fetched changelogs");
		}
	}

	private static void invalidInputNeverReachesTheServer() throws Exception {
		try (Fixture fixture = new Fixture()) {
			HgnModCatalog catalog = fixture.catalog();
			expect(IllegalArgumentException.class, "Unsupported Minecraft", () ->
				catalog.search("", "1.21.4&admin=true", "fabric", 0));
			expect(IllegalArgumentException.class, "Unsupported Minecraft", () ->
				catalog.search("", "1.21.4", "quilt", 0));
			expect(IllegalArgumentException.class, "Invalid catalogue", () ->
				catalog.search("x".repeat(81), "1.21.4", "fabric", 0));
			expect(IllegalArgumentException.class, "Invalid catalogue", () ->
				catalog.search("", "1.21.4", "fabric", -1));
			expect(IllegalArgumentException.class, "Invalid catalogue", () ->
				catalog.search("", "1.21.4", "fabric", 5_001));
			expect(NullPointerException.class, "query", () ->
				catalog.search(null, "1.21.4", "fabric", 0));
			expect(IllegalArgumentException.class, "Invalid Modrinth project", () ->
				catalog.releases("../Project1", "1.21.4", "fabric"));
			expect(IllegalArgumentException.class, "Invalid Modrinth project", () ->
				catalog.releases(null, "1.21.4", "fabric"));
			check(fixture.requestCount() == 0, "Invalid input reached the HTTP server");
			expect(IllegalArgumentException.class, "Catalogue endpoint", () ->
				new HgnModCatalog(HttpClient.newHttpClient(), URI.create("https://example.com/v2/")));
			expect(IllegalArgumentException.class, "Catalogue endpoint", () ->
				new HgnModCatalog(HttpClient.newHttpClient(), fixture.endpoint().resolve("search")));
		}
	}

	private static void httpAndJsonFailuresAreReported() throws Exception {
		try (Fixture fixture = new Fixture()) {
			HgnModCatalog catalog = fixture.catalog();
			fixture.respond(429, "{}");
			expect(IOException.class, "HTTP 429", () -> catalog.search("", "1.21.4", "fabric", 0));
			fixture.respond(200, "{broken");
			expect(IOException.class, "invalid JSON", () -> catalog.search("", "1.21.4", "fabric", 0));
			fixture.respond(200, "[]");
			expect(IOException.class, "invalid object", () -> catalog.search("", "1.21.4", "fabric", 0));
			fixture.respond(200, "{}");
			expect(IOException.class, "invalid hits list", () -> catalog.search("", "1.21.4", "fabric", 0));
			fixture.respond(200, "{}");
			expect(IOException.class, "invalid list", () -> catalog.releases("Project1", "1.21.4", "fabric"));
			byte[] oversized = new byte[8 * 1024 * 1024 + 1];
			Arrays.fill(oversized, (byte) ' ');
			fixture.respond(200, oversized);
			expect(IOException.class, "too large", () -> catalog.search("", "1.21.4", "fabric", 0));
		}
	}

	private static String facet(JsonArray facets, int index) {
		JsonArray group = facets.get(index).getAsJsonArray();
		check(group.size() == 1, "Search facet groups must contain one required term");
		return group.get(0).getAsString();
	}

	private static Map<String, String> parameters(URI uri) {
		Map<String, String> parameters = new LinkedHashMap<>();
		for (String field : uri.getRawQuery().split("&")) {
			int separator = field.indexOf('=');
			check(separator > 0, "Malformed catalogue query parameter");
			String name = URLDecoder.decode(field.substring(0, separator), StandardCharsets.UTF_8);
			String value = URLDecoder.decode(field.substring(separator + 1), StandardCharsets.UTF_8);
			check(parameters.put(name, value) == null, "Duplicate catalogue query parameter");
		}
		return parameters;
	}

	private static void expect(Class<? extends Exception> expected, String message,
		CheckedAction action) throws Exception {
		try {
			action.run();
		} catch (Exception error) {
			check(expected.isInstance(error) && error.getMessage() != null
				&& error.getMessage().contains(message),
				"Expected " + expected.getSimpleName() + " containing '" + message + "', got " + error);
			return;
		}
		throw new AssertionError("Expected " + expected.getSimpleName() + " containing '" + message + "'");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface CheckedAction {
		void run() throws Exception;
	}

	private record Captured(URI uri, String method, String accept, String userAgent) {
	}

	private record Response(int status, byte[] body) {
	}

	private static final class Fixture implements AutoCloseable {
		private final HttpServer server;
		private final AtomicReference<Captured> captured = new AtomicReference<>();
		private final AtomicInteger requestCount = new AtomicInteger();
		private volatile Response response = new Response(200, "{}".getBytes(StandardCharsets.UTF_8));

		private Fixture() throws IOException {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/v2/", this::handle);
			server.start();
		}

		private HgnModCatalog catalog() {
			return new HgnModCatalog(HttpClient.newHttpClient(), endpoint());
		}

		private URI endpoint() {
			return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/");
		}

		private void respond(int status, String body) {
			respond(status, body.getBytes(StandardCharsets.UTF_8));
		}

		private void respond(int status, byte[] body) {
			response = new Response(status, body);
		}

		private Captured captured() {
			Captured value = captured.get();
			check(value != null, "The catalogue never sent an HTTP request");
			return value;
		}

		private int requestCount() {
			return requestCount.get();
		}

		private void handle(HttpExchange exchange) throws IOException {
			requestCount.incrementAndGet();
			captured.set(new Captured(exchange.getRequestURI(), exchange.getRequestMethod(),
				exchange.getRequestHeaders().getFirst("Accept"),
				exchange.getRequestHeaders().getFirst("User-Agent")));
			Response value = response;
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(value.status(), value.body().length);
			try (OutputStream stream = exchange.getResponseBody()) {
				stream.write(value.body());
			} finally {
				exchange.close();
			}
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}
}
