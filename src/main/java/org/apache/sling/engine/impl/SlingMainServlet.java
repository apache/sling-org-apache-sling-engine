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

import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.GenericServlet;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.request.SlingRequestEvent;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.auth.core.AuthenticationSupport;
import org.apache.sling.engine.impl.debug.RequestInfoProviderImpl;
import org.apache.sling.engine.impl.helper.ClientAbortException;
import org.apache.sling.engine.impl.helper.RequestListenerManager;
import org.apache.sling.engine.impl.helper.SlingServletContext;
import org.apache.sling.engine.impl.request.RequestData;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.component.propertytypes.ServiceVendor;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>SlingMainServlet</code>
 */
@SuppressWarnings("serial")
@Component(configurationPid = SlingMainServlet.PID)
@ServiceDescription("Apache Sling Main Servlet")
@ServiceVendor("The Apache Software Foundation")
@Designate(ocd=SlingMainServlet.Config.class)
public class SlingMainServlet extends GenericServlet {

    public static final String PID = "org.apache.sling.engine.impl.SlingMainServlet";

    @ObjectClassDefinition(name ="Apache Sling Main Servlet",
            description="Main processor of the Sling framework controlling all " +
                    "aspects of processing requests inside of Sling, namely authentication, " +
                    "resource resolution, servlet/script resolution and execution of servlets " +
                    "and scripts.")
    public @interface Config {

        @AttributeDefinition(name = "Number of Calls per Request",
                description = "Defines the maximum number of Servlet and Script " +
                     "calls while processing a single client request. This number should be high " +
                     "enough to not limit request processing artificially. On the other hand it " +
                     "should not be too high to allow the mechanism to limit the resources required " +
                     "to process a request in case of errors. The default value is 1000.")
        int sling_max_calls() default RequestData.DEFAULT_MAX_CALL_COUNTER;

        @AttributeDefinition(name = "Recursion Depth",
                description = "The maximum number of recursive Servlet and " +
                     "Script calls while processing a single client request. This number should not " +
                     "be too high, otherwise StackOverflowErrors may occurr in case of erroneous " +
                     "scripts and servlets. The default value is 50. ")
        int sling_max_inclusions() default RequestData.DEFAULT_MAX_INCLUSION_COUNTER;

        @AttributeDefinition(name = "Allow the HTTP TRACE method",
                description = "If set to true, the HTTP TRACE method will be " +
                     "enabled. By default the HTTP TRACE methods is disabled as it can be used in " +
                     "Cross Site Scripting attacks on HTTP servers.")
        boolean sling_trace_allow() default false;

        @AttributeDefinition(name = "Number of Requests to Record",
                description = "Defines the number of requests that " +
                     "internally recorded for display on the \"Recent Requests\" Web Console page. If " +
                     "this value is less than or equal to zero, no requests are internally kept. The " +
                     "default value is 20. ")
        int sling_max_record_requests() default RequestInfoProviderImpl.STORED_REQUESTS_COUNT;

        @AttributeDefinition(name = "Recorded Request Path Patterns",
                description = "One or more regular expressions which " +
                            "limit the requests which are stored by the \"Recent Requests\" Web Console page.")
        String[] sling_store_pattern_requests();

        @AttributeDefinition(name = "Server Info",
                description = "The server info returned by Sling. If this field is left empty, Sling generates a default into.")
        String sling_serverinfo();

        @AttributeDefinition(name = "Additional response headers",
                description = "Provides mappings for additional response headers "
                    + "Each entry is of the form 'bundleId [ \":\" responseHeaderName ] \"=\" responseHeaderValue'")
        String[] sling_additional_response_headers() default {"X-Content-Type-Options=nosniff", "X-Frame-Options=SAMEORIGIN"};

        @AttributeDefinition(name = "Servlet Name", description = "Optional name for the Sling main servlet registered by this component")
        String servlet_name();
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    private volatile RequestListenerManager requestListenerManager;

    private BundleContext bundleContext;

    /** default log */
    private final Logger log = LoggerFactory.getLogger(SlingMainServlet.class);

    /**
     * The product info provider
     */
    @Reference
    private ProductInfoProvider productInfoProvider;

    /**
     * The Sling http context
     */
    @Reference(target = SlingServletContext.TARGET)
    private ServletContext slingServletContext;

    @Reference
    private SlingRequestProcessorImpl requestProcessorImpl;

    private volatile boolean allowTrace;

    private volatile ServiceRegistration<Servlet> servletRegistration;

    // ---------- Servlet API -------------------------------------------------

