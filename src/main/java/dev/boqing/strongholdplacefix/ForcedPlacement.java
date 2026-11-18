package dev.boqing.strongholdplacefix;

import net.minecraft.core.BlockPos;

/**
 * Holds the position requested by {@code /place structure ...} for the duration of a
 * single structure-generation call.
 *
 * <p>Only the server thread ever sets this, and it is cleared as soon as the command
 * (or the structure generation it triggered) finishes.  Natural world-gen runs on
 * worker threads and therefore never sees this value.</p>
 */
public final class ForcedPlacement {
    private static final ThreadLocal<BlockPos> TARGET = new ThreadLocal<>();

    private ForcedPlacement() {
    }

    public static void set(BlockPos pos) {
        TARGET.set(pos);
    }

    public static BlockPos get() {
        return TARGET.get();
    }

    public static void clear() {
        TARGET.remove();
    }
}
