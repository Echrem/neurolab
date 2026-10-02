package lab.neurolab.minecraft;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A handheld entry point into the telemetry view; it never gives control of the mob to the player. */
public final class NeuroViewerItem extends Item {
    public NeuroViewerItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.sidedSuccess(true);
        if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof Mob mob)
                || !BrainAttachmentService.isAttached(mob)) {
            player.displayClientMessage(Component.literal("Neuro Viewer needs a brain-attached mob."), true);
            return InteractionResult.CONSUME;
        }
        serverPlayer.setCamera(mob);
        player.displayClientMessage(Component.literal("Viewing " + mob.getName().getString()
                + " · right-click air or press V to return."), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.setCamera(serverPlayer);
            player.displayClientMessage(Component.literal("Neuro view closed."), true);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, true);
    }
}
