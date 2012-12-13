/*
 *  Copyright 2012 Peter Karich
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper;

import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.Location2IDIndex;
import com.graphhopper.storage.Location2IDQuadtree;
import com.graphhopper.storage.MMapDirectory;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DouglasPeucker;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GeoPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Main wrapper of the offline API for a simple and efficient usage.
 *
 * @see GraphHopperAPI
 * @author Peter Karich
 */
public class GraphHopper implements GraphHopperAPI {

    private Graph graph;
    private AlgorithmPreparation prepare;
    private Location2IDIndex index;
    private boolean inMemory = true;
    private boolean memoryMapped;
    private boolean storeOnFlush;
    private boolean levelGraph;
    private double minPathPrecision = 1;
    private static String defaultAlgo = "astar";

    public GraphHopper() {
        prepare = Helper.createAlgoPrepare(defaultAlgo);
    }

    /**
     * For testing
     */
    GraphHopper(Graph g) {
        this();
        this.graph = g;
        initIndex(new RAMDirectory());
    }

    public GraphHopper setInMemory(boolean inMemory, boolean storeOnFlush) {
        if (inMemory) {
            this.inMemory = true;
            this.memoryMapped = false;
            this.storeOnFlush = storeOnFlush;
        } else {
            memoryMapped();
        }
        return this;
    }

    public GraphHopper memoryMapped() {
        this.inMemory = false;
        memoryMapped = true;
        return this;
    }

    public GraphHopper levelGraph() {
        levelGraph = true;
        return this;
    }

    // TODO accept zipped folders and osm files too!
    @Override
    public GraphHopper load(String graphHopperFile) {
        if (graph != null)
            throw new IllegalStateException("graph is already loaded");

        GraphStorage storage;
        Directory dir;
        if (memoryMapped) {
            dir = new MMapDirectory(graphHopperFile);
        } else if (inMemory) {
            dir = new RAMDirectory(graphHopperFile, storeOnFlush);
        } else
            throw new IllegalStateException("either memory mapped or in-memory!");

        if (levelGraph)
            // TODO use level algorithm then!?
            storage = new LevelGraphStorage(dir);
        else
            storage = new GraphStorage(dir);

        if (!storage.loadExisting())
            throw new IllegalStateException("TODO load via OSMReader!");

        graph = storage;
        initIndex(dir);
        return this;
    }

    @Override
    public GraphHopper minPathPrecision(double precision) {
        minPathPrecision = precision;
        return this;
    }

    @Override
    public GraphHopper algorithm(String algo) {
        // TODO does not work with levelgraph/CH!
        if (levelGraph)
            throw new IllegalStateException("not supported yet");

        prepare = Helper.createAlgoPrepare(algo);
        return this;
    }

    @Override
    public PathHelper route(GeoPoint startPoint, GeoPoint endPoint) {
        if (prepare == null)
            prepare = Helper.createAlgoPrepare(defaultAlgo);

        RoutingAlgorithm algo = prepare.createAlgo();
        int from = index.findID(startPoint.lat, startPoint.lon);
        int to = index.findID(endPoint.lat, endPoint.lon);
        Path path = algo.calcPath(from, to);
        path.simplify(new DouglasPeucker(graph).setMaxDist(minPathPrecision));
        int nodes = path.nodes();
        List<GeoPoint> list = new ArrayList<GeoPoint>(nodes);
        if (path.found())
            for (int i = 0; i < nodes; i++) {
                list.add(new GeoPoint(graph.getLatitude(path.node(i)), graph.getLongitude(path.node(i))));
            }
        return new PathHelper(list).distance(path.distance()).time(path.time());
    }

    private void initIndex(Directory dir) {
        Location2IDQuadtree tmp = new Location2IDQuadtree(graph, dir);
        if (!tmp.loadExisting()) {
            BBox bbox = graph.getBounds();
            double dist = new DistanceCalc().calcDist(bbox.maxLat, bbox.minLon, bbox.minLat, bbox.maxLon);
            // convert to km and maximum 5000km => 25mio capacity, minimum capacity is 2000
            dist = Math.min(dist / 1000, 5000);
            tmp.prepareIndex(Math.max(2000, (int) (dist * dist)));
        }
        index = tmp;
    }
}
