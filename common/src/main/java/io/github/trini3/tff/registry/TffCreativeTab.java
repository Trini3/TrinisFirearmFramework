package io.github.trini3.tff.registry;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.api.TffApi;
import io.github.trini3.tff.platform.TffPlatform;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** One "Firearms" tab holding every gun, ammo and attachment registered through {@link TffApi}. */
public final class TffCreativeTab {
	public static final Supplier<CreativeModeTab> FIREARMS = TffPlatform.INSTANCE.register(
		BuiltInRegistries.CREATIVE_MODE_TAB, Tff.id("firearms"),
		() -> TffPlatform.INSTANCE.createCreativeTab(
			Component.translatable("itemGroup.tff.firearms"),
			TffCreativeTab::icon,
			() -> TffApi.creativeTabItems().stream().map(item -> new ItemStack(item.get())).toList()));

	private TffCreativeTab() {}

	private static ItemStack icon() {
		return TffApi.creativeTabItems().stream()
			.map(s -> new ItemStack(s.get()))
			.filter(s -> s.getItem() instanceof io.github.trini3.tff.gun.GunItem)
			.findFirst()
			.orElseGet(() -> new ItemStack(TffBlocks.ATTACHMENT_BENCH_ITEM.get()));
	}

	public static void init() {}
}
