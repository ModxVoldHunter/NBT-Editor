package com.luneruniverse.minecraft.mod.nbteditor.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.luneruniverse.minecraft.mod.nbteditor.misc.MixinLink;

import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.HorseScreenHandler;

@Mixin(HorseScreenHandler.class)
public class HorseScreenHandlerMixin {
	@Inject(method = "<init>(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/entity/passive/AbstractHorseEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Inventory;onOpen(Lnet/minecraft/entity/player/PlayerEntity;)V"))
	private void initHead(int syncId, PlayerInventory playerInventory, Inventory inventory, AbstractHorseEntity horse, CallbackInfo info) {
		MixinLink.SCREEN_HANDLER_OWNER.put(Thread.currentThread(), playerInventory.player);
	}
	@Inject(method = "<init>(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/entity/passive/AbstractHorseEntity;)V", at = @At("RETURN"))
	private void initReturn(int syncId, PlayerInventory playerInventory, Inventory inventory, AbstractHorseEntity horse, CallbackInfo info) {
		MixinLink.SCREEN_HANDLER_OWNER.remove(Thread.currentThread());
	}
}
