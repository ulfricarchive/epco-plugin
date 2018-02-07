package com.ulfric.enterprisepayments.checkout;

import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EpcoPlugin extends JavaPlugin implements Runnable {

	private final MediaType json = MediaType.parse("application/json; charset=utf-8");
	private final OkHttpClient client = new OkHttpClient();
	private final Gson gson = new Gson();

	@Override
	public void onEnable() {
		saveDefaultConfig();
		long delay = 20 * TimeUnit.MINUTES.toSeconds(2);
		getServer().getScheduler().runTaskTimerAsynchronously(this, this, delay, delay);
	}

	@Override
	public void run() {
		try {
			runChecked();
		} catch (Exception exception) {
			getLogger().log(Level.SEVERE, "Could not grab due commands because of an error", exception);
		}
	}

	private void runChecked() throws Exception {
		String url = url();
		Request getCommandsRequest = new Request.Builder().url(url).get().build();
		Response getCommandsResponse = client.newCall(getCommandsRequest).execute();
		if (getCommandsResponse.code() != 200) {
			getLogger().severe("Got response code: " + getCommandsResponse.code() + " from GetCommands");
			return;
		}
		DueCommands due = gson.fromJson(getCommandsResponse.body().string(), DueCommands.class);
		if (due != null && due.getCommands() != null && !due.getCommands().isEmpty()) {
			NumberFormat format = NumberFormat.getIntegerInstance();
			List<Command> stillDueList = runCommands(due.getCommands());
			if (!stillDueList.isEmpty()) {
				getLogger().info("Pending commands remaining: " + format.format(stillDueList.size()));
				DueCommands stillDue = new DueCommands();
				stillDue.setCommands(stillDueList);
				String body = gson.toJson(stillDue);
				Request stillDueRequest = new Request.Builder().url(url).post(RequestBody.create(json, body)).build();
				if (client.newCall(stillDueRequest).execute().code() != 200) {
					getLogger().severe("Failed to requeue commands: " + body);
				}
			}
			getLogger().info("Pending commands executed: " + format.format(due.getCommands().size() - stillDueList.size()) + " / " + format.format(due.getCommands().size()));
		} else {
			getLogger().info("No pending commands were found");
		}
	}

	private List<Command> runCommands(List<Command> commands) {
		return trySynchronously(() -> {
			Server server = getServer();
			CommandSender console = server.getConsoleSender();
			return commands.stream().map(command -> {
				Player player = server.getPlayer(command.getUniqueId());
				if (player == null) {
					return command;
				}
				server.dispatchCommand(console, command.getCommand().replace("{name}", player.getName()));
				return null;
			}).filter(Objects::nonNull)
			.collect(Collectors.toList());
		});
	}

	private String url() {
		return trySynchronously(() -> getConfig().getString("url") + "/commands/" + getConfig().getString("secret"));
	}

	private <T> T trySynchronously(Supplier<T> supplier) {
		try {
			return getServer().getScheduler().callSyncMethod(this, supplier::get).get();
		} catch (InterruptedException | ExecutionException thatsOk) {
			return supplier.get();
		}
	}

}
