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

import org.apache.sling.engine.impl.filter.FilterHandle;
import org.apache.sling.engine.impl.filter.ServletFilterManager;
import org.apache.sling.engine.impl.filter.ServletFilterManager.FilterChainType;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

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

    private final ServletFilterManager filterManager;

    @Activate
    public WebConsoleConfigPrinter(@Reference final ServletFilterManager filterManager) {
        this.filterManager = filterManager;
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
        for (FilterChainType type : FilterChainType.values()) {
            pw.println();
            pw.println(type + " Filters:");
            printFilterChain(pw, filterManager.getFilterChain(type).getFilters());
        }
    }
}
