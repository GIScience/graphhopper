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
package com.graphhopper.util;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.CHConfig;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class CHEdgeIteratorTest {
    @Test
    public void testUpdateFlags() {
        CarFlagEncoder carFlagEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = EncodingManager.create(carFlagEncoder);
        FastestWeighting weighting = new FastestWeighting(carFlagEncoder);
        EdgeFilter carOutFilter = AccessFilter.outEdges(carFlagEncoder.getAccessEnc());
        GraphHopperStorage g = new GraphBuilder(encodingManager).setCHConfigs(CHConfig.nodeBased("p", weighting)).create();
        BooleanEncodedValue accessEnc = carFlagEncoder.getAccessEnc();
        DecimalEncodedValue avSpeedEnc = carFlagEncoder.getAverageSpeedEnc();
        g.edge(0, 1).setDistance(12).set(accessEnc, true, true).set(avSpeedEnc, 10.0);
        g.edge(0, 2).setDistance(13).set(accessEnc, true, true).set(avSpeedEnc, 20.0);
        g.freeze();

        CHGraph lg = g.getCHGraph();
        assertEquals(2, GHUtility.count(lg.getAllEdges()));
        assertEquals(1, GHUtility.count(lg.createEdgeExplorer(carOutFilter).setBaseNode(1)));
        EdgeIteratorState iter = GHUtility.getEdge(lg, 0, 1);
        assertEquals(1, iter.getAdjNode());
        assertTrue(iter.get(accessEnc));
        assertTrue(iter.getReverse(accessEnc));
        assertEquals(10.0, iter.get(avSpeedEnc), .1);
        assertEquals(10.0, iter.getReverse(avSpeedEnc), .1);

        // update setProperties
        iter.set(accessEnc, true, false).set(avSpeedEnc, 20.0);
        assertEquals(12, iter.getDistance(), 1e-4);

        // update distance
        iter.setDistance(10);
        assertEquals(10, iter.getDistance(), 1e-4);
        assertEquals(0, GHUtility.count(lg.createEdgeExplorer(carOutFilter).setBaseNode(1)));
        iter = GHUtility.getEdge(lg, 0, 1);

        assertTrue(iter.get(accessEnc));
        assertFalse(iter.getReverse(accessEnc));
        assertEquals(20.0, iter.get(avSpeedEnc), .1);

        assertEquals(10, iter.getDistance(), 1e-4);
        assertEquals(1, GHUtility.getNeighbors(lg.createEdgeExplorer().setBaseNode(1)).size());
        assertEquals(0, GHUtility.getNeighbors(lg.createEdgeExplorer(carOutFilter).setBaseNode(1)).size());
    }
}
