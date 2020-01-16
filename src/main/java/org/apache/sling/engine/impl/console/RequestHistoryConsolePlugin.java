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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.request.ResponseUtil;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.engine.impl.SlingMainServlet;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.component.propertytypes.ServiceVendor;

/**
 * Felix OSGi console plugin that displays info about recent requests processed
 * by Sling. Info about all requests can be found in the logs, but this is
 * useful when testing or explaining things.
 */
@Component(service = Servlet.class,
    configurationPid = SlingMainServlet.PID,
    property = {
            "felix.webconsole.label=" + RequestHistoryConsolePlugin.LABEL,
            "felix.webconsole.title=Recent requests",
            "felix.webconsole.category=Sling"
    })
@ServiceDescription("Web Console Plugin to display information about recent Sling requests")
@ServiceVendor("The Apache Software Foundation")
public class RequestHistoryConsolePlugin extends HttpServlet {

    private static final long serialVersionUID = -5738101314957623511L;

    public static final String LABEL = "requests";

    public static final String INDEX = "index";

    public static final String CLEAR = "clear";

    private static volatile RequestHistoryConsolePlugin instance;

    public static final int STORED_REQUESTS_COUNT = 20;

    private volatile RequestInfoMap requests;

    private volatile List<Pattern> storePatterns = Collections.emptyList();

    @Activate
    public RequestHistoryConsolePlugin(final SlingMainServlet.Config config) {
        update(config);
        instance = this;
    }


    @Modified
    protected void update(final SlingMainServlet.Config config) {
        this.requests = (config.sling_max_record_requests() > 0)
                ? new RequestInfoMap(config.sling_max_record_requests())
                : null;
        final List<Pattern> compiledPatterns = new ArrayList<>();
        if (config.sling_store_pattern_requests() != null) {
            for (String pattern : config.sling_store_pattern_requests()) {
                if (pattern != null && pattern.trim().length() > 0) {
                    compiledPatterns.add(Pattern.compile(pattern));
                }
            }
        }
        this.storePatterns = compiledPatterns;

    }

    @Deactivate
    protected void deactivate() {
        instance = null;
        clear();
    }

    public static void recordRequest(final SlingHttpServletRequest r) {
        final RequestHistoryConsolePlugin local = instance;
        if (local != null) {
            local.addRequest(r);
        }
    }

    private void addRequest(SlingHttpServletRequest r) {
        final RequestInfoMap local = requests;
        if (local != null) {
            String requestPath = r.getPathInfo();
            boolean accept = true;
            final List<Pattern> patterns = storePatterns;
            if (!patterns.isEmpty()) {
                accept = false;
                for (Pattern pattern : patterns) {
                    if (pattern.matcher(requestPath).matches()) {
                        accept = true;
                        break;
                    }
                }
            }

            if (accept) {
                RequestInfo info = new RequestInfo(r);
                synchronized (local) {
                    local.put(info.getKey(), info);
                }
            }
        }
    }

    private void clear() {
        final RequestInfoMap local = requests;
        if (local != null) {
            local.clear();
        }
    }

