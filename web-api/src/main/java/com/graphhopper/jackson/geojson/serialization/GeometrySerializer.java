/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson.serialization;

import org.locationtech.jts.geom.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.util.Arrays;

import static com.graphhopper.jackson.geojson.GeoJson.*;

public class GeometrySerializer extends ValueSerializer<Geometry> {

    @Override
    public void serialize(Geometry value, JsonGenerator jgen, SerializationContext provider) throws JacksonException {
        writeGeometry(jgen, value);
    }

    public void writeGeometry(JsonGenerator jgen, Geometry value) throws JacksonException {
        if (value instanceof Polygon) {
            writePolygon(jgen, (Polygon) value);

        } else if (value instanceof Point) {
            writePoint(jgen, (Point) value);

        } else if (value instanceof MultiPoint) {
            writeMultiPoint(jgen, (MultiPoint) value);

        } else if (value instanceof MultiPolygon) {
            writeMultiPolygon(jgen, (MultiPolygon) value);

        } else if (value instanceof LineString) {
            writeLineString(jgen, (LineString) value);

        } else if (value instanceof MultiLineString) {
            writeMultiLineString(jgen, (MultiLineString) value);

        } else if (value instanceof GeometryCollection) {
            writeGeometryCollection(jgen, (GeometryCollection) value);

        } else {
            throw DatabindException.from(jgen, "Geometry type "
                    + value.getClass().getName() + " cannot be serialized as GeoJSON." +
                    "Supported types are: " + Arrays.asList(
                    Point.class.getName(),
                    LineString.class.getName(),
                    Polygon.class.getName(),
                    MultiPoint.class.getName(),
                    MultiLineString.class.getName(),
                    MultiPolygon.class.getName(),
                    GeometryCollection.class.getName()));
        }
    }

    private void writeGeometryCollection(JsonGenerator jgen, GeometryCollection value) throws JacksonException {
        jgen.writeStartObject();
        jgen.writeStringProperty(TYPE, GEOMETRY_COLLECTION);
        jgen.writeArrayPropertyStart(GEOMETRIES);

        for (int i = 0; i != value.getNumGeometries(); ++i) {
            writeGeometry(jgen, value.getGeometryN(i));
        }

        jgen.writeEndArray();
        jgen.writeEndObject();
    }

    private void writeMultiPoint(JsonGenerator jgen, MultiPoint value) throws JacksonException {
        jgen.writeStartObject();
        jgen.writeStringProperty(TYPE, MULTI_POINT);
        jgen.writeArrayPropertyStart(COORDINATES);

        for (int i = 0; i != value.getNumGeometries(); ++i) {
            writePointCoords(jgen, (Point) value.getGeometryN(i));
        }

        jgen.writeEndArray();
        jgen.writeEndObject();
    }

    private void writeMultiLineString(JsonGenerator jgen, MultiLineString value) throws JacksonException {
        jgen.writeStartObject();
        jgen.writeStringProperty(TYPE, MULTI_LINE_STRING);
        jgen.writeArrayPropertyStart(COORDINATES);

        for (int i = 0; i != value.getNumGeometries(); ++i) {
            writeLineStringCoords(jgen, (LineString) value.getGeometryN(i));
        }

        jgen.writeEndArray();
        jgen.writeEndObject();
    }

    @Override
    public Class<Geometry> handledType() {
        return Geometry.class;
    }

    private void writeMultiPolygon(JsonGenerator jgen, MultiPolygon value) throws JacksonException {
        jgen.writeStartObject();
        jgen.writeStringProperty(TYPE, MULTI_POLYGON);
        jgen.writeArrayPropertyStart(COORDINATES);

        for (int i = 0; i != value.getNumGeometries(); ++i) {
            writePolygonCoordinates(jgen, (Polygon) value.getGeometryN(i));
        }

        jgen.writeEndArray();
        jgen.writeEndObject();
    }

    private void writePolygon(JsonGenerator jgen, Polygon value) throws JacksonException {
        jgen.writeStartObject();
        jgen.writeStringProperty(TYPE, POLYGON);
        jgen.writeName(COORDINATES);
        writePolygonCoordinates(jgen, value);

        jgen.writeEndObject();
    }

    private void writePolygonCoordinates(JsonGenerator jgen, Polygon value) throws JacksonException {
        jgen.writeStartArray();
        writeLineStringCoords(jgen, value.getExteriorRing());

        for (int i = 0; i < value.getNumInteriorRing(); ++i) {
            writeLineStringCoords(jgen, value.getInteriorRingN(i));
        }
        jgen.writeEndArray();
    }

    private void writeLineStringCoords(JsonGenerator jgen, LineString ring) throws JacksonException {
        jgen.writeStartArray();
        for (int i = 0; i != ring.getNumPoints(); ++i) {
            Point p = ring.getPointN(i);
            writePointCoords(jgen, p);
        }
        jgen.writeEndArray();
    }

    private void writeLineString(JsonGenerator jgen, LineString lineString) throws JacksonException {
        jgen.writeStartObject();
        jgen.writeStringProperty(TYPE, LINE_STRING);
        jgen.writeName(COORDINATES);
        writeLineStringCoords(jgen, lineString);
        jgen.writeEndObject();
    }

    private void writePoint(JsonGenerator jgen, Point p) throws JacksonException {
        jgen.writeStartObject();
        jgen.writeStringProperty(TYPE, POINT);
        jgen.writeName(COORDINATES);
        writePointCoords(jgen, p);
        jgen.writeEndObject();
    }

    private void writePointCoords(JsonGenerator jgen, Point p) throws JacksonException {
        jgen.writeStartArray();

        jgen.writeNumber(p.getCoordinate().x);
        jgen.writeNumber(p.getCoordinate().y);

        if (!Double.isNaN(p.getCoordinate().z)) {
            jgen.writeNumber(p.getCoordinate().z);
        }
        jgen.writeEndArray();
    }

}
