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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;

public class ParameterMapTest {

    private static final int ORIGINAL_MAX_PARAMS = ParameterMap.DEFAULT_MAX_PARAMS;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private void resetToDefaults() {
        ParameterMap.setMaxParameters(ORIGINAL_MAX_PARAMS);
        ParameterMap.setFailOnParameterLimit(false);
    }

    @Before
    public void setUp() {
        resetToDefaults();
    }

    @After
    public void tearDown() {
        resetToDefaults();
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
        exception.expect(IllegalStateException.class);
        exception.expectMessage("Too many name/value pairs");
        exception.expectMessage("2");
        pm.addParameter(createTestParameter("param3", "value3"), false);
    }

    @Test
    public void testParameterLimitExactlyAtBoundary() {
        ParameterMap pm = new ParameterMap();
        ParameterMap.setMaxParameters(1);
        ParameterMap.setFailOnParameterLimit(false);

        // Add exactly at limit
        pm.addParameter(createTestParameter("param1", "value1"), false);
        assertEquals(1, pm.size());

        // Next addition should trigger warning and be ignored
        pm.addParameter(createTestParameter("param2", "value2"), false);
        assertEquals(1, pm.size()); // Should remain 1
    }

    @Test
    public void testUnlimitedParameters() {
        ParameterMap pm = new ParameterMap();
        ParameterMap.setMaxParameters(-1); // Unlimited
        ParameterMap.setFailOnParameterLimit(false);

        // Should allow unlimited parameters
        for (int i = 1; i <= 100; i++) {
            pm.addParameter(createTestParameter("param" + i, "value" + i), false);
        }
        assertEquals(100, pm.size());
    }

    @Test
    public void testFailOnLimitWithLargeLimit() {
        ParameterMap pm = new ParameterMap();
        ParameterMap.setMaxParameters(5);
        ParameterMap.setFailOnParameterLimit(true);

        // Add up to limit
        for (int i = 1; i <= 5; i++) {
            pm.addParameter(createTestParameter("param" + i, "value" + i), false);
        }
        assertEquals(5, pm.size());

        // Next should fail
        exception.expect(IllegalStateException.class);
        exception.expectMessage("Too many name/value pairs");
        exception.expectMessage("5");
        pm.addParameter(createTestParameter("param6", "value6"), false);
    }

    @Test
    public void testFailOnLimitDisabledWithZeroLimit() {
        ParameterMap pm = new ParameterMap();
        ParameterMap.setMaxParameters(0); // Becomes -1 (unlimited)
        ParameterMap.setFailOnParameterLimit(true); // Shouldn't matter since unlimited

        // Should allow parameters despite failOnLimit=true
        pm.addParameter(createTestParameter("param1", "value1"), false);
        assertEquals(1, pm.size());
    }

    private RequestParameter createTestParameter(String name, String value) {
        return new ContainerRequestParameter(name, value, "UTF-8");
    }
}
