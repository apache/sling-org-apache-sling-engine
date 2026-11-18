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

import java.util.Collection;
import java.util.Map;

import jakarta.servlet.http.Part;
import org.apache.sling.api.request.RequestParameter;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Test.None;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ParameterMapTest {

    private ParameterMap map = new ParameterMap();

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#clear()}.
     */
    @Test
    public void testClear() {
        assertThrows(UnsupportedOperationException.class, map::clear);
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#setMaxParameters(int)}.
     */
    @Test(expected = None.class)
    public void testSetMaxParameters() {
        try {
            ParameterMap.setMaxParameters(11);
            ParameterMap.setMaxParameters(0);
        } finally {
            // restore the default
            ParameterMap.setMaxParameters(ParameterMap.DEFAULT_MAX_PARAMS);
        }
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#getValue(java.lang.String)}.
     */
    @Test
    public void testGetValue() {
        assertNull(map.getValue("key1"));

        map.setParameters("key1", new RequestParameter[0]);
        assertNull(map.getValue("key1"));

        final ContainerRequestParameter param1 = new ContainerRequestParameter("key1", "value1", null);
        map.setParameters("key1", new RequestParameter[] {param1});
        assertEquals(param1, map.getValue("key1"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#getValues(java.lang.String)}.
     */
    @Test
    public void testGetValues() {
        assertNull(map.getValues("key1"));

        final ContainerRequestParameter param1 = new ContainerRequestParameter("key1", "value1", null);
        final RequestParameter[] values1 = new RequestParameter[] {param1};
        map.setParameters("key1", values1);
        assertArrayEquals(values1, map.getValues("key1"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#renameParameter(java.lang.String, java.lang.String)}.
     */
    @Test
    public void testRenameParameter() {
        final ContainerRequestParameter param1 = new ContainerRequestParameter("key1", "value1", null);
        map.setParameters("key1", new RequestParameter[] {param1});

        map.renameParameter("key1", "key2");
        assertNull(map.getValue("key1"));
        assertEquals(param1, map.getValue("key2"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#addParameter(org.apache.sling.api.request.RequestParameter, boolean)}.
     */
    @Test
    public void testAddParameter() {
        final ContainerRequestParameter param1 = new ContainerRequestParameter("key1", "value1", null);
        map.addParameter(param1, true);

        assertEquals(param1, map.getValue("key1"));

        final ContainerRequestParameter param2 = new ContainerRequestParameter("key1", "value2", null);
        map.addParameter(param2, false);
        assertEquals(param1, map.getValue("key1"));

        final ContainerRequestParameter param3 = new ContainerRequestParameter("key1", "value3", null);
        map.addParameter(param3, true);
        assertEquals(param3, map.getValue("key1"));

        try {
            ParameterMap.setMaxParameters(3);
            final ContainerRequestParameter param4 = new ContainerRequestParameter("key1", "value4", null);
            map.addParameter(param4, true);
            assertEquals(param3, map.getValue("key1"));
        } finally {
            // restore the default
            ParameterMap.setMaxParameters(ParameterMap.DEFAULT_MAX_PARAMS);
        }
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#setParameters(java.lang.String, org.apache.sling.api.request.RequestParameter[])}.
     */
    @Test
    public void testSetParameters() {
        final ContainerRequestParameter param1 = new ContainerRequestParameter("key1a", "value1a", null);
        final RequestParameter[] values1 = new RequestParameter[] {param1};
        map.setParameters("key1a", values1);
        assertEquals("value1a", map.getStringValue("key1a"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#getStringValue(java.lang.String)}.
     */
    @Test
    public void testGetStringValue() {
        assertNull(map.getStringValue("key1"));

        final ContainerRequestParameter param1 = new ContainerRequestParameter("key1", "value1", null);
        final RequestParameter[] values1 = new RequestParameter[] {param1};
        map.setParameters("key1", values1);
        assertEquals("value1", map.getStringValue("key1"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#getStringValues(java.lang.String)}.
     */
    @Test
    public void testGetStringValues() {
        assertNull(map.getStringValues("key1"));

        final ContainerRequestParameter param1 = new ContainerRequestParameter("key1", "value1", null);
        final RequestParameter[] values1 = new RequestParameter[] {param1};
        map.setParameters("key1", values1);
        assertArrayEquals(new String[] {"value1"}, map.getStringValues("key1"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#getStringParameterMap()}.
     */
    @Test
    public void testGetStringParameterMap() {
        assertTrue(map.getStringParameterMap().isEmpty());

        final ContainerRequestParameter param1 = new ContainerRequestParameter("key1", "value1", null);
        final RequestParameter[] values1 = new RequestParameter[] {param1};
        map.setParameters("key1", values1);
        assertFalse(map.getStringParameterMap().isEmpty());
        // one more time for cached value coverage
        assertFalse(map.getStringParameterMap().isEmpty());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#getPart(java.lang.String)}.
     */
    @Test
    public void testGetPart() {
        assertNull(map.getPart("key1"));

        final ContainerRequestParameter param1 = new ContainerRequestParameter("key1", "value1", null);
        map.addParameter(param1, true);
        assertNull(map.getPart("key1"));

        Part part2 = Mockito.mock(Part.class);
        Mockito.doReturn("key1").when(part2).getName();
        final MultipartRequestParameter param2 = new MultipartRequestParameter(part2, null);
        map.addParameter(param2, true);

        assertEquals("key1", ((Part) map.getPart("key1")).getName());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#getParts()}.
     */
    @Test
    public void testGetParts() {
        assertTrue(map.getParts().isEmpty());

        map.setParameters("key1", new RequestParameter[0]);
        assertTrue(map.getParts().isEmpty());

        final ContainerRequestParameter param1 = new ContainerRequestParameter("key1", "value1", null);
        map.setParameters("key1", new RequestParameter[] {param1});
        assertTrue(map.getParts().isEmpty());

        Part part2 = Mockito.mock(Part.class);
        Mockito.doReturn("key1").when(part2).getName();
        final MultipartRequestParameter param2 = new MultipartRequestParameter(part2, null);
        map.setParameters("key1", new RequestParameter[] {param2});

        Collection<?> parts = map.getParts();
        assertEquals(1, parts.size());
        assertEquals("key1", ((Part) parts.iterator().next()).getName());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#getRequestParameterList()}.
     */
    @Test
    public void testGetRequestParameterList() {
        assertTrue(map.getRequestParameterList().isEmpty());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#put(java.lang.String, org.apache.sling.api.request.RequestParameter[])}.
     */
    @Test
    public void testPutStringRequestParameterArray() {
        final RequestParameter[] value = new RequestParameter[] {new ContainerRequestParameter("key1", "value1", null)};
        assertThrows(UnsupportedOperationException.class, () -> map.put("key1", value));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#putAll(java.util.Map)}.
     */
    @Test
    public void testPutAllMapOfQextendsStringQextendsRequestParameter() {
        Map<String, RequestParameter[]> map1 = Map.of();
        assertThrows(UnsupportedOperationException.class, () -> map.putAll(map1));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterMap#remove(java.lang.Object)}.
     */
    @Test
    public void testRemoveObject() {
        assertThrows(UnsupportedOperationException.class, () -> map.remove("key1"));
    }

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
        exception.expect(SlingParameterParseException.class);
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
        exception.expect(SlingParameterParseException.class);
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
