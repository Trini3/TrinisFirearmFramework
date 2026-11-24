package io.github.trini3.tff.registry;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.bench.AttachmentBenchMenu;
import io.github.trini3.tff.platform.TffPlatform;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

public final class TffMenus {
	public static final Supplier<MenuType<AttachmentBenchMenu>> ATTACHMENT_BENCH = TffPlatform.INSTANCE.register(
		BuiltInRegistries.MENU, Tff.id("attachment_bench"),
		() -> TffPlatform.INSTANCE.createMenuType(AttachmentBenchMenu::new));

	private TffMenus() {}

	public static void init() {}
}
