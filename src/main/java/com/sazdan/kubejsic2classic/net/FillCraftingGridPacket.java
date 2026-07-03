package com.sazdan.kubejsic2classic.net;

import ic2.api.recipes.ingridients.inputs.IInput;
import ic2.core.platform.recipes.crafting.RecipeIC2Base;
import ic2.core.platform.recipes.crafting.ShapedIC2Recipe;
import ic2.core.platform.recipes.crafting.ShapelessIC2Recipe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server request to fill the vanilla 3×3 {@link CraftingMenu} grid for an IC2
 * count-aware crafting recipe, moving {@link IInput#getInputSize()} items into each slot
 * (JEI's built-in crafting transfer only moves 1 per slot, which never matches an IC2
 * count recipe — see Phase 6 notes in CLAUDE.md).
 *
 * <p>Only IC2 recipes ({@link RecipeIC2Base}) are handled here; the JEI plugin delegates
 * every other crafting recipe to JEI's default transfer handler, so this packet is never
 * sent for vanilla recipes.
 */
public final class FillCraftingGridPacket {
    private final ResourceLocation recipeId;
    private final boolean maxTransfer;

    public FillCraftingGridPacket(ResourceLocation recipeId, boolean maxTransfer) {
        this.recipeId = recipeId;
        this.maxTransfer = maxTransfer;
    }

    public static void encode(FillCraftingGridPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.recipeId);
        buf.writeBoolean(msg.maxTransfer);
    }

    public static FillCraftingGridPacket decode(FriendlyByteBuf buf) {
        return new FillCraftingGridPacket(buf.readResourceLocation(), buf.readBoolean());
    }

    public static void handle(FillCraftingGridPacket msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !(player.containerMenu instanceof CraftingMenu menu)) {
                return;
            }
            Recipe<?> recipe = player.server.getRecipeManager().byKey(msg.recipeId).orElse(null);
            if (!(recipe instanceof RecipeIC2Base)) {
                return;
            }

            // Required IInput per 3×3 grid slot (row-major, index 0..8); null = empty.
            IInput[] required = new IInput[9];
            if (recipe instanceof ShapedIC2Recipe shaped) {
                for (int r = 0; r < shaped.height && r < 3; r++) {
                    for (int c = 0; c < shaped.width && c < 3; c++) {
                        required[r * 3 + c] = shaped.inputs[r * shaped.width + c];
                    }
                }
            } else if (recipe instanceof ShapelessIC2Recipe shapeless) {
                int g = 0;
                for (IInput in : shapeless.inputs) {
                    if (g >= 9) {
                        break;
                    }
                    required[g++] = in;
                }
            } else {
                return;
            }

            Inventory inv = player.getInventory();
            Container grid = menu.getSlot(1).container; // CraftingContainer, slots 0..8

            // Return whatever is currently in the grid so we start from a clean slate.
            for (int g = 0; g < 9; g++) {
                ItemStack existing = grid.getItem(g);
                if (!existing.isEmpty()) {
                    grid.setItem(g, ItemStack.EMPTY);
                    inv.placeItemBackInInventory(existing);
                }
            }

            // Pull the required count (best-effort) into each slot.
            for (int g = 0; g < 9; g++) {
                IInput in = required[g];
                if (in == null) {
                    continue;
                }
                int need = Math.min(in.getInputSize(), 64);
                ItemStack pulled = pullMatching(inv, in, need);
                if (!pulled.isEmpty()) {
                    grid.setItem(g, pulled);
                }
            }

            menu.slotsChanged(grid);
            menu.broadcastChanges();
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Removes up to {@code need} items matching {@code in} from the player's main inventory
     * and returns them as a single stack (one item type — a crafting slot holds one type).
     */
    private static ItemStack pullMatching(Inventory inv, IInput in, int need) {
        ItemStack result = ItemStack.EMPTY;
        for (int i = 0; i < inv.items.size() && need > 0; i++) {
            ItemStack stack = inv.items.get(i);
            if (stack.isEmpty() || !in.matches(stack)) {
                continue;
            }
            if (result.isEmpty()) {
                result = stack.copy();
                result.setCount(0);
            } else if (!ItemStack.isSameItemSameTags(result, stack)) {
                continue; // stick to the first matched item type
            }
            int take = Math.min(stack.getCount(), need);
            result.grow(take);
            stack.shrink(take);
            need -= take;
        }
        return result;
    }
}
