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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.RecursionTooDeepException;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.request.RequestUtil;
import org.apache.sling.api.request.TooManyCallsException;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.api.wrappers.JavaxToJakartaRequestWrapper;
import org.apache.sling.api.wrappers.JavaxToJakartaResponseWrapper;
import org.apache.sling.api.wrappers.SlingJakartaHttpServletRequestWrapper;
import org.apache.sling.api.wrappers.SlingJakartaHttpServletResponseWrapper;
import org.apache.sling.engine.impl.SlingJakartaHttpServletRequestImpl;
import org.apache.sling.engine.impl.SlingJakartaHttpServletResponseImpl;
import org.apache.sling.engine.impl.SlingMainServlet;
import org.apache.sling.engine.impl.SlingRequestProcessorImpl;
import org.apache.sling.engine.impl.adapter.SlingServletRequestAdapter;
import org.apache.sling.engine.impl.adapter.SlingServletResponseAdapter;
import org.apache.sling.engine.impl.parameters.ParameterSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.api.SlingConstants.SLING_CURRENT_SERVLET_NAME;

/**
 * The <code>RequestData</code> class provides access to objects which are set
 * on a Servlet Request wide basis such as the repository session, the
 * persistence manager, etc.
 *
 * The setup order is:
 * <ol>
 *   <li>Invoke constructor</li>
 *   <li>Invoke initResource()</li>
 *   <li>Invoke initServlet()</li>
 * </ol>
 * @see ContentData
 */
public class RequestData {

    /** Logger - use static as a new object is created with every request */
    private static final Logger log = LoggerFactory.getLogger(RequestData.class);

    /**
     * The name of the request attribute providing the resource addressed by the
     * request URL.
     */
    public static final String REQUEST_RESOURCE_PATH_ATTR = "$$sling.request.resource$$";

    /**
     * The name of the request attribute to override the max call number (-1 for infinite or integer value).
     */
    private static String REQUEST_MAX_CALL_OVERRIDE = "sling.max.calls";

    /** The request processor used for request dispatching and other stuff */
    private final SlingRequestProcessorImpl slingRequestProcessor;

    private final long startTimestamp;

    /** The original servlet Servlet Request Object */
    private final HttpServletRequest servletRequest;

    /** The original servlet Servlet Response object */
    private final HttpServletResponse servletResponse;

    /** The original servlet Servlet Request Object */
    private final SlingJakartaHttpServletRequest slingRequest;

    /** The original servlet Servlet Response object */
    private final SlingJakartaHttpServletResponse slingResponse;

    private final boolean protectHeadersOnInclude;

    private final boolean checkContentTypeOnInclude;

    /** The parameter support class */
    private ParameterSupport parameterSupport;

    private ResourceResolver resourceResolver;

    private final RequestProgressTracker requestProgressTracker;

    /** the current ContentData */
    private ContentData currentContentData;

    /**
     * the number of servlets called by
     * {@link #service(SlingHttpServletRequest, SlingHttpServletResponse)}
     */
    private int servletCallCounter;

    /**
     * The name of the currently active serlvet.
     *
     * @see #setActiveServletName(String)
     * @see #getActiveServletName()
     */
    private String activeServletName;

    /**
     * Recursion depth
     */
    private int recursionDepth;

    /**
     * The peak value for the recursion depth.
     */
    private int peakRecusionDepth;

    /**
     * Current dispatching info
     */
    private DispatchingInfo dispatchingInfo;

    private final boolean disableCheckCompliantGetUserPrincipal;

    private static volatile boolean loggedNonCompliantGetUserPrincipalWarning = false;

    /**
     * Prevent traversal using '/../' or '/..' even if '[' or '}' is used in-between
     */
    private static final Set<Character> SKIPPED_TRAVERSAL_CHARS = new HashSet<>();

    static {
        SKIPPED_TRAVERSAL_CHARS.add('[');
        SKIPPED_TRAVERSAL_CHARS.add('}');
    }

