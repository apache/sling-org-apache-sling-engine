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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.engine.impl.SlingRequestProcessorImpl;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.imposters.ByteBuddyClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InitResourceTest {

    private Mockery context;
    private RequestData requestData;
    private HttpServletRequest req;
    private HttpServletResponse resp;
    private ResourceResolver resourceResolver;

    private final String requestURL;
    private final String pathInfo;
    private final String expectedResolvePath;
    private final String servletPath;
    private final String contextPath;

    @Parameters(name = "URL={0} path={1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {"http://localhost/one;v=1.1", "/one;v=1.1", "/one;v=1.1", "", ""},
            {"http://localhost/two;v=1.1", "/two", "/two;v=1.1", "", ""},
            {"http://localhost/three", "/three", "/three", "", ""},
            {"http://localhost/four%3Bv=1.1", "/four", "/four", "", ""},
            {"http://localhost/five%3Bv=1.1", "/five;v=1.1", "/five;v=1.1", "", ""},
            {"http://localhost/six;v=1.1", "/six;v=1.1", "/six;v=1.1", "", ""},
            {"http://localhost/seven", "/seven;v=1.1", "/seven;v=1.1", "", ""},
            {
                "http://localhost/context/path;v=1.1/more/foo;x=y/end",
                "/path/more/foo/end",
                "/path;v=1.1/more/foo;x=y/end",
                "/context",
                ""
            },
            {
                "http://localhost:4502/content;foo=bar/we-retail;bar=baz/us/en.html",
                "/content/we-retail/us/en.html",
                "/content;foo=bar/we-retail;bar=baz/us/en.html",
                "",
                ""
            }
        });
    }

    public InitResourceTest(
            String requestURL, String pathInfo, String expectedResolvePath, String contextPath, String servletPath) {
        this.requestURL = requestURL;
        this.pathInfo = pathInfo;
        this.expectedResolvePath = expectedResolvePath;
        this.contextPath = contextPath;
        this.servletPath = servletPath;
    }

    @Before
    public void setup() throws Exception {
        context = new Mockery() {
            {
                setImposteriser(ByteBuddyClassImposteriser.INSTANCE);
            }
        };

        req = context.mock(HttpServletRequest.class);
        resp = context.mock(HttpServletResponse.class);
        resourceResolver = context.mock(ResourceResolver.class);
        final SlingRequestProcessorImpl processor = context.mock(SlingRequestProcessorImpl.class);

        context.checking(new Expectations() {
            {
                allowing(req).getRequestURL();
                will(returnValue(new StringBuffer(requestURL)));

                allowing(req).getRequestURI();

                allowing(req).getPathInfo();
                will(returnValue(pathInfo));

                allowing(req).getContextPath();
                will(returnValue(contextPath));

                allowing(req).getServletPath();
                will(returnValue(servletPath));

                allowing(req).getMethod();
                will(returnValue("GET"));

                allowing(req).getAttribute(RequestData.REQUEST_RESOURCE_PATH_ATTR);
                will(returnValue(null));
                allowing(req)
                        .setAttribute(with(equal(RequestData.REQUEST_RESOURCE_PATH_ATTR)), with(any(Object.class)));

                allowing(req).getAttribute(RequestProgressTracker.class.getName());
                will(returnValue(null));

                // Verify that the ResourceResolver is called with the expected path
                allowing(resourceResolver)
                        .resolve(with(any(HttpServletRequest.class)), with(equal(expectedResolvePath)));

                allowing(processor).getMaxCallCounter();
                will(returnValue(2));
                allowing(processor).getAdditionalResponseHeaders();
                will(returnValue(Collections.emptyList()));
            }
        });

        requestData = new RequestData(processor, req, resp, false, false, true);
    }

    @Test
    public void resolverPathMatches() {
        requestData.initResource(resourceResolver);
    }
}
