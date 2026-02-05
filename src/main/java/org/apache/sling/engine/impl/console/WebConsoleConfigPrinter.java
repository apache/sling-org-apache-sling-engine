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
package org.apache.sling.engine.impl.console;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;

import jakarta.servlet.Filter;
import org.apache.sling.engine.impl.SlingHttpContext;
import org.apache.sling.engine.impl.filter.FilterHandle;
import org.apache.sling.engine.impl.filter.ServletFilterManager;
import org.apache.sling.engine.impl.filter.ServletFilterManager.FilterChainType;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.runtime.HttpServiceRuntime;
import org.osgi.service.http.runtime.dto.ServletContextDTO;

/**
 * This is a configuration printer for the web console which
 * prints out the currently configured filter chains.
 *
 */
@Component(
        service = WebConsoleConfigPrinter.class,
        property = {
            "felix.webconsole.label=slingfilter",
            "felix.webconsole.title=Sling Servlet Filter",
            "felix.webconsole.configprinter.modes=always"
        })
public class WebConsoleConfigPrinter {

    private final BundleContext bundleContext;
    private final ServletFilterManager filterManager;
    private final HttpServiceRuntime httpServiceRuntime;

    @Activate
    public WebConsoleConfigPrinter(
            BundleContext bundleContext,
            @Reference final ServletFilterManager filterManager,
            @Reference HttpServiceRuntime httpServiceRuntime) {
        this.bundleContext = bundleContext;
        this.filterManager = filterManager;
        this.httpServiceRuntime = httpServiceRuntime;
    }

    private static boolean isRelevantContext(ServletContextDTO ctx) {
        return SlingHttpContext.SERVLET_CONTEXT_NAME.equals(ctx.name);
    }

    private String getServiceRanking(long serviceId) {
        ServiceReference<?> ref = null;
        try {
            Collection<ServiceReference<Filter>> refs = bundleContext.getServiceReferences(
                    Filter.class, "(" + Constants.SERVICE_ID + "=" + serviceId + ")");
            if (refs.isEmpty()) {
                Collection<ServiceReference<javax.servlet.Filter>> javaxRefs = bundleContext.getServiceReferences(
                        javax.servlet.Filter.class, "(" + Constants.SERVICE_ID + "=" + serviceId + ")");
                if (!javaxRefs.isEmpty()) {
                    ref = javaxRefs.iterator().next();
                }
            } else {
                ref = refs.iterator().next();
            }
        } catch (InvalidSyntaxException e) {
            throw new IllegalStateException("Invalid syntax given to lookup service", e);
        }
        if (ref != null) {
            Object ranking = ref.getProperty(Constants.SERVICE_RANKING);
            return ranking != null ? ranking.toString() : "0";
        }
        return "?";
    }

    private void printOsgiHttpWhiteboardFilters(PrintWriter pw, ServletContextDTO ctx) {
        pw.println();
        pw.printf("OSGi Http Whiteboard Filters for Context %s:%n", ctx.name);
        Arrays.stream(ctx.filterDTOs).forEach(filter -> {
            pw.printf("%s : %s (id: %d)%n", getServiceRanking(filter.serviceId), filter.name, filter.serviceId);
        });
    }

    private void printOsgiHttpWhiteboardFilters(PrintWriter pw) {
        Arrays.stream(httpServiceRuntime.getRuntimeDTO().servletContextDTOs)
                .filter(ctx -> isRelevantContext(ctx))
                .forEach(ctx -> printOsgiHttpWhiteboardFilters(pw, ctx));
    }

    /**
     * Helper method for printing out a filter chain.
     */
    private void printFilterChain(final PrintWriter pw, final FilterHandle[] entries) {
        for (final FilterHandle entry : entries) {
            pw.printf(
                    "%d : %s (id: %d, property: %s); called: %d; time: %dms; time/call: %dµs%n",
                    entry.getOrder(),
                    entry.getWrappedJavaxFilter()
                            .map(f -> "Jakarta wrapper for " + f.getClass())
                            .orElse(entry.getFilter().getClass().toString()),
                    entry.getFilterId(),
                    entry.getOrderSource(),
                    entry.getCalls(),
                    entry.getTime(),
                    entry.getTimePerCall());
        }
    }

    /**
     * Print out the servlet filter chains.
     *
     * @param pw the writer to use.
     *
     * <p>
     * see org.apache.felix.webconsole.ConfigurationPrinter#printConfiguration(java.io.PrintWriter)
     */
    public void printConfiguration(PrintWriter pw) {
        pw.println("Current Apache Sling Servlet Filter Configuration");
        printOsgiHttpWhiteboardFilters(pw);
        for (FilterChainType type : FilterChainType.values()) {
            pw.println();
            pw.println("Sling " + type + " Filters:");
            printFilterChain(pw, filterManager.getFilterChain(type).getFilters());
        }
    }
}
