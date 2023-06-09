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

import static org.apache.sling.api.SlingConstants.ERROR_SERVLET_NAME;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingException;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.SlingServletException;
import org.apache.sling.api.adapter.AdapterManager;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.request.builder.impl.ServletContextImpl;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.ErrorHandler;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.api.wrappers.SlingHttpServletResponseWrapper;
import org.apache.sling.commons.mime.MimeTypeService;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.engine.impl.debug.RequestInfoProviderImpl;
import org.apache.sling.engine.impl.filter.ErrorFilterChain;
import org.apache.sling.engine.impl.filter.FilterHandle;
import org.apache.sling.engine.impl.filter.RequestSlingFilterChain;
import org.apache.sling.engine.impl.filter.ServletFilterManager;
import org.apache.sling.engine.impl.filter.ServletFilterManager.FilterChainType;
import org.apache.sling.engine.impl.filter.SlingComponentFilterChain;
import org.apache.sling.engine.impl.helper.SlingServletContext;
import org.apache.sling.engine.impl.parameters.ParameterSupport;
import org.apache.sling.engine.impl.request.ContentData;
import org.apache.sling.engine.impl.request.DispatchingInfo;
import org.apache.sling.engine.impl.request.RequestData;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.http.runtime.HttpServiceRuntime;
import org.osgi.service.http.runtime.dto.ServletContextDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = {SlingRequestProcessor.class, SlingRequestProcessorImpl.class},
    configurationPid = Config.PID)
public class SlingRequestProcessorImpl implements SlingRequestProcessor {

    /** default log */
    private final Logger log = LoggerFactory.getLogger(SlingRequestProcessorImpl.class);

    @Reference
    private ServletResolver servletResolver;

    @Reference
    private ServletFilterManager filterManager;

    @Reference
    private RequestProcessorMBeanImpl mbean;

    @Reference
    private HttpServiceRuntime httpServiceRuntime;

    @Reference(target = SlingServletContext.TARGET)
    private ServletContext slingServletContext;

    @Reference(cardinality=ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    private volatile AdapterManager adapterManager;

    /**
     * Resolves MIME types
     *
     * @see #getMimeType(String)
     */
    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL)
    private volatile MimeTypeService mimeTypeService;

    private final DefaultErrorHandler errorHandler = new DefaultErrorHandler();

    private volatile int maxCallCounter = Config.DEFAULT_MAX_CALL_COUNTER;

    private volatile int maxInclusionCounter = Config.DEFAULT_MAX_INCLUSION_COUNTER;

    private volatile List<StaticResponseHeader> additionalResponseHeaders = Collections.emptyList();

    private volatile boolean protectHeadersOnInclude;
    private volatile boolean checkContentTypeOnInclude;

    private BundleContext bundleContext;
    @Activate
    public void activate(final BundleContext context, final Config config) {
        this.errorHandler.setServerInfo(this.slingServletContext.getServerInfo());
        this.modified(config);
        this.bundleContext = context;
    }

    @Modified
    public void modified(final Config config) {
        final List<StaticResponseHeader> mappings = new ArrayList<>();
        final String[] props = config.sling_additional_response_headers();
        if ( props != null ) {
            for (final String prop : props) {
                if (prop != null && prop.trim().length() > 0 ) {
                    try {
                        final StaticResponseHeader mapping = new StaticResponseHeader(prop.trim());
                        mappings.add(mapping);
                    } catch (final IllegalArgumentException iae) {
                        log.info("configure: Ignoring '{}': {}", prop, iae.getMessage());
                    }
                }
            }
        }
        this.additionalResponseHeaders = mappings;

        // configure the request limits
        this.maxInclusionCounter = config.sling_max_inclusions();
        this.maxCallCounter = config.sling_max_calls();
        this.protectHeadersOnInclude = config.sling_includes_protectheaders();
        this.checkContentTypeOnInclude = config.sling_includes_checkcontenttype();
    }

    @Reference(name = "ErrorHandler",
               cardinality=ReferenceCardinality.OPTIONAL,
               policy = ReferencePolicy.DYNAMIC,
               policyOption = ReferencePolicyOption.GREEDY)
    void setErrorHandler(final ErrorHandler handler) {
        this.errorHandler.setDelegate(handler);
    }

