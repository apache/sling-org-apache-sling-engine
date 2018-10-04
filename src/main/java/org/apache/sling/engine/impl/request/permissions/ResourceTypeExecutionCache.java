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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.sling.api.resource.observation.ExternalResourceChangeListener;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.osgi.service.component.annotations.Component;

@Component(
        service = {ResourceTypeExecutionCache.class, ResourceChangeListener.class},
        property = {
                ResourceChangeListener.PATHS + "=glob:" + ResourceTypePermissionEvaluator.RESOURCE_TYPE_PERMISSIONS + "/**/*"
        }
)
public class ResourceTypeExecutionCache implements ResourceChangeListener, ExternalResourceChangeListener {

    private Map<String, Boolean> preEvaluatedPermissions = new CachingMap(65536);
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock readLock = rwl.readLock();
    private final Lock writeLock = rwl.writeLock();

    public Boolean getCachedPermission(String key) {
        readLock.lock();
        try {
            return preEvaluatedPermissions.get(key);
        } finally {
            readLock.unlock();
        }
    }

    public void setCachedPermission(String key, Boolean value) {
        writeLock.lock();
        try {
            preEvaluatedPermissions.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void onChange(List<ResourceChange> changes) {
        writeLock.lock();
        try {
            preEvaluatedPermissions.clear();
        } finally {
            writeLock.unlock();
        }
    }

    private class CachingMap extends LinkedHashMap<String, Boolean> {

        private final int capacity;

        private CachingMap(int capacity) {
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > capacity;
        }
    }
}
