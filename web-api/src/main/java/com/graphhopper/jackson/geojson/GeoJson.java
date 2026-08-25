/*
 * Ported to Jackson 3 from bedatadriven/jackson-datatype-jts
 * (https://github.com/bedatadriven/jackson-datatype-jts), licensed under the
 * Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package com.graphhopper.jackson.geojson;

public class GeoJson {
    public static final String POINT = "Point";
    public static final String LINE_STRING = "LineString";
    public static final String POLYGON = "Polygon";

    public static final String MULTI_POINT = "MultiPoint";
    public static final String MULTI_LINE_STRING = "MultiLineString";
    public static final String MULTI_POLYGON = "MultiPolygon";

    public static final String GEOMETRY_COLLECTION = "GeometryCollection";

    public static final String TYPE = "type";

    public static final String GEOMETRIES = "geometries";

    public static final String COORDINATES = "coordinates";
}
