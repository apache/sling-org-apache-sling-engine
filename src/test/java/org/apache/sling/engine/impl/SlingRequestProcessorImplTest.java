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

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.engine.impl.filter.ServletFilterManager;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.GenericServlet;
import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SlingRequestProcessorImplTest {

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private ServletResolver servletResolver;

    @Mock
    private SlingHttpServletRequest httpServletRequest;

    private SlingRequestProcessor slingRequestProcessor;

    @Before
    public void setUp() {
        ServletFilterManager filterManager = mock(ServletFilterManager.class);

        // Set up the underlying testee
        slingRequestProcessor = new SlingRequestProcessorImpl();
        ((SlingRequestProcessorImpl) slingRequestProcessor).setFilterManager(filterManager);
        ((SlingRequestProcessorImpl) slingRequestProcessor).setServletResolver(servletResolver);

        // Mock necessary method calls of mocked request
        when(httpServletRequest.getPathInfo()).thenReturn("");
        when(httpServletRequest.getServletPath()).thenReturn("");
        when(httpServletRequest.getRequestURL()).thenReturn(new StringBuffer());

        // Mock resource, its metadata and the resource resolver
        Resource resource = mock(Resource.class);
        ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
        when(resource.getResourceMetadata()).thenReturn(resourceMetadata);
        when(resourceResolver.resolve(any(HttpServletRequest.class), anyString())).thenReturn(resource);
    }

    /**
     * Previously {@link SlingRequestProcessorImpl} would not flush the response's buffer resulting in empty strings.
     *
     * @see <a href="https://issues.apache.org/jira/browse/SLING-8047">SLING-8047</a>
     */
    @Test
    public void verifyPrintWritersBufferIsFlushed() throws Exception {
        // GIVEN
        // Setup test servlet
        Servlet testServlet = new TestServlet("foobar");
        when(servletResolver.resolveServlet(any(SlingHttpServletRequest.class))).thenReturn(testServlet);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        HttpServletResponse httpServletResponse = new CustomMockSlingHttpServletResponse(byteArrayOutputStream);

        // WHEN
        slingRequestProcessor.processRequest(httpServletRequest, httpServletResponse, resourceResolver);

        // THEN
        assertThat(byteArrayOutputStream.toString(), is("foobar"));
    }

    /**
     * Custom mock {@link HttpServletResponse} that allows us to read what has been written to {@link PrintWriter}.
     */
    private class CustomMockSlingHttpServletResponse extends MockSlingHttpServletResponse {

        private final PrintWriter printWriter;

        private CustomMockSlingHttpServletResponse(OutputStream outputStream) {
            this.printWriter = new PrintWriter(
                    new BufferedOutputStream(outputStream, /* buffer-size */ 8192), /* auto-flush */ false);
        }

        @Override
        public PrintWriter getWriter() {
            return printWriter;
        }

        @Override
        public void flushBuffer() {
            printWriter.flush();
        }
    }

    /**
     * Simple test filter that will write a string to response's {@link PrintWriter print writer}.
     */
    private class TestServlet extends GenericServlet {

        private final String value;

        private TestServlet(String value) {
            this.value = value;
        }

        @Override
        public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws IOException {
            servletResponse.getWriter().write(value);
        }
    }
}
