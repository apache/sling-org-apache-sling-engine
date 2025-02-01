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

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlingRequestDispatcher implements RequestDispatcher {

    /** default log */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Resource resource;

    private final RequestDispatcherOptions options;

    private final String path;

    private final boolean protectHeadersOnInclude;
    private final boolean checkContentTypeOnInclude;

    public SlingRequestDispatcher(
            String path,
            RequestDispatcherOptions options,
            boolean protectHeadersOnInclude,
            boolean checkContentTypeOnInclude) {
        this.path = path;
        this.options = options;
        this.resource = null;
        this.protectHeadersOnInclude = protectHeadersOnInclude;
        this.checkContentTypeOnInclude = checkContentTypeOnInclude;
    }

    public SlingRequestDispatcher(
            Resource resource,
            RequestDispatcherOptions options,
            boolean protectHeadersOnInclude,
            boolean checkContentTypeOnInclude) {
        this.resource = resource;
        this.options = options;
        this.path = resource.getPath();
        this.protectHeadersOnInclude = protectHeadersOnInclude;
        this.checkContentTypeOnInclude = checkContentTypeOnInclude;
    }

    @Override
    public void include(ServletRequest request, ServletResponse sResponse) throws ServletException, IOException {

        // guard access to the request and content data: If the request is
        // not (wrapping) a SlingHttpServletRequest, accessing the request Data
        // throws an IllegalArgumentException and we cannot continue
        final ContentData cd;
        try {
            cd = RequestData.getRequestData(request).getContentData();
        } catch (IllegalArgumentException iae) {
            throw new ServletException(iae.getMessage());
        }

        // ClassCastException is not expected here because we operate in
        // HTTP requests only (it cannot be excluded though if some client
        // code uses a ServletRequestWrapper rather than an
        // HttpServletRequestWrapper ...)
        final HttpServletRequest hRequest = (HttpServletRequest) request;
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        info.setRequestContent(cd.getResource());
        info.setRequestServlet(cd.getServlet());
        info.setRequestPathInfo(cd.getRequestPathInfo());
        info.setContextPath(hRequest.getContextPath());
        info.setPathInfo(hRequest.getPathInfo());
        info.setQueryString(hRequest.getQueryString());
        info.setRequestUri(hRequest.getRequestURI());
        info.setServletPath(hRequest.getServletPath());

        dispatch(request, sResponse, info);
    }

    @Override
    public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException {

        // fail forwarding if the response has already been committed
        if (response.isCommitted()) {
            throw new IllegalStateException("Response already committed");
        }

        // reset the response, will throw an IllegalStateException
        // if already committed, which will not be the case because
        // we already tested for this condition
        response.resetBuffer();

        // ensure inclusion information attributes are not set
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.FORWARD);

        // now just include as normal
        dispatch(request, response, info);

        // finally, we would have to ensure the response is committed
        // and closed
        if (!response.isCommitted()) {
            response.flushBuffer();
        }
    }

    private String getAbsolutePath(SlingJakartaHttpServletRequest request, String path) {
        // path is already absolute
        if (path.startsWith("/")) {
            return path;
        }

        // get parent of current request
        String uri = request.getResource().getPath();
        int lastSlash = uri.lastIndexOf('/');
        if (lastSlash >= 0) {
            uri = uri.substring(0, lastSlash);
        }

        // append relative path to parent
        return uri + '/' + path;
    }

    /**
     * Dispatch the request
     * @param request The request
     * @param response The response
     * @param dispatchingInfo Is this an include (or forward)
     */
    private void dispatch(
            final ServletRequest request, final ServletResponse response, final DispatchingInfo dispatchingInfo)
            throws ServletException, IOException {
        SlingJakartaHttpServletRequest cRequest = RequestData.unwrap(request);
        RequestData rd = RequestData.getRequestData(cRequest);
        String absPath = getAbsolutePath(cRequest, path);
        RequestProgressTracker requestProgressTracker = cRequest.getRequestProgressTracker();

        // if the response is not an HttpServletResponse, fail gracefully not
        // doing anything
        if (!(response instanceof HttpServletResponse)) {
            log.error("include: Failed to include {}, response has wrong type", absPath);
            return;
        }

        if (resource == null) {
            String timerName = "resolveIncludedResource(" + absPath + ")";
            requestProgressTracker.startTimer(timerName);

            // resolve the absolute path in the resource resolver, using
            // only those parts of the path as if it would be request path
            resource = cRequest.getResourceResolver().resolve(absPath);

            // if the resource could not be resolved, fail gracefully
            if (resource == null) {
                log.error("include: Could not resolve {} to a resource, not including", absPath);
                return;
            }

            requestProgressTracker.logTimer(timerName, "path={0} resolves to Resource={1}", absPath, resource);
        }

        // ensure request path info and optional merges
        SlingRequestPathInfo info = getMergedRequestPathInfo(cRequest);
        requestProgressTracker.log("Including resource {0} ({1})", resource, info);
        if (dispatchingInfo.getType() == DispatcherType.INCLUDE) {
            final boolean protectHeaders = this.options != null
                    ? Boolean.parseBoolean(this.options.getOrDefault(
                            RequestDispatcherOptions.OPT_PROTECT_HEADERS_ON_INCLUDE,
                            String.valueOf(this.protectHeadersOnInclude)))
                    : this.protectHeadersOnInclude;
            dispatchingInfo.setProtectHeadersOnInclude(protectHeaders);
            dispatchingInfo.setCheckContentTypeOnInclude(this.checkContentTypeOnInclude);
        }

        rd.getSlingRequestProcessor().dispatchRequest(request, response, resource, info, dispatchingInfo);
    }

    /**
     * Returns a {@link SlingRequestPathInfo} object to use to select the
     * servlet or script to call to render the resource to be dispatched to.
     * <p>
     * <b>Note:</b> If this request dispatcher has been created with resource
     * type overwriting request dispatcher options, the resource to dispatch to
     * may be wrapped with a {@link TypeOverwritingResourceWrapper} as a result
     * of calling this method.
     */
    private SlingRequestPathInfo getMergedRequestPathInfo(final SlingJakartaHttpServletRequest cRequest) {
        SlingRequestPathInfo info = new SlingRequestPathInfo(resource);
        info = info.merge(cRequest.getRequestPathInfo());

        // merge request dispatcher options and resource type overwrite
        if (options != null) {
            info = info.merge(options);

            // ensure overwritten resource type
            String rtOverwrite = options.getForceResourceType();
            if (rtOverwrite != null && !rtOverwrite.equals(resource.getResourceType())) {
                resource = new TypeOverwritingResourceWrapper(resource, rtOverwrite);
            }
        }

        return info;
    }

    private static class TypeOverwritingResourceWrapper extends ResourceWrapper {

        private final String resourceType;

        TypeOverwritingResourceWrapper(Resource delegatee, String resourceType) {
            super(delegatee);
            this.resourceType = resourceType;
        }

        @Override
        public String getResourceType() {
            return resourceType;
        }

        /**
         * Overwrite this here because the wrapped resource will return null as
         * a super type instead of the resource type overwritten here
         */
        @Override
        public String getResourceSuperType() {
            return null;
        }

        @Override
        public boolean isResourceType(final String resourceType) {
            return this.getResourceResolver().isResourceType(this, resourceType);
        }
    }
}
