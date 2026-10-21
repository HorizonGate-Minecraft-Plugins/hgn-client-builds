package online.horizongate.hgn.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import net.minecraft.client.Minecraft;
import online.horizongate.hgn.HgnClient;

/**
 * HTTPS transport for launcher-member cosmetics. The launcher supplies a
 * short-lived bearer token through a one-use file; no Minecraft server plugin,
 * plugin message, Mojang access token, or server resource pack is involved.
 */
final class HgnMembershipClient {
	private static final int MAX_SESSION_BYTES = 1024 * 1024;
	private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
	private static final int MAX_CONNECT_HEADER_BYTES = 2048;
	private static final String PINNED_ALPHA_PUBLIC_HOST = "81.172.128.251";
	private static final int PINNED_ALPHA_PORT = 25582;
	private static final String BACKEND_LAN_ADDRESS_ENV = "HGN_CLIENT_BACKEND_LAN_ADDRESS";
	private static final int LAN_CONNECT_TIMEOUT_MILLIS = 2000;
	private static final int LOOPBACK_CONNECT_TIMEOUT_MILLIS = 250;
	private static final int LOOPBACK_TLS_TIMEOUT_MILLIS = 1000;
	private static final long REQUEST_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(12);
	private static final long LAN_RETRY_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(30);
	private static final long NO_LAN_FAILURE_NANOS = Long.MIN_VALUE;
	private static final String PINNED_ROOT_RESOURCE =
		"/assets/hgn-client/security/hgn_alpha_ip_root.pem";
	private static final String MEMBERSHIP_STATUS_ENV = "HGN_CLIENT_MEMBERSHIP_STATUS";
	private static final String PREPARATION_UNAVAILABLE = "preparation_unavailable";
	private static final Set<String> ROUTES = Set.of(
		"/v2/cosmetics/snapshot",
		"/v2/cosmetics/equip",
		"/v2/cosmetics/unequip",
		"/v2/cosmetics/equipment",
		"/v2/rewards/active-minute"
	);

	private static final AtomicLong LAN_LAST_FAILURE_NANOS =
		new AtomicLong(NO_LAN_FAILURE_NANOS);
	private static final AtomicInteger LAN_ACTIVE_CONNECTIONS = new AtomicInteger();
	private static final AtomicInteger NETWORK_THREAD_SEQUENCE = new AtomicInteger();
	private static final AtomicInteger RESPONSE_THREAD_SEQUENCE = new AtomicInteger();
	private static final ExecutorService NETWORK_EXECUTOR = Executors.newFixedThreadPool(2,
		action -> daemonThread("hgn-member-worker-" + NETWORK_THREAD_SEQUENCE.incrementAndGet(), action));
	private static final ExecutorService RESPONSE_EXECUTOR = Executors.newFixedThreadPool(2,
		action -> daemonThread("hgn-member-response-" + RESPONSE_THREAD_SEQUENCE.incrementAndGet(), action));
	private static volatile Transports transports;
	private static volatile Session session;
	private static volatile Initialization initialization = Initialization.PENDING;

	private HgnMembershipClient() {
	}

	private static Transports transports() throws IOException {
		Transports current = transports;
		if (current != null) return current;
		synchronized (HgnMembershipClient.class) {
			if (transports == null) transports = pinnedHttpClients();
			return transports;
		}
	}

