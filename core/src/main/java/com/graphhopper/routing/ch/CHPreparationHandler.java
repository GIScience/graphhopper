/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.ch;

import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.storage.*;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters.CH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.graphhopper.util.Helper.createFormatter;
import static com.graphhopper.util.Helper.getMemInfo;

/**
 * This class handles the different CH preparations
 *
 * @author Peter Karich
 * @author easbar
 */
public class CHPreparationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CHPreparationHandler.class);
    // we first add the profiles and later read them to create the config objects (because they require
    // the actual Weightings)
    private final List<CHProfile> chProfiles = new ArrayList<>();
    private final List<CHConfig> chConfigs = new ArrayList<>();
    private int preparationThreads;
// ORS-GH MOD START change visibility private-> protected and allow overriding String constants
    protected PMap pMap = new PMap();
    protected static String PREPARE = CH.PREPARE;
    protected static String DISABLE = CH.DISABLE;
// ORS-GH MOD END

    public CHPreparationHandler() {
        setPreparationThreads(1);
    }

    public void init(GraphHopperConfig ghConfig) {
        // throw explicit error for deprecated configs
        if (ghConfig.has("prepare.threads"))
            throw new IllegalStateException("Use " + PREPARE + "threads instead of prepare.threads");
        if (ghConfig.has("prepare.chWeighting") || ghConfig.has("prepare.chWeightings") || ghConfig.has("prepare.ch.weightings"))
            throw new IllegalStateException("Use profiles_ch instead of prepare.chWeighting, prepare.chWeightings or prepare.ch.weightings, see #1922 and docs/core/profiles.md");
        if (ghConfig.has("prepare.ch.edge_based"))
            throw new IllegalStateException("Use profiles_ch instead of prepare.ch.edge_based, see #1922 and docs/core/profiles.md");

        setPreparationThreads(ghConfig.getInt(PREPARE + "threads", getPreparationThreads()));
        setCHProfiles(ghConfig.getCHProfiles());
        pMap = ghConfig.asPMap();
    }

    public final boolean isEnabled() {
        return !chProfiles.isEmpty();
    }

    /**
     * Decouple CH profiles from PrepareContractionHierarchies as we need CH profiles for the
     * graphstorage and the graphstorage for the preparation.
     */
    public CHPreparationHandler addCHConfig(CHConfig chConfig) {
        chConfigs.add(chConfig);
        return this;
    }

    public final boolean hasCHConfigs() {
        return !chConfigs.isEmpty();
    }

    public List<CHConfig> getCHConfigs() {
        return chConfigs;
    }

    public List<CHConfig> getNodeBasedCHConfigs() {
        List<CHConfig> result = new ArrayList<>();
        for (CHConfig chConfig : chConfigs) {
            if (!chConfig.getTraversalMode().isEdgeBased()) {
                result.add(chConfig);
            }
        }
        return result;
    }

    public CHPreparationHandler setCHProfiles(CHProfile... chProfiles) {
        setCHProfiles(Arrays.asList(chProfiles));
        return this;
    }

    public CHPreparationHandler setCHProfiles(Collection<CHProfile> chProfiles) {
        this.chProfiles.clear();
        this.chProfiles.addAll(chProfiles);
        return this;
    }

    public List<CHProfile> getCHProfiles() {
        return chProfiles;
    }

    public int getPreparationThreads() {
        return preparationThreads;
    }

    /**
     * This method changes the number of threads used for preparation on import. Default is 1. Make
     * sure that you have enough memory when increasing this number!
     */
    public void setPreparationThreads(int preparationThreads) {
        this.preparationThreads = preparationThreads;
    }

    public Map<String, RoutingCHGraph> load(GraphHopperStorage ghStorage, List<CHConfig> chConfigs) {
        Map<String, RoutingCHGraph> loaded = Collections.synchronizedMap(new LinkedHashMap<>());
        List<Callable<String>> callables = chConfigs.stream()
                .map(c -> (Callable<String>) () -> {
                    CHStorage chStorage = ghStorage.loadCHStorage(c.getName(), c.isEdgeBased());
                    if (chStorage != null)
                        loaded.put(c.getName(), ghStorage.createCHGraph(chStorage, c));
                    else {
                        // todo: this is ugly, see comments in LMPreparationHandler
                        ghStorage.getDirectory().remove("nodes_ch_" + c.getName());
                        ghStorage.getDirectory().remove("shortcuts_" + c.getName());
                    }
                    return c.getName();
                })
                .collect(Collectors.toList());
        GHUtility.runConcurrently(callables, preparationThreads);
        return loaded;
    }

    public Map<String, PrepareContractionHierarchies.Result> prepare(GraphHopperStorage ghStorage, List<CHConfig> chConfigs, final boolean closeEarly) {
        if (chConfigs.isEmpty()) {
            LOGGER.info("There are no CHs to prepare");
            return Collections.emptyMap();
        }
        LOGGER.info("Creating CH preparations, {}", getMemInfo());
        List<PrepareContractionHierarchies> preparations = chConfigs.stream()
                .map(c -> createCHPreparation(ghStorage, c))
                .collect(Collectors.toList());
        Map<String, PrepareContractionHierarchies.Result> results = Collections.synchronizedMap(new LinkedHashMap<>());
        List<Callable<String>> callables = new ArrayList<>(preparations.size());
        for (int i = 0; i < preparations.size(); ++i) {
            PrepareContractionHierarchies prepare = preparations.get(i);
            LOGGER.info((i + 1) + "/" + preparations.size() + " calling " +
                    "CH prepare.doWork for profile '" + prepare.getCHConfig().getName() + "' " + prepare.getCHConfig().getTraversalMode() + " ... (" + getMemInfo() + ")");
            callables.add(() -> {
                final String name = prepare.getCHConfig().getName();
                // toString is not taken into account so we need to cheat, see http://stackoverflow.com/q/6113746/194609 for other options
                Thread.currentThread().setName(name);
                PrepareContractionHierarchies.Result result = prepare.doWork();
                results.put(name, result);
                prepare.flush();
                if (closeEarly)
                    prepare.close();
                ghStorage.getProperties().put(CH.PREPARE + "date." + name, createFormatter().format(new Date()));
                return name;
            });
        }
        GHUtility.runConcurrently(callables, preparationThreads);
        LOGGER.info("Finished CH preparation, {}", getMemInfo());
        return results;
    }

    public void createPreparations(GraphHopperStorage ghStorage) {
// TODO migration
//        if (!isEnabled() || !preparations.isEmpty())
//            return;
        if (!hasCHConfigs())
            throw new IllegalStateException("No CH profiles found");
// TODO migration
//        LOGGER.info("Creating CH preparations, {}", getMemInfo());
//        for (CHConfig chConfig : chConfigs) {
//            addPreparation(createCHPreparation(ghStorage, chConfig));
//        }
    }

// ORS-GH MOD START change visibility private-> protected
    protected PrepareContractionHierarchies createCHPreparation(GraphHopperStorage ghStorage, CHConfig chConfig) {
// ORS-GH MOD END
        PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraphHopperStorage(ghStorage, chConfig);
        pch.setParams(pMap);
        return pch;
    }

    public GraphHopperStorage getPreparation(String profile) {
//        TODO migration
        return null;
    }
}
