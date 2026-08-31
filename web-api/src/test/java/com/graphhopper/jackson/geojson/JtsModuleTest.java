/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

// ORS-GH MOD - new class
class JtsModuleTest {
    private static final GeometryFactory GF = new GeometryFactory();
    private final JsonMapper mapper = JsonMapper.builder().addModule(new JtsModule()).build();

    @Test
    void roundTripsPoint() {
        assertRoundTrips(GF.createPoint(new Coordinate(102.0, 0.5)), Point.class);
    }

    @Test
    void roundTripsMultiPoint() {
        assertRoundTrips(GF.createMultiPointFromCoords(new Coordinate[]{
                new Coordinate(100.0, 0.0),
                new Coordinate(101.0, 1.0)}), MultiPoint.class);
    }

    @Test
    void roundTripsLineString() {
        assertRoundTrips(GF.createLineString(new Coordinate[]{
                new Coordinate(102.0, 0.0),
                new Coordinate(103.0, 1.0),
                new Coordinate(104.0, 0.0)}), LineString.class);
    }

    @Test
    void roundTripsMultiLineString() {
        LineString a = GF.createLineString(new Coordinate[]{new Coordinate(100.0, 0.0), new Coordinate(101.0, 1.0)});
        LineString b = GF.createLineString(new Coordinate[]{new Coordinate(102.0, 2.0), new Coordinate(103.0, 3.0)});
        assertRoundTrips(GF.createMultiLineString(new LineString[]{a, b}), MultiLineString.class);
    }

    @Test
    void roundTripsPolygonWithHole() {
        LinearRing shell = GF.createLinearRing(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(0, 10), new Coordinate(10, 10), new Coordinate(10, 0), new Coordinate(0, 0)});
        LinearRing hole = GF.createLinearRing(new Coordinate[]{
                new Coordinate(2, 2), new Coordinate(2, 4), new Coordinate(4, 4), new Coordinate(4, 2), new Coordinate(2, 2)});
        assertRoundTrips(GF.createPolygon(shell, new LinearRing[]{hole}), Polygon.class);
    }

    @Test
    void roundTripsMultiPolygon() {
        Polygon a = GF.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(0, 1), new Coordinate(1, 1), new Coordinate(1, 0), new Coordinate(0, 0)});
        Polygon b = GF.createPolygon(new Coordinate[]{
                new Coordinate(5, 5), new Coordinate(5, 6), new Coordinate(6, 6), new Coordinate(6, 5), new Coordinate(5, 5)});
        assertRoundTrips(GF.createMultiPolygon(new Polygon[]{a, b}), MultiPolygon.class);
    }

    @Test
    void roundTripsGeometryCollection() {
        Point point = GF.createPoint(new Coordinate(102.0, 0.5));
        LineString line = GF.createLineString(new Coordinate[]{new Coordinate(102.0, 0.0), new Coordinate(103.0, 1.0)});
        assertRoundTrips(GF.createGeometryCollection(new Geometry[]{point, line}), org.locationtech.jts.geom.GeometryCollection.class);
    }

    private <T extends Geometry> void assertRoundTrips(T geometry, Class<T> type) {
        String json = mapper.writeValueAsString(geometry);
        T parsed = mapper.readValue(json, type);
        assertEquals(geometry, parsed);
    }
}
