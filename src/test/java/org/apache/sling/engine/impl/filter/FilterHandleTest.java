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
package org.apache.sling.engine.impl.filter;

import org.junit.Test;

import static org.apache.sling.engine.EngineConstants.SLING_FILTER_PATTERN;
import static org.junit.Assert.*;

public class FilterHandleTest extends AbstractFilterTest {

    /**
     * a filter with no predicate should be selected always, a filter with select only if predicate is good
     */
    @Test
    public void testSelect(){
        FilterHandle handle = new FilterHandle(null, predicate(), 0L, 0, "", null);
        assertTrue("filter should be selected when no predicate", handle.select(mockRequest("/content/test/no/predicate", null, null, null, null)));
        handle = new FilterHandle(null, predicate(SLING_FILTER_PATTERN,"/content/test/.*"), 0L, 0, "", null);
        assertTrue("filter should be selected when matching predicate", handle.select(mockRequest("/content/test/matching/predicate", null, null, null, null)));
        handle = new FilterHandle(null, predicate(SLING_FILTER_PATTERN,"/content/foo/.*"), 0L, 0, "", null);
        assertFalse("filter should not be selected when no matching predicate", handle.select(mockRequest("/content/test/no/matching/predicate", null, null, null, null)));
    }
}