/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson;

import com.graphhopper.jackson.geojson.parsers.*;
import com.graphhopper.jackson.geojson.serialization.GeometryDeserializer;
import com.graphhopper.jackson.geojson.serialization.GeometrySerializer;
import org.locationtech.jts.geom.*;
import tools.jackson.core.Version;
import tools.jackson.databind.module.SimpleModule;

public class JtsModule extends SimpleModule {

    public JtsModule() {
        this(new GeometryFactory());
    }

    public JtsModule(GeometryFactory geometryFactory) {
        super("JtsModule", new Version(1, 0, 0, null, "com.graphhopper", "graphhopper-web-api"));

        addSerializer(Geometry.class, new GeometrySerializer());
        GenericGeometryParser genericGeometryParser = new GenericGeometryParser(geometryFactory);
        addDeserializer(Geometry.class, new GeometryDeserializer<>(genericGeometryParser));
        addDeserializer(Point.class, new GeometryDeserializer<>(new PointParser(geometryFactory)));
        addDeserializer(MultiPoint.class, new GeometryDeserializer<>(new MultiPointParser(geometryFactory)));
        addDeserializer(LineString.class, new GeometryDeserializer<>(new LineStringParser(geometryFactory)));
        addDeserializer(MultiLineString.class, new GeometryDeserializer<>(new MultiLineStringParser(geometryFactory)));
        addDeserializer(Polygon.class, new GeometryDeserializer<>(new PolygonParser(geometryFactory)));
        addDeserializer(MultiPolygon.class, new GeometryDeserializer<>(new MultiPolygonParser(geometryFactory)));
        addDeserializer(GeometryCollection.class, new GeometryDeserializer<>(new GeometryCollectionParser(geometryFactory, genericGeometryParser)));
    }
}
