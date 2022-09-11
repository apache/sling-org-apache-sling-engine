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
package org.apache.sling.engine.impl.debug;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.engine.RequestInfo;
import org.apache.sling.engine.RequestInfoProvider;
import org.apache.sling.engine.impl.Config;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;

/**
 * Track requests.
 */
@Component(service = {RequestInfoProvider.class},
        immediate=true, // track requests from the start
        configurationPid = Config.PID)
public class RequestInfoProviderImpl implements RequestInfoProvider {

    private volatile ConcurrentNavigableMap<String, RequestInfo> requests;

    private volatile List<Pattern> patterns;

    private volatile int maxSize;

    private static volatile RequestInfoProviderImpl INSTANCE;

    @Activate
    public RequestInfoProviderImpl(final Config config) {
        update(config);
        INSTANCE = this;
    }

    @Modified
    protected void update(final Config config) {
        this.maxSize = config.sling_max_record_requests();
        if ( this.maxSize < 0 ) {
            this.maxSize = 0;
        }
        this.requests = (this.maxSize > 0) ? new ConcurrentSkipListMap<>() : null;
        final List<Pattern> compiledPatterns = new ArrayList<>();
        if (config.sling_store_pattern_requests() != null) {
            for (final String pattern : config.sling_store_pattern_requests()) {
                if (pattern != null && pattern.trim().length() > 0) {
                    compiledPatterns.add(Pattern.compile(pattern.trim()));
                }
            }
        }
        this.patterns = compiledPatterns;

    }

    @Deactivate
    protected void deactivate() {
        INSTANCE = null;
        this.requests = null;
        this.patterns = Collections.emptyList();
    }

    public static void recordRequest(final SlingHttpServletRequest r) {
        final RequestInfoProviderImpl local = INSTANCE;
        if (local != null) {
            local.addRequest(r);
        }
    }

    private void addRequest(final SlingHttpServletRequest r) {
        final ConcurrentNavigableMap<String, RequestInfo> local = requests;
        if (local != null && isEnabledFor(r.getPathInfo())) {
            final RequestInfo info = new RequestInfoImpl(r);
            synchronized (local) {
                if ( local.size() == this.maxSize ) {
                    local.remove(local.firstKey());
                }
                local.put(info.getId(), info);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return this.requests != null;
    }

    @Override
    public boolean isEnabledFor(final String path) {
        if ( this.requests != null ) {
            boolean accept = patterns.isEmpty();
            for (Pattern pattern : patterns) {
                if (pattern.matcher(path).matches()) {
                    accept = true;
                    break;
                }
            }
            return accept;
        }
        return false;
    }

    @Override
    public int getMayNumberOfInfos() {
        return this.getMaxNumberOfInfos();
    }

    @Override
    public int getMaxNumberOfInfos() {
        return this.maxSize;
    }

    @Override
    public void clear() {
        final ConcurrentNavigableMap<String, RequestInfo> local = requests;
        if (local != null) {
            local.clear();
        }
    }

    @Override
    public RequestInfo getRequestInfo(final String id) {
        final ConcurrentNavigableMap<String, RequestInfo> local = requests;
        if ( local != null ) {
            return local.get(id);
        }
        return null;
    }

    @Override
    public Iterable<RequestInfo> getRequestInfos() {
        final ConcurrentNavigableMap<String, RequestInfo> local = requests;
        if ( local != null ) {
            return local.values();
        }
        return Collections.emptyList();
    }

    private static class RequestInfoImpl implements RequestInfo {

        private static AtomicLong requestCounter = new AtomicLong(0);

        private final String id;

        private final String method;

        private final String path;

        private final String userId;

        private final String log;

        RequestInfoImpl(final SlingHttpServletRequest request) {
            this.id = String.valueOf(System.currentTimeMillis()).concat("-").concat(String.valueOf(requestCounter.incrementAndGet()));
            this.method = request.getMethod();
            this.path = request.getPathInfo() == null ? "" : request.getPathInfo();
            this.userId = request.getRemoteUser();
            String text;
            try ( final StringWriter writer = new StringWriter()) {
                final PrintWriter pw = new PrintWriter(writer);
                request.getRequestProgressTracker().dump(pw);
                pw.flush();
                text = writer.toString();
            } catch ( final IOException ioe) {
                text = "";
            }
            this.log = text;
        }

        @Override
        public @NotNull String getId() {
            return this.id;
        }

        @Override
        public @NotNull String getMethod() {
            return this.method;
        }

        @Override
        public @NotNull String getPath() {
            return this.path;
        }

        @Override
        public @Nullable String getUserId() {
            return this.userId;
        }

        @Override
        public @NotNull String getLog() {
            return this.log;
        }
    }
}