    public RequestData(
            SlingRequestProcessorImpl slingRequestProcessor,
            HttpServletRequest request,
            HttpServletResponse response,
            boolean protectHeadersOnInclude,
            boolean checkContentTypeOnInclude,
            boolean disableCheckCompliantGetUserPrincipal) {
        this.startTimestamp = System.currentTimeMillis();

        this.slingRequestProcessor = slingRequestProcessor;

        this.servletRequest = request;
        this.servletResponse = response;
        this.protectHeadersOnInclude = protectHeadersOnInclude;
        this.checkContentTypeOnInclude = checkContentTypeOnInclude;
        this.disableCheckCompliantGetUserPrincipal = disableCheckCompliantGetUserPrincipal;

        this.slingRequest = new SlingJakartaHttpServletRequestImpl(this, this.servletRequest);

        this.slingResponse = new SlingJakartaHttpServletResponseImpl(this, this.servletResponse);

        // Use tracker from SlingHttpServletRequest
        if (request instanceof SlingJakartaHttpServletRequest) {
            this.requestProgressTracker = ((SlingJakartaHttpServletRequest) request).getRequestProgressTracker();
        } else {
            // Getting the RequestProgressTracker from the request attributes like
            // this should not be generally used, it's just a way to pass it from
            // its creation point to here, so it's made available via
            // the Sling request's getRequestProgressTracker method.
            final Object o = request.getAttribute(RequestProgressTracker.class.getName());
            if (o instanceof RequestProgressTracker) {
                this.requestProgressTracker = (RequestProgressTracker) o;
            } else {
                log.warn("RequestProgressTracker not found in request attributes");
                this.requestProgressTracker = Builders.newRequestProgressTracker();
                this.requestProgressTracker.log("Method={0}, PathInfo={1}", request.getMethod(), request.getPathInfo());
            }
        }
    }

    public Resource initResource(ResourceResolver resourceResolver) {
        // keep the resource resolver for request processing
        this.resourceResolver = resourceResolver;

        // resolve the resource
        requestProgressTracker.startTimer("ResourceResolution");
        final SlingJakartaHttpServletRequest request = getSlingRequest();

        StringBuffer requestURL = servletRequest.getRequestURL();
        String path = request.getPathInfo();
        if (requestURL.indexOf(";") > -1 && !path.contains(";")) {
            try {
                final URL rUrl = new URL(requestURL.toString());
                final String prefix = request.getContextPath().concat(request.getServletPath());
                path = rUrl.getPath().substring(prefix.length());
            } catch (final MalformedURLException e) {
                // ignore
            }
        }

        Resource resource = resourceResolver.resolve(request, path);
        if (request.getAttribute(REQUEST_RESOURCE_PATH_ATTR) == null) {
            request.setAttribute(REQUEST_RESOURCE_PATH_ATTR, resource.getPath());
        }
        requestProgressTracker.logTimer(
                "ResourceResolution",
                "URI={0} resolves to Resource={1}",
                getServletRequest().getRequestURI(),
                resource);
        return resource;
    }

    public void initServlet(final Resource resource, final ServletResolver sr) {
        // the resource and the request path info, will never be null
        RequestPathInfo requestPathInfo = new SlingRequestPathInfo(resource);
        ContentData contentData = setContent(resource, requestPathInfo);

        requestProgressTracker.log("Resource Path Info: {0}", requestPathInfo);

        // finally resolve the servlet for the resource
        requestProgressTracker.startTimer("ServletResolution");
        Servlet servlet = sr.resolve(slingRequest);
        requestProgressTracker.logTimer(
                "ServletResolution",
                "URI={0} handled by Servlet={1}",
                getServletRequest().getRequestURI(),
                (servlet == null ? "-none-" : RequestUtil.getServletName(servlet)));
        contentData.setServlet(servlet);
    }

    public SlingRequestProcessorImpl getSlingRequestProcessor() {
        return slingRequestProcessor;
    }

    public HttpServletRequest getServletRequest() {
        return servletRequest;
    }

    public HttpServletResponse getServletResponse() {
        return servletResponse;
    }

    public SlingJakartaHttpServletRequest getSlingRequest() {
        return slingRequest;
    }

    public SlingJakartaHttpServletResponse getSlingResponse() {
        return slingResponse;
    }

    public DispatchingInfo getDispatchingInfo() {
        return dispatchingInfo;
    }

    public void setDispatchingInfo(final DispatchingInfo dispatchingInfo) {
        this.dispatchingInfo = dispatchingInfo;
    }

