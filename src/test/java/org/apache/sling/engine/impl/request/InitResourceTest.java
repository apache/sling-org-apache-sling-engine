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

/**
 * Verifies {@link RequestData#initResource(ResourceResolver)}, in particular the fallback
 * that re-derives the resolution path from the raw request URL when the container strips
 * URL path parameters ({@code ;name=value}) from {@link HttpServletRequest#getPathInfo()}.
 *
 * <p>Each row below documents, in this order:
 * <ul>
 *   <li><b>rawRequestURL</b> - the untouched URL as it appears on the wire (what
 *       {@code getRequestURL()} returns) - i.e. the attacker/client-controlled input;</li>
 *   <li><b>containerPathInfo</b> - what the servlet container hands back from
 *       {@code getPathInfo()} after its own decoding/normalization (path parameters already
 *       stripped);</li>
 *   <li><b>expectedResolvedPath</b> - the path {@code initResource()} must hand to the
 *       {@code ResourceResolver}, i.e. what the raw URL is effectively "decoded to" once path
 *       parameters are safely re-attached (or not, if re-attaching them would be unsafe).</li>
 * </ul>
 *
 * <p>Note: {@code servletPath} is the empty string in every row below except the
 * null-path-info row - not because it was not worth testing, but because
 * {@code SlingJakartaHttpServletRequestImpl.getServletPath()} always returns {@code ""} (Sling
 * registers with the HTTP Whiteboard at servlet path {@code "/*"}), so the container's servlet
 * path is unreachable at this call site regardless of what the underlying
 * {@code HttpServletRequest} mock reports; only {@code contextPath} contributes to the prefix
 * that gets stripped in practice.
 */
@RunWith(Parameterized.class)
public class InitResourceTest {

    private Mockery context;
    private RequestData requestData;
    private HttpServletRequest req;
    private HttpServletResponse resp;
    private ResourceResolver resourceResolver;

