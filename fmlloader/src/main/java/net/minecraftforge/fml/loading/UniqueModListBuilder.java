/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static net.minecraftforge.fml.loading.LogMarkers.LOADING;

@ApiStatus.Internal
@Deprecated(since = "1.21.3", forRemoval = true) // TODO: [FML][Loading] Convert to package private in 1.22
public class UniqueModListBuilder {
    private final static Logger LOGGER = LogUtils.getLogger();

    private final List<ModFile> modFiles;

    public UniqueModListBuilder(final List<ModFile> modFiles) {
        this.modFiles = modFiles;
    }

    public UniqueModListData buildUniqueList() {
        List<ModFile> uniqueModList;
        List<ModFile> uniqueLibListWithVersion;

        // Collect mod files by module name. This will be used for deduping purposes
        final Map<String, List<ModFile>> modFilesByFirstId = modFiles.stream()
                .filter(mf -> mf.getModFileInfo() != null)
                .collect(groupingBy(UniqueModListBuilder::getModId));

        final Map<String, List<ModFile>> libFilesWithVersionByModuleName = modFiles.stream()
                .filter(mf -> mf.getModFileInfo() == null)
                .collect(groupingBy(UniqueModListBuilder::getModId));

        // Select the newest by artifact version sorting of non-unique files thus identified
        uniqueModList = modFilesByFirstId.entrySet().stream()
                .map(UniqueModListBuilder::selectNewestModInfo)
                .toList();

        // Select the newest by artifact version sorting of non-unique files thus identified
        uniqueLibListWithVersion = libFilesWithVersionByModuleName.entrySet().stream()
                .map(UniqueModListBuilder::selectNewestModInfo)
                .toList();

        // Transform to the full mod id list
        final Map<String, List<IModInfo>> modIds = uniqueModList.stream()
                .filter(mf -> mf.getModFileInfo() != null) //Filter out non-mod files, we don't care about those for now.....
                .map(ModFile::getModInfos)
                .flatMap(Collection::stream)
                .collect(groupingBy(IModInfo::getModId));

        // Transform to the full lib id list
        final Map<String, List<ModFile>> versionedLibIds = uniqueLibListWithVersion.stream()
                .map(UniqueModListBuilder::getModId)
                .collect(Collectors.toMap(
                    Function.identity(),
                    libFilesWithVersionByModuleName::get
                ));

        // Its theoretically possible that some mod has somehow moved an id to a secondary place, thus causing a dupe.
        // We can't handle this
        final List<String> dupedModErrors = modIds.values().stream()
                .filter(modInfos -> modInfos.size() > 1)
                .map(mods -> String.format("\tMod ID: '%s' from mod files: %s",
                        mods.getFirst().getModId(),
                        mods.stream()
                                .map(modInfo -> modInfo.getOwningFile().getFile().getFileName()).collect(joining(", "))
                )).toList();

        if (!dupedModErrors.isEmpty()) {
            LOGGER.error(LOADING, "Found duplicate mods:\n{}", dupedModErrors.stream().collect(joining("\n")));
            throw new EarlyLoadingException("Duplicate mods found", null, dupedModErrors.stream()
                    .map(s -> new EarlyLoadingException.ExceptionData(s))
                    .toList());
        }


        final List<String> dupedLibErrors = versionedLibIds.values().stream()
                .filter(modFiles -> modFiles.size() > 1)
                .map(mods -> String.format("\tLibrary: '%s' from files: %s",
                        getModId(mods.getFirst()),
                        mods.stream().map(ModFile::getFileName).collect(joining(", "))
                )).toList();

        if (!dupedLibErrors.isEmpty()) {
            LOGGER.error(LOADING, "Found duplicate plugins or libraries:\n{}", dupedLibErrors.stream().collect(joining("\n")));
            throw new EarlyLoadingException("Duplicate plugins or libraries found", null, dupedLibErrors.stream()
                    .map(s -> new EarlyLoadingException.ExceptionData(s))
                    .toList());
        }

        // Collect unique mod files by module name. This will be used for deduping purposes
        final Map<String, List<ModFile>> uniqueModFilesByFirstId = uniqueModList.stream()
                .collect(groupingBy(UniqueModListBuilder::getModId));

        final List<ModFile> loadedList = new ArrayList<>(uniqueModList);
        loadedList.addAll(uniqueLibListWithVersion);

        return new UniqueModListData(loadedList, uniqueModFilesByFirstId);
    }

    private static ModFile selectNewestModInfo(Map.Entry<String, List<ModFile>> fullList) {
        List<ModFile> modInfoList = fullList.getValue();
        if (modInfoList.size() > 1) {
            LOGGER.debug("Found {} mods for first modid {}, selecting most recent based on version data", modInfoList.size(), fullList.getKey());
            modInfoList.sort(Comparator.comparing(UniqueModListBuilder::getVersion).reversed());
            LOGGER.debug("Selected file {} for modid {} with version {}", modInfoList.getFirst().getFileName(), fullList.getKey(), getVersion(modInfoList.getFirst()));
        }
        return modInfoList.get(0);
    }

    private static ArtifactVersion getVersion(final ModFile mf) {
        if (mf.getModFileInfo() == null || mf.getModInfos() == null || mf.getModInfos().isEmpty()) {
            return mf.getJarVersion();
        }

        return mf.getModInfos().get(0).getVersion();
    }

    private static String getModId(ModFile modFile) {
        if (modFile.getModFileInfo() == null || modFile.getModFileInfo().getMods().isEmpty()) {
            return modFile.getSecureJar().name();
        }

        return modFile.getModFileInfo().getMods().get(0).getModId();
    }

    public record UniqueModListData(List<ModFile> modFiles, Map<String, List<ModFile>> modFilesByFirstId) {}
}
