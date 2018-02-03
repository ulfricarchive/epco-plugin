package com.ulfric.enterprisepayments.checkout;

import java.util.UUID;

public class Command {

	private UUID uniqueId;
	private String command;

	public UUID getUniqueId() {
		return uniqueId;
	}

	public void setUniqueId(UUID uniqueId) {
		this.uniqueId = uniqueId;
	}

	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

}
