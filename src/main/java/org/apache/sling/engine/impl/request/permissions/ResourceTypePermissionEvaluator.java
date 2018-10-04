/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.engine.impl.request.permissions;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.SlingRequestEvent;
import org.apache.sling.api.request.SlingRequestListener;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = {ResourceTypePermissionEvaluator.class, SlingRequestListener.class}
)
public class ResourceTypePermissionEvaluator implements SlingRequestListener {

    static final String RESOURCE_TYPE_PERMISSIONS = "/sling/permissions/sling:resourceType";
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceTypePermissionEvaluator.class);
    private boolean shortCircuit = false;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private ResourceTypeExecutionCache resourceTypeExecutionCache;

    private final ThreadLocalResourceResolver threadLocalResourceResolver = new ThreadLocalResourceResolver();

    public boolean canExecute(SlingHttpServletRequest request) {
        if (!shortCircuit) {
            final Resource accessedResource = request.getResource();
            final String resourceType = accessedResource.getResourceType();
            final String userId = request.getResourceResolver().getUserID();
            if (userId != null) {
                String permissionPath = resourceType.startsWith("/") ? RESOURCE_TYPE_PERMISSIONS + resourceType :
                        RESOURCE_TYPE_PERMISSIONS + "/" + resourceType + "/http." + request.getMethod();
                while (!RESOURCE_TYPE_PERMISSIONS.equals(permissionPath)) {
                    String cacheKey = userId + ":" + permissionPath;
                    Boolean cached = resourceTypeExecutionCache.getCachedPermission(cacheKey);
                    if (cached != null) {
                        return cached;
                    }
                    Resource permission = accessedResource.getResourceResolver().getResource(permissionPath);
                    if (permission != null) {
                        resourceTypeExecutionCache.setCachedPermission(cacheKey, true);
                        return true;
                    } else {
                        ResourceResolver elevatedResourceResolver = threadLocalResourceResolver.get();
                        if (elevatedResourceResolver != null) {
                            permission = elevatedResourceResolver.getResource(permissionPath);
                            if (permission != null) {
                                resourceTypeExecutionCache.setCachedPermission(cacheKey, false);
                                return false;
                            }
                        }
                    }
                    permissionPath = ResourceUtil.getParent(permissionPath);
                }
                resourceTypeExecutionCache.setCachedPermission(userId + ":" + (resourceType.startsWith("/") ?
                        RESOURCE_TYPE_PERMISSIONS + resourceType :
                        RESOURCE_TYPE_PERMISSIONS + "/" + resourceType + "/http." + request.getMethod()), true);
                return true;
            }
        }
        return true;
    }

    @Override
    public void onEvent(SlingRequestEvent event) {
        if (!shortCircuit) {
            synchronized (this) {
                if (event.getType() == SlingRequestEvent.EventType.EVENT_INIT) {
                    threadLocalResourceResolver.set(getServiceResourceResolver());
                } else if (event.getType() == SlingRequestEvent.EventType.EVENT_DESTROY) {
                    final ResourceResolver resolver = threadLocalResourceResolver.get();
                    if (resolver != null) {
                        threadLocalResourceResolver.remove();
                        resolver.close();
                    }
                }
            }
        }
    }

    private class ThreadLocalResourceResolver extends ThreadLocal<ResourceResolver> {

        @Override
        protected ResourceResolver initialValue() {
            return getServiceResourceResolver();
        }
    }

    private ResourceResolver getServiceResourceResolver() {
        try {
            return resourceResolverFactory.getServiceResourceResolver(null);
        } catch (LoginException e) {
            LOGGER.warn("Cannot obtain a privileged resource resolver. sling:resourceType permission evaluation will not be applied.", e);
            shortCircuit = true;
        }
        return null;
    }


}
