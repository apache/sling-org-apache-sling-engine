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
package org.apache.sling.engine.impl;

import org.apache.sling.api.SlingException;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

public class IncludeNoContentTypeOverrideResponseWrapper extends IncludeResponseWrapper {

    private final RequestProgressTracker requestProgressTracker;
    private final String activeServletName;

    /**
     * Wraps a response object and throws a {@link RuntimeException} if {@link #setContentType(String)} is called with
     * a value that would override the response's previously set value.
     *
     * @param requestProgressTracker the {@code RequestProgressTracker} used to log when an override of the {@code
     *                               Content-Type} header is detected
     * @param activeServletName      the name of the active servlet, used for logging
     * @param wrappedResponse        the response to be wrapped
     */
    public IncludeNoContentTypeOverrideResponseWrapper(@NotNull RequestProgressTracker requestProgressTracker,
                                                       @NotNull String activeServletName,
                                                       @NotNull SlingHttpServletResponse wrappedResponse) {
        super(wrappedResponse);
        this.requestProgressTracker = requestProgressTracker;
        this.activeServletName = activeServletName;
    }

    @Override
    public void setContentType(String type) {
        String contentTypeString = getContentType();
        if (contentTypeString != null) {
            Optional<String> currentMime = Arrays.stream(contentTypeString.split(";")).findFirst();
            Optional<String> setMime = Arrays.stream(type.split(";")).findFirst();
            if (currentMime.isPresent() && setMime.isPresent() && !currentMime.get().equals(setMime.get())) {
                String message = String.format(
                        "Servlet %s tried to override the 'Content-Type' header from '%s' to '%s', however the %s forbids this via the %s configuration property.",
                        activeServletName,
                        currentMime.get(),
                        setMime.get(),
                        Config.PID,
                        "sling.includes.checkcontenttype"
                );
                requestProgressTracker.log(message);
                throw new ContentTypeChangeException(message);
            }
        }
    }

    private static class ContentTypeChangeException extends SlingException {
        protected ContentTypeChangeException(String text) {
            super(text);
        }
    }
}
