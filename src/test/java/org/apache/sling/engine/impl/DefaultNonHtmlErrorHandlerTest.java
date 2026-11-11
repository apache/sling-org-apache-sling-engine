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
package org.apache.sling.engine.impl;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class DefaultNonHtmlErrorHandlerTest {

    private DefaultNonHtmlErrorHandler handler;
    private SlingJakartaHttpServletRequest request;
    private SlingJakartaHttpServletResponse response;

    @Before
    public void setup() {
        handler = new DefaultNonHtmlErrorHandler();
        request = mock(SlingJakartaHttpServletRequest.class);
        response = mock(SlingJakartaHttpServletResponse.class);
    }

    @Test
    public void testStatusIsSentWithEmptyBody() throws Exception {
        handler.handleError(404, "secret detail", request, response);

        final InOrder order = inOrder(response);
        order.verify(response).setStatus(404);
        order.verify(response).setContentLength(0);
        order.verify(response).flushBuffer();
        verify(response, never()).getWriter();
        verify(response, never()).getOutputStream();
        verify(response, never()).setContentType(anyString());
    }

    @Test
    public void testThrowableIsSentAsInternalServerErrorWithEmptyBody() throws Exception {
        handler.handleError(new IllegalStateException("secret detail"), request, response);

        final InOrder order = inOrder(response);
        order.verify(response).setStatus(500);
        order.verify(response).setContentLength(0);
        order.verify(response).flushBuffer();
        verify(response, never()).getWriter();
        verify(response, never()).getOutputStream();
    }
}
