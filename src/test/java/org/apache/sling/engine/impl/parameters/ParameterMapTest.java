/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.engine.impl.parameters;

import org.apache.sling.api.request.RequestParameter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ParameterMapTest {

    private static final int ORIGINAL_MAX_PARAMS = ParameterMap.DEFAULT_MAX_PARAMS;

    @Before
    public void setUp() {
        // Reset to default values
        ParameterMap.setMaxParameters(ORIGINAL_MAX_PARAMS);
        ParameterMap.setFailOnParameterLimit(false);
    }

    @After
    public void tearDown() {
        // Reset to default values
        ParameterMap.setMaxParameters(ORIGINAL_MAX_PARAMS);
        ParameterMap.setFailOnParameterLimit(false);
    }

    @Test
    public void testDefaultBehavior() {
        ParameterMap pm = new ParameterMap();
        ParameterMap.setMaxParameters(2);

        // Should work normally within limit
        pm.addParameter(createTestParameter("param1", "value1"), false);
        pm.addParameter(createTestParameter("param2", "value2"), false);
        assertEquals(2, pm.size());

        // Should log warning and continue when exceeding limit
        pm.addParameter(createTestParameter("param3", "value3"), false);
        assertEquals(2, pm.size()); // Should still be 2, param3 ignored
    }

    @Test
    public void testFailOnParameterLimit() {
        ParameterMap pm = new ParameterMap();
        ParameterMap.setMaxParameters(2);
        ParameterMap.setFailOnParameterLimit(true);

        // Should work normally within limit
        pm.addParameter(createTestParameter("param1", "value1"), false);
        pm.addParameter(createTestParameter("param2", "value2"), false);
        assertEquals(2, pm.size());

        // Should throw exception when exceeding limit
        try {
            pm.addParameter(createTestParameter("param3", "value3"), false);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Too many name/value pairs"));
            assertTrue(e.getMessage().contains("2"));
        }
    }

    private RequestParameter createTestParameter(String name, String value) {
        return new ContainerRequestParameter(name, value, "UTF-8");
    }
}
