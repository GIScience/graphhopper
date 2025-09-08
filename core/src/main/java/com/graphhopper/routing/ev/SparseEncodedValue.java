package com.graphhopper.routing.ev;

public interface SparseEncodedValue<E> extends EncodedValue {
    public E get(int edgeID);
    public void set(int edgeID, Object value);
}
