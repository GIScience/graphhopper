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
package com.graphhopper.routing.lm;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.LMProfile;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.JsonFeatureCollection;
import com.graphhopper.util.Parameters.Landmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.graphhopper.util.Helper.*;

/**
 * This class deals with the A*, landmark and triangulation (ALT) preparations.
 *
 * @author Peter Karich
 */
public class LMPreparationHandler {
// ORS-GH MOD START enable logging for subclasses
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
// ORS-GH MOD END
    private int landmarkCount = 16;

    private List<PrepareLandmarks> preparations = new ArrayList<>();
    // we first add the profiles and later read them to create the config objects (because they require
    // the actual Weightings)
    private final List<LMProfile> lmProfiles = new ArrayList<>();
// ORS-GH MOD START
    private final List<LMConfig> lmConfigs = new ArrayList<>();
// ORS-GH MOD END
    private final Map<String, Double> maximumWeights = new HashMap<>();
    private int minNodes = -1;
    private final List<String> lmSuggestionsLocations = new ArrayList<>(5);
    private int preparationThreads;
    private boolean logDetails = false;
    private AreaIndex<SplitArea> areaIndex;

// ORS-GH MOD START facilitate overriding in subclasses
    protected String PREPARE = Landmark.PREPARE;
    protected String DISABLE = Landmark.DISABLE;
    protected String COUNT = Landmark.COUNT;
// ORS-GH MOD END

    public LMPreparationHandler() {
        setPreparationThreads(1);
    }

    public void init(GraphHopperConfig ghConfig) {
// ORS-GH MOD START allow overriding fetching of lm profiles in order to use with core profiles
        init(ghConfig, ghConfig.getLMProfiles());
    }

    protected void init(GraphHopperConfig ghConfig, List<LMProfile> lmProfiles) {
// ORS-GH MOD END
        // throw explicit error for deprecated configs
        if (ghConfig.has("prepare.lm.weightings")) {
            throw new IllegalStateException("Use profiles_lm instead of prepare.lm.weightings, see #1922 and docs/core/profiles.md");
        }

        setPreparationThreads(ghConfig.getInt(PREPARE + "threads", getPreparationThreads()));
// ORS-GH MOD START
        //setLMProfiles(ghConfig.getLMProfiles());
        setLMProfiles(lmProfiles);
// ORS-GH MOD END

        landmarkCount = ghConfig.getInt(COUNT, landmarkCount);
        logDetails = ghConfig.getBool(PREPARE + "log_details", false);
        minNodes = ghConfig.getInt(PREPARE + "min_network_size", -1);

        for (String loc : ghConfig.getString(PREPARE + "suggestions_location", "").split(",")) {
            if (!loc.trim().isEmpty())
                lmSuggestionsLocations.add(loc.trim());
        }

        if (!isEnabled())
            return;

        String splitAreaLocation = ghConfig.getString(PREPARE + "split_area_location", "");
        JsonFeatureCollection landmarkSplittingFeatureCollection = loadLandmarkSplittingFeatureCollection(splitAreaLocation);
        if (landmarkSplittingFeatureCollection != null && !landmarkSplittingFeatureCollection.getFeatures().isEmpty()) {
            List<SplitArea> splitAreas = landmarkSplittingFeatureCollection.getFeatures().stream()
                    .map(SplitArea::fromJsonFeature)
                    .collect(Collectors.toList());
            areaIndex = new AreaIndex<>(splitAreas);
        }
    }

    public int getLandmarks() {
        return landmarkCount;
    }

