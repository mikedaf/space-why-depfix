package space.why.depfix;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.ModFileFactory;
import net.minecraftforge.forgespi.locating.IDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Space Why dependency fix: a tiny early-loading plugin (not a normal mod) that relaxes a dependency version range
 * IN MEMORY while Forge discovers mods, like the "dependencyOverrides" option newer loaders have. No other mod's jar
 * is modified.
 *
 * Forge loads IDependencyLocator services from jars in the mods folder into its service layer and calls scanMods()
 * with every discovered mod, before ModSorter.verifyDependencyVersions() checks the ranges.
 *
 * Current override:
 *   zps (Zero Point Systems 2.6.x) declares the OPTIONAL dependency vs_clockwork [0.5.7,), but Create: Clockwork
 *   0.5.7 isn't released. The only Clockwork API ZPS uses (ClockworkGasses.INSTANCE / getHELIUM() / getSTEAM())
 *   exists unchanged in 0.5.6, and both bundle the same Kelvin library, so ZPS is allowed to accept 0.5.6.
 *   Self-limiting: it only applies while the installed vs_clockwork is inside ONLY_FOR (0.5.6), so installing
 *   Clockwork 0.5.7+ makes this plugin do nothing. Remove it then.
 */
public class DependencyOverrides implements IDependencyLocator {
    private static final Logger LOG = LoggerFactory.getLogger("SpaceWhyDepFix");

    // owner mod, dependency mod, new range, only when the installed dependency version is in this range
    private static final String[][] OVERRIDES = {
        {"zps", "vs_clockwork", "[0.5.6,)", "[0.5.6,0.5.7)"},
    };

    @Override
    public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
        try {
            Map<String, ArtifactVersion> installed = new java.util.HashMap<>();
            for (IModFile file : loadedMods) {
                if (file.getModFileInfo() == null) continue;   // plain libraries have no mod info
                for (IModInfo info : file.getModInfos()) installed.put(info.getModId(), info.getVersion());
            }
            for (IModFile file : loadedMods) {
                if (file.getModFileInfo() == null) continue;
                for (String[] o : OVERRIDES) {
                    boolean owns = file.getModInfos().stream().anyMatch(i -> i.getModId().equals(o[0]));
                    ArtifactVersion have = installed.get(o[1]);
                    if (!owns) continue;
                    if (have == null || !VersionRange.createFromVersionSpec(o[3]).containsVersion(have)) {
                        LOG.info("[Space Why depfix] {} -> {}: not needed (installed: {})", o[0], o[1], have);
                        continue;
                    }
                    // Forge re-reads mods.toml later (ModFile.identifyMods), so wrap this file's parser: every read of
                    // the real mods.toml gets the relaxed range applied right after it's parsed.
                    Field pf = file.getClass().getDeclaredField("parser");
                    pf.setAccessible(true);
                    ModFileFactory.ModFileInfoParser original = (ModFileFactory.ModFileInfoParser) pf.get(file);
                    ModFileFactory.ModFileInfoParser wrapped = f -> {
                        IModFileInfo info = original.build(f);
                        relax(info, o);
                        return info;
                    };
                    pf.set(file, wrapped);
                    if (!relax(file.getModFileInfo(), o)) {
                        LOG.warn("[Space Why depfix] {} has no dependency on {} to relax (already fixed upstream?)", o[0], o[1]);
                        continue;
                    }
                    LOG.info("[Space Why depfix] {} now accepts {} {} (installed {}). Remove space_why_depfix once {} 0.5.7+ is installed.",
                            o[0], o[1], o[2], have, o[1]);
                }
            }
        } catch (Throwable t) {
            LOG.error("[Space Why depfix] couldn't apply dependency overrides; Forge will report the original error", t);
        }
        return List.of();
    }

    /** Returns whether a matching dependency was found and relaxed. */
    private static boolean relax(IModFileInfo info, String[] o) {
        if (info == null) return false;
        boolean changed = false;
        try {
            for (IModInfo mod : info.getMods()) {
                if (!mod.getModId().equals(o[0])) continue;
                for (IModInfo.ModVersion dep : mod.getDependencies()) {
                    if (!dep.getModId().equals(o[1])) continue;
                    Field f = dep.getClass().getDeclaredField("versionRange");
                    f.setAccessible(true);
                    f.set(dep, VersionRange.createFromVersionSpec(o[2]));
                    changed = true;
                }
            }
        } catch (Throwable t) {
            LOG.error("[Space Why depfix] couldn't relax {} -> {}", o[0], o[1], t);
            return false;
        }
        return changed;
    }

    @Override
    public String name() {
        return "space_why_depfix";
    }

    @Override
    public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
    }

    @Override
    public boolean isValid(IModFile modFile) {
        return true;
    }
}
