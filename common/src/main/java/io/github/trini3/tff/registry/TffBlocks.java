package io.github.trini3.tff.registry;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.api.TffApi;
import io.github.trini3.tff.bench.AttachmentBenchBlock;
import io.github.trini3.tff.platform.TffPlatform;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public final class TffBlocks {
	public static final Supplier<AttachmentBenchBlock> ATTACHMENT_BENCH = TffPlatform.INSTANCE.register(
		BuiltInRegistries.BLOCK, Tff.id("attachment_bench"),
		() -> new AttachmentBenchBlock(BlockBehaviour.Properties.of()
			.setId(ResourceKey.create(Registries.BLOCK, Tff.id("attachment_bench")))
			.mapColor(MapColor.WOOD)
			.instrument(NoteBlockInstrument.BASS)
			.strength(2.5f)
			.sound(SoundType.WOOD)
			.ignitedByLava()));

	public static final Supplier<BlockItem> ATTACHMENT_BENCH_ITEM = TffPlatform.INSTANCE.register(
		BuiltInRegistries.ITEM, Tff.id("attachment_bench"),
		() -> {
			BlockItem item = new BlockItem(ATTACHMENT_BENCH.get(), new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, Tff.id("attachment_bench")))
				.useBlockDescriptionPrefix());
			item.registerBlocks(Item.BY_BLOCK, item);
			return item;
		});

	private TffBlocks() {}

	public static void init() {
		TffApi.addToCreativeTab(ATTACHMENT_BENCH_ITEM);
	}
}
