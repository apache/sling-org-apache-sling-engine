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
package org.apache.sling.engine.impl.filter;

import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.Filter;

import org.apache.sling.api.SlingHttpServletRequest;

public class FilterHandle implements Comparable<FilterHandle> {

    private final Filter filter;

    private final long filterId;

    private final int order;

    private final String orderSource;

    private AtomicLong calls;

    private AtomicLong time;

    private FilterPredicate predicate;

    FilterProcessorMBeanImpl mbean;

    FilterHandle(Filter filter, FilterPredicate predicate, long filterId, int order, final String orderSource,
            FilterProcessorMBeanImpl mbean) {
        this.filter = filter;
        this.predicate = predicate;
        this.filterId = filterId;
        this.order = order;
        this.orderSource = orderSource;
        this.calls = new AtomicLong();
        this.time = new AtomicLong();
        this.mbean = mbean;
    }

    public Filter getFilter() {
        return filter;
    }

    public long getFilterId() {
        return filterId;
    }

    public int getOrder() {
        return order;
    }

    public String getOrderSource() {
        return orderSource;
    }

    boolean select(SlingHttpServletRequest slingHttpServletRequest) {
      if (predicate != null){
          return predicate.test(slingHttpServletRequest);
      }
      return true;
    }

    public long getCalls() {
        return calls.get();
    }

    public long getTime() {
        return time.get();
    }

    public long getTimePerCall() {
        return (getCalls() > 0) ? (1000L * getTime() / getCalls()) : -1;
    }

    void track() {
        calls.incrementAndGet();
    }

    void trackTime(long time) {
        this.time.addAndGet(time);
        if (mbean != null) {
            mbean.addFilterHandle(this);
        }
    }

    /**
     * Note: this class has a natural ordering that is inconsistent with
     * equals.
     */
    @Override
    public int compareTo(FilterHandle other) {
        if (this == other || equals(other)) {
            return 0;
        }

        if (order > other.order) {
            return -1;
        } else if (order < other.order) {
            return 1;
        }
        return (this.filterId < other.filterId) ? -1 : ((this.filterId == other.filterId) ? 0 : 1);
    }

    @Override
    public int hashCode() {
        if ( filter == null ) {
            return 0;
        }
        return filter.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof FilterHandle) {
            FilterHandle other = (FilterHandle) obj;
            return getFilter().equals(other.getFilter());
        }

        return false;
    }
}