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

import java.io.IOException;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.auth.core.AuthenticationSupport;
import org.apache.sling.commons.mime.MimeTypeService;
import org.apache.sling.engine.impl.parameters.ParameterSupport;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.http.context.ServletContextHelper;
import org.osgi.service.http.whiteboard.annotations.RequireHttpWhiteboard;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>SlingHttpContext</code> implements the OSGi HttpContext used to
 * register the {@link SlingMainServlet} with the OSGi HttpService.
 */
@RequireHttpWhiteboard
@Component(service = ServletContextHelper.class)
@HttpWhiteboardContext(name = SlingMainServlet.SERVLET_CONTEXT_NAME, path = "/")
public class SlingHttpContext extends ServletContextHelper {

    /** Logger */
    private final Logger log = LoggerFactory.getLogger(SlingHttpContext.class);

    /**
     * Resolves MIME types
     *
     * @see #getMimeType(String)
     */
    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL)
    private volatile MimeTypeService mimeTypeService;

    /**
     * Handles security
     *
     * @see #handleSecurity(HttpServletRequest, HttpServletResponse)
     */
    private final AuthenticationSupport authenticationSupport;

    @Activate
    public SlingHttpContext(@Reference final AuthenticationSupport support) {
        this.authenticationSupport = support;
    }

    /**
     * Returns the MIME type as resolved by the <code>MimeTypeService</code> or
     * <code>null</code> if the service is not available.
     */
    @Override
    public String getMimeType(final String name) {
        MimeTypeService mtservice = mimeTypeService;
        if (mtservice != null) {
            return mtservice.getMimeType(name);
        }

        log.debug(
            "getMimeType: MimeTypeService not available, cannot resolve mime type for {}",
            name);
        return null;
    }

    /**
     * Always returns <code>null</code> because resources are all provided
     * through the {@link SlingMainServlet}.
     */
    @Override
    public URL getResource(final String name) {
        return null;
    }

    /**
     * Tries to authenticate the request using the
     * <code>SlingAuthenticator</code>.
     */
    @Override
    public boolean handleSecurity(HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        final RequestProgressTracker t = Builders.newRequestProgressTracker();
        t.log("Method={0}, PathInfo={1}", request.getMethod(), request.getPathInfo());
        request.setAttribute(RequestProgressTracker.class.getName(), t);
        final String timerName = "handleSecurity";
        t.startTimer(timerName);

        // SLING-559: ensure correct parameter handling according to
        // ParameterSupport
        request = ParameterSupport.getParameterSupportRequestWrapper(request);

        final boolean result = this.authenticationSupport.handleSecurity(request, response);
        t.logTimer(timerName, "authenticator {0} returns {1}", this.authenticationSupport, result);
        return result;
    }

    @Override
    public void finishSecurity(HttpServletRequest request, HttpServletResponse response) {
        super.finishSecurity(request, response);
        // get ResourceResolver (set by AuthenticationSupport)
        final Object resolverObject = request.getAttribute(AuthenticationSupport.REQUEST_ATTRIBUTE_RESOLVER);
        final ResourceResolver resolver = (resolverObject instanceof ResourceResolver)
                ? (ResourceResolver) resolverObject
                : null;
        if (resolver != null) {
            // it's safe to call close() several times - checking isLive() can be expensive
            resolver.close();
        }
    }
}