    private void printLinksTable(PrintWriter pw, List<RequestInfo> values, String currentRequestIndex) {
        final List<String> links = new ArrayList<String>();
        if (values != null) {
            for (RequestInfo info : values) {
                final String key = ResponseUtil.escapeXml(info.getKey());
                final boolean isCurrent = info.getKey().equals(currentRequestIndex);
                final StringBuilder sb = new StringBuilder();
                sb.append("<span style='white-space: pre; text-align:right; font-size:80%'>");
                sb.append(String.format("%1$8s", key));
                sb.append("</span> ");
                sb.append("<a href='" + LABEL + "?index=" + key + "'>");
                if (isCurrent) {
                    sb.append("<b>");
                }
                sb.append(ResponseUtil.escapeXml(info.getLabel()));
                if (isCurrent) {
                    sb.append("</b>");
                }
                sb.append("</a> ");
                links.add(sb.toString());
            }
        }

        final int nCols = 5;
        while ((links.size() % nCols) != 0) {
            links.add("&nbsp;");
        }

        pw.println("<table class='nicetable ui-widget'>");
        pw.println("<tr>\n");
        if (values.isEmpty()) {
            pw.print("No Requests recorded");
        } else {
            int i = 0;
            for (String str : links) {
                if ((i++ % nCols) == 0) {
                    pw.println("</tr>");
                    pw.println("<tr>");
                }
                pw.print("<td>");
                pw.print(str);
                pw.println("</td>");
            }
        }
        pw.println("</tr>");

        pw.println("</table>");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        final RequestInfoMap local = requests;

        // get all requests and select request to display
        final String key = req.getParameter(INDEX);
        final List<RequestInfo> values;
        RequestInfo info = null;
        if (local != null) {
            synchronized (local) {
                values = new ArrayList<>(local.values());
                if (key != null) {
                    info = local.get(key);
                }
            }
        } else {
            values = null;
        }

        final PrintWriter pw = resp.getWriter();

        if (local != null) {
            pw.println("<p class='statline ui-state-highlight'>Recorded "
                    + values.size() + " requests (max: " + local.getMaxSize() + ")</p>");
        } else {
            pw.println("<p class='statline ui-state-highlight'>Request Recording disabled</p>");
        }

        pw.println("<div class='ui-widget-header ui-corner-top buttonGroup'>");
        pw.println("<span style='float: left; margin-left: 1em'>Recent Requests</span>");
        pw.println("<form method='POST'><input type='hidden' name='clear' value='clear'><input type='submit' value='Clear' class='ui-state-default ui-corner-all'></form>");
        pw.println("</div>");

        printLinksTable(pw, values, key);
        pw.println("<br/>");

        if (info != null) {

            pw.println("<table class='nicetable ui-widget'>");

            // Links to other requests
            pw.println("<thead>");
            pw.println("<tr>");
            pw.printf(
                "<th class='ui-widget-header'>Request %s (%s %s) by %s - RequestProgressTracker Info</th>%n",
                key, ResponseUtil.escapeXml(info.getMethod()),
                ResponseUtil.escapeXml(info.getPathInfo()), ResponseUtil.escapeXml(info.getUser()));
            pw.println("</tr>");
            pw.println("</thead>");

            pw.println("<tbody>");

            // Request Progress Tracker Info
            pw.println("<tr><td>");
            final Iterator<String> it = info.getTracker().getMessages();
            pw.print("<pre>");
            while (it.hasNext()) {
                pw.print(ResponseUtil.escapeXml(it.next()));
            }
            pw.println("</pre></td></tr>");
            pw.println("</tbody></table>");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (req.getParameter(CLEAR) != null) {
            clear();
            resp.sendRedirect(req.getRequestURI());
        }
    }

    private static class RequestInfo {

        private static AtomicLong requestCounter = new AtomicLong(0);

        private final String key;

        private final String method;

        private final String pathInfo;

        private final String user;

        private final RequestProgressTracker tracker;

        RequestInfo(SlingHttpServletRequest request) {
            this.key = String.valueOf(requestCounter.incrementAndGet());
            this.method = request.getMethod();
            this.pathInfo = request.getPathInfo();
            this.user = request.getRemoteUser();
            this.tracker = request.getRequestProgressTracker();
        }

        public String getKey() {
            return key;
        }

        public String getMethod() {
            return method;
        }

        public String getPathInfo() {
            return pathInfo;
        }

        public String getUser() {
            return user;
        }

        public String getLabel() {
            final StringBuilder sb = new StringBuilder();

            sb.append(getMethod());
            sb.append(' ');

            final String path = getPathInfo();
            if (path != null && path.length() > 0) {
                sb.append(ResourceUtil.getName(getPathInfo()));
            } else {
                sb.append('/');
            }

            return sb.toString();
        }

        public RequestProgressTracker getTracker() {
            return tracker;
        }
    }

    private static class RequestInfoMap extends LinkedHashMap<String, RequestInfo> {

        private static final long serialVersionUID = 4120391774146501524L;

        private int maxSize;

        RequestInfoMap(int maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, RequestInfo> eldest) {
            return size() > maxSize;
        }

        public int getMaxSize() {
            return maxSize;
        }
    }
}