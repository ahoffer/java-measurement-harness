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
package org.openjdk.jmh.results.format;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;

public class NormalizedFormat implements ResultFormat {

    private final CSVPrinter printer;

    public NormalizedFormat(PrintStream out) {
        try {
            printer = new CSVPrinter(out, CSVFormat.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeOut(Collection<RunResult> results) {

        List<String> names = results.stream()
                .map(x -> x.getPrimaryResult()
                        .getLabel())
                .collect(Collectors.toList());

        List<Result> primaryResults = results.stream()
                .map(x -> x.getPrimaryResult())
                .collect(Collectors.toList());

        List<Long> sampleCounts = results.stream()
                .map(x -> x.getPrimaryResult()
                        .getSampleCount())
                .collect(Collectors.toList());

        List<Double> scores = results.stream()
                .map(x -> x.getPrimaryResult()
                        .getScore())
                .collect(Collectors.toList());

        List<Double> errors = results.stream()
                .map(x -> x.getPrimaryResult()
                        .getScoreError())
                .collect(Collectors.toList());

        List<String> units = results.stream()
                .map(x -> x.getPrimaryResult()
                        .getScoreUnit())
                .collect(Collectors.toList());

        Set<String> parameterNames = getParameterNames(results);
        List<List<String>> parameterValues = parameterNames.stream()
                .map(pName -> results.stream()
                        .map(runResult -> runResult.getParams()
                                .getParam(pName))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());

        List<Map<String, Result>> secondaries = results.stream()
                .map(x -> x.getSecondaryResults())
                .collect(Collectors.toList());

        List<Result> secondaryResults = results.stream()
                .map(x -> x.getSecondaryResults()
                        .entrySet())
                .flatMap(s -> s.stream())
                .collect(Collectors.mapping(m -> m.getValue(), Collectors.toList()));

        Set<String> secondaryResultNames = secondaryResults.stream()
                .collect(Collectors.mapping(Result::getLabel,
                        Collectors.toCollection(TreeSet::new)));

        List<Result> aggregatesResults = Stream.concat(primaryResults.stream(),
                secondaryResults.stream())
                .collect(Collectors.toList());

        List<BenchmarkParams> benchmarkParameters = results.stream()
                .map(x -> x.getParams())
                .collect(Collectors.toList());

        List<List<String>> table = new ArrayList<>();
        int primaryResultsIdx = 0;
        for (int resultIdx = 0; resultIdx < aggregatesResults.size(); ++resultIdx) {
            Result result = aggregatesResults.get(resultIdx);
            //            for (int paramIdx = 0; paramIdx < parameterNames.size(); ++paramIdx) {
            List<String> row = new ArrayList<>();

            // Map Result index to Run Result index
            int rrIdx = resultIdx % results.size();

            // Test Name
            row.add(names.get(rrIdx));

            // Parameter Values
            for (List<String> v : parameterValues) {
                row.add(v.get(rrIdx));
            }

            // Metric's label
            //            if (resultIdx == primaryResultsIdx) {
            //                row.add("PRIMARY");
            //            } else {
            row.add(trimPunctuation(result.getLabel()));
            //            }

            // Number of iterations used for measurement
            row.add(String.valueOf(result.getSampleCount()));

            // Aggregation policy (sum, min, max, ...)
            row.add(((AggregationPolicy) getFieldContents(result, "policy")).toString());

            // Value of the metric
            row.add(String.valueOf(result.getScore()));

            // Margin of error
            row.add(String.valueOf(result.getScoreError()));

            // Units of the metric
            row.add(result.getScoreUnit());

            // Print the row
            table.add(row);
        }

        try {
            List<String> header = new ArrayList<>();
            header.add("Test");
            header.addAll(parameterNames);
            header.add("Metric");
            header.add("Samples");
            header.add("Statistic Type");
            header.add("Statistic Value");
            header.add("Statistics Margin of Margin");
            header.add("Unit");

            printer.printRecord(header);
            printer.printRecords(table);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    Set<String> getParameterNames(Collection<RunResult> results) {
        // Use sorted set for parameter values line up with the parameter name in the header.
        return results.stream()
                .map(x -> x.getParams()
                        .getParamsKeys())
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public String trimPunctuation(String input) {
        String goodChars = "^[^a-zA-Z]+";
        return input.replaceFirst(goodChars, "")
                .replaceAll(goodChars, "");

    }

    Object getFieldContents(Object object, String fieldName) {
        Class myClass = object.getClass();
        Field myField = null;
        try {
            myField = getField(myClass, fieldName);
            myField.setAccessible(true); //required if field is not normally accessible
            return myField.get(object);
        } catch (NoSuchFieldException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    Field getField(Class clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw e;
            } else {
                return getField(superClass, fieldName);
            }
        }
    }
}



