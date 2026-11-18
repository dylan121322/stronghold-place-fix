package dev.boqing.strongholdplacefix.mixin;

import javax.annotation.Nullable;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Clears the cached bounding box after pieces have been moved vertically. */
@Mixin(StructureStart.class)
public interface StructureStartAccessor {
    @Accessor("f_163593_")
    void strongholdplacefix$setCachedBoundingBox(@Nullable BoundingBox boundingBox);
}
