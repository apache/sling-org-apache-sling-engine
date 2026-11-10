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

import java.io.IOException;
import java.util.Collections;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.request.TooManyCallsException;
import org.apache.sling.engine.impl.SlingJakartaHttpServletRequestImpl;
import org.apache.sling.engine.impl.SlingJakartaHttpServletResponseImpl;
import org.apache.sling.engine.impl.SlingRequestProcessorImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class RequestDataTest {

    private RequestData requestData;
    private HttpServletRequest req;
    private HttpServletResponse resp;
    private SlingJakartaHttpServletRequest slingRequest;
    private SlingJakartaHttpServletResponse slingResponse;

    @Before
    public void setup() throws ServletException, IOException {
        req = Mockito.mock(HttpServletRequest.class);
        resp = Mockito.mock(HttpServletResponse.class);

        final ContentData contentData = Mockito.mock(ContentData.class);
        final RequestPathInfo requestPathInfo = Mockito.mock(RequestPathInfo.class);
        final Servlet servlet = Mockito.mock(Servlet.class);
        final ServletConfig servletConfig = Mockito.mock(ServletConfig.class);
        final SlingRequestProcessorImpl processor = Mockito.mock(SlingRequestProcessorImpl.class);
        Mockito.when(req.getServletPath()).thenReturn("/");
        Mockito.when(req.getPathInfo()).thenReturn("");
        Mockito.when(req.getMethod()).thenReturn("GET");
        Mockito.when(contentData.getRequestPathInfo()).thenReturn(requestPathInfo);
        Mockito.when(requestPathInfo.getResourcePath()).thenReturn("/");
        Mockito.when(requestPathInfo.getSelectors()).thenReturn(new String[0]);
        Mockito.when(contentData.getServlet()).thenReturn(servlet);
        Mockito.when(servlet.getServletConfig()).thenReturn(servletConfig);
        Mockito.when(servletConfig.getServletName()).thenReturn("SERVLET_NAME");
        Mockito.when(processor.getMaxCallCounter()).thenReturn(2);
        Mockito.when(processor.getAdditionalResponseHeaders()).thenReturn(Collections.emptyList());

        requestData = new RequestData(processor, req, resp, false, false, true) {
            @Override
            public ContentData getContentData() {
                return contentData;
            }
        };

        slingRequest = new SlingJakartaHttpServletRequestImpl(requestData, req);
        slingResponse = new SlingJakartaHttpServletResponseImpl(requestData, resp);
    }

    private void assertTooManyCallsException(int failAtCall) throws Exception {
        for (int i = 0; i < failAtCall - 1; i++) {
            RequestData.service(slingRequest, slingResponse);
        }
        try {
            RequestData.service(slingRequest, slingResponse);
            fail("Expected RequestData.service to fail when called " + failAtCall + " times");
        } catch (TooManyCallsException tme) {
            // as expected
        }
    }

    @Test
    public void testTooManyCallsDefault() throws Exception {
        assertTooManyCallsException(3);
    }

    @Test
    public void testTooManyCallsOverride() throws Exception {
        Mockito.when(req.getAttribute(Mockito.anyString())).thenReturn(1);
        assertTooManyCallsException(2);
    }

    @Test
    public void testConsecutiveDots() {
        assertValidRequest(true, "/path/content../test");
        assertValidRequest(false, "/../path/content../test");
        assertValidRequest(false, "/../path/content/.../test");
        assertValidRequest(false, "../path/content/.../test");
        assertValidRequest(false, "/content/.../test");
    }

    @Test
    public void testConsecutiveDotsAfterPathSeparator() {
        assertValidRequest(false, "/path/....");
        assertValidRequest(false, "/path/..");
        assertValidRequest(true, "/path/foo..");
        assertValidRequest(true, "/path/..foo..");
    }

    @Test
    public void testDots() {
        assertValidRequest(false, "/a/.../b");
        assertValidRequest(false, "/a/............../b");
        assertValidRequest(true, "/a/..........helloo......./b");
        assertValidRequest(true, "/path/content./test");
        assertValidRequest(true, "/./path/content./test");
        assertValidRequest(true, "/./path/content/./test");
        assertValidRequest(true, "./path/content/./test");
        assertValidRequest(true, "/content/./test");
    }

    @Test
    public void testNullResolutionPath() {
        assertValidRequest(true, null);
    }

    @Test
    public void testDotsAnd5B() {
        assertValidRequest(false, "/a/..[[./b");
        assertValidRequest(false, "/a/[............../b");
        assertValidRequest(true, "/a/..........helloo......./b");
        assertValidRequest(false, "/a/[..");
    }

    @Test
    public void testDotsAnd7D() {
        assertValidRequest(false, "/a/..}}./b");
        assertValidRequest(false, "/a/}............../b");
        assertValidRequest(false, "/a/..}..}./b");
        assertValidRequest(false, "/a/}..");
        assertValidRequest(false, "/a/..}");
        assertValidRequest(true, "/a/}./b");
        assertValidRequest(true, "/a//b/c");
    }

    @Test
    public void testDotsAnd7D5B() {
        assertValidRequest(false, "/a/..}[./b");
        assertValidRequest(false, "/a/}....[........../b");
        assertValidRequest(true, "/a/..........[helloo}......./b");
        assertValidRequest(false, "/a/[}....");
        assertValidRequest(false, "/a/....}[");
        assertValidRequest(true, "/a/.}[");
    }

    @Test
    public void testValidRequest() {
        // HttpRequest with valid path
        assertValidRequest(true, "/path");
    }

    private static void assertValidRequest(boolean expected, String path) {
        assertEquals("Expected " + expected + " for " + path, expected, RequestData.isValidRequest(path));
    }
}
