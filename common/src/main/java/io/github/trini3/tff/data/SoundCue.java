package io.github.trini3.tff.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ExtraCodecs;

/**
 * A sound played a number of ticks into a timed action (a reload). Cues live in the gun
 * definition, so the server (for everyone nearby) and the shooter's own client (right away,
 * without waiting for the server) play them on the same tick.
 *
 * @param tick   ticks after the action starts
 * @param sound  the sound event
 * @param volume volume multiplier
 * @param pitch  pitch multiplier (a small random spread is added on top)
 */
public record SoundCue(int tick, Holder<SoundEvent> sound, float volume, float pitch) {
	public static final Codec<SoundCue> CODEC = RecordCodecBuilder.create(i -> i.group(
		ExtraCodecs.NON_NEGATIVE_INT.fieldOf("tick").forGetter(SoundCue::tick),
		SoundEvent.CODEC.fieldOf("sound").forGetter(SoundCue::sound),
		Codec.floatRange(0f, 4f).optionalFieldOf("volume", 1f).forGetter(SoundCue::volume),
		Codec.floatRange(0.5f, 2f).optionalFieldOf("pitch", 1f).forGetter(SoundCue::pitch)
	).apply(i, SoundCue::new));

	public static final Codec<List<SoundCue>> LIST_CODEC = CODEC.listOf();

	/** The cues that fall on {@code tick}. */
	public static List<SoundCue> at(List<SoundCue> cues, long tick) {
		if (cues.isEmpty()) {
			return List.of();
		}
		return cues.stream().filter(cue -> cue.tick == tick).toList();
	}
}