    void unsetErrorHandler(final ErrorHandler handler) {
        this.errorHandler.setDelegate(null);
    }

    public int getMaxCallCounter() {
        return maxCallCounter;
    }

    public int getMaxIncludeCounter() {
        return maxInclusionCounter;
    }

    public List<StaticResponseHeader> getAdditionalResponseHeaders() {
        return this.additionalResponseHeaders;
    }

    public <Type> Type adaptTo(Object object, Class<Type> type) {
        final AdapterManager adapterManager = this.adapterManager;
        if (adapterManager != null) {
            return adapterManager.getAdapter(object, type);
        }

        // no adapter manager, nothing to adapt to
        return null;
    }

    /**
     * This method is directly called by the Sling main servlet.
     */
    public void doProcessRequest(final HttpServletRequest servletRequest,
            final HttpServletResponse servletResponse,
            final ResourceResolver resourceResolver) throws IOException {
        final ServletResolver sr = this.servletResolver;

        // check that we have all required services
        if (resourceResolver == null || sr == null) {
            // Dependencies are missing
            // In this case we must not use the Sling error handling infrastructure but
            // just return a 503 status response handled by the servlet container environment

            final int status = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            String errorMessage = "Required service missing (";
            if ( resourceResolver == null ) {
                errorMessage = errorMessage.concat("ResourceResolver");
                if ( sr == null ) {
                    errorMessage = errorMessage.concat(", ");
                }
            }
            if ( sr == null ) {
                errorMessage = errorMessage.concat("ServletResolver");
            }
            log.debug("{}), cannot service requests, sending status {}", errorMessage, status);
            servletResponse.sendError(status, errorMessage);
            return;
        }

        // setting the Sling request and response
        final RequestData requestData = new RequestData(this, servletRequest, servletResponse, protectHeadersOnInclude, checkContentTypeOnInclude);
        final SlingHttpServletRequest request = requestData.getSlingRequest();
        final SlingHttpServletResponse response = requestData.getSlingResponse();

        try {
            // initialize the request data - resolve resource and servlet
            final Resource resource = requestData.initResource(resourceResolver);
            requestData.initServlet(resource, sr);

            final FilterHandle[] filters = filterManager.getFilters(FilterChainType.REQUEST);
            final FilterChain processor = new RequestSlingFilterChain(this, filters);

            request.getRequestProgressTracker().log("Applying ".concat(FilterChainType.REQUEST.name()).concat("filters"));

            processor.doFilter(request, response);

        } catch ( final SlingHttpServletResponseImpl.WriterAlreadyClosedException wace ) {
            // this is an exception case, log as error
            log.error("Writer has already been closed.", wace);

        } catch (final ResourceNotFoundException rnfe) {
            // send this exception as a 404 status
            log.debug("service: Resource {} not found", rnfe.getResource());
            handleError(HttpServletResponse.SC_NOT_FOUND, rnfe.getMessage(), request, response);

        } catch (final SlingException se) {
            // if we have request data and a non-null active servlet name
            // we assume, that this is the name of the causing servlet
            if (requestData.getActiveServletName() != null) {
                request.setAttribute(ERROR_SERVLET_NAME,
                    requestData.getActiveServletName());
            }

            // send this exception as is (albeit unwrapping and wrapped
            // exception.
            Throwable t = se;
            while ( t instanceof SlingException && t.getCause() != null ) {
                t = t.getCause();
            }
            log.error("service: Uncaught SlingException ", t);
            handleError(t, request, response);

        } catch (final AccessControlException ace) {
            // SLING-319 if anything goes wrong, send 403/FORBIDDEN
            log.debug(
                "service: Authenticated user {} does not have enough rights to executed requested action",
                request.getRemoteUser());
            handleError(HttpServletResponse.SC_FORBIDDEN, null, request,
                response);

        } catch (final IOException ioe) {
            // forward IOException up the call chain to properly handle it
            throw ioe;

        } catch (final Throwable t) {
            // we assume, that this is the name of the causing servlet
            if (requestData.getActiveServletName() != null) {
                request.setAttribute(ERROR_SERVLET_NAME,
                    requestData.getActiveServletName());
            }

            log.error("service: Uncaught Throwable", t);
            handleError(t, request, response);

        } finally {
            // record the request for the web console and info provider
            RequestInfoProviderImpl.recordRequest(request);

            if (mbean != null) {
                mbean.addRequestData(requestData);
            }
        }
    }

