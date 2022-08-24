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

import com.graphhopper.routing.weighting.Weighting;

public class RoutingCHGraphImpl implements RoutingCHGraph {
    private final BaseGraph baseGraph;
    private final CHStorage chStorage;
    private final Weighting weighting;

    // ORS-GH MOD START - CALT
    // TODO ORS: provide a reason for removal of 'final'
    // TODO ORS: shortcuts got moved somewhere, probably chStorage?
    //final DataAccess shortcuts;
    //    DataAccess shortcuts;
    // ORS-GH MOD END
    // ORS-GH MOD START
    // CALT add member variable
    private boolean isTypeCore;
    private int coreNodeCount = -1;
    private int S_TIME;
    // ORS-GH MOD END

    public RoutingCHGraphImpl(BaseGraph baseGraph, CHStorage chStorage, Weighting weighting) {
        if (weighting.hasTurnCosts() && !chStorage.isEdgeBased())
            throw new IllegalArgumentException("Weighting has turn costs, but CHStorage is node-based");
        this.baseGraph = baseGraph;
        this.chStorage = chStorage;
        this.weighting = weighting;
        // ORS-GH MOD START
        // CALT include type in directory location
        // this.nodesCH = dir.find("nodes_ch_" + name, DAType.getPreferredInt(dir.getDefaultType()));
        // this.shortcuts = dir.find("shortcuts_" + name, DAType.getPreferredInt(dir.getDefaultType()));
        // TODO ORS: This need to be moved probably to chStorage
        // TODO ORS (minor): use polymorphism instead of this mix of string & boolean flags
//        this.nodesCH = dir.find("nodes_" + chConfig.getType() + "_" + name, DAType.getPreferredInt(dir.getDefaultType()));
//        this.shortcuts = dir.find("shortcuts_" + chConfig.getType() + "_" + name, DAType.getPreferredInt(dir.getDefaultType()));
//        this.isTypeCore = chConfig.getType().equals(CHProfile.TYPE_CORE);
        // ORS-GH MOD END
    }

    @Override
    public int getNodes() {
        return baseGraph.getNodes();
    }

    @Override
    public int getEdges() {
        return baseGraph.getEdges() + chStorage.getShortcuts();
    }

    @Override
    public RoutingCHEdgeExplorer createInEdgeExplorer() {
        return RoutingCHEdgeIteratorImpl.inEdges(chStorage, baseGraph, weighting);
    }

    @Override
    public RoutingCHEdgeExplorer createOutEdgeExplorer() {
        return RoutingCHEdgeIteratorImpl.outEdges(chStorage, baseGraph, weighting);
    }

    @Override
    public RoutingCHEdgeIteratorState getEdgeIteratorState(int chEdge, int adjNode) {
        RoutingCHEdgeIteratorStateImpl edgeState =
                new RoutingCHEdgeIteratorStateImpl(chStorage, baseGraph, new BaseGraph.EdgeIteratorStateImpl(baseGraph), weighting);
        if (edgeState.init(chEdge, adjNode))
            return edgeState;
        // if edgeId exists, but adjacent nodes do not match
        return null;
    }

    @Override
    public int getLevel(int node) {
        return chStorage.getLevel(chStorage.toNodePointer(node));
    }

    @Override
    public Graph getBaseGraph() {
        return baseGraph;
    }

    @Override
    public Weighting getWeighting() {
        return weighting;
    }

    @Override
    public boolean hasTurnCosts() {
        return weighting.hasTurnCosts();
    }

    @Override
    public boolean isEdgeBased() {
        return chStorage.isEdgeBased();
    }

    @Override
    public double getTurnWeight(int edgeFrom, int nodeVia, int edgeTo) {
        return weighting.calcTurnWeight(edgeFrom, nodeVia, edgeTo);
    }

    // ORS-GH MOD START add method
    public int getCoreNodes() {
        return chStorage.getCoreNodes();
    }
    // ORS-GH MOD END

    // ORS-GH MOD START
    // CALT add method
    // TODO ORS: need a different way to create the name, ideally without the
    //           use of weightings
    public RoutingCHGraphImpl setShortcutsStorage(Weighting w, Directory dir, String suffix, boolean edgeBased){
        // ORS ORIGINAL: final String name = AbstractWeighting.weightingToFileName(w);
        // ORS temporal fix:
        final String name = w.getName(); // TODO ORS: can we use chConfig.getName()?

//        this.shortcuts = dir.find("shortcuts_" + suffix + name);
        return this;
    }
    // ORS-GH MOD END
}
