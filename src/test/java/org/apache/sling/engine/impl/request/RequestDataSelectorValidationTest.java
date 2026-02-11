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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Selector-focused validation for RequestData.isValidRequest.
 * Kept separate from RequestDataTest, which covers path traversal patterns.
 */
public class RequestDataSelectorValidationTest {

    @Test
    public void testEmptySelectorInvalid() {
        assertValidRequest(false, "/content", "");
    }

    @Test
    public void testWhitespaceOnlySelectorInvalid() {
        assertValidRequest(false, "/content", "  \t\n");
    }

    @Test
    public void testValidSelectorsAccepted() {
        assertValidRequest(true, "/content", "print", "a4", "json");
    }

    @Test
    public void testMixedValidAndEmptySelectorInvalid() {
        assertValidRequest(false, "/content", "print", "", "json");
    }

    private static void assertValidRequest(boolean expected, String path, String... selectors) {
        boolean result = RequestData.isValidRequest(path, selectors);
        String message = String.format(
                "Expected %s for path=%s, selectors=%s", expected, path, java.util.Arrays.toString(selectors));
        if (expected) {
            assertTrue(message, result);
        } else {
            assertFalse(message, result);
        }
    }
}
