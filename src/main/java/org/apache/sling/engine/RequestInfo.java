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
package org.apache.sling.engine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information about a single request.
 * @see RequestInfoProvider
 * @since 2.5
 */
public interface RequestInfo {
    
    /**
     * Get the unique id for the request
     * @return The id
     */
    @NotNull String getId();

    /**
     * Get the request method
     * @return The request method
     */
    @NotNull String getMethod();

    /**
     * Get the requested path
     * @return The path
     */
    @NotNull String getPath();

    /**
     * Get the user id for the request
     * @return the user id or {@code null}
     */
    @Nullable String getUserId();

    /**
     * Get the log for the request
     * @return The request log, multi-line output
     */
    @NotNull String getLog();
}
