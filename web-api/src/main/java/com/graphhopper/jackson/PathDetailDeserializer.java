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
package com.graphhopper.jackson;

import com.graphhopper.util.details.PathDetail;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

// ORS-GH MOD - ported to Jackson 3
public class PathDetailDeserializer extends ValueDeserializer<PathDetail> {

    @Override
    public PathDetail deserialize(JsonParser jp, DeserializationContext ctxt) throws JacksonException {
        JsonNode pathDetail = jp.readValueAsTree();
        if (pathDetail.size() != 3)
            throw new StreamReadException(jp, "PathDetail array must have exactly 3 entries but was " + pathDetail.size());

        JsonNode from = pathDetail.get(0);
        JsonNode to = pathDetail.get(1);
        JsonNode val = pathDetail.get(2);

        PathDetail pd;
        if (val.isBoolean())
            pd = new PathDetail(val.asBoolean());
        else if (val.isDouble())
            pd = new PathDetail(val.asDouble());
        else if (val.canConvertToLong())
            pd = new PathDetail(val.asLong());
        else if (val.isTextual())
            pd = new PathDetail(val.asString());
        else
            throw new StreamReadException(jp, "Unsupported type of PathDetail value " + pathDetail.getNodeType().name());

        pd.setFirst(from.asInt());
        pd.setLast(to.asInt());
        return pd;
    }
}