    @Override
    public void service(ServletRequest req, ServletResponse res)
            throws ServletException {
        if (req instanceof HttpServletRequest
            && res instanceof HttpServletResponse) {

            HttpServletRequest request = (HttpServletRequest) req;

            // set the thread name according to the request
            String threadName = setThreadName(request);

            final RequestListenerManager localRLM = requestListenerManager;
            if (localRLM != null) {
                localRLM.sendEvent(request, SlingRequestEvent.EventType.EVENT_INIT);
            }

            ResourceResolver resolver = null;
            try {
                if (!allowTrace && "TRACE".equals(request.getMethod())) {
                    HttpServletResponse response = (HttpServletResponse) res;
                    response.setStatus(405);
                    response.setHeader("Allow", "GET, HEAD, POST, PUT, DELETE, OPTIONS");
                    return;
                }

                // get ResourceResolver (set by AuthenticationSupport)
                Object resolverObject = request.getAttribute(AuthenticationSupport.REQUEST_ATTRIBUTE_RESOLVER);
                resolver = (resolverObject instanceof ResourceResolver)
                        ? (ResourceResolver) resolverObject
                        : null;

                // real request handling for HTTP requests
                requestProcessorImpl.doProcessRequest(request, (HttpServletResponse) res,
                    resolver);

            } catch (ClientAbortException cae) {
                log.debug("service: ClientAbortException, probable cause is client aborted request or network problem", cae);

            } catch (Throwable t) {

                // some failure while handling the request, log the issue
                // and terminate. We do not call error handling here, because
                // we assume the real request handling would have done this.
                // So here we just log

                log.error("service: Uncaught Problem handling the request", t);

            } finally {


                // close the resource resolver (not relying on servlet request
                // listener to do this for now; see SLING-1270)
                if (resolver != null) {
                    resolver.close();
                }

                if (localRLM != null) {
                    localRLM.sendEvent(request, SlingRequestEvent.EventType.EVENT_DESTROY);
                }

                // reset the thread name
                if (threadName != null) {
                    Thread.currentThread().setName(threadName);
                }
            }

        } else {
            throw new ServletException(
                "Apache Sling must be run in an HTTP servlet environment.");
        }
    }

    // ---------- Property Setter for SCR --------------------------------------

    @Modified
    protected void modified(final Config config) {
        setup(config);
    }

    private Dictionary<String, Object> getServletContextRegistrationProps(final String servletName) {
        final Dictionary<String, Object> servletConfig = new Hashtable<>();
        servletConfig.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT,
                "(" + HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME + "=" + SlingHttpContext.SERVLET_CONTEXT_NAME + ")");
        servletConfig.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/");
        servletConfig.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME, servletName);
        servletConfig.put(Constants.SERVICE_DESCRIPTION, "Apache Sling Engine Main Servlet");
        servletConfig.put(Constants.SERVICE_VENDOR, "The Apache Software Foundation");

        return servletConfig;
    }

    protected void setup(final Config config) {
        // configure method filter
        this.allowTrace = config.sling_trace_allow();

        String servletName = config.servlet_name();
        if (servletName == null || servletName.isEmpty()) {
            servletName = this.productInfoProvider.getProductInfo();
        }
        if (this.servletRegistration == null) {
            this.servletRegistration = bundleContext.registerService(Servlet.class, this,
                    getServletContextRegistrationProps(servletName));
        } else {
            // check if the servlet name has changed and update properties
            if (!servletName.equals(this.servletRegistration.getReference()
                    .getProperty(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME))) {
                this.servletRegistration.setProperties(getServletContextRegistrationProps(servletName));
            }
        }
    }

    @Activate
    protected void activate(final BundleContext bundleContext, final Config config) {
        this.bundleContext = bundleContext;
        this.setup(config);
    }

    @Override
    public void init() throws ServletException {
        log.info("{} ready to serve requests", this.slingServletContext.getServerInfo());
    }

    @Deactivate
    protected void deactivate() {
        if ( this.servletRegistration != null ) {
            this.servletRegistration.unregister();
            this.servletRegistration = null;
        }

        this.bundleContext = null;
        log.info("{} shut down", this.slingServletContext.getServerInfo());
    }

    /**
     * Sets the name of the current thread to the IP address of the remote
     * client with the current system time and the first request line consisting
     * of the method, path and protocol.
     *
     * @param request The request to extract the remote IP address, method,
     *            request URL and protocol from.
     * @return The name of the current thread before setting the new name.
     */
    private String setThreadName(HttpServletRequest request) {

        // get the name of the current thread (to be returned)
        Thread thread = Thread.currentThread();
        String oldThreadName = thread.getName();

        // construct and set the new thread name of the form:
        // 127.0.0.1 [1224156108055] GET /system/console/config HTTP/1.1
        final StringBuilder buf = new StringBuilder();
        buf.append(request.getRemoteAddr());
        buf.append(" [").append(System.currentTimeMillis()).append("] ");
        buf.append(request.getMethod()).append(' ');
        buf.append(request.getRequestURI()).append(' ');
        buf.append(request.getProtocol());
        thread.setName(buf.toString());

        // return the previous thread name
        return oldThreadName;
    }
}
