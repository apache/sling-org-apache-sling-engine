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
package org.apache.sling.engine.impl.adapter;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.sling.api.servlets.ErrorHandler;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

@Component
public class EngineErrorHandlerTracker {

    private final Map<Long, ServiceRegistration<org.apache.sling.api.servlets.ErrorHandler>> errorHandlers = new ConcurrentHashMap<>();

    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.MULTIPLE)
    public void bindErrorHandler(final org.apache.sling.engine.servlets.ErrorHandler errorHandler,
            final ServiceReference<org.apache.sling.engine.servlets.ErrorHandler> reference) {
        final long id = (long) reference.getProperty(Constants.SERVICE_ID);
        final Dictionary<String, Object> properties = new Hashtable<>();
        if ( reference.getProperty(Constants.SERVICE_RANKING) != null ) {
            properties.put(Constants.SERVICE_RANKING, reference.getProperty(Constants.SERVICE_RANKING));
        }
        errorHandlers.put(id, reference
            .getBundle()
            .getBundleContext()
            .registerService(ErrorHandler.class, errorHandler, properties));
    }

    public void unbindErrorHandler(final org.apache.sling.engine.servlets.ErrorHandler errorHandler, final ServiceRegistration<org.apache.sling.engine.servlets.ErrorHandler> registration) {
        final long id = (long) registration.getReference().getProperty(Constants.SERVICE_ID);
        final ServiceRegistration<ErrorHandler> reg = errorHandlers.remove(id);
        if ( reg != null ) {
            try {
                reg.unregister();
            } catch ( final IllegalStateException ise) {
                // ignore
            }
        }
    }
}
