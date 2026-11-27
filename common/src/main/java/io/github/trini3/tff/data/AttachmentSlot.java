package io.github.trini3.tff.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Where an attachment goes on a gun. Only scopes exist so far; new slots are added here. */
public enum AttachmentSlot implements StringRepresentable {
	SCOPE("scope");

	public static final Codec<AttachmentSlot> CODEC = StringRepresentable.fromEnum(AttachmentSlot::values);
	private final String name;

	AttachmentSlot(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
