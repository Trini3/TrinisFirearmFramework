package io.github.trini3.tff.bench;

import io.github.trini3.tff.data.AttachmentSlot;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.registry.TffBlocks;
import io.github.trini3.tff.registry.TffMenus;
import net.minecraft.core.HolderLookup;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Attachment bench: one gun slot and one scope slot. The scope slot is a live view of the
 * gun's {@code tff:attachments} component: putting a scope in fits it, taking it out removes
 * it. The slot's content is always rebuilt from the gun, so it can never hold a second copy.
 */
public class AttachmentBenchMenu extends AbstractContainerMenu {
	public static final int GUN_SLOT = 0;
	public static final int SCOPE_SLOT = 1;
	private static final int INV_START = 2;
	private static final int INV_END = INV_START + 36;

	private final ContainerLevelAccess access;
	private final HolderLookup.Provider registries;
	private boolean syncing;

	private final SimpleContainer container = new SimpleContainer(2) {
		@Override
		public void setChanged() {
			super.setChanged();
			AttachmentBenchMenu.this.slotsChanged(this);
		}
	};

	public AttachmentBenchMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL);
	}

	public AttachmentBenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(TffMenus.ATTACHMENT_BENCH.get(), containerId);
		this.access = access;
		this.registries = inventory.player.registryAccess();
		this.addSlot(new Slot(this.container, GUN_SLOT, 44, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return GunItem.isGun(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		this.addSlot(new ScopeSlot(this.container, SCOPE_SLOT, 116, 35));
		this.addStandardInventorySlots(inventory, 8, 84);
	}

	private ItemStack gun() {
		return this.container.getItem(GUN_SLOT);
	}

	/** Rebuilds the scope slot from the gun. */
	@Override
	public void slotsChanged(Container changed) {
		if (this.syncing) {
			return;
		}
		this.syncing = true;
		try {
			ItemStack shown = AttachmentLogic.fittedItem(this.gun(), AttachmentSlot.SCOPE, this.registries);
			if (!ItemStack.matches(shown, this.container.getItem(SCOPE_SLOT))) {
				this.container.setItem(SCOPE_SLOT, shown);
			}
		} finally {
			this.syncing = false;
		}
		super.slotsChanged(changed);
	}

	private final class ScopeSlot extends Slot {
		ScopeSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return !this.hasItem() && AttachmentLogic.fittable(AttachmentBenchMenu.this.gun(), AttachmentSlot.SCOPE, stack, AttachmentBenchMenu.this.registries).isPresent();
		}

		@Override
		public boolean mayPickup(Player player) {
			return !AttachmentBenchMenu.this.gun().isEmpty();
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public void set(ItemStack stack) {
			// Update the gun first: setting the slot triggers slotsChanged, which mirrors the gun.
			ItemStack gun = AttachmentBenchMenu.this.gun();
			if (!AttachmentBenchMenu.this.syncing) {
				if (stack.isEmpty()) {
					AttachmentLogic.remove(gun, AttachmentSlot.SCOPE);
				} else if (AttachmentLogic.fit(gun, AttachmentSlot.SCOPE, stack, AttachmentBenchMenu.this.registries)) {
					AttachmentBenchMenu.this.playSound();
				}
			}
			super.set(stack);
		}

		@Override
		public ItemStack remove(int amount) {
			AttachmentLogic.remove(AttachmentBenchMenu.this.gun(), AttachmentSlot.SCOPE);
			AttachmentBenchMenu.this.playSound();
			return super.remove(amount);
		}
	}

	private void playSound() {
		this.access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.6f, 1.2f));
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, TffBlocks.ATTACHMENT_BENCH.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index == GUN_SLOT || index == SCOPE_SLOT) {
			if (index == SCOPE_SLOT) {
				// Take it out through remove() so the gun loses the scope before the copy is handed over.
				ItemStack taken = slot.remove(1);
				if (!this.moveItemStackTo(taken, INV_START, INV_END, true)) {
					// No room: put it back on the gun.
					slot.set(taken);
					return ItemStack.EMPTY;
				}
				return original;
			}
			if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (GunItem.isGun(stack)) {
			if (!this.moveItemStackTo(stack, GUN_SLOT, GUN_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (this.slots.get(SCOPE_SLOT).mayPlace(stack)) {
			ItemStack one = stack.split(1);
			this.slots.get(SCOPE_SLOT).setByPlayer(one);
		} else if (index < INV_START + 27) {
			if (!this.moveItemStackTo(stack, INV_START + 27, INV_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, INV_START, INV_START + 27, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		// The scope slot only mirrors the gun; clear it silently so only the gun is handed back.
		this.syncing = true;
		this.container.removeItemNoUpdate(SCOPE_SLOT);
		this.syncing = false;
		this.access.execute((level, pos) -> this.clearContainer(player, this.container));
	}
}
