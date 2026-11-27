package io.github.trini3.tff.client;

import net.minecraft.client.Minecraft;

/**
 * Animation time for guns: client ticks plus the frame's partial tick, in seconds. It stops while
 * the game is paused, like the world does, and never jumps when the server corrects the world
 * time. Kept as a double so animations stay smooth however long the game has been running.
 */
public final class ClientClock {
	private static long ticks;

	private ClientClock() {}

	/** Called at the start of every client tick that isn't paused. */
	public static void tick() {
		ticks++;
	}

	public static long ticks() {
		return ticks;
	}

	/** Seconds, including the current frame's partial tick. */
	public static double now() {
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		return (ticks + (double) partial) / 20.0;
	}
}
