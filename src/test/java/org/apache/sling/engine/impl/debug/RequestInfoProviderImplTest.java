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
package org.apache.sling.engine.impl.debug;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.engine.RequestInfo;
import org.apache.sling.engine.impl.Config;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class RequestInfoProviderImplTest {

    @Test
    public void testDisabledProvider() {
        final Config config = Mockito.mock(Config.class);
        Mockito.when(config.sling_max_record_requests()).thenReturn(0);

        final SlingJakartaHttpServletRequest request = Mockito.mock(SlingJakartaHttpServletRequest.class);
        Mockito.when(request.getPathInfo()).thenReturn("/content");
        Mockito.when(request.getRemoteUser()).thenReturn("admin");
        Mockito.when(request.getMethod()).thenReturn("GET");
        Mockito.when(request.getRequestProgressTracker()).thenReturn(Builders.newRequestProgressTracker());

        final RequestInfoProviderImpl provider = new RequestInfoProviderImpl(config);
        assertEquals(0, provider.getMayNumberOfInfos());
        RequestInfoProviderImpl.recordRequest(request);

        assertFalse(provider.getRequestInfos().iterator().hasNext());
    }

    @Test
    public void testEnabledProvider() {
        final Config config = Mockito.mock(Config.class);
        Mockito.when(config.sling_max_record_requests()).thenReturn(5);

        final SlingJakartaHttpServletRequest request = Mockito.mock(SlingJakartaHttpServletRequest.class);
        Mockito.when(request.getPathInfo()).thenReturn("/content");
        Mockito.when(request.getRemoteUser()).thenReturn("admin");
        Mockito.when(request.getMethod()).thenReturn("GET");
        Mockito.when(request.getRequestProgressTracker()).thenReturn(Builders.newRequestProgressTracker());

        final RequestInfoProviderImpl provider = new RequestInfoProviderImpl(config);
        assertEquals(5, provider.getMayNumberOfInfos());
        RequestInfoProviderImpl.recordRequest(request);

        String id = null;
        for (final RequestInfo info : provider.getRequestInfos()) {
            if (id != null) {
                fail("More than one request info");
            }
            id = info.getId();
        }
        final RequestInfo info = provider.getRequestInfo(id);
        assertNotNull(info);

        assertEquals("/content", info.getPath());
        assertEquals("admin", info.getUserId());
        assertEquals(id, info.getId());
        assertEquals("GET", info.getMethod());
        assertFalse(info.getLog().isEmpty());
    }
}
