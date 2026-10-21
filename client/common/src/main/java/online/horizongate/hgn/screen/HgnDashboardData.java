package online.horizongate.hgn.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import online.horizongate.hgn.HgnOfficialServers;

/** Loads only information that already exists in the active Minecraft profile. */
public final class HgnDashboardData {
	public record RecentWorld(String id, String name, String detail, long lastPlayed) {
	}

	public record SavedServer(String name, String address) {
	}

	public record Snapshot(List<RecentWorld> worlds, List<SavedServer> servers) {
		public Snapshot {
			worlds = List.copyOf(worlds);
			servers = List.copyOf(servers);
		}

		public static Snapshot empty() {
			return new Snapshot(List.of(), List.of());
		}
	}

	private HgnDashboardData() {
	}

	public static CompletableFuture<Snapshot> load(Minecraft minecraft) {
		List<SavedServer> servers = loadServers(minecraft);
		try {
			LevelStorageSource source = minecraft.getLevelSource();
			LevelStorageSource.LevelCandidates candidates = source.findLevelCandidates();
			return source.loadLevelSummaries(candidates).handle((summaries, failure) -> {
				if (failure != null || summaries == null) {
					return new Snapshot(List.of(), servers);
				}
				return new Snapshot(loadWorlds(summaries), servers);
			});
		} catch (Exception ignored) {
			return CompletableFuture.completedFuture(new Snapshot(List.of(), servers));
		}
	}

	private static List<RecentWorld> loadWorlds(List<LevelSummary> summaries) {
		return summaries.stream()
			.sorted(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed())
			.limit(3)
			.map(summary -> new RecentWorld(
				summary.getLevelId(),
				summary.getLevelName(),
				summary.getInfo().getString(),
				summary.getLastPlayed()))
			.toList();
	}

	private static List<SavedServer> loadServers(Minecraft minecraft) {
		ArrayList<SavedServer> servers = new ArrayList<>();
		try {
			ServerList list = new ServerList(minecraft);
			list.load();
			for (int index = 0; index < list.size() && servers.size() < 3; index++) {
				ServerData server = list.get(index);
				if (server != null && server.ip != null && !server.ip.isBlank()) {
					boolean official = HgnOfficialServers.ALL.stream().anyMatch(candidate ->
						candidate.address().equalsIgnoreCase(server.ip));
					if (official) continue;
					String name = server.name == null || server.name.isBlank() ? server.ip : server.name;
					servers.add(new SavedServer(name, server.ip));
				}
			}
		} catch (Exception ignored) {
			return List.of();
		}
		return List.copyOf(servers);
	}
}
