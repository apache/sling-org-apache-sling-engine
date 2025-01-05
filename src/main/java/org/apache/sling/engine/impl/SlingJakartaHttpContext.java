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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

import org.apache.sling.api.wrappers.JavaxToJakartaRequestWrapper;
import org.apache.sling.api.wrappers.JavaxToJakartaResponseWrapper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceRanking;
import org.osgi.service.http.context.ServletContextHelper;
import org.osgi.service.http.whiteboard.annotations.RequireHttpWhiteboard;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardContext;

/**
 * The <code>SlingJakartaHttpContext</code> registers a servlet context helper
 * for compatibility with the Javax Servlet API
 * @deprecated Use {@link SlingHttpContext} instead.
 */
@RequireHttpWhiteboard
@Component(service = ServletContextHelper.class)
@HttpWhiteboardContext(name = SlingHttpContext.SERVLET_CONTEXT_NAME, path = "/")
@ServiceRanking(-1)
@Deprecated
public class SlingJakartaHttpContext extends ServletContextHelper {

    private final org.osgi.service.servlet.context.ServletContextHelper delegate;

    @Activate
    public SlingJakartaHttpContext(
            @Reference(target = "(name=" + SlingHttpContext.SERVLET_CONTEXT_NAME + ")")
                    org.osgi.service.servlet.context.ServletContextHelper delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean handleSecurity(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException {
        return this.delegate.handleSecurity(
                JavaxToJakartaRequestWrapper.toJakartaRequest(request),
                JavaxToJakartaResponseWrapper.toJakartaResponse(response));
    }

    @Override
    public void finishSecurity(final HttpServletRequest request, final HttpServletResponse response) {
        this.delegate.finishSecurity(
                JavaxToJakartaRequestWrapper.toJakartaRequest(request),
                JavaxToJakartaResponseWrapper.toJakartaResponse(response));
    }

    @Override
    public String getMimeType(final String name) {
        return this.delegate.getMimeType(name);
    }

    @Override
    public String getRealPath(final String path) {
        return this.delegate.getRealPath(path);
    }

    @Override
    public URL getResource(final String name) {
        return this.delegate.getResource(name);
    }

    @Override
    public Set<String> getResourcePaths(final String path) {
        return this.delegate.getResourcePaths(path);
    }
}
