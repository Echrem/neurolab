package lab.neurolab.minecraft;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Captures and reapplies a bounded learned-synapse imprint across mobs. */
public final class NeuroMindImprinterItem extends Item {
    private static final String IMPRINT_MARKER = "NeuroLabMindImprint";
    private static final String SYNAPSE_DATA = "NeuroLabLearnedSynapses";

    public NeuroMindImprinterItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.sidedSuccess(true);
        if (!(target instanceof Mob mob) || !(player instanceof ServerPlayer)) {
            player.displayClientMessage(Component.translatable("message.neurolab.imprinter.mob_required"), true);
            return InteractionResult.CONSUME;
        }
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = custom == null ? new CompoundTag() : custom.copyTag();
        if (!tag.getBoolean(IMPRINT_MARKER)) {
            if (!BrainAttachmentService.isAttached(mob)) {
                player.displayClientMessage(Component.translatable("message.neurolab.imprinter.source_required"), true);
                return InteractionResult.CONSUME;
            }
            long[] state = BrainAttachmentService.copyMindSnapshot(mob);
            tag.putLongArray(SYNAPSE_DATA, state);
            tag.putBoolean(IMPRINT_MARKER, true);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            player.displayClientMessage(Component.translatable("message.neurolab.imprinter.captured", state.length), true);
            return InteractionResult.CONSUME;
        }
        long[] state = tag.getLongArray(SYNAPSE_DATA);
        String failure = BrainAttachmentService.applyMindCopy(mob, state);
        if (failure != null) {
            player.displayClientMessage(Component.literal(failure), true);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("message.neurolab.imprinter.applied",
                mob.getName().getString(), state.length), true);
        return InteractionResult.CONSUME;
    }
}
