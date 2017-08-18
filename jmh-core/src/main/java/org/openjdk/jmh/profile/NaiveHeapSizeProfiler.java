/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.jmh.profile;

import static java.lang.Runtime.getRuntime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

public class NaiveHeapSizeProfiler implements InternalProfiler {

    static final int SAMPLING_PERIOD = 100;

    List<Long> heapSizes;

    ScheduledExecutorService executor;

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        executor = Executors.newScheduledThreadPool(1);
        heapSizes = new ArrayList<>(1024);
        Runnable sampler = () -> heapSizes.add(getMemoryInUse());
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(sampler,
                10,
                SAMPLING_PERIOD,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams,
            IterationParams iterationParams, IterationResult result) {

        executor.shutdownNow();
        Collection<Result> results = new ArrayList<>();
        for (Long l : heapSizes) {
            results.add(new ScalarResult("Heap-Avg", l, "B", AggregationPolicy.AVG));
            results.add(new ScalarResult("Heap-Max", l, "B", AggregationPolicy.MAX));
        }
        return results;
    }

    @Override
    public String getDescription() {
        return "Naive heap average";
    }

    public long getMemoryInUse() {
        return getRuntime().totalMemory() - getRuntime().freeMemory();

    }
}