	private static Transports pinnedHttpClients() throws IOException {
		try (InputStream input = HgnMembershipClient.class.getResourceAsStream(PINNED_ROOT_RESOURCE)) {
			if (input == null) throw new IOException("pinned HGN TLS root is missing");
			Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
			KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
			trustStore.load(null, null);
			trustStore.setCertificateEntry("hgn-alpha-direct-ip", certificate);
			TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
				TrustManagerFactory.getDefaultAlgorithm());
			trustManagers.init(trustStore);
			SSLContext tls = SSLContext.getInstance("TLS");
			tls.init(null, trustManagers.getTrustManagers(), null);
			HttpClient publicTransport = pinnedHttpClient(
				tls, Duration.ofSeconds(8), directProxySelector());
			HttpClient lanTransport = null;
			try {
				InetSocketAddress tunnel = startLanTunnel(tls);
				lanTransport = pinnedHttpClient(
					tls, Duration.ofSeconds(2), ProxySelector.of(tunnel));
			} catch (IOException | RuntimeException ignored) {
			}
			return new Transports(lanTransport, publicTransport);
		} catch (Exception exception) {
			if (exception instanceof IOException ioException) throw ioException;
			throw new IOException("pinned HGN TLS clients could not be created", exception);
		}
	}

	private static HttpClient pinnedHttpClient(SSLContext tls, Duration connectTimeout,
		ProxySelector proxySelector) {
		SSLParameters parameters = new SSLParameters();
		parameters.setEndpointIdentificationAlgorithm("HTTPS");
		parameters.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
		return HttpClient.newBuilder()
			.sslContext(tls)
			.sslParameters(parameters)
			.executor(NETWORK_EXECUTOR)
			.connectTimeout(connectTimeout)
			.followRedirects(HttpClient.Redirect.NEVER)
			.version(HttpClient.Version.HTTP_1_1)
			.proxy(proxySelector)
			.build();
	}

	private static ProxySelector directProxySelector() {
		return new ProxySelector() {
			@Override
			public List<Proxy> select(URI uri) {
				return List.of(Proxy.NO_PROXY);
			}

			@Override
			public void connectFailed(URI uri, SocketAddress address, IOException failure) {
			}
		};
	}

	private static InetSocketAddress startLanTunnel(SSLContext tls) throws IOException {
		InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
		ServerSocket listener = new ServerSocket();
		listener.bind(new InetSocketAddress(loopback, 0), 16);
		Thread acceptor = daemonThread("hgn-lan-tunnel", () -> acceptLanConnections(listener, tls));
		try {
			acceptor.start();
		} catch (RuntimeException exception) {
			listener.close();
			throw exception;
		}
		return new InetSocketAddress(loopback, listener.getLocalPort());
	}

	private static void acceptLanConnections(ServerSocket listener, SSLContext tls) {
		while (!listener.isClosed()) {
			Socket downstream;
			try {
				downstream = listener.accept();
			} catch (IOException ignored) {
				return;
			}
			if (LAN_ACTIVE_CONNECTIONS.incrementAndGet() > 16) {
				LAN_ACTIVE_CONNECTIONS.decrementAndGet();
				closeQuietly(downstream);
				continue;
			}
			Thread connection = daemonThread("hgn-lan-connection", () -> {
				try (downstream) {
					serveLanConnection(downstream, tls);
				} catch (IOException ignored) {
				} finally {
					LAN_ACTIVE_CONNECTIONS.decrementAndGet();
				}
			});
			try {
				connection.start();
			} catch (RuntimeException ignored) {
				LAN_ACTIVE_CONNECTIONS.decrementAndGet();
				closeQuietly(downstream);
			}
		}
	}

	private static void serveLanConnection(Socket downstream, SSLContext tls) throws IOException {
		downstream.setSoTimeout(3000);
		if (!readExactConnectRequest(downstream.getInputStream())) {
			writeProxyResponse(downstream, "HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n");
			return;
		}
		Socket backend;
		try {
			backend = connectInternalBackend(tls);
		} catch (IOException ignored) {
			writeProxyResponse(downstream,
				"HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n");
			return;
		}
		try (Socket upstream = backend) {
			writeProxyResponse(downstream, "HTTP/1.1 200 Connection Established\r\n\r\n");
			downstream.setSoTimeout(0);
			relayLanConnection(downstream, upstream);
		}
	}

	private static Socket connectInternalBackend(SSLContext tls) throws IOException {
		InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
		IOException loopbackFailure;
		try {
			// A different local service may own the port. Verify the pinned HGN TLS
			// identity before selecting loopback, without sending the POST twice.
			probePinnedLoopback(tls);
			return connectBackend(loopback, LOOPBACK_CONNECT_TIMEOUT_MILLIS);
		} catch (IOException failure) {
			loopbackFailure = failure;
		}
		String configured;
		try {
			configured = System.getenv(BACKEND_LAN_ADDRESS_ENV);
		} catch (SecurityException failure) {
			throw new IOException("HGN backend LAN address could not be read", failure);
		}
		InetAddress lan = HgnMembershipLanAddress.configured(configured);
		if (lan.equals(loopback)) throw loopbackFailure;
		return connectBackend(lan, LAN_CONNECT_TIMEOUT_MILLIS);
	}

	private static Socket connectBackend(InetAddress address, int timeout) throws IOException {
		Socket upstream = new Socket();
		try {
			upstream.connect(new InetSocketAddress(address, PINNED_ALPHA_PORT), timeout);
			return upstream;
		} catch (IOException failure) {
			closeQuietly(upstream);
			throw failure;
		}
	}

	private static void probePinnedLoopback(SSLContext tls) throws IOException {
		Socket socket = new Socket();
		try {
			socket.connect(new InetSocketAddress(
				InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), PINNED_ALPHA_PORT),
				LOOPBACK_CONNECT_TIMEOUT_MILLIS);
			socket.setSoTimeout(LOOPBACK_TLS_TIMEOUT_MILLIS);
			try (SSLSocket verified = (SSLSocket) tls.getSocketFactory().createSocket(
				socket, PINNED_ALPHA_PUBLIC_HOST, PINNED_ALPHA_PORT, true)) {
				SSLParameters parameters = verified.getSSLParameters();
				parameters.setEndpointIdentificationAlgorithm("HTTPS");
				parameters.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
				verified.setSSLParameters(parameters);
				verified.startHandshake();
			}
		} finally {
			closeQuietly(socket);
		}
	}

	private static boolean readExactConnectRequest(InputStream input) throws IOException {
		byte[] header = new byte[MAX_CONNECT_HEADER_BYTES];
		int length = 0;
		while (length < header.length) {
			int value = input.read();
			if (value < 0 || value > 127) return false;
			header[length++] = (byte) value;
			if (length >= 4
				&& header[length - 4] == '\r' && header[length - 3] == '\n'
				&& header[length - 2] == '\r' && header[length - 1] == '\n') {
				String request = new String(header, 0, length, StandardCharsets.US_ASCII);
				int firstLineEnd = request.indexOf("\r\n");
				String expected = "CONNECT " + PINNED_ALPHA_PUBLIC_HOST + ':'
					+ PINNED_ALPHA_PORT + " HTTP/1.1";
				return firstLineEnd > 0 && expected.equals(request.substring(0, firstLineEnd));
			}
		}
		return false;
	}

	private static void relayLanConnection(Socket downstream, Socket upstream) throws IOException {
		Thread response = daemonThread("hgn-lan-response", () -> {
			try {
				upstream.getInputStream().transferTo(downstream.getOutputStream());
				downstream.shutdownOutput();
			} catch (IOException ignored) {
			}
		});
		response.start();
		try {
			downstream.getInputStream().transferTo(upstream.getOutputStream());
			upstream.shutdownOutput();
			response.join(1000);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private static void writeProxyResponse(Socket socket, String response) throws IOException {
		OutputStream output = socket.getOutputStream();
		output.write(response.getBytes(StandardCharsets.US_ASCII));
		output.flush();
	}

	private static Thread daemonThread(String name, Runnable action) {
		Thread thread = new Thread(action, name);
		thread.setDaemon(true);
		return thread;
	}

	private static void closeQuietly(Socket socket) {
		try {
			socket.close();
		} catch (IOException ignored) {
		}
	}

	static boolean initialize() {
		if (initialization != Initialization.PENDING) {
			return initialization == Initialization.ACTIVE;
		}
		String configured = System.getenv("HGN_CLIENT_SESSION_FILE");
		if (configured == null || configured.isBlank()) {
			initialization = Initialization.TERMINAL;
			if (PREPARATION_UNAVAILABLE.equals(System.getenv(MEMBERSHIP_STATUS_ENV))) {
				HgnCosmetics.membershipLaunchUnavailable();
				HgnClient.LOGGER.info("HGN member cosmetics could not be prepared for this launch.");
			} else {
				HgnClient.LOGGER.info(
					"HGN member cosmetics are unavailable: launcher session was not supplied.");
			}
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (!runtimeReady(minecraft)) return false;
		UUID expectedPlayerId = minecraft.getUser().getProfileId();
		String expectedUsername = minecraft.getUser().getName();
		Path gameDirectory = minecraft.gameDirectory.toPath();
		synchronized (HgnMembershipClient.class) {
			if (initialization != Initialization.PENDING) {
				return initialization == Initialization.ACTIVE;
			}
			initialization = Initialization.INITIALIZING;
			try {
				NETWORK_EXECUTOR.execute(() -> initializeSession(configured, gameDirectory,
					expectedPlayerId, expectedUsername));
			} catch (RuntimeException failure) {
				initialization = Initialization.TERMINAL;
				HgnClient.LOGGER.warn("HGN member cosmetics initialization could not start.");
			}
		}
		return false;
	}

	private static void initializeSession(String configured, Path gameDirectory,
		UUID expectedPlayerId, String expectedUsername) {
		Path ownedFile = null;
		try {
			ownedFile = ownedSessionFile(configured, gameDirectory);
			long size = Files.size(ownedFile);
			if (size <= 0 || size > MAX_SESSION_BYTES) {
				throw new IOException("session file has an invalid size");
			}
			JsonObject object = JsonParser.parseString(
				Files.readString(ownedFile, StandardCharsets.UTF_8)).getAsJsonObject();
			UUID playerId = UUID.fromString(object.get("playerId").getAsString());
			String username = object.get("username").getAsString();
			if (!playerId.equals(expectedPlayerId)
				|| !username.equalsIgnoreCase(expectedUsername)) {
				throw new IOException("launcher session belongs to another Minecraft profile");
			}
			URI base = validatedBase(object.get("apiBase").getAsString());
			String token = object.get("accessToken").getAsString();
			long expiresAt = object.get("expiresAt").getAsLong();
			JsonObject launchSnapshot = object.getAsJsonObject("snapshot");
			long now = System.currentTimeMillis();
			if (token.length() < 40 || token.length() > 128
				|| !token.chars().allMatch(value -> Character.isLetterOrDigit(value)
					|| value == '_' || value == '-')
				|| expiresAt <= now || expiresAt > now + Duration.ofHours(24).toMillis()
					+ Duration.ofMinutes(5).toMillis()) {
				throw new IOException("launcher session is invalid or expired");
			}
			Session accepted = new Session(base, token, expiresAt, expectedPlayerId);
			// Build certificate stores, HTTP clients and the optional LAN tunnel on
			// this worker before exposing the session to the game thread.
			transports();
			synchronized (HgnMembershipClient.class) {
				if (initialization != Initialization.INITIALIZING) return;
				if (launchSnapshot != null
					&& !HgnCosmetics.acceptLaunchSnapshot(launchSnapshot, expectedPlayerId)) {
					throw new IOException("launcher Cosmetics snapshot is invalid");
				}
				session = accepted;
				initialization = Initialization.ACTIVE;
			}
			HgnClient.LOGGER.info("HGN member cosmetics session loaded from the launcher.");
		} catch (Exception ignored) {
			synchronized (HgnMembershipClient.class) {
				if (initialization == Initialization.INITIALIZING) {
					initialization = Initialization.TERMINAL;
				}
			}
			HgnClient.LOGGER.warn("HGN member cosmetics session handoff was rejected.");
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null) HgnCosmetics.membershipLaunchUnavailable();
			else minecraft.execute(HgnCosmetics::membershipLaunchUnavailable);
		} finally {
			if (ownedFile != null) {
				try {
					Files.deleteIfExists(ownedFile);
				} catch (Exception ignored) {
					HgnClient.LOGGER.debug(
						"Could not delete the consumed HGN session handoff file.");
				}
			}
		}
	}

	private static boolean runtimeReady(Minecraft minecraft) {
		return minecraft != null && minecraft.getUser() != null
			&& minecraft.getUser().getProfileId() != null
			&& minecraft.gameDirectory != null;
	}

	private static Path ownedSessionFile(String configured, Path gameDirectory) throws IOException {
		Path supplied = Path.of(configured);
		if (!supplied.isAbsolute()) throw new IOException("session file path must be absolute");
		Path handoffDirectory = gameDirectory.resolve(".hgn-client").toRealPath();
		Path file = supplied.normalize();
		Path parent = file.getParent();
		String name = file.getFileName() == null ? "" : file.getFileName().toString();
		if (parent == null || !parent.toRealPath().equals(handoffDirectory)
			|| !name.matches("session-[A-Za-z0-9]{6,64}\\.json") || Files.isSymbolicLink(file)) {
			throw new IOException("session file is outside the launcher handoff directory");
		}
		BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class,
			LinkOption.NOFOLLOW_LINKS);
		if (!attributes.isRegularFile()) throw new IOException("session handoff is not a regular file");
		return file;
	}

	static boolean available() {
		if (initialization != Initialization.ACTIVE) return false;
		Session current = session;
		if (current == null) return false;
		if (current.expiresAt() <= System.currentTimeMillis()) {
			invalidateSession(current, "membership_session_expired",
				"HGN member session expired. Relaunch from the HGN Launcher to reconnect.");
			return false;
		}
		return true;
	}

	static boolean initializing() {
		return initialization == Initialization.PENDING
			|| initialization == Initialization.INITIALIZING;
	}

	static UUID playerId() {
		Session current = session;
		return current == null ? null : current.playerId();
	}

	static MemberRequest post(String route, JsonObject body) {
		Session current = session;
		if (!available() || current == null) {
			return MemberRequest.failed(current, new IOException("HGN member session is unavailable"));
		}
		if (!ROUTES.contains(route)) {
			return MemberRequest.failed(current, new IOException("HGN member route is not allowlisted"));
		}
		byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
		if (bytes.length == 0 || bytes.length > 64 * 1024) {
			return MemberRequest.failed(current, new IOException("HGN member request is too large"));
		}

		URI requestUri = current.base().resolve(route.substring(1));
		Transports currentTransports;
		try {
			currentTransports = transports();
		} catch (IOException exception) {
			return MemberRequest.failed(current, exception);
		}
		long startedAt = System.nanoTime();
		CompletableFuture<JsonObject> response = sendWithFallback(
			currentTransports, current, requestUri, bytes, startedAt)
			.thenCompose(received -> decodeWithDeadline(received, requestUri, startedAt))
			.whenComplete((payload, failure) -> {
				Failure details = failure(failure);
				if (details.status() == 401) {
					invalidateSession(current, details.code(), details.message());
				}
			});
		return new MemberRequest(current, response);
	}

	private static CompletableFuture<HttpResponse<InputStream>> sendWithFallback(
		Transports currentTransports, Session current, URI requestUri, byte[] body,
		long startedAt) {
		HttpClient lan = currentTransports.lan();
		long now = System.nanoTime();
		long remaining = remainingNanos(startedAt, now, REQUEST_TIMEOUT_NANOS);
		if (remaining == 0L) return failedRequestTimeout();
		if (!isPinnedProduction(current.base()) || lan == null
			|| !lanCooldownElapsed(LAN_LAST_FAILURE_NANOS.get(), now)) {
			return send(currentTransports.publicTransport(), current, requestUri, body,
				Duration.ofNanos(remaining));
		}
		return send(lan, current, requestUri, body, Duration.ofNanos(remaining))
			.handle((response, failure) -> {
				if (failure == null) {
					LAN_LAST_FAILURE_NANOS.set(NO_LAN_FAILURE_NANOS);
					return CompletableFuture.<HttpResponse<InputStream>>completedFuture(response);
				}
				long failedAt = System.nanoTime();
				LAN_LAST_FAILURE_NANOS.set(failedAt);
				long retryRemaining = remainingNanos(
					startedAt, failedAt, REQUEST_TIMEOUT_NANOS);
				if (retryRemaining == 0L) {
					return HgnMembershipClient.<HttpResponse<InputStream>>failedRequestTimeout();
				}
				return send(currentTransports.publicTransport(), current, requestUri, body,
					Duration.ofNanos(retryRemaining));
			})
			.thenCompose(future -> future);
	}

	private static boolean lanCooldownElapsed(long failedAt, long now) {
		if (failedAt == NO_LAN_FAILURE_NANOS) return true;
		long elapsed = now - failedAt;
		return elapsed < 0L || elapsed >= LAN_RETRY_COOLDOWN_NANOS;
	}

	private static long remainingNanos(long startedAt, long now, long timeoutNanos) {
		long elapsed = now - startedAt;
		if (elapsed < 0L || elapsed >= timeoutNanos) return 0L;
		return timeoutNanos - elapsed;
	}

	private static <T> CompletableFuture<T> failedRequestTimeout() {
		return CompletableFuture.failedFuture(
			new IOException("HGN member request timed out"));
	}

	private static CompletableFuture<HttpResponse<InputStream>> send(
		HttpClient transport, Session current, URI requestUri, byte[] body, Duration timeout) {
		HttpRequest request = HttpRequest.newBuilder(requestUri)
			.timeout(timeout)
			.header("Authorization", "Bearer " + current.token())
			.header("Content-Type", "application/json; charset=utf-8")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofByteArray(body))
			.build();
		return transport.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
	}

	private static CompletableFuture<JsonObject> decodeWithDeadline(
		HttpResponse<InputStream> response, URI expectedUri, long startedAt) {
		long remaining = remainingNanos(
			startedAt, System.nanoTime(), REQUEST_TIMEOUT_NANOS);
		if (remaining == 0L) {
			closeResponseBody(response);
			return failedRequestTimeout();
		}
		try {
			return CompletableFuture.supplyAsync(() -> decode(response, expectedUri), RESPONSE_EXECUTOR)
				.orTimeout(remaining, TimeUnit.NANOSECONDS)
				.whenComplete((payload, failure) -> {
					if (failure != null) closeResponseBody(response);
				});
		} catch (RuntimeException exception) {
			closeResponseBody(response);
			return CompletableFuture.failedFuture(exception);
		}
	}

	private static void closeResponseBody(HttpResponse<InputStream> response) {
		try {
			response.body().close();
		} catch (IOException ignored) {
		}
	}

	static void deliver(MemberRequest response, String requestId, String operation) {
		response.whenComplete((payload, failure) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null) return;
			minecraft.execute(() -> {
				if (!response.current()) return;
				if (failure != null || payload == null) {
					Failure details = failure(failure);
					HgnCosmetics.transportFailure(requestId, operation,
						failure == null ? "Membership backend did not respond." : details.message());
					return;
				}
				HgnCosmetics.handle(payload);
			});
		});
	}

	private static boolean isCurrentSession(Session expected) {
		if (expected == null || session != expected || !available()) return false;
		return session == expected;
	}

	static Failure failure(Throwable throwable) {
		Throwable cause = throwable;
		while (cause != null && cause.getCause() != null
			&& (cause instanceof java.util.concurrent.CompletionException
				|| cause instanceof java.util.concurrent.ExecutionException)) {
			cause = cause.getCause();
		}
		if (cause instanceof BackendException backend) {
			return new Failure(backend.status, backend.code, backend.getMessage());
		}
		return new Failure(0, "membership_backend_unavailable", "Membership backend is unavailable.");
	}

	private static void invalidateSession(Session expected, String code, String message) {
		synchronized (HgnMembershipClient.class) {
			if (session != expected) return;
			session = null;
			initialization = Initialization.TERMINAL;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			HgnCosmetics.membershipSessionExpired(code, message);
			return;
		}
		minecraft.execute(() -> HgnCosmetics.membershipSessionExpired(code, message));
	}

	private static JsonObject decode(HttpResponse<InputStream> response, URI expectedUri) {
		try (InputStream input = response.body()) {
			if (!expectedUri.equals(response.uri())) {
				throw new IOException("HGN member response used an unexpected origin");
			}
			byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
			if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("HGN member response is too large");
			JsonObject payload = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				String code = payload.has("error") ? payload.get("error").getAsString()
					: "membership_request_rejected";
				String message = payload.has("message") ? payload.get("message").getAsString()
					: "Membership request was rejected.";
				throw new BackendException(response.statusCode(), code, message);
			}
			return payload;
		} catch (IOException | RuntimeException exception) {
			throw new java.util.concurrent.CompletionException(exception);
		}
	}

	private static URI validatedBase(String value) throws IOException {
		URI uri;
		try {
			uri = URI.create(value.endsWith("/") ? value : value + "/");
		} catch (RuntimeException exception) {
			throw new IOException("invalid membership API URL", exception);
		}
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		boolean pinnedProduction = "https".equalsIgnoreCase(uri.getScheme())
			&& PINNED_ALPHA_PUBLIC_HOST.equals(host) && uri.getPort() == PINNED_ALPHA_PORT;
		boolean development = Boolean.getBoolean("hgn.client.allowLocalBackend")
			&& "http".equalsIgnoreCase(uri.getScheme())
			&& (host.equals("localhost") || host.equals("127.0.0.1"));
		if (!pinnedProduction && !development) {
			throw new IOException("membership API must use the pinned HGN Alpha IP origin");
		}
		if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
			throw new IOException("membership API URL contains forbidden components");
		}
		if (!"/".equals(uri.getPath())) throw new IOException("membership API URL must use its origin root");
		return uri;
	}

	private static boolean isPinnedProduction(URI uri) {
		return "https".equalsIgnoreCase(uri.getScheme())
			&& PINNED_ALPHA_PUBLIC_HOST.equalsIgnoreCase(uri.getHost())
			&& uri.getPort() == PINNED_ALPHA_PORT;
	}

	private static final class BackendException extends IOException {
		private final int status;
		private final String code;

		private BackendException(int status, String code, String message) {
			super(message);
			this.status = status;
			this.code = code;
		}
	}

	record Failure(int status, String code, String message) {
	}

	private enum Initialization {
		PENDING,
		INITIALIZING,
		ACTIVE,
		TERMINAL
	}

	static final class MemberRequest {
		private final Session expected;
		private final CompletableFuture<JsonObject> response;

		private MemberRequest(Session expected, CompletableFuture<JsonObject> response) {
			this.expected = expected;
			this.response = response;
		}

		private static MemberRequest failed(Session expected, Throwable failure) {
			return new MemberRequest(expected, CompletableFuture.failedFuture(failure));
		}

		void whenComplete(BiConsumer<JsonObject, Throwable> completion) {
			response.whenComplete(completion);
		}

		boolean current() {
			return isCurrentSession(expected);
		}
	}

	private record Session(URI base, String token, long expiresAt, UUID playerId) {
	}

	private record Transports(HttpClient lan, HttpClient publicTransport) {
	}
}