    // ---------- Request Helper

    /**
     * Unwraps the ServletRequest to a SlingJakartaHttpServletRequest.
     *
     * @param request the request
     * @return the unwrapped request
     * @throws IllegalArgumentException If the <code>request</code> is not a
     *             <code>SlingJakartaHttpServletRequest</code> and not a
     *             <code>ServletRequestWrapper</code> wrapping a
     *             <code>SlingJakartaHttpServletRequest</code>.
     */
    @SuppressWarnings("deprecation")
    public static SlingJakartaHttpServletRequest unwrap(ServletRequest request) {

        // early check for most cases
        if (request instanceof SlingJakartaHttpServletRequest) {
            return (SlingJakartaHttpServletRequest) request;
        }

        // unwrap wrappers
        while (request instanceof ServletRequestWrapper) {
            request = ((ServletRequestWrapper) request).getRequest();

            // immediate termination if we found one
            if (request instanceof SlingJakartaHttpServletRequest) {
                return (SlingJakartaHttpServletRequest) request;
            }
        }
        // javax to jakarta wrapper?
        if (request instanceof org.apache.felix.http.jakartawrappers.HttpServletRequestWrapper) {
            javax.servlet.ServletRequest req =
                    ((org.apache.felix.http.jakartawrappers.HttpServletRequestWrapper) request).getRequest();
            while (req instanceof javax.servlet.ServletRequestWrapper) {
                req = ((javax.servlet.ServletRequestWrapper) req).getRequest();
            }
            if (req instanceof org.apache.felix.http.javaxwrappers.HttpServletRequestWrapper) {
                return unwrap(((org.apache.felix.http.javaxwrappers.HttpServletRequestWrapper) req).getRequest());
            }
            // check for usage of builder
            if (req instanceof SlingHttpServletRequest) {
                // we start again at the top javax request
                req = ((org.apache.felix.http.jakartawrappers.HttpServletRequestWrapper) request).getRequest();
                do {
                    if (req instanceof SlingHttpServletRequest) {
                        return JavaxToJakartaRequestWrapper.toJakartaRequest((SlingHttpServletRequest) req);
                    }
                    if (req instanceof javax.servlet.ServletRequestWrapper) {
                        req = ((javax.servlet.ServletRequestWrapper) req).getRequest();
                    } else {
                        req = null;
                    }
                } while (req != null);
            }
            throw new IllegalArgumentException("ServletRequest not wrapping SlingJakartaHttpServletRequest: " + req);
        }

        // if we unwrapped everything and did not find a
        // SlingJakartaHttpServletRequest, we lost
        throw new IllegalArgumentException("ServletRequest not wrapping SlingJakartaHttpServletRequest: " + request);
    }

    /**
     * Unwraps the SlingJakartaHttpServletRequest to a SlingJakartaHttpServletRequestImpl
     *
     * @param request the request
     * @return the unwrapped request
     * @throws IllegalArgumentException If <code>request</code> is not a
     *             <code>SlingJakartaHttpServletRequestImpl</code> and not
     *             <code>SlingJakartaHttpServletRequestWrapper</code> wrapping a
     *             <code>SlingJakartaHttpServletRequestImpl</code>.
     */
    public static SlingJakartaHttpServletRequestImpl unwrap(SlingJakartaHttpServletRequest request) {
        while (request instanceof SlingJakartaHttpServletRequestWrapper) {
            request = ((SlingJakartaHttpServletRequestWrapper) request).getSlingRequest();
        }

        // javax to jakarta wrapper?
        if (request instanceof org.apache.felix.http.jakartawrappers.HttpServletRequestWrapper) {
            javax.servlet.ServletRequest req =
                    ((org.apache.felix.http.jakartawrappers.HttpServletRequestWrapper) request).getRequest();
            while (req instanceof javax.servlet.ServletRequestWrapper) {
                req = ((javax.servlet.ServletRequestWrapper) req).getRequest();
            }
            if (req instanceof org.apache.felix.http.javaxwrappers.HttpServletRequestWrapper) {
                final ServletRequest r =
                        ((org.apache.felix.http.javaxwrappers.HttpServletRequestWrapper) req).getRequest();
                if (r instanceof SlingJakartaHttpServletRequest) {
                    return unwrap((SlingJakartaHttpServletRequest) r);
                }
                throw new IllegalArgumentException("SlingJakartaHttpServletRequest not of correct type: " + r);
            }
            throw new IllegalArgumentException("SlingJakartaHttpServletRequest not of correct type: " + req);
        }
        if (request instanceof SlingJakartaHttpServletRequestImpl) {
            return (SlingJakartaHttpServletRequestImpl) request;
        }

        throw new IllegalArgumentException("SlingJakartaHttpServletRequest not of correct type: " + request);
    }

