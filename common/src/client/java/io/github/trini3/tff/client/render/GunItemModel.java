package io.github.trini3.tff.client.render;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.tff.Tff;
import io.github.trini3.tff.anim.GunAnimation;
import io.github.trini3.tff.anim.Pose;
import io.github.trini3.tff.client.ClientClock;
import io.github.trini3.tff.client.gun.GunView;
import io.github.trini3.tff.client.gun.LocalGun;
import io.github.trini3.tff.client.gun.RemoteGuns;
import io.github.trini3.tff.data.AttachmentDefinition;
import io.github.trini3.tff.data.GunAttachments;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.gun.GunStats;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The {@code tff:gun} item model: a gun made of ordinary item models (one per moving part), posed
 * every frame by the gun's animation, plus the player's arms in first person. Every part is drawn
 * as a normal item layer, and the arms through the player renderer, so the result renders like
 * any item and works with shader packs. Used in {@code assets/<namespace>/items/<gun>.json}:
 * <pre>{@code
 * {"model": {"type": "tff:gun",
 *   "transforms": "mymod:item/rifle",        // model whose display transforms the gun uses
 *   "animation": "mymod:rifle",              // assets/mymod/tff/animations/rifle.json
 *   "parts": [
 *     {"model": "mymod:item/rifle_body"},                      // follows the "gun" bone
 *     {"model": "mymod:item/rifle_bolt", "bone": "bolt"},
 *     {"model": "mymod:item/rifle_iron_sight", "hidden_by": ["mymod:scope"]},
 *     {"model": "mymod:item/scope_on_rifle", "attachment": "mymod:scope", "sight": {"position": [8, 12, 6]}},
 *     {"model": "tff:item/muzzle_flash", "flash": true}
 *   ],
 *   "sight": {"position": [8, 10.5, 6]},     // eye point when aiming, in model pixels
 *   "muzzle": {"bone": "gun", "position": [8, 9, -12]}}}
 * }</pre>
 * Parts follow the animation bone named by {@code bone} (default {@code gun}). Flash parts are
 * authored with their base on the model centre (8, 8, 8) and the flame toward -Z; they are moved
 * to the muzzle and show for {@code flash_ticks} after each shot, turned and sized a little
 * differently every time. When aiming, the whole gun is shifted so the sight point sits on the
 * crosshair; the animation's {@code aim} clip only needs to square the gun up and set how close
 * it comes.
 */
public final class GunItemModel implements ItemModel {
	public static final Identifier ID = Tff.id("gun");

	/** Vanilla's first-person item position (right hand); see {@code applyItemArmTransform}. */
	private static final float HAND_X = 0.56f;
	private static final float HAND_Y = -0.52f;
	private static final float HAND_Z = -0.72f;
	/** Mirrors item model space (0 to 1) across its middle, for left-handed players. */
	private static final Matrix4fc MIRROR = new Matrix4f().translation(1f, 0f, 0f).scale(-1f, 1f, 1f);
	/** Mirrors an arm around its hand. */
	private static final Matrix4fc MIRROR_ARM = new Matrix4f().scaling(-1f, 1f, 1f);
	private static final Matrix4fc IDENTITY = new Matrix4f();

	private final List<BakedPart> parts;
	private final ModelRenderProperties properties;
	private final Matrix4fc transformation;
	private final Optional<Identifier> animationId;
	private final Optional<Anchor> sight;
	private final Optional<Anchor> muzzle;
	private final int flashTicks;
	private final boolean arms;
	private final boolean animatedTextures;

	// Per-frame scratch. Item models are only updated on the render thread, one item at a time.
	private final GunView view = new GunView();
	private final Matrix4f local = new Matrix4f();
	private final Matrix4f shift = new Matrix4f();
	private final Vector3f vector = new Vector3f();
	private GunAnimation animation = GunAnimation.EMPTY;
	private Pose pose = new Pose(GunAnimation.EMPTY);
	private int[] partBones = new int[0];
	private int muzzleBone = -1;
	private int rightArmBone = -1;
	private int leftArmBone = -1;
	private @Nullable Anchor alignedSight;
	private final Vector3f alignment = new Vector3f();

	private GunItemModel(List<BakedPart> parts, ModelRenderProperties properties, Matrix4fc transformation, Unbaked unbaked) {
		this.parts = parts;
		this.properties = properties;
		this.transformation = transformation;
		this.animationId = unbaked.animation;
		this.sight = unbaked.sight;
		this.muzzle = unbaked.muzzle;
		this.flashTicks = unbaked.flashTicks;
		this.arms = unbaked.arms;
		this.animatedTextures = parts.stream().anyMatch(BakedPart::animatedTexture);
	}

