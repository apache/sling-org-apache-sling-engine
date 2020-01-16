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
package org.apache.sling.engine.impl.helper;

import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.sling.api.request.SlingRequestEvent;
import org.apache.sling.api.request.SlingRequestListener;
import org.apache.sling.engine.impl.SlingMainServlet;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.component.propertytypes.ServiceVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = RequestListenerManager.class)
@ServiceDescription("Request listener manager")
@ServiceVendor("The Apache Software Foundation")
public class RequestListenerManager  {

    private final ServletContext servletContext;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, fieldOption = FieldOption.REPLACE)
    private volatile List<SlingRequestListener> listeners;

    @Activate
    public RequestListenerManager(@Reference(target = "(name=" + SlingMainServlet.SERVLET_CONTEXT_NAME
            + ")") final ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	public void sendEvent ( final HttpServletRequest request,
	        final SlingRequestEvent.EventType type) {
        final List<SlingRequestListener> local = listeners;
        if (local != null && !local.isEmpty()) {
		    final SlingRequestEvent event = new SlingRequestEvent(this.servletContext, request, type);
            for (final SlingRequestListener service : local) {
                try {
                    service.onEvent(event);
                } catch (final Throwable t) {
                    logger.error("Error invoking sling request listener " + service + " : " + t.getMessage(), t);
                }
			}
		}
	}
}
