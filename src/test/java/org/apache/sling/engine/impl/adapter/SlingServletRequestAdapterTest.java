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
package org.apache.sling.engine.impl.adapter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class SlingServletRequestAdapterTest {

    @Test
    public void testGetRequestedSessionIdReturnsClientPresentedId() {
        final SlingJakartaHttpServletRequest slingRequest = Mockito.mock(SlingJakartaHttpServletRequest.class);
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        final HttpSession session = Mockito.mock(HttpSession.class);
        Mockito.when(session.getId()).thenReturn("live-server-session-id");
        Mockito.when(request.getSession(false)).thenReturn(session);
        Mockito.when(request.getRequestedSessionId()).thenReturn("client-presented-id");

        final SlingServletRequestAdapter adapter = new SlingServletRequestAdapter(slingRequest, request);

        // the servlet spec defines getRequestedSessionId() as the id sent by
        // the client - it must never be answered with the live session id
        assertEquals("client-presented-id", adapter.getRequestedSessionId());
        verify(request, times(1)).getRequestedSessionId();
        // regression guard: the live session must never be consulted for this
        // call, otherwise the fix could silently regress to leaking the live
        // session id again
        verify(request, never()).getSession(anyBoolean());
        verify(session, never()).getId();
    }

    @Test
    public void testGetRequestedSessionIdNullWhenClientSentNone() {
        final SlingJakartaHttpServletRequest slingRequest = Mockito.mock(SlingJakartaHttpServletRequest.class);
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        final HttpSession session = Mockito.mock(HttpSession.class);
        Mockito.when(session.getId()).thenReturn("live-server-session-id");
        Mockito.when(request.getSession(false)).thenReturn(session);
        Mockito.when(request.getRequestedSessionId()).thenReturn(null);

        final SlingServletRequestAdapter adapter = new SlingServletRequestAdapter(slingRequest, request);

        // even with a live session, a request without a client session id
        // must report null (e.g. for session fixation detection)
        assertNull(adapter.getRequestedSessionId());
        verify(request, times(1)).getRequestedSessionId();
        // regression guard: must not fall back to the live session id when
        // the client did not present one
        verify(request, never()).getSession(anyBoolean());
        verify(session, never()).getId();
    }

    @Test
    public void testGetRequestedSessionIdReturnsClientPresentedIdWithoutLiveSession() {
        final SlingJakartaHttpServletRequest slingRequest = Mockito.mock(SlingJakartaHttpServletRequest.class);
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        // no live session exists (e.g. it expired or was never created),
        // yet the client still presented a (now stale/invalid) session id
        Mockito.when(request.getSession(false)).thenReturn(null);
        Mockito.when(request.getRequestedSessionId()).thenReturn("stale-client-id");

        final SlingServletRequestAdapter adapter = new SlingServletRequestAdapter(slingRequest, request);

        assertEquals("stale-client-id", adapter.getRequestedSessionId());
        verify(request, times(1)).getRequestedSessionId();
        verify(request, never()).getSession(anyBoolean());
    }
}
