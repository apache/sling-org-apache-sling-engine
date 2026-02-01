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

import java.lang.reflect.Field;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SlingMainServletTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private SlingMainServlet servlet;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        servlet = new SlingMainServlet();
        setAllowTrace(false);
        setupCommonMocks();
    }

    private void setupCommonMocks() {
        when(request.getMethod()).thenReturn("TRACE");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getRequestURI()).thenReturn("/test");
        when(request.getProtocol()).thenReturn("HTTP/1.1");
    }

    @Test
    public void testTraceDisabledReturns405AndAllowHeader() throws ServletException {
        // Act
        servlet.service(request, response);

        // Assert
        verify(response, times(1)).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(response, times(1)).setHeader("Allow", "GET, HEAD, POST, PUT, DELETE, OPTIONS");
    }

    private void setAllowTrace(boolean value) {
        try {
            Field field = SlingMainServlet.class.getDeclaredField("allowTrace");
            field.setAccessible(true);
            field.set(servlet, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set allowTrace via reflection", e);
        }
    }
}