    /**
     * Unwraps the ServletResponse to a SlingJakartaHttpServletResponse.
     *
     * @param response the response
     * @return the unwrapped response
     * @throws IllegalArgumentException If the <code>response</code> is not a
     *             <code>SlingJakartaHttpServletResponse</code> and not a
     *             <code>ServletResponseWrapper</code> wrapping a
     *             <code>SlingJakartaHttpServletResponse</code>.
     */
    @SuppressWarnings("deprecation")
    public static SlingJakartaHttpServletResponse unwrap(ServletResponse response) {

        // early check for most cases
        if (response instanceof SlingJakartaHttpServletResponse) {
            return (SlingJakartaHttpServletResponse) response;
        }

        // unwrap wrappers
        while (response instanceof ServletResponseWrapper) {
            response = ((ServletResponseWrapper) response).getResponse();

            // immediate termination if we found one
            if (response instanceof SlingJakartaHttpServletResponse) {
                return (SlingJakartaHttpServletResponse) response;
            }
        }

        // javax to jakarta wrapper?
        if (response instanceof org.apache.felix.http.jakartawrappers.HttpServletResponseWrapper) {
            javax.servlet.ServletResponse res =
                    ((org.apache.felix.http.jakartawrappers.HttpServletResponseWrapper) response).getResponse();
            while (res instanceof javax.servlet.ServletResponseWrapper) {
                res = ((javax.servlet.ServletResponseWrapper) res).getResponse();
            }
            if (res instanceof org.apache.felix.http.javaxwrappers.HttpServletResponseWrapper) {
                return unwrap(((org.apache.felix.http.javaxwrappers.HttpServletResponseWrapper) res).getResponse());
            }
            // check for usage of builder
            if (res instanceof SlingHttpServletResponse) {
                // we start again at the top javax response
                res = ((org.apache.felix.http.jakartawrappers.HttpServletResponseWrapper) response).getResponse();
                do {
                    if (res instanceof SlingHttpServletResponse) {
                        return JavaxToJakartaResponseWrapper.toJakartaResponse((SlingHttpServletResponse) res);
                    }
                    if (res instanceof javax.servlet.ServletResponseWrapper) {
                        res = ((javax.servlet.ServletResponseWrapper) res).getResponse();
                    } else {
                        res = null;
                    }
                } while (res != null);
            }
            throw new IllegalArgumentException("ServletResponse not wrapping SlingJakartaHttpServletResponse: " + res);
        }

        // if we unwrapped everything and did not find a
        // SlingJakartaHttpServletResponse, we lost
        throw new IllegalArgumentException("ServletResponse not wrapping SlingJakartaHttpServletResponse: " + response);
    }

    /**
     * Unwraps a SlingJakartaHttpServletResponse to a SlingJakartaHttpServletResponseImpl
     *
     * @param response the response
     * @return the unwrapped response
     * @throws IllegalArgumentException If <code>response</code> is not a
     *             <code>SlingJakartaHttpServletResponseImpl</code> and not
     *             <code>SlingJakartaHttpServletResponse</code> wrapping a
     *             <code>SlingJakartaHttpServletResponseImpl</code>.
     */
    public static SlingJakartaHttpServletResponseImpl unwrap(SlingJakartaHttpServletResponse response) {
        while (response instanceof SlingJakartaHttpServletResponseWrapper) {
            response = ((SlingJakartaHttpServletResponseWrapper) response).getSlingResponse();
        }

        // javax to jakarta wrapper?
        if (response instanceof org.apache.felix.http.jakartawrappers.HttpServletResponseWrapper) {
            javax.servlet.ServletResponse res =
                    ((org.apache.felix.http.jakartawrappers.HttpServletResponseWrapper) response).getResponse();
            while (res instanceof javax.servlet.ServletResponseWrapper) {
                res = ((javax.servlet.ServletResponseWrapper) res).getResponse();
            }
            if (res instanceof org.apache.felix.http.javaxwrappers.HttpServletResponseWrapper) {
                final ServletResponse r =
                        ((org.apache.felix.http.javaxwrappers.HttpServletResponseWrapper) res).getResponse();
                if (r instanceof SlingJakartaHttpServletResponse) {
                    return unwrap((SlingJakartaHttpServletResponse) r);
                }
                throw new IllegalArgumentException("SlingJakartaHttpServletResponse not of correct type: " + r);
            }
            throw new IllegalArgumentException("SlingJakartaHttpServletResponse not of correct type: " + res);
        }
        if (response instanceof SlingJakartaHttpServletResponseImpl) {
            return (SlingJakartaHttpServletResponseImpl) response;
        }

        throw new IllegalArgumentException("SlingJakartaHttpServletResponse not of correct type: " + response);
    }

