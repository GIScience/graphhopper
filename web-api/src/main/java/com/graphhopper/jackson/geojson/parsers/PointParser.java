/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson.parsers;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import tools.jackson.databind.JsonNode;

import static com.graphhopper.jackson.geojson.GeoJson.COORDINATES;

// ORS-GH MOD - new class
public class PointParser extends BaseParser implements GeometryParser<Point> {

    public PointParser(GeometryFactory geometryFactory) {
        super(geometryFactory);
    }

    public static Coordinate coordinateFromJson(JsonNode array) {
        assert array.isArray() && (array.size() == 2 || array.size() == 3) : "expecting coordinate array with single point [ x, y, |z| ]";

        if (array.size() == 2) {
            return new Coordinate(
                    array.get(0).asDouble(),
                    array.get(1).asDouble());
        }

        return new Coordinate(
                array.get(0).asDouble(),
                array.get(1).asDouble(),
                array.get(2).asDouble());
    }

    public static Coordinate[] coordinatesFromJson(JsonNode array) {
        Coordinate[] points = new Coordinate[array.size()];
        for (int i = 0; i != array.size(); ++i) {
            points[i] = PointParser.coordinateFromJson(array.get(i));
        }
        return points;
    }

    public Point pointFromJson(JsonNode node) {
        return geometryFactory.createPoint(
                coordinateFromJson(node.get(COORDINATES)));
    }

    @Override
    public Point geometryFromJson(JsonNode node) {
        return pointFromJson(node);
    }
}
