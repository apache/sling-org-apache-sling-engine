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
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.request.ResponseUtil;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.engine.RequestInfo;
import org.apache.sling.engine.RequestInfoProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.component.propertytypes.ServiceVendor;

/**
 * Felix OSGi console plugin that displays info about recent requests processed
 * by Sling. Info about all requests can be found in the logs, but this is
 * useful when testing or explaining things.
 */
@Component(service = Servlet.class,
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

    private final RequestInfoProvider infoProvider;

    @Activate
    public RequestHistoryConsolePlugin(final @Reference RequestInfoProvider provider) {
        this.infoProvider = provider;
    }

    private void printLinksTable(final PrintWriter pw, final List<RequestInfo> values, final String currentRequestIndex) {
        final List<String> links = new ArrayList<String>();
        for (final RequestInfo info : values) {
            final String key = ResponseUtil.escapeXml(info.getId());
            final boolean isCurrent = info.getId().equals(currentRequestIndex);
            final StringBuilder sb = new StringBuilder();
            sb.append("<span style='white-space: pre; text-align:right; font-size:80%'>");
            sb.append(String.format("%1$8s", key));
            sb.append("</span> ");
            sb.append("<a href='" + LABEL + "?index=" + key + "'>");
            if (isCurrent) {
                sb.append("<b>");
            }
            sb.append(ResponseUtil.escapeXml(getLabel(info)));
            if (isCurrent) {
                sb.append("</b>");
            }
            sb.append("</a> ");
            links.add(sb.toString());
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
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        // get all requests and select request to display
        final String key = req.getParameter(INDEX);
        final RequestInfo info = key == null ? null : this.infoProvider.getRequestInfo(key);
        final List<RequestInfo> values = new ArrayList<>();
        for(final RequestInfo i : this.infoProvider.getRequestInfos()) {
            values.add(i);
        }

        final PrintWriter pw = resp.getWriter();

        if (this.infoProvider.getMayNumberOfInfos() > 0) {
            pw.println("<p class='statline ui-state-highlight'>Recorded "
                    + values.size() + " requests (max: " + this.infoProvider.getMayNumberOfInfos() + ")</p>");
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
                ResponseUtil.escapeXml(info.getPath()), ResponseUtil.escapeXml(info.getUserId()));
            pw.println("</tr>");
            pw.println("</thead>");

            pw.println("<tbody>");

            // Request Progress Tracker Info
            pw.println("<tr><td><pre>");
            pw.print(ResponseUtil.escapeXml(info.getLog()));
            pw.println("</pre></td></tr>");
            pw.println("</tbody></table>");
        }
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException {
        if (req.getParameter(CLEAR) != null) {
            this.infoProvider.clear();
            resp.sendRedirect(req.getRequestURI());
        }
    }

    public String getLabel(final RequestInfo info) {
        final StringBuilder sb = new StringBuilder();

        sb.append(info.getMethod());
        sb.append(' ');

        final String path = info.getPath();
        if (path.length() > 0) {
            sb.append(ResourceUtil.getName(path));
        } else {
            sb.append('/');
        }

        return sb.toString();
    }
}