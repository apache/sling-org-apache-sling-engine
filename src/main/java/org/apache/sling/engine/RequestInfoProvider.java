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

/**
  * This service can be used to gather information about requests processed by the
  * engine.
  *
  * @since 2.5
  */
 public interface RequestInfoProvider {

    /**
     * Get the request info for the id
     * @param id The id
     * @return The request info or {@code null}
     */
    RequestInfo getRequestInfo(String id);

    /**
     * Get the request infos
     * @return An iterator for the request infos
     */
    Iterable<RequestInfo> getRequestInfos();

    /**
     * Get the maximum number of provided infos
     * @return The maximum number, {@code 0} if no infos are recorded
     */
    int getMayNumberOfInfos();

    /**
     * Clear all request infos
     */
    void clear();
 }