    /**
     * @param request the request
     * @return the request data
     * @throws IllegalArgumentException If the <code>request</code> is not a
     *             <code>SlingHttpServletRequest</code> and not a
     *             <code>ServletRequestWrapper</code> wrapping a
     *             <code>SlingHttpServletRequest</code>.
     */
    public static RequestData getRequestData(ServletRequest request) {
        return unwrap(unwrap(request)).getRequestData();
    }

    /**
     * @param request the request
     * @return the request data
     * @throws IllegalArgumentException If <code>request</code> is not a
     *             <code>SlingHttpServletRequestImpl</code> and not
     *             <code>SlingHttpServletRequestWrapper</code> wrapping a
     *             <code>SlingHttpServletRequestImpl</code>.
     */
    public static RequestData getRequestData(SlingJakartaHttpServletRequest request) {
        return unwrap(request).getRequestData();
    }

    /**
     * @param request the request
     * @return the sling http servlet request
     * @throws IllegalArgumentException if <code>request</code> is not a
     *             <code>HttpServletRequest</code> of if <code>request</code>
     *             is not backed by <code>SlingHttpServletRequestImpl</code>.
     */
    public static SlingJakartaHttpServletRequest toSlingHttpServletRequest(ServletRequest request) {

        // unwrap to SlingHttpServletRequest, may throw if no
        // SlingHttpServletRequest is wrapped in request
        SlingJakartaHttpServletRequest cRequest = unwrap(request);

        // ensure the SlingHttpServletRequest is backed by
        // SlingHttpServletRequestImpl
        RequestData.unwrap(cRequest);

        // if the request is not wrapper at all, we are done
        if (cRequest == request) {
            return cRequest;
        }

        // ensure the request is a HTTP request
        if (!(request instanceof HttpServletRequest)) {
            throw new IllegalArgumentException("Request is not an HTTP request");
        }

        // otherwise, we create a new response wrapping the servlet response
        // and unwrapped component response
        return new SlingServletRequestAdapter(cRequest, (HttpServletRequest) request);
    }

    /**
     * @param response the response
     * @return the sling http servlet response
     * @throws IllegalArgumentException if <code>response</code> is not a
     *             <code>HttpServletResponse</code> of if
     *             <code>response</code> is not backed by
     *             <code>SlingHttpServletResponseImpl</code>.
     */
    public static SlingJakartaHttpServletResponse toSlingHttpServletResponse(ServletResponse response) {

        // unwrap to SlingHttpServletResponse
        SlingJakartaHttpServletResponse cResponse = unwrap(response);

        // if the servlet response is actually the SlingHttpServletResponse, we
        // are done
        if (cResponse == response) {
            return cResponse;
        }

        // ensure the response is a HTTP response
        if (!(response instanceof HttpServletResponse)) {
            throw new IllegalArgumentException("Response is not an HTTP response");
        }

        // otherwise, we create a new response wrapping the servlet response
        // and unwrapped component response
        return new SlingServletResponseAdapter(cResponse, (HttpServletResponse) response);
    }

