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
package org.apache.sling.engine.impl.request;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Direct unit tests for {@link RequestData#stripPathParameters(String)}, the helper used by
 * {@link RequestData#initResource} to check whether a path re-derived from the raw request URL
 * is equivalent - modulo URL path parameters ({@code ;name=value}) - to the container-provided
 * path info.
 */
public class StripPathParametersTest {

    @Test
    public void noPathParameterIsUnchanged() {
        assertEquals("/one/two", RequestData.stripPathParameters("/one/two"));
    }

    @Test
    public void singlePathParameterIsRemoved() {
        assertEquals("/one", RequestData.stripPathParameters("/one;v=1.1"));
    }

    @Test
    public void pathParameterInMiddleSegmentIsRemoved() {
        assertEquals("/one/two", RequestData.stripPathParameters("/one;v=1.1/two"));
    }

    @Test
    public void multiplePathParametersInSameSegmentAreAllRemoved() {
        assertEquals("/one/two", RequestData.stripPathParameters("/one;a=1;b=2/two"));
    }

    @Test
    public void trailingPathParameterWithNoFollowingSegmentIsRemoved() {
        assertEquals("/one", RequestData.stripPathParameters("/one;v=1.1"));
        // no characters at all after the trailing ';'
        assertEquals("/one", RequestData.stripPathParameters("/one;"));
    }

    @Test
    public void emptyPathParameterValueIsRemoved() {
        assertEquals("/one/two", RequestData.stripPathParameters("/one;=/two"));
    }

    @Test
    public void leadingPathParameterOnRootIsRemoved() {
        assertEquals("/one", RequestData.stripPathParameters(";x=1/one"));
    }

    @Test
    public void pathParameterConsumingWholeInputYieldsEmptyString() {
        assertEquals("", RequestData.stripPathParameters(";x=1"));
    }

    @Test
    public void emptyStringIsUnchanged() {
        assertEquals("", RequestData.stripPathParameters(""));
    }
}
