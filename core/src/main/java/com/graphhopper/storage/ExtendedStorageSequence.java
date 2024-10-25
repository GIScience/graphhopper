package com.graphhopper.storage;

import java.io.IOException;
import java.util.ArrayList;

// ORS-GH MOD - additional class
public class ExtendedStorageSequence  implements GraphExtension {

    private GraphExtension[] extensions;
    private int numExtensions;

    public ExtendedStorageSequence(ArrayList<GraphExtension> seq) {
        numExtensions = seq.size();
        extensions = seq.toArray(new GraphExtension[numExtensions]);
    }

    public GraphExtension[] getExtensions() {
        return extensions;
    }

    public ExtendedStorageSequence create(long initSize) {
        for (int i = 0; i < numExtensions; i++) {
            extensions[i].create(initSize);
        }

        return this;
    }

    public void init(Graph graph, Directory dir) {
        for (int i = 0; i < numExtensions; i++) {
            extensions[i].init(graph, dir);
        }
    }

    public boolean loadExisting() {
        boolean result = true;
        for (int i = 0; i < numExtensions; i++) {
            if (!extensions[i].loadExisting()) {
                result = false;
                break;
            }
        }

        return result;
    }

    public void flush() {
        for (int i = 0; i < numExtensions; i++) {
            extensions[i].flush();
        }
    }

    @Override
    public long getCapacity() {
        long ret = 0;
        for (int i = 0; i < numExtensions; i++) {
            ret += extensions[i].getCapacity();
        }
        return ret;
    }
    @Override
    public void close() {
        for (int i = 0; i < numExtensions; i++) {
            try {
                extensions[i].close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isClosed() {
        // TODO Auto-generated method stub
        return false;
    }
}

