package dev.boqing.strongholdplacefix;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import dev.boqing.strongholdplacefix.mixin.StructureStartAccessor;

/**
 * Shared vertical placement logic used by the place-command redirect.
 *
 * <p>The requested Y is placed on the nearest edge of the generated structure:
 * if the request is above the structure, the top is aligned; if below, the
 * bottom is aligned; if the structure already spans the requested Y it is left
 * alone.  This keeps buried structures (strongholds, mineshafts, ancient
 * cities) hanging below the requested Y and surface structures (villages,
 * pyramids) standing on it.</p>
 */
public final class PlacementShifter {
    private PlacementShifter() {
    }

    public static void shift(StructureStart start, BlockPos target, int seaLevel, LevelHeightAccessor levelHeightAccessor) {
        if (start == null || target == null || !start.isValid()) {
            return;
        }

        Set<StructurePiece> pieces = Collections.newSetFromMap(new IdentityHashMap<>());
        for (StructurePiece piece : (List<StructurePiece>) start.getPieces()) {
            collectPieces(piece, pieces, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        }
        if (pieces.isEmpty()) {
            return;
        }

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (StructurePiece piece : pieces) {
            BoundingBox box = piece.getBoundingBox();
            minY = Math.min(minY, box.minY());
            maxY = Math.max(maxY, box.maxY());
            minX = Math.min(minX, box.minX());
            maxX = Math.max(maxX, box.maxX());
            minZ = Math.min(minZ, box.minZ());
            maxZ = Math.max(maxZ, box.maxZ());
        }

        int requestedY = target.getY();
        int delta = 0;
        if (requestedY > maxY) {
            delta = requestedY - maxY;
        } else if (requestedY < minY) {
            delta = requestedY - minY;
        }
        int minBuildHeight = levelHeightAccessor.getMinBuildHeight();
        int maxBuildHeight = levelHeightAccessor.getMaxBuildHeight();
        delta = Mth.clamp(delta, minBuildHeight - minY, maxBuildHeight - 1 - maxY);

        if (delta != 0) {
            for (StructurePiece piece : pieces) {
                piece.move(0, delta, 0);
            }
            ((StructureStartAccessor) (Object) start).strongholdplacefix$setCachedBoundingBox(null);
        }

        StrongholdPlaceFix.LOGGER.info(
                "[strongholdplacefix] shifted {} by {} (requested y={}, natural y {}..{}, sea level {}); final y {}..{}; bbox x {}..{} z {}..{} (chunks {}..{}, {}..{})",
                start.getStructure().getClass().getSimpleName(), delta, requestedY, minY, maxY, seaLevel,
                minY + delta, maxY + delta,
                minX, maxX, minZ, maxZ,
                minX >> 4, maxX >> 4, minZ >> 4, maxZ >> 4);
    }

    private static void collectPieces(Object owner, Set<StructurePiece> out, Set<Object> seen, int depth) {
        if (owner == null || depth > 12 || !seen.add(owner)) {
            return;
        }
        if (owner instanceof StructurePiece piece) {
            if (!out.add(piece)) {
                return;
            }
            for (Class<?> type = piece.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    try {
                        field.setAccessible(true);
                        collectPieces(field.get(piece), out, seen, depth + 1);
                    } catch (Throwable ignored) {
                        // Inaccessible/synthetic field: skip.
                    }
                }
            }
            return;
        }
        if (owner instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                collectPieces(element, out, seen, depth + 1);
            }
            return;
        }
        if (owner.getClass().isArray()) {
            int length = Array.getLength(owner);
            for (int i = 0; i < length; i++) {
                collectPieces(Array.get(owner, i), out, seen, depth + 1);
            }
        }
    }
}
