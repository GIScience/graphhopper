/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson.parsers;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import tools.jackson.databind.JsonNode;

import static com.graphhopper.jackson.geojson.GeoJson.COORDINATES;

// ORS-GH MOD - new class
public class PolygonParser extends BaseParser implements GeometryParser<Polygon> {

    public PolygonParser(GeometryFactory geometryFactory) {
        super(geometryFactory);
    }

    public Polygon polygonFromJson(JsonNode node) {
        JsonNode arrayOfRings = node.get(COORDINATES);
        return polygonFromJsonArrayOfRings(arrayOfRings);
    }

    public Polygon polygonFromJsonArrayOfRings(JsonNode arrayOfRings) {
        LinearRing shell = linearRingsFromJson(arrayOfRings.get(0));
        int size = arrayOfRings.size();
        LinearRing[] holes = new LinearRing[size - 1];
        for (int i = 1; i < size; i++) {
            holes[i - 1] = linearRingsFromJson(arrayOfRings.get(i));
        }
        return geometryFactory.createPolygon(shell, holes);
    }

    private LinearRing linearRingsFromJson(JsonNode coordinates) {
        assert coordinates.isArray() : "expected coordinates array";
        return geometryFactory.createLinearRing(PointParser.coordinatesFromJson(coordinates));
    }

    @Override
    public Polygon geometryFromJson(JsonNode node) {
        return polygonFromJson(node);
    }
}