    // ---------- SlingRequestProcessor interface

    /**
     * @see org.apache.sling.engine.SlingRequestProcessor#processRequest(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.apache.sling.api.resource.ResourceResolver)
     */
    @Override
    public void processRequest(final HttpServletRequest servletRequest,
            final HttpServletResponse servletResponse,
            final ResourceResolver resourceResolver) throws IOException {
        // set the marker for the parameter support
        final Object oldValue = servletRequest.getAttribute(ParameterSupport.MARKER_IS_SERVICE_PROCESSING);
        servletRequest.setAttribute(ParameterSupport.MARKER_IS_SERVICE_PROCESSING, Boolean.TRUE);
        Collection<ServiceReference<ServletRequestListener>> requestListenerServiceReferencesForSyntheticRequest = getRequestListenersForSyntheticRequest(servletRequest);
        Set<ServletRequestListener> requestListenersForSyntheticRequest = requestListenerServiceReferencesForSyntheticRequest.stream().map(bundleContext::getService).collect(Collectors.toSet());
        ServletRequestEvent event = new ServletRequestEvent(slingServletContext, servletRequest);
        try {
            requestListenersForSyntheticRequest.forEach(l -> l.requestInitialized(event));
            this.doProcessRequest(servletRequest, servletResponse, resourceResolver);
        } finally {
            // restore the old value
            if ( oldValue != null ) {
                servletRequest.setAttribute(ParameterSupport.MARKER_IS_SERVICE_PROCESSING, oldValue);
            } else {
                servletRequest.removeAttribute(ParameterSupport.MARKER_IS_SERVICE_PROCESSING);
            }
            requestListenersForSyntheticRequest.forEach(l -> l.requestDestroyed(event));
            requestListenerServiceReferencesForSyntheticRequest.forEach(bundleContext::ungetService);
        }
    }

    private Collection<ServiceReference<ServletRequestListener>> getRequestListenersForSyntheticRequest(HttpServletRequest servletRequest) {
        if (!isSyntheticRequest(servletRequest)) {
            return Collections.emptyList();
        } else {
            return Stream.of(getSlingServletContextDTO().listenerDTOs)
                .filter(l -> Arrays.stream(l.types).anyMatch(ServletRequestListener.class.getName()::equals))
                .map(l -> {
                    try {
                        return bundleContext.getServiceReferences(ServletRequestListener.class, "(service.id=" + l.serviceId + ")").iterator().next();
                    } catch (InvalidSyntaxException e) {
                        // should not happen
                        throw new IllegalStateException(e);
                    }
                })
                .collect(Collectors.toSet());
        }
    }

    private boolean isSyntheticRequest(HttpServletRequest servletRequest) {
        return servletRequest.getServletContext().getClass().getName().equals(ServletContextImpl.class.getName());
    }

    private ServletContextDTO getSlingServletContextDTO() {
        return Stream.of(httpServiceRuntime.getRuntimeDTO().servletContextDTOs).filter(c -> c.name.equals(SlingHttpContext.SERVLET_CONTEXT_NAME)).findFirst().orElseThrow(() -> new IllegalStateException("Could not retrieve Sling Servlet Context from OSGi HTTP Whiteboard"));
    }
    
    /**
     * Renders the component defined by the RequestData's current ComponentData
     * instance after calling all filters of the given
     * {@link org.apache.sling.engine.impl.filter.ServletFilterManager.FilterChainType
     * filterChainType}.
     *
     * @param request
     * @param response
     * @param filterChainType
     * @throws IOException
     * @throws ServletException
     */

    public void processComponent(SlingHttpServletRequest request,
            SlingHttpServletResponse response,
            final FilterChainType filterChainType) throws IOException,
            ServletException {

        final FilterHandle filters[] = filterManager.getFilters(filterChainType);

        FilterChain processor = new SlingComponentFilterChain(filters);
        request.getRequestProgressTracker().log(
            "Applying " + filterChainType + "filters");
        processor.doFilter(request, response);
    }

    // ---------- Generic Content Request processor ----------------------------

