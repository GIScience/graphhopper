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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

// ORS-GH MOD - ported to Jackson 3
public class InstructionListSerializer extends ValueSerializer<InstructionList> {
    @Override
    public void serialize(InstructionList instructions, JsonGenerator jsonGenerator, SerializationContext serializationContext) throws JacksonException {
        List<Map<String, Object>> instrList = new ArrayList<>(instructions.size());
        int pointsIndex = 0;
        for (Instruction instruction : instructions) {
            Map<String, Object> instrJson = new HashMap<>();
            instrList.add(instrJson);

            instrJson.put("text", Helper.firstBig(instruction.getTurnDescription(instructions.getTr())));

            instrJson.put("street_name", instruction.getName());
            instrJson.put("time", instruction.getTime());
            instrJson.put("distance", Helper.round(instruction.getDistance(), 3));
            instrJson.put("sign", instruction.getSign());
            instrJson.putAll(instruction.getExtraInfoJSON());

            int tmpIndex = pointsIndex + instruction.getLength();
            instrJson.put("interval", Arrays.asList(pointsIndex, tmpIndex));
            pointsIndex = tmpIndex;

        }
        jsonGenerator.writePOJO(instrList);
    }
}