    public final boolean isEnabled() {
        return !lmProfiles.isEmpty();
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

    public LMPreparationHandler setLMProfiles(LMProfile... lmProfiles) {
        return setLMProfiles(Arrays.asList(lmProfiles));
    }

    /**
     * Enables the use of landmarks to reduce query times.
     */
    public LMPreparationHandler setLMProfiles(Collection<LMProfile> lmProfiles) {
        this.lmProfiles.clear();
        this.maximumWeights.clear();
        for (LMProfile profile : lmProfiles) {
            if (profile.usesOtherPreparation())
                continue;
            maximumWeights.put(profile.getProfile(), profile.getMaximumLMWeight());
        }
        this.lmProfiles.addAll(lmProfiles);
        return this;
    }

    public List<LMProfile> getLMProfiles() {
        return lmProfiles;
    }

    /**
     * Decouple weightings from PrepareLandmarks as we need weightings for the graphstorage and the
     * graphstorage for the preparation.
     */
    public LMPreparationHandler addLMConfig(LMConfig lmConfig) {
        lmConfigs.add(lmConfig);
        return this;
    }

    /**
     * Loads the landmark data for all given configs if available.
     *
     * @return the loaded landmark storages
     */
    public List<LandmarkStorage> loadOrDoWork(List<LMConfig> lmConfigs, GraphHopperStorage ghStorage) {
        List<LandmarkStorage> loaded = Collections.synchronizedList(new ArrayList<>());
        List<Callable<String>> loadingCallables = lmConfigs.stream()
                .map(lmConfig -> (Callable<String>) () -> {
                    // todo: specifying ghStorage and landmarkCount should not be necessary, because all we want to do
                    //       is load the landmark data and these parameters are only needed to calculate the landmarks.
                    //       we should also work towards a separation of the storage and preparation related code in
                    //       landmark storage
                    LandmarkStorage lms = new LandmarkStorage(ghStorage, ghStorage.getDirectory(), lmConfig, landmarkCount);
                    if (lms.loadExisting())
                        loaded.add(lms);
                    else {
                        // todo: this is very ugly. all we wanted to do was see if the landmarks exist already, but now
                        //       we need to remove the DAs from the directory. This is because otherwise we cannot
                        //       create these DataAccess again when we actually prepare the landmarks that don't exist
                        //       yet.
                        ghStorage.getDirectory().remove("landmarks_" + lmConfig.getName());
                        ghStorage.getDirectory().remove("landmarks_subnetwork_" + lmConfig.getName());
                    }
                    return lmConfig.getName();
                })
                .collect(Collectors.toList());
        GHUtility.runConcurrently(loadingCallables, preparationThreads);
        return loaded;
    }

    public boolean hasLMProfiles() {
        return !lmConfigs.isEmpty();
    }

    public List<LMConfig> getLMConfigs() {
        return lmConfigs;
    }

    public List<PrepareLandmarks> getPreparations() {
        return preparations;
    }

    /**
     * Prepares the landmark data for all given configs
     */
    public List<PrepareLandmarks> prepare(List<LMConfig> lmConfigs, GraphHopperStorage ghStorage, LocationIndex locationIndex, final boolean closeEarly) {
        createPreparations(ghStorage, locationIndex);
        List<Callable<String>> prepareCallables = new ArrayList<>();
        for (int i = 0; i < preparations.size(); i++) {
            PrepareLandmarks prepare = preparations.get(i);
            final int count = i + 1;
            final String name = prepare.getLMConfig().getName();
            prepareCallables.add(() -> {
                LOGGER.info(count + "/" + lmConfigs.size() + " calling LM prepare.doWork for " + prepare.getLMConfig().getWeighting() + " ... (" + getMemInfo() + ")");
                Thread.currentThread().setName(name);
                prepare.doWork();
                if (closeEarly)
                    prepare.close();
                LOGGER.info("LM {} finished {}", name, getMemInfo());
                ghStorage.getProperties().put(Landmark.PREPARE + "date." + name, createFormatter().format(new Date()));
                return name;
            });
        }
        GHUtility.runConcurrently(prepareCallables, preparationThreads);
        LOGGER.info("Finished LM preparation, {}", getMemInfo());
        return preparations;
    }

    /**
     * This method creates the landmark storages ready for landmark creation.
     */
    public List<PrepareLandmarks> createPreparations(GraphHopperStorage ghStorage, LocationIndex locationIndex) {
        LOGGER.info("Creating LM preparations, {}", getMemInfo());
        List<LandmarkSuggestion> lmSuggestions = new ArrayList<>(lmSuggestionsLocations.size());
        if (!lmSuggestionsLocations.isEmpty()) {
            try {
                for (String loc : lmSuggestionsLocations) {
                    lmSuggestions.add(LandmarkSuggestion.readLandmarks(loc, locationIndex));
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

// ORS-GH MOD START abstract to a method in order to facilitate overriding
        return createPreparationsInternal(ghStorage, lmSuggestions);
    }

    protected List<PrepareLandmarks> createPreparationsInternal(GraphHopperStorage ghStorage, List<LandmarkSuggestion> lmSuggestions) {
// ORS-GH MOD END
        for (LMConfig lmConfig : lmConfigs) {
            Double maximumWeight = maximumWeights.get(lmConfig.getName());
            if (maximumWeight == null)
                throw new IllegalStateException("maximumWeight cannot be null. Default should be just negative. " +
                        "Couldn't find " + lmConfig.getName() + " in " + maximumWeights);

            PrepareLandmarks prepareLandmarks = new PrepareLandmarks(ghStorage.getDirectory(), ghStorage,
                    lmConfig, landmarkCount).
                    setLandmarkSuggestions(lmSuggestions).
                    setMaximumWeight(maximumWeight).
                    setLogDetails(logDetails);
            if (minNodes > 1)
                prepareLandmarks.setMinimumNodes(minNodes);
            // using the area index we separate certain areas from each other but we do not change the base graph for this
            // so that other algorithms still can route between these areas
            if (areaIndex != null)
                prepareLandmarks.setAreaIndex(areaIndex);
            preparations.add(prepareLandmarks);
        }
        return preparations;
    }

    private JsonFeatureCollection loadLandmarkSplittingFeatureCollection(String splitAreaLocation) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JtsModule());
        URL builtinSplittingFile = LandmarkStorage.class.getResource("map.geo.json");
        try (Reader reader = splitAreaLocation.isEmpty() ?
                new InputStreamReader(builtinSplittingFile.openStream(), UTF_CS) :
                new InputStreamReader(new FileInputStream(splitAreaLocation), UTF_CS)) {
            JsonFeatureCollection result = objectMapper.readValue(reader, JsonFeatureCollection.class);
            if (splitAreaLocation.isEmpty()) {
                LOGGER.info("Loaded built-in landmark splitting collection from {}", builtinSplittingFile);
            } else {
                LOGGER.info("Loaded landmark splitting collection from {}", splitAreaLocation);
            }
            return result;
        } catch (IOException e) {
            LOGGER.error("Problem while reading border map GeoJSON. Skipping this.", e);
            return null;
        }
    }

// ORS-GH MOD START add methods
    public Map<String, Double> getMaximumWeights() {
        return maximumWeights;
    }

    public int getMinNodes() {
        return minNodes;
    }

    public boolean getLogDetails() {
        return logDetails;
    }
// ORS-GH MOD END
}