	@Override
	public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext,
					   @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
		output.appendModelIdentityElement(this);
		this.bindAnimation();
		Minecraft mc = Minecraft.getInstance();
		double now = ClientClock.now();
		LivingEntity holder = owner == null ? null : owner.asLivingEntity();
		boolean inHand = displayContext.firstPerson() || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
			|| displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
		boolean animated = inHand && holder != null && level != null && isMainHand(holder, displayContext);
		boolean firstPerson = animated && displayContext.firstPerson();
		boolean mirror = displayContext.leftHand();

		GunView view = this.view.clear();
		if (animated) {
			if (holder == mc.player && LocalGun.tracks(item)) {
				GunItem.definition(item, level.registryAccess()).ifPresent(gun -> LocalGun.fill(view, mc.player, gun, now));
			} else {
				RemoteGuns.fill(view, holder, item, level, now, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
			}
			output.setAnimated();
		}

		Pose pose = this.pose;
		if (animated) {
			GunPoser.pose(pose, view, firstPerson, now);
		} else {
			pose.reset();
		}
		pose.compute(firstPerson);
		this.shift.identity();
		if (firstPerson && view.aim > 0f) {
			this.updateAlignment(item, level);
			this.shift.translation(this.alignment.x * view.aim / 16f, this.alignment.y * view.aim / 16f, this.alignment.z * view.aim / 16f);
		}
		if (firstPerson && holder == mc.player) {
			this.recordMuzzle(mirror);
		}

		GunAttachments attachments = GunStats.attachments(item);
		boolean foil = item.hasFoil();
		boolean flashing = animated && view.fireAge >= 0f && view.fireAge * 20f < this.flashTicks;
		int visible = 0;
		for (int i = 0; i < this.parts.size(); i++) {
			BakedPart part = this.parts.get(i);
			if (!part.shownWith(attachments) || (part.flash && !flashing)) {
				continue;
			}
			int bone = part.flash && this.muzzleBone >= 0 ? this.muzzleBone : this.partBones[i];
			if (bone >= 0 && pose.isHidden(bone)) {
				continue;
			}
			Matrix4f local = this.local.set(this.transformation);
			if (mirror) {
				local.mul(MIRROR);
			}
			local.mul(this.shift).mul(bone >= 0 ? pose.matrix(bone) : IDENTITY);
			if (part.flash) {
				this.flashTransform(local, view.shotNumber);
			}
			if (mirror) {
				local.mul(MIRROR);
			}
			ItemStackRenderState.LayerRenderState layer = output.newLayer();
			if (foil) {
				layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
			}
			layer.setExtents(part.extents);
			layer.setLocalTransform(local);
			this.properties.applyToLayer(layer, displayContext);
			layer.setQuads(part.quads);
			visible |= 1 << Math.min(i, 31);
		}
		output.appendModelIdentityElement(visible);
		if (foil) {
			output.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);
			output.setAnimated();
		}
		if (this.animatedTextures) {
			output.setAnimated();
		}
		if (firstPerson && this.arms && holder instanceof AbstractClientPlayer player && !player.isInvisible()) {
			this.addArms(output, displayContext, player, mirror);
		}
	}

	private static boolean isMainHand(LivingEntity holder, ItemDisplayContext context) {
		return context.leftHand() == (holder.getMainArm() == HumanoidArm.LEFT);
	}

	/** Re-binds bone indices when the animation file is (re)loaded. */
	private void bindAnimation() {
		GunAnimation current = this.animationId.map(GunAnimations::get).orElse(GunAnimation.EMPTY);
		if (current == this.animation && this.partBones.length == this.parts.size()) {
			return;
		}
		this.animation = current;
		this.pose = new Pose(current);
		this.partBones = new int[this.parts.size()];
		for (int i = 0; i < this.parts.size(); i++) {
			this.partBones[i] = current.boneIndex(this.parts.get(i).bone);
		}
		this.muzzleBone = this.muzzle.map(anchor -> current.boneIndex(anchor.bone)).orElse(-1);
		this.rightArmBone = current.boneIndex(GunAnimation.RIGHT_ARM);
		this.leftArmBone = current.boneIndex(GunAnimation.LEFT_ARM);
		this.alignedSight = null;
	}

	/** The sight in use: that of a shown part which has one (a scope), else the gun's own. */
	private Optional<Anchor> activeSight(ItemStack item) {
		GunAttachments attachments = GunStats.attachments(item);
		for (BakedPart part : this.parts) {
			if (part.sight.isPresent() && part.attachment.isPresent() && part.shownWith(attachments)) {
				return part.sight;
			}
		}
		return this.sight;
	}

	/**
	 * Works out how far to shift the gun (in model pixels) so that, in the settled aim pose, the
	 * sight point lands on the middle of the screen. Only sideways and up/down: how close the gun
	 * comes is the aim clip's business. Cached until the animation or sight changes.
	 */
	private void updateAlignment(ItemStack item, @Nullable ClientLevel level) {
		Optional<Anchor> maybeSight = this.activeSight(item);
		if (maybeSight.isEmpty()) {
			this.alignment.zero();
			return;
		}
		Anchor sight = maybeSight.get();
		if (sight.equals(this.alignedSight)) {
			return;
		}
		Pose reference = new Pose(this.animation);
		GunPoser.aimReference(reference);
		reference.compute(true);
		int bone = this.animation.boneIndex(sight.bone);
		Vector3f point = new Vector3f(sight.position).div(16f);
		if (bone >= 0) {
			reference.matrix(bone).transformPosition(point);
		}
		PoseStack.Pose hand = new PoseStack.Pose();
		hand.translate(HAND_X, HAND_Y, HAND_Z);
		this.properties.transforms().getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).apply(false, hand);
		Matrix4f view = new Matrix4f(hand.pose()).mul(this.transformation);
		view.transformPosition(point);
		// A shift made inside the item transform moves the gun on screen by the transform's linear part.
		Matrix3f linear = view.get3x3(new Matrix3f()).invert();
		this.alignment.set(-point.x, -point.y, 0f).mul(linear).mul(16f);
		this.alignedSight = sight;
	}

	/** Remembers where the muzzle is drawn, in the hand's view space, for the shooter's own smoke. */
	private void recordMuzzle(boolean mirror) {
		if (this.muzzle.isEmpty()) {
			return;
		}
		Vector3f point = this.vector.set(this.muzzle.get().position).div(16f);
		if (this.muzzleBone >= 0) {
			this.pose.matrix(this.muzzleBone).transformPosition(point);
		}
		this.shift.transformPosition(point);
		this.transformation.transformPosition(point);
		PoseStack.Pose hand = new PoseStack.Pose();
		hand.translate(HAND_X, HAND_Y, HAND_Z);
		this.properties.transforms().getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).apply(false, hand);
		hand.pose().transformPosition(point);
		if (mirror) {
			point.x = -point.x;
		}
		LocalGun.setMuzzleInView(point);
	}

	/**
	 * Moves a flash model (authored with its base on the model centre, 8 8 8, flame toward -Z) to
	 * the muzzle, turned around the barrel axis and sized a little differently for every shot.
	 */
	private void flashTransform(Matrix4f local, long shot) {
		if (this.muzzle.isEmpty()) {
			return;
		}
		long hash = (shot + 1L) * 0x9E3779B97F4A7C15L;
		// Quarter turns keep the pixel art on the pixel grid, like vanilla's own sprites.
		float roll = (float) ((hash >>> 40) & 3) * (float) (Math.PI / 2.0);
		float scale = 0.85f + ((hash >>> 16) & 0xFFFF) / 65536f * 0.3f;
		Vector3fc m = this.muzzle.get().position;
		local.translate(m.x() / 16f, m.y() / 16f, m.z() / 16f)
			.rotateZ(roll)
			.scale(scale)
			.translate(-0.5f, -0.5f, -0.5f);
	}

	private void addArms(ItemStackRenderState output, ItemDisplayContext context, AbstractClientPlayer player, boolean mirror) {
		EntityRenderer<?, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
		if (!(renderer instanceof AvatarRenderer<?> avatar)) {
			return;
		}
		Identifier skin = player.getSkin().body().texturePath();
		boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
		// Bones are authored for a right-handed player; a left-handed one gets the mirror image,
		// so the "right arm" bone moves their left arm.
		this.addArm(output, context, avatar, skin, slim, player, this.rightArmBone, !mirror, mirror);
		this.addArm(output, context, avatar, skin, slim, player, this.leftArmBone, mirror, mirror);
	}

	private void addArm(ItemStackRenderState output, ItemDisplayContext context, AvatarRenderer<?> avatar, Identifier skin, boolean slim,
						AbstractClientPlayer player, int bone, boolean rightArm, boolean mirror) {
		if (bone < 0 || this.pose.isHidden(bone)) {
			return;
		}
		Vector3fc pivot = this.animation.bone(bone).pivot();
		Matrix4f local = this.local.set(this.transformation);
		if (mirror) {
			local.mul(MIRROR);
		}
		local.mul(this.shift).mul(this.pose.matrix(bone)).translate(pivot.x() / 16f, pivot.y() / 16f, pivot.z() / 16f);
		if (mirror) {
			local.mul(MIRROR_ARM);
		}
		boolean sleeve = player.isModelPartShown(rightArm ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		layer.setLocalTransform(local);
		this.properties.applyToLayer(layer, context);
		layer.setupSpecialModel(ArmRenderer.INSTANCE, new ArmRenderer.Arm(avatar, skin, rightArm, slim, sleeve));
	}

	/** A point on a bone, in model pixels. */
	public record Anchor(String bone, Vector3fc position) {
		public static final Codec<Anchor> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("bone", GunAnimation.GUN).forGetter(Anchor::bone),
			ExtraCodecs.VECTOR3F.fieldOf("position").forGetter(Anchor::position)
		).apply(i, Anchor::new));
	}

	/**
	 * One part of the gun.
	 *
	 * @param model      item model drawn for the part
	 * @param bone       animation bone it follows
	 * @param attachment only shown while this attachment is fitted
	 * @param hiddenBy   hidden while any of these attachments is fitted (iron sights under a scope)
	 * @param flash      a muzzle flash: only shown just after a shot
	 * @param sight      eye point while aiming, used instead of the gun's when this attachment part shows
	 */
	public record Part(Identifier model, String bone, Optional<ResourceKey<AttachmentDefinition>> attachment,
					   List<ResourceKey<AttachmentDefinition>> hiddenBy, boolean flash, Optional<Anchor> sight) {
		public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("model").forGetter(Part::model),
			Codec.STRING.optionalFieldOf("bone", GunAnimation.GUN).forGetter(Part::bone),
			ResourceKey.codec(TffRegistries.ATTACHMENT).optionalFieldOf("attachment").forGetter(Part::attachment),
			ResourceKey.codec(TffRegistries.ATTACHMENT).listOf().optionalFieldOf("hidden_by", List.of()).forGetter(Part::hiddenBy),
			Codec.BOOL.optionalFieldOf("flash", false).forGetter(Part::flash),
			Anchor.CODEC.optionalFieldOf("sight").forGetter(Part::sight)
		).apply(i, Part::new));
	}

	private record BakedPart(ItemQuads quads, Supplier<Vector3fc[]> extents, boolean animatedTexture, String bone,
							 Optional<ResourceKey<AttachmentDefinition>> attachment, List<ResourceKey<AttachmentDefinition>> hiddenBy,
							 boolean flash, Optional<Anchor> sight) {
		boolean shownWith(GunAttachments attachments) {
			if (this.attachment.isPresent() && !attachments.has(this.attachment.get())) {
				return false;
			}
			for (ResourceKey<AttachmentDefinition> key : this.hiddenBy) {
				if (attachments.has(key)) {
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * @param transforms model whose display transforms (and inventory lighting) the whole gun uses
	 * @param animation  animation file, {@code assets/<namespace>/tff/animations/<path>.json}
	 * @param parts      the parts, drawn in order
	 * @param sight      eye point while aiming
	 * @param muzzle     barrel tip: flash centre and the shooter's smoke
	 * @param flashTicks how long a flash shows
	 * @param arms       whether the player's arms are drawn in first person
	 */
	public record Unbaked(Identifier transforms, Optional<Identifier> animation, List<Part> parts, Optional<Anchor> sight,
						  Optional<Anchor> muzzle, int flashTicks, boolean arms) implements ItemModel.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Identifier.CODEC.fieldOf("transforms").forGetter(Unbaked::transforms),
			Identifier.CODEC.optionalFieldOf("animation").forGetter(Unbaked::animation),
			ExtraCodecs.nonEmptyList(Part.CODEC.listOf()).fieldOf("parts").forGetter(Unbaked::parts),
			Anchor.CODEC.optionalFieldOf("sight").forGetter(Unbaked::sight),
			Anchor.CODEC.optionalFieldOf("muzzle").forGetter(Unbaked::muzzle),
			ExtraCodecs.intRange(0, 20).optionalFieldOf("flash_ticks", 2).forGetter(Unbaked::flashTicks),
			Codec.BOOL.optionalFieldOf("arms", true).forGetter(Unbaked::arms)
		).apply(i, Unbaked::new));

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			resolver.markDependency(this.transforms);
			for (Part part : this.parts) {
				resolver.markDependency(part.model);
			}
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
			ModelBaker baker = context.blockModelBaker();
			ResolvedModel transformsModel = baker.getModel(this.transforms);
			ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, transformsModel, transformsModel.getTopTextureSlots());
			List<BakedPart> baked = new ArrayList<>(this.parts.size());
			for (Part part : this.parts) {
				ResolvedModel model = baker.getModel(part.model);
				TextureSlots slots = model.getTopTextureSlots();
				QuadCollection quads = model.bakeTopGeometry(slots, baker, BlockModelRotation.IDENTITY);
				List<BakedQuad> all = quads.getAll();
				baked.add(new BakedPart(ItemQuads.split(all), Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(all)),
					quads.hasMaterialFlag(2), part.bone, part.attachment, part.hiddenBy, part.flash, part.sight));
			}
			return new GunItemModel(List.copyOf(baked), properties, new Matrix4f(transformation), this);
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
