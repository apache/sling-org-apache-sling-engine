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

import java.util.Arrays;
import java.util.Optional;

import org.apache.sling.api.SlingException;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.wrappers.SlingHttpServletResponseWrapper;
import org.apache.sling.engine.impl.request.RequestData;
import org.jetbrains.annotations.NotNull;

public class IncludeNoContentTypeOverrideResponseWrapper extends SlingHttpServletResponseWrapper {

    private final RequestData requestData;

    /**
     * Wraps a response object and throws a {@link RuntimeException} if {@link #setContentType(String)} is called with
     * a value that would override the response's previously set value.
     *
     * @param requestData     the request data object
     * @param wrappedResponse the response to be wrapped
     */
    public IncludeNoContentTypeOverrideResponseWrapper(@NotNull RequestData requestData,
                                                       @NotNull SlingHttpServletResponse wrappedResponse) {
        super(wrappedResponse);
        this.requestData = requestData;
    }

    @Override
    public void setContentType(String type) {
        String contentTypeString = getContentType();
        if (contentTypeString != null) {
            if (type == null) {
                String message = getMessage(contentTypeString, "null");
                requestData.getRequestProgressTracker().log("ERROR: " + message);
                throw new ContentTypeChangeException(message);
            }
            Optional<String> currentMime = Arrays.stream(contentTypeString.split(";")).findFirst();
            Optional<String> setMime = Arrays.stream(type.split(";")).findFirst();
            if (currentMime.isPresent() && setMime.isPresent() && !currentMime.get().equals(setMime.get())) {
                String message = getMessage(contentTypeString, type);
                requestData.getRequestProgressTracker().log("ERROR: " + message);
                throw new ContentTypeChangeException(message);
            }
            getResponse().setContentType(type);
        }
    }

    private String getMessage(String currentContentType, String setContentType) {
        return String.format(
                "Servlet %s tried to override the 'Content-Type' header from '%s' to '%s', however the" +
                        " %s forbids this via the %s configuration property.",
                requestData.getActiveServletName(),
                currentContentType,
                setContentType,
                Config.PID,
                "sling.includes.checkcontenttype"
        );
    }

    private static class ContentTypeChangeException extends SlingException {
        protected ContentTypeChangeException(String text) {
            super(text);
        }
    }
}
