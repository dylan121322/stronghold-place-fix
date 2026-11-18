package dev.boqing.strongholdplacefix.mixin;

import dev.boqing.strongholdplacefix.ForcedPlacement;
import dev.boqing.strongholdplacefix.PlacementShifter;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures the {@code BlockPos} passed to {@code /place structure} so the stronghold
 * generation code can honour its Y.
 *
 * <p>The target method name is the SRG name on purpose: Forge 1.20.1 runs on SRG
 * member names and this mod ships without a refmap, so the runtime names must be
 * written literally here.  Class names are official names in SRG, hence the normal
 * {@code @Mixin(PlaceCommand.class)}.</p>
 */
@Mixin(PlaceCommand.class)
public abstract class PlaceCommandMixin {
    @Inject(method = "m_214587_", at = @At("HEAD"))
    private static void strongholdplacefix$beginForcedPlacement(
            CommandSourceStack source,
            Holder.Reference<Structure> structure,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir) {
        // Always reset first: a previous command may have failed with an exception and
        // left a stale value behind.  Every structure gets the requested position; the
        // generation hooks decide how to apply it.
        ForcedPlacement.clear();
        ForcedPlacement.set(pos);
    }

    @Inject(method = "m_214587_", at = @At("RETURN"))
    private static void strongholdplacefix$endForcedPlacement(
            CommandSourceStack source,
            Holder.Reference<Structure> structure,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir) {
        ForcedPlacement.clear();
    }

    /**
     * Runs the vanilla generation call, then applies the generic vertical shift to
     * whatever StructureStart came back.  A redirect is used instead of a mixin on
     * Structure#generate so that modded Structure subclasses which override the
     * generation method are covered as well.
     */
    @Redirect(
            method = "m_214587_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/Structure;m_226596_("
                            + "Lnet/minecraft/core/RegistryAccess;"
                            + "Lnet/minecraft/world/level/chunk/ChunkGenerator;"
                            + "Lnet/minecraft/world/level/biome/BiomeSource;"
                            + "Lnet/minecraft/world/level/levelgen/RandomState;"
                            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;"
                            + "JLnet/minecraft/world/level/ChunkPos;"
                            + "ILnet/minecraft/world/level/LevelHeightAccessor;"
                            + "Ljava/util/function/Predicate;)"
                            + "Lnet/minecraft/world/level/levelgen/structure/StructureStart;"))
    private static StructureStart strongholdplacefix$generateAndShift(
            Structure structure,
            RegistryAccess registryAccess,
            ChunkGenerator chunkGenerator,
            BiomeSource biomeSource,
            RandomState randomState,
            StructureTemplateManager templateManager,
            long seed,
            ChunkPos chunkPos,
            int references,
            LevelHeightAccessor levelHeightAccessor,
            Predicate<Holder<Biome>> validBiomes) {
        StructureStart start = structure.generate(
                registryAccess, chunkGenerator, biomeSource, randomState, templateManager,
                seed, chunkPos, references, levelHeightAccessor, validBiomes);
        PlacementShifter.shift(start, ForcedPlacement.get(), chunkGenerator.getSeaLevel(), levelHeightAccessor);
        return start;
    }

    /**
     * Generation is finished by the time the command validates that the structure's
     * chunks are loaded.  Clearing here also covers the failure path (where the
     * command throws and RETURN injections never run).
     */
    @Inject(method = "m_214543_", at = @At("HEAD"))
    private static void strongholdplacefix$clearOnChunkCheck(
            ServerLevel level, ChunkPos from, ChunkPos to, CallbackInfo ci) {
        ForcedPlacement.clear();
    }
}
