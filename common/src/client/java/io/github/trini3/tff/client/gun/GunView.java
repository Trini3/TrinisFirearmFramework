package io.github.trini3.tff.client.gun;

/**
 * What a gun is doing in the frame being drawn, as its animation sees it. Filled by
 * {@link LocalGun} for the player's own gun (from its predicted timeline) or by
 * {@link RemoteGuns} for guns held by anyone else (from the synced gun state). One scratch
 * instance is reused, so nothing here is kept between frames.
 */
public final class GunView {
	/**
	 * Seconds the empty pose takes to ease out once a reload starts (it snaps in when the last
	 * round is fired). A reload clip that keeps a part where the empty pose put it, such as a hammer
	 * that stays down until the reload cocks it, moves that part there linearly over the same time.
	 */
	public static final float EMPTY_FADE_SECONDS = 0.2f;

	/** Aim-down-sights blend, 0 (hip) to 1 (aimed), already eased. */
	public float aim;
	/** Sprint pose blend, 0 to 1. */
	public float sprint;
	/** Empty pose blend, 0 (loaded) to 1 (no rounds). */
	public float empty;
	/** Seconds since the last shot, or negative when there was none. */
	public float fireAge;
	/** Whether the last shot was fired while aiming. */
	public boolean fireAimed;
	/** Number of the last shot, used to vary the muzzle flash between shots. */
	public long shotNumber;
	/** Seconds since the last dry fire, or negative when there was none. */
	public float dryAge;
	/** Progress through the current reload step, 0 to 1. */
	public float reloadProgress;
	/** Reload clip blend: 1 while reloading, fading out after a cancelled reload, else 0. */
	public float reloadWeight;
	/** 0 for the first reload step, then 1, 2, ... for each further round of a per-round reload. */
	public int reloadStep;

	public GunView clear() {
		this.aim = 0f;
		this.sprint = 0f;
		this.empty = 0f;
		this.fireAge = -1f;
		this.fireAimed = false;
		this.shotNumber = 0L;
		this.dryAge = -1f;
		this.reloadProgress = 0f;
		this.reloadWeight = 0f;
		this.reloadStep = 0;
		return this;
	}
}