    /**
     * Helper method to call the servlet for the current content data. If the
     * current content data has no servlet, <em>NOT_FOUND</em> (404) error is
     * sent and the method terminates.
     * <p>
     * If the the servlet exists, the
     * {@link org.apache.sling.api.SlingConstants#SLING_CURRENT_SERVLET_NAME} request attribute is set
     * to the name of that servlet and that servlet name is also set as the
     * {@link #setActiveServletName(String) currently active servlet}. After
     * the termination of the servlet (normal or throwing a Throwable) the
     * request attribute is reset to the previous value. The name of the
     * currently active servlet is only reset to the previous value if the
     * servlet terminates normally. In case of a Throwable, the active servlet
     * name is not reset and indicates which servlet caused the potential abort
     * of the request.
     *
     * @param request The request object for the service method
     * @param response The response object for the service method
     * @throws IOException May be thrown by the servlet's service method
     * @throws ServletException May be thrown by the servlet's service method
     */
    public static void service(SlingJakartaHttpServletRequest request, SlingJakartaHttpServletResponse response)
            throws IOException, ServletException {

        if (!isValidRequest(
                request.getRequestPathInfo().getResourcePath(),
                request.getRequestPathInfo().getSelectors())) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed request syntax");
            return;
        }

        RequestData requestData = RequestData.getRequestData(request);
        Servlet servlet = requestData.getContentData().getServlet();
        if (servlet == null) {

            log.warn(
                    "Did not find a servlet to handle the request (path={},selectors={},extension={},suffix={})",
                    request.getRequestPathInfo().getResourcePath(),
                    Arrays.toString(request.getRequestPathInfo().getSelectors()),
                    request.getRequestPathInfo().getExtension(),
                    request.getRequestPathInfo().getSuffix());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No Servlet to handle request");

        } else {

            String name = RequestUtil.getServletName(servlet);

            // verify the number of service calls in this request
            if (requestData.hasServletMaxCallCount(request)) {
                throw new TooManyCallsException(name);
            }

            // replace the current servlet name in the request
            Object oldValue = request.getAttribute(SLING_CURRENT_SERVLET_NAME);
            request.setAttribute(SLING_CURRENT_SERVLET_NAME, name);

            // setup the tracker for this service call
            String timerName = name + "#" + requestData.servletCallCounter;
            requestData.servletCallCounter++;
            requestData.getRequestProgressTracker().startTimer(timerName);

            String prevServletName = requestData.setActiveServletName(name);
            try {
                servlet.service(request, response);
            } finally {
                requestData.setActiveServletName(prevServletName);
                request.setAttribute(SLING_CURRENT_SERVLET_NAME, oldValue);
                requestData.getRequestProgressTracker().logTimer(timerName);
            }
        }
    }

    /*
     * Don't allow path segments that contain only dots or a mix of dots and %5B.
     * Additionally, check that we didn't have an empty selector from a dot replacement.
     */
    static boolean isValidRequest(String resourcePath, String... selectors) {
        for (String selector : selectors) {
            if (selector.trim().isEmpty()) {
                return false;
            }
        }
        return resourcePath == null || !traversesParentPath(resourcePath);
    }

    // ---------- Content inclusion stacking -----------------------------------

    public ContentData setContent(final Resource resource, final RequestPathInfo requestPathInfo) {
        if (this.recursionDepth >= this.slingRequestProcessor.getMaxIncludeCounter()) {
            throw new RecursionTooDeepException(requestPathInfo.getResourcePath());
        }
        this.recursionDepth++;
        if (this.recursionDepth > this.peakRecusionDepth) {
            this.peakRecusionDepth = this.recursionDepth;
        }
        currentContentData = new ContentData(resource, requestPathInfo);
        return currentContentData;
    }

    public void resetContent(final ContentData data) {
        this.recursionDepth--;
        currentContentData = data;
    }

    public ContentData getContentData() {
        return currentContentData;
    }

    public ResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    public RequestProgressTracker getRequestProgressTracker() {
        return requestProgressTracker;
    }

    public int getPeakRecusionDepth() {
        return peakRecusionDepth;
    }

    public int getServletCallCount() {
        return servletCallCounter;
    }

    public boolean protectHeadersOnInclude() {
        return protectHeadersOnInclude;
    }

    public boolean checkContentTypeOnInclude() {
        return checkContentTypeOnInclude;
    }

    /**
     * Returns {@code true} if the number of {@code RequestDispatcher.include}
     * calls has been reached within the given request. That maximum number may
     * either be defined by the {@link #REQUEST_MAX_CALL_OVERRIDE} request
     * attribute or the {@link SlingMainServlet#PROP_MAX_CALL_COUNTER}
     * configuration of the {@link SlingMainServlet}.
     *
     * @param request The request to check
     * @return {@code true} if the maximum number of calls has been reached (or
     *         surpassed)
     */
    private boolean hasServletMaxCallCount(final ServletRequest request) {
        // verify the number of service calls in this request
        log.debug("Servlet call counter : {}", getServletCallCount());

        // max number of calls can be overriden with a request attribute (-1 for
        // infinite or integer value)
        int maxCallCounter = this.slingRequestProcessor.getMaxCallCounter();
        Object reqMaxOverride = request.getAttribute(REQUEST_MAX_CALL_OVERRIDE);
        if (reqMaxOverride instanceof Number) {
            maxCallCounter = ((Number) reqMaxOverride).intValue();
        }

        return (maxCallCounter >= 0) && getServletCallCount() >= maxCallCounter;
    }

    public long getElapsedTimeMsec() {
        return System.currentTimeMillis() - startTimestamp;
    }

    /**
     * Sets the name of the currently active servlet and returns the name of the
     * previously active servlet.
     *
     * @param servletName the servlet name
     * @return the previous servlet name
     */
    public String setActiveServletName(String servletName) {
        String old = activeServletName;
        activeServletName = servletName;
        return old;
    }

    /**
     * Returns the name of the currently active servlet. If this name is not
     * <code>null</code> at the end of request processing, more precisly in
     * the case of an uncaught <code>Throwable</code> at the end of request
     * processing, this is the name of the servlet causing the uncaught
     * <code>Throwable</code>.
     *
     * @return the current servlet name
     */
    public String getActiveServletName() {
        return activeServletName;
    }

    // ---------- Parameter support -------------------------------------------

    public ServletInputStream getInputStream() throws IOException {
        if (parameterSupport != null && parameterSupport.requestDataUsed()) {
            throw new IllegalStateException("Request Data has already been read");
        }

        // may throw IllegalStateException if the reader has already been
        // acquired
        return getServletRequest().getInputStream();
    }

    public BufferedReader getReader() throws UnsupportedEncodingException, IOException {
        if (parameterSupport != null && parameterSupport.requestDataUsed()) {
            throw new IllegalStateException("Request Data has already been read");
        }

        // may throw IllegalStateException if the input stream has already been
        // acquired
        return getServletRequest().getReader();
    }

    public ParameterSupport getParameterSupport() {
        if (parameterSupport == null) {
            parameterSupport = ParameterSupport.getInstance(getServletRequest());
        }

        return parameterSupport;
    }

    /*
     * Traverses the path segment wise and checks
     * if there is any path with only dots (".")
     * skipping SKIPPED_TRAVERSAL_CHARS characters in segment.
     */
    private static boolean traversesParentPath(String path) {
        int index = 0;
        while (index < path.length()) {
            int charCount = 0;
            int dotCount = 0;
            // count dots (".") and total chars in each path segment (between two '/')
            while (index < path.length() && path.charAt(index) != '/') {
                char c = path.charAt(index);
                if (!SKIPPED_TRAVERSAL_CHARS.contains(c)) {
                    if (c == '.') {
                        dotCount++;
                    }
                    charCount++;
                }
                index++;
            }
            // if all chars are dots (".")
            // path is traversing the parent directory
            if (charCount > 1 && dotCount == charCount) {
                return true;
            }
            index++;
        }
        return false;
    }

    public boolean isDisableCheckCompliantGetUserPrincipal() {
        return disableCheckCompliantGetUserPrincipal;
    }

    public void logNonCompliantGetUserPrincipalWarning() {
        if (!loggedNonCompliantGetUserPrincipalWarning) {
            loggedNonCompliantGetUserPrincipalWarning = true;
            log.warn(
                    "Request.getUserPrincipal() called without a remoteUser set. This is not compliant to the servlet spec "
                            + "and might return a principal even if the request is not authenticated. Please update your code to use getAuthType() "
                            + "to check for anonymous requests first.");
        }
    }
}
