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
package org.apache.sling.engine.impl.parameters;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.settings.SlingSettingsService;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

/**
 *
 */
public class RequestParameterSupportConfigurerTest {

    @Rule
    public final OsgiContext osgiContext = new OsgiContext();

    private RequestParameterSupportConfigurer filter;

    @Before
    public void before() {
        // Satisfy mandatory reference to SlingSettingsService
        osgiContext.registerService(SlingSettingsService.class, Mockito.mock(SlingSettingsService.class));

        // Satisfy mandatory reference to RequestParameterConfig
        osgiContext.registerInjectActivateService(RequestParameterConfig.class);

        filter = osgiContext.registerInjectActivateService(RequestParameterSupportConfigurer.class);
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.RequestParameterSupportConfigurer#doFilter(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse, jakarta.servlet.FilterChain)}.
     */
    @Test
    public void testDoFilterForServletRequest() throws IOException, ServletException {
        ServletRequest mockRequest1 = Mockito.mock(ServletRequest.class);
        doFilterForServletRequest(mockRequest1);
    }

    @Test
    public void testDoFilterForParameterSupportHttpServletRequestWrapper() throws IOException, ServletException {
        ParameterSupportHttpServletRequestWrapper mockRequest1 =
                Mockito.mock(ParameterSupportHttpServletRequestWrapper.class);
        doFilterForServletRequest(mockRequest1);
    }

    @Test
    public void testDoFilterForSlingJakartaHttpServletRequest() throws IOException, ServletException {
        SlingJakartaHttpServletRequest mockRequest1 = Mockito.mock(SlingJakartaHttpServletRequest.class);
        doFilterForServletRequest(mockRequest1);
    }

    private void doFilterForServletRequest(ServletRequest mockRequest1) throws IOException, ServletException {
        ServletResponse mockResponse1 = Mockito.mock(ServletResponse.class);
        FilterChain chain1 = Mockito.mock(FilterChain.class);
        filter.doFilter(mockRequest1, mockResponse1, chain1);
        Mockito.verify(chain1, times(1)).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    public void testDoFilterForHttpServletRequest() throws IOException, ServletException {
        HttpServletRequest mockRequest1 = Mockito.mock(HttpServletRequest.class);
        ServletResponse mockResponse1 = Mockito.mock(ServletResponse.class);
        FilterChain chain1 = Mockito.mock(FilterChain.class);
        filter.doFilter(mockRequest1, mockResponse1, chain1);
        Mockito.verify(chain1, times(1)).doFilter(any(HttpServletRequestWrapper.class), any(ServletResponse.class));
    }
}
