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
package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import static com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER;

/**
 * Calculates the fastest route with distance influence controlled by a new parameter.
 * <p>
 *
 * @author Peter Karich
 */
public class ShortFastestWeighting extends FastestWeighting {
    private static final String NAME = "short_fastest";
    private static final String TIME_FACTOR = "short_fastest.time_factor";
    private static final String DISTANCE_FACTOR = "short_fastest.distance_factor";
    private final double distanceFactor;
    private final double timeFactor;

    public ShortFastestWeighting(FlagEncoder encoder, PMap map, TurnCostProvider turnCostProvider) {
        super(encoder, map, turnCostProvider);
        timeFactor = checkBounds(TIME_FACTOR, map.getDouble(TIME_FACTOR, 1), 0, 10);

        // default value derived from the cost for time e.g. 25€/hour and for distance 0.5€/km
        distanceFactor = checkBounds(DISTANCE_FACTOR, map.getDouble(DISTANCE_FACTOR, 0.07), 0, 10);

        if (timeFactor < 1e-5 && distanceFactor < 1e-5)
            throw new IllegalArgumentException("[" + NAME + "] one of distance_factor or time_factor has to be non-zero");
    }

    public ShortFastestWeighting(FlagEncoder encoder, double distanceFactor) {
        this(encoder, distanceFactor, NO_TURN_COST_PROVIDER);
    }

    public ShortFastestWeighting(FlagEncoder encoder, double distanceFactor, TurnCostProvider turnCostProvider) {
        super(encoder, new PMap(), turnCostProvider);
        this.distanceFactor = checkBounds(DISTANCE_FACTOR, distanceFactor, 0, 10);
        this.timeFactor = 1;
    }

    @Override
    public double getMinWeight(double distance) {
        return super.getMinWeight(distance) * timeFactor + distance * distanceFactor;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse, long edgeEnterTime) {
        double time = super.calcEdgeWeight(edgeState, reverse, edgeEnterTime);
        return time * timeFactor + edgeState.getDistance() * distanceFactor;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
