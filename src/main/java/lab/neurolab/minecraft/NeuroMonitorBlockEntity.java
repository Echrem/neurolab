package lab.neurolab.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Rendering anchor for the client-side telemetry display. */
public final class NeuroMonitorBlockEntity extends BlockEntity {
    public NeuroMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(NeuroLabEntities.NEURO_MONITOR_ENTITY.get(), pos, state);
    }
}