    /**
     * Dispatches the request on behalf of the
     * {@link org.apache.sling.engine.impl.request.SlingRequestDispatcher}.
     * @param request The request
     * @param response The response
     * @param resource The resource
     * @param resolvedURL Request path info
     * @param include Is this an include (or forward) ?
     * @param protectHeadersOnInclude Should the headers be protected on include?
     * @param checkContentTypeOnInclude Should we prevent changing the Content-Type on include?
     */
    public void dispatchRequest(final ServletRequest request,
            final ServletResponse response, final Resource resource,
            final RequestPathInfo resolvedURL, 
            final DispatchingInfo dispatchingInfo)
    throws IOException, ServletException {

        // we need a SlingHttpServletRequest/SlingHttpServletResponse tupel
        // to continue
        final SlingHttpServletRequest cRequest = RequestData.toSlingHttpServletRequest(request);
        final SlingHttpServletResponse cResponse = RequestData.toSlingHttpServletResponse(response);

        // get the request data (and btw check the correct type)
        final RequestData requestData = RequestData.getRequestData(cRequest);
        final ContentData oldContentData = requestData.getContentData();
        final ContentData contentData = requestData.setContent(resource, resolvedURL);

        final DispatchingInfo oldDispatchingInfo = requestData.getDispatchingInfo();
        requestData.setDispatchingInfo(dispatchingInfo);
        try {
            // resolve the servlet
            Servlet servlet = servletResolver.resolveServlet(cRequest);
            contentData.setServlet(servlet);

            final FilterChainType type = dispatchingInfo.getType() == DispatcherType.INCLUDE
                    ? FilterChainType.INCLUDE
                    : FilterChainType.FORWARD;
            processComponent(cRequest, cResponse, type);
        } finally {
            requestData.resetContent(oldContentData);
            requestData.setDispatchingInfo(oldDispatchingInfo);
        }
    }

    // ---------- Error Handling with Filters

    private void handleError(final FilterChain errorFilterChain,
            final SlingHttpServletRequest request,
            final SlingHttpServletResponse response) 
    throws IOException {
        request.getRequestProgressTracker().log("Applying " + FilterChainType.ERROR + " filters");

        try {
            // wrap the response ensuring getWriter will fall back to wrapping
            // the response output stream if reset does not reset this
            errorFilterChain.doFilter(request, new ErrorResponseWrapper(response));
        } catch (final ServletException se) {
            throw new SlingServletException(se);
        }
    }

    void handleError(final int status, final String message,
            final SlingHttpServletRequest request,
            final SlingHttpServletResponse response) throws IOException {
        final FilterHandle[] filters = filterManager.getFilters(FilterChainType.ERROR);
        final FilterChain processor = new ErrorFilterChain(filters, errorHandler, status, message);
        this.handleError(processor, request, response);
    }

    private void handleError(final Throwable throwable,
            final SlingHttpServletRequest request,
            final SlingHttpServletResponse response) throws IOException {
        final FilterHandle[] filters = filterManager.getFilters(FilterChainType.ERROR);
        final FilterChain processor = new ErrorFilterChain(filters, errorHandler, throwable);
        this.handleError(processor, request, response);
    }

    private static class ErrorResponseWrapper extends
            SlingHttpServletResponseWrapper {

        private PrintWriter writer;

        public ErrorResponseWrapper(SlingHttpServletResponse wrappedResponse) {
            super(wrappedResponse);
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                try {
                    writer = super.getWriter();
                } catch (IllegalStateException ise) {
                    // resetting the response did not reset the output channel
                    // status and we have to create a writer based on the output
                    // stream using the character encoding already set on the
                    // response, defaulting to ISO-8859-1
                    OutputStream out = getOutputStream();
                    String encoding = getCharacterEncoding();
                    if (encoding == null) {
                        encoding = "ISO-8859-1";
                        setCharacterEncoding(encoding);
                    }
                    Writer w = new OutputStreamWriter(out, encoding);
                    writer = new PrintWriter(w);
                }
            }
            return writer;
        }

        /**
         * Flush the writer if the {@link #getWriter()} method was called
         * to potentially wrap an OuputStream still existing in the response.
         */
        @Override
        public void flushBuffer() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            super.flushBuffer();
        }
    }

    /**
     * Returns the MIME type as resolved by the <code>MimeTypeService</code> or
     * <code>null</code> if the service is not available.
     */
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
}