    private final String description;
    private final String rawRequestURL;
    private final String containerPathInfo;
    private final String expectedResolvedPath;
    private final String contextPath;
    private final String servletPath;

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "container preserves a single path parameter as-is: no re-derivation needed",
                "http://localhost/one;v=1.1",
                "/one;v=1.1",
                "/one;v=1.1",
                "",
                ""
            },
            {
                "container strips a single path parameter: re-derived and re-attached",
                "http://localhost/two;v=1.1",
                "/two",
                "/two;v=1.1",
                "",
                ""
            },
            {
                "no path parameter present at all: path passed through unchanged",
                "http://localhost/three",
                "/three",
                "/three",
                "",
                ""
            },
            {
                "raw URL has a percent-encoded semicolon (not a real path parameter): container "
                        + "already decoded and dropped it, no re-derivation is attempted",
                "http://localhost/four%3Bv=1.1",
                "/four",
                "/four",
                "",
                ""
            },
            {
                "raw URL has a percent-encoded semicolon that the container decoded and kept: "
                        + "path passed through unchanged (already contains ';')",
                "http://localhost/five%3Bv=1.1",
                "/five;v=1.1",
                "/five;v=1.1",
                "",
                ""
            },
            {
                "container already exposes the literal ';' in path info: path passed through unchanged",
                "http://localhost/six;v=1.1",
                "/six;v=1.1",
                "/six;v=1.1",
                "",
                ""
            },
            {
                "path parameter present only in container-provided path info (no ';' in raw URL): "
                        + "path passed through unchanged",
                "http://localhost/seven",
                "/seven;v=1.1",
                "/seven;v=1.1",
                "",
                ""
            },
            {
                "multiple path parameters across multiple segments, behind a context path: all "
                        + "re-attached at their original segment",
                "http://localhost/context/path;v=1.1/more/foo;x=y/end",
                "/path/more/foo/end",
                "/path;v=1.1/more/foo;x=y/end",
                "/context",
                ""
            },
            {
                "multiple path parameters across multiple segments, no context path: all "
                        + "re-attached at their original segment",
                "http://localhost:4502/content;foo=bar/we-retail;bar=baz/us/en.html",
                "/content/we-retail/us/en.html",
                "/content;foo=bar/we-retail;bar=baz/us/en.html",
                "",
                ""
            },
            {
                "raw URL has a percent-encoded space: URI decoding brings the re-derived path "
                        + "back to the same canonical (decoded) form as the container path info, "
                        + "'%20' -> ' ', so it can be safely compared and re-attached",
                "http://localhost/a%20b;v=1.1",
                "/a b",
                "/a b;v=1.1",
                "",
                ""
            },
            {
                "re-derived path (params stripped) does not match container path info at all: "
                        + "the raw URL is rejected outright, container path info wins",
                "http://localhost/other;x=1",
                "/two",
                "/two",
                "",
                ""
            },
            {
                "raw URL contains a literal '..;x' traversal segment the container normalized "
                        + "away: rejected because it is not equivalent to the container path info "
                        + "modulo path parameters, so it never resurfaces as a literal segment",
                "http://localhost/private/..;x/secret",
                "/secret",
                "/secret",
                "",
                ""
            },
            {
                "raw URL contains a percent-encoded '..;x' traversal segment ('%2e%2e' -> '..'): "
                        + "same rejection as the literal '..;x' case above, decoding does not "
                        + "change the outcome",
                "http://localhost/private/%2e%2e;x/secret",
                "/secret",
                "/secret",
                "",
                ""
            },
            {
                "raw URL path does not even start with the context path prefix: fall back to "
                        + "container path info instead of an out-of-bounds substring (previously a 500)",
                "http://localhost/ctx;x/one",
                "/one",
                "/one",
                "/context",
                ""
            },
            {
                "raw URL shorter than the context+servlet path prefix: fall back to container "
                        + "path info instead of an out-of-bounds substring (previously a 500)",
                "http://localhost/a;b",
                "/x",
                "/x",
                "/context",
                ""
            },
            {
                "raw URL is not a syntactically valid URI (unencoded space): URISyntaxException "
                        + "is swallowed, falls back to container path info",
                "http://localhost/a b;v=1.1",
                "/a b",
                "/a b",
                "",
                ""
            },
            {
                "container's own getServletPath()/getPathInfo() both return null (buggy/unusual "
                        + "container): the Sling request wrapper then also exposes pathInfo=null; "
                        + "must not NPE, path stays null (this is the only row using "
                        + "servletPath=null instead of \"\")",
                "http://localhost/context;x=1",
                null,
                null,
                "/context",
                null
            },
            {
                "path parameter sits immediately after the context+servlet prefix, leaving an "
                        + "empty base path once the prefix is stripped: still re-attached correctly",
                "http://localhost/context;x=1",
                "",
                ";x=1",
                "/context",
                ""
            },
            {
                "raw URL differs from container path info only in case ('/One' vs '/one'), not "
                        + "just by path parameters: rejected as a near-miss, container path info wins",
                "http://localhost/one;x=1",
                "/One",
                "/One",
                "",
                ""
            },
        });
    }

    public InitResourceTest(
            String description,
            String rawRequestURL,
            String containerPathInfo,
            String expectedResolvedPath,
            String contextPath,
            String servletPath) {
        this.description = description;
        this.rawRequestURL = rawRequestURL;
        this.containerPathInfo = containerPathInfo;
        this.expectedResolvedPath = expectedResolvedPath;
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
                will(returnValue(new StringBuffer(rawRequestURL)));

                allowing(req).getRequestURI();

                allowing(req).getPathInfo();
                will(returnValue(containerPathInfo));

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

                // Verify that the ResourceResolver is called with the expected (re-derived or
                // passed-through) path
                if (expectedResolvedPath == null) {
                    allowing(resourceResolver).resolve(with(any(HttpServletRequest.class)), with(aNull(String.class)));
                } else {
                    allowing(resourceResolver)
                            .resolve(with(any(HttpServletRequest.class)), with(equal(expectedResolvedPath)));
                }

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
