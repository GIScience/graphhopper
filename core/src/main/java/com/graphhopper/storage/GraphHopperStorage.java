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
package com.graphhopper.storage;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.Constants;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.dustinj.timezonemap.TimeZoneMap;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
// ORS-GH MOD END

/**
 * This class manages all storage related methods and delegates the calls to the associated graphs.
 * The associated graphs manage their own necessary data structures and are used to provide e.g.
 * different traversal methods. By default this class implements the graph interface and results in
 * identical behavior as the Graph instance from getBaseGraph()
 * <p>
 *
 * @author Peter Karich
 */
public class GraphHopperStorage implements Graph, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphHopperStorage.class);
    private final Directory dir;
    private final EncodingManager encodingManager;
    private final StorableProperties properties;
    private final BaseGraph baseGraph;

    // ORS-GH MOD START - additional code
    private ExtendedStorageSequence graphExtensions;

    public ExtendedStorageSequence getExtensions() {
        return graphExtensions;
    }

    private ConditionalEdges conditionalAccess;
    private ConditionalEdges conditionalSpeed;

    public ConditionalEdgesMap getConditionalAccess(FlagEncoder encoder) {
        return getConditionalAccess(encoder.toString());
    }

    public ConditionalEdgesMap getConditionalAccess(String encoderName) {
        return conditionalAccess.getConditionalEdgesMap(encoderName);
    }

    public ConditionalEdgesMap getConditionalSpeed(FlagEncoder encoder) {
        return getConditionalSpeed(encoder.toString());
    }

    public ConditionalEdgesMap getConditionalSpeed(String encoderName) {
        return conditionalSpeed.getConditionalEdgesMap(encoderName);
    }

    // FIXME: temporal solution until an external storage for time zones is introduced.
    private TimeZoneMap timeZoneMap;

    public TimeZoneMap getTimeZoneMap() {
        return timeZoneMap;
    }

    public void setTimeZoneMap(TimeZoneMap timeZoneMap) {
        this.timeZoneMap = timeZoneMap;
    }
    // ORS-GH MOD END

    // same flush order etc
    private final Collection<CHEntry> chEntries;
    private final int segmentSize;

    /**
     * Use {@link GraphBuilder} to create a graph
     */
    public GraphHopperStorage(Directory dir, EncodingManager encodingManager, boolean withElevation, boolean withTurnCosts, int segmentSize) {
        if (encodingManager == null)
            throw new IllegalArgumentException("EncodingManager needs to be non-null since 0.7. Create one using EncodingManager.create or EncodingManager.create(flagEncoderFactory, ghLocation)");

        this.encodingManager = encodingManager;
        this.dir = dir;
        this.properties = new StorableProperties(dir);
        this.segmentSize = segmentSize;
        // ORS-GH MOD START - additional storages
        if (encodingManager.hasConditionalAccess()) {
            this.conditionalAccess = new ConditionalEdges(encodingManager, ConditionalEdges.ACCESS, dir);
        }

        if (encodingManager.hasConditionalSpeed()) {
            this.conditionalSpeed = new ConditionalEdges(encodingManager, ConditionalEdges.SPEED, dir);
        }
        // ORS-GH MOD END
        baseGraph = new BaseGraph(dir, encodingManager.getIntsForFlags(), withElevation, withTurnCosts, segmentSize);
        chEntries = new ArrayList<>();
    }

    // ORS-GH MOD START: additional method
    public void setExtendedStorages(ExtendedStorageSequence seq) {
        this.graphExtensions = seq;
    }
    // ORS-GH MOD END
    // ORS-GH MOD START allow overriding in ORS
    public CHStorage createCHStorage(CHConfig chConfig) {
        if (getCHConfigs().contains(chConfig))
            throw new IllegalArgumentException("For the given CH profile a CHStorage already exists: '" + chConfig.getName() + "'");
        CHEntry chEntry = createCHEntry(chConfig);
        chEntries.add(chEntry);
        return chEntry.chStore;
    }
    // ORS-GH MOD END

    // ORS-GH MOD START allow overriding in ORS
    protected CHEntry createCHEntry(CHConfig chConfig) {
        if (getCHConfigs().contains(chConfig))
            throw new IllegalArgumentException("For the given CH profile a CHStorage already exists: '" + chConfig.getName() + "'");
        if (!isFrozen())
            throw new IllegalStateException("graph must be frozen before we can create ch graphs");
        CHStorage store = new CHStorage(dir, chConfig.getName(), segmentSize, chConfig.isEdgeBased());
        store.setLowShortcutWeightConsumer(s -> {
            // we just log these to find mapping errors
            NodeAccess nodeAccess = baseGraph.getNodeAccess();
            LOGGER.warn("Setting weights smaller than " + s.minWeight + " is not allowed. " +
                    "You passed: " + s.weight + " for the shortcut " +
                    " nodeA (" + nodeAccess.getLat(s.nodeA) + "," + nodeAccess.getLon(s.nodeA) +
                    " nodeB " + nodeAccess.getLat(s.nodeB) + "," + nodeAccess.getLon(s.nodeB));
        });
        store.create();
        // we use a rather small value here. this might result in more allocations later, but they should
        // not matter that much. if we expect a too large value the shortcuts DataAccess will end up
        // larger than needed, because we do not do something like trimToSize in the end.
        double expectedShortcuts = 0.3 * baseGraph.getEdges();
        store.init(baseGraph.getNodes(), (int) expectedShortcuts);
        CHEntry chEntry = new CHEntry(chConfig, store, new RoutingCHGraphImpl(baseGraph, store, chConfig.getWeighting()));
        return chEntry;
    }
    // ORS-GH MOD END

    public CHStorage loadCHStorage(String chGraphName, boolean edgeBased) {
        CHStorage store = new CHStorage(dir, chGraphName, segmentSize, edgeBased);
        return store.loadExisting() ? store : null;
    }

    public RoutingCHGraph createCHGraph(CHStorage store, CHConfig chConfig) {
        return new RoutingCHGraphImpl(baseGraph, store, chConfig.getWeighting());
    }

    // ORS-GH MOD START
    // CALT
    // TODO ORS: should calt provide its own classes instead of modifying ch?
    public RoutingCHGraphImpl getIsochroneGraph(Weighting weighting) {
        if (chEntries.isEmpty())
            throw new IllegalStateException("Cannot find graph implementation");
        Iterator<CHEntry> iterator = chEntries.iterator();
        while(iterator.hasNext()){
            CHEntry cg = iterator.next();
            if(cg.chConfig.getType() == "isocore"
                    && cg.chConfig.getWeighting().getName() == weighting.getName()
                    && cg.chConfig.getWeighting().getFlagEncoder().toString() == weighting.getFlagEncoder().toString())
                return cg.chGraph;
        }
        throw new IllegalStateException("No isochrone graph was found");
    }
    // ORS-GH MOD END

    public List<CHConfig> getCHConfigs() {
        return chEntries.stream().map(c -> c.chConfig).collect(Collectors.toList());
    }

    public List<CHConfig> getCHConfigs(boolean edgeBased) {
        List<CHConfig> result = new ArrayList<>();
        List<CHConfig> chConfigs = getCHConfigs();
        for (CHConfig profile : chConfigs) {
            if (edgeBased == profile.isEdgeBased()) {
                result.add(profile);
            }
        }
        return result;
    }

    /**
     * @return the directory where this graph is stored.
     */
    public Directory getDirectory() {
        return dir;
    }

    /**
     * After configuring this storage you need to create it explicitly.
     */
    public GraphHopperStorage create(long byteCount) {
        baseGraph.checkNotInitialized();
        if (encodingManager == null)
            throw new IllegalStateException("EncodingManager can only be null if you call loadExisting");

        dir.create();

        properties.create(100);
        properties.put("graph.encoded_values", encodingManager.toEncodedValuesAsString());
        properties.put("graph.flag_encoders", encodingManager.toFlagEncodersAsString());
        long initSize = Math.max(byteCount, 100);

        baseGraph.create(initSize);

        // ORS-GH MOD START - create extended/conditional storages
        if (graphExtensions != null) {
            graphExtensions.create(initSize);
        }
        // TODO ORS: Find out byteCount to create these
        if (conditionalAccess != null) {
            conditionalAccess.create(initSize);
        }
        if (conditionalSpeed != null) {
            conditionalSpeed.create(initSize);
        }
        // ORS-GH MOD END

        chEntries.forEach(ch -> ch.chStore.create());

        List<CHConfig> chConfigs = getCHConfigs();
        List<String> chProfileNames = new ArrayList<>(chConfigs.size());
        for (CHConfig chConfig : chConfigs) {
            chProfileNames.add(chConfig.getName());
        }
        properties.put("graph.ch.profiles", chProfileNames.toString());
        return this;
    }

    public EncodingManager getEncodingManager() {
        return encodingManager;
    }

    public StorableProperties getProperties() {
        return properties;
    }

    public boolean loadExisting() {
        baseGraph.checkNotInitialized();
        if (properties.loadExisting()) {
            if (properties.containsVersion())
                throw new IllegalStateException("The GraphHopper file format is not compatible with the data you are " +
                        "trying to load. You either need to use an older version of GraphHopper or run a new import");
            // check encoding for compatibility
            String flagEncodersStr = properties.get("graph.flag_encoders");

            if (!encodingManager.toFlagEncodersAsString().equalsIgnoreCase(flagEncodersStr)) {
                throw new IllegalStateException("Encoding does not match:"
                        + "\nGraphhopper config: " + encodingManager.toFlagEncodersAsString()
                        + "\nGraph: " + flagEncodersStr
                        + "\nChange configuration to match the graph or delete " + dir.getLocation());
            }

            String encodedValueStr = properties.get("graph.encoded_values");
            if (!encodingManager.toEncodedValuesAsString().equalsIgnoreCase(encodedValueStr)) {
                throw new IllegalStateException("Encoded values do not match:"
                        + "\nGraphhopper config: " + encodingManager.toEncodedValuesAsString()
                        + "\nGraph: " + encodedValueStr
                        + "\nChange configuration to match the graph or delete " + dir.getLocation());
            }
            baseGraph.loadExisting();
            return true;
        }
        return false;
    }

    // ORS-GH MOD START add ORS hook
    public void loadExistingORS() {};
    // ORS-GH MOD END

    public void flush() {
        baseGraph.flush();
        properties.flush();
        // ORS-GH MOD START - additional code
        if (graphExtensions != null) {
            graphExtensions.flush();
        }
        // ORS-GH MOD END
    }

    @Override
    public void close() {
        properties.close();
        baseGraph.close();
        // ORS-GH MOD START - additional code
        if (graphExtensions != null) {
            graphExtensions.close();
        }
        if (conditionalAccess != null) {
            conditionalAccess.close();
        }
        if (conditionalSpeed != null) {
            conditionalSpeed.close();
        }
        // ORS-GH MOD END
        chEntries.stream().map(ch -> ch.chStore).filter(s -> !s.isClosed()).forEach(CHStorage::close);
    }

    public boolean isClosed() {
        return baseGraph.isClosed();
    }

    public long getCapacity() {
        long cnt = baseGraph.getCapacity() + properties.getCapacity();
        // ORS-GH MOD START - additional code
        if (graphExtensions != null) {
            cnt += graphExtensions.getCapacity();
        }
        if (conditionalAccess != null) {
            cnt += conditionalAccess.getCapacity();
        }
        if (conditionalSpeed != null) {
            cnt += conditionalSpeed.getCapacity();
        }
        // ORS-GH MOD END
        return cnt;
    }

    /**
     * Avoid that edges and nodes of the base graph are further modified. Necessary as hook for e.g.
     * ch graphs on top to initialize themselves
     */
    public synchronized void freeze() {
        if (isFrozen())
            return;
        baseGraph.freeze();
    }

    public boolean isFrozen() {
        return baseGraph.isFrozen();
    }

    public String toDetailsString() {
        return baseGraph.toDetailsString();
    }

    @Override
    public String toString() {
        return encodingManager
                + "|" + getDirectory().getDefaultType()
                + "|" + baseGraph.nodeAccess.getDimension() + "D"
                + "|" + (baseGraph.supportsTurnCosts() ? baseGraph.turnCostStorage : "no_turn_cost")
                + "|" + getVersionsString();
    }

    private String getVersionsString() {
        return "nodes:" + Constants.VERSION_NODE +
                ",edges:" + Constants.VERSION_EDGE +
                ",geometry:" + Constants.VERSION_GEOMETRY +
                ",location_index:" + Constants.VERSION_LOCATION_IDX +
                ",string_index:" + Constants.VERSION_STRING_IDX +
                ",nodesCH:" + Constants.VERSION_NODE_CH +
                ",shortcuts:" + Constants.VERSION_SHORTCUT;
    }

    // now delegate all Graph methods to BaseGraph to avoid ugly programming flow ala
    // GraphHopperStorage storage = ..;
    // Graph g = storage.getBaseGraph();
    // instead directly the storage can be used to traverse the base graph
    @Override
    public Graph getBaseGraph() {
        return baseGraph;
    }

    @Override
    public int getNodes() {
        return baseGraph.getNodes();
    }

    @Override
    public int getEdges() {
        return baseGraph.getEdges();
    }

    @Override
    public NodeAccess getNodeAccess() {
        return baseGraph.getNodeAccess();
    }

    @Override
    public BBox getBounds() {
        return baseGraph.getBounds();
    }

    @Override
    public EdgeIteratorState edge(int a, int b) {
        return baseGraph.edge(a, b);
    }

    @Override
    public EdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
        return baseGraph.getEdgeIteratorState(edgeId, adjNode);
    }

    @Override
    public EdgeIteratorState getEdgeIteratorStateForKey(int edgeKey) {
        return baseGraph.getEdgeIteratorStateForKey(edgeKey);
    }

    @Override
    public AllEdgesIterator getAllEdges() {
        return baseGraph.getAllEdges();
    }

    @Override
    public EdgeExplorer createEdgeExplorer(EdgeFilter filter) {
        return baseGraph.createEdgeExplorer(filter);
    }

    @Override
    public TurnCostStorage getTurnCostStorage() {
        return baseGraph.getTurnCostStorage();
    }

    @Override
    public Weighting wrapWeighting(Weighting weighting) {
        return weighting;
    }

    @Override
    public int getOtherNode(int edge, int node) {
        return baseGraph.getOtherNode(edge, node);
    }

    @Override
    public boolean isAdjacentToNode(int edge, int node) {
        return baseGraph.isAdjacentToNode(edge, node);
    }

    /**
     * Flush and free base graph resources like way geometries and StringIndex
     */
    public void flushAndCloseGeometryAndNameStorage() {
        baseGraph.flushAndCloseGeometryAndNameStorage();
    }

    // ORS-GH MOD START change to public in order to be able to access it from ORSGraphHopperStorage subclass
    public static class CHEntry {
        public CHConfig chConfig;
        public CHStorage chStore;
        public RoutingCHGraphImpl chGraph;

        // ORS-GH MOD END
        public CHEntry(CHConfig chConfig, CHStorage chStore, RoutingCHGraphImpl chGraph) {
            this.chConfig = chConfig;
            this.chStore = chStore;
            this.chGraph = chGraph;
        }
    }
}
