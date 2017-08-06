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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ResultRole;
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
        Set<String> parameterNames = getParameterNames(results);

        List<List<String>> table = new ArrayList<>();
        for (RunResult run : results) {

            List<Result> actualResults = new ArrayList<>();
            actualResults.add(run.getPrimaryResult());
            actualResults.addAll(run.getSecondaryResults()
                    .entrySet()
                    .stream()
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList()));

            for (Result actualResult : actualResults) {
                List<String> row = new ArrayList<>();

                // Current results has information about outcomes, but not about test parameters
                Result currentResult = actualResult;

                // Test Name
                row.add(run.getPrimaryResult()
                        .getLabel());

                // Parameter Values
                for (String pName : parameterNames) {
                    String paramValue = run.getParams()
                            .getParam(pName);
                    row.add(paramValue);
                }

                // Run "mode" (single shot, throughput, ...)
                row.add(run.getParams()
                        .getMode()
                        .shortLabel());

                // Metric Name
                String metricName;
                if (currentResult.getRole()
                        .equals(ResultRole.PRIMARY)) {
                    metricName = run.getParams()
                            .getMode()
                            .longLabel();
                } else {
                    metricName = trimPunctuation(currentResult.getLabel());
                }
                row.add(metricName);

                // Number of iterations used for measurement
                row.add(String.valueOf(currentResult.getSampleCount()));

                // Aggregation policy (sum, min, max, ...)
                row.add(getPolicyOf(currentResult).toString());

                // Value of the metric
                row.add(String.valueOf(currentResult.getScore()));

                // Margin of error
                row.add(String.valueOf(doubleToString(currentResult.getScoreError())));

                // Units of the metric
                row.add(currentResult.getScoreUnit());

                // Print the row
                table.add(row);
            }
        }

        try {
            List<String> header = new ArrayList<>();
            header.add("Test");
            header.addAll(parameterNames);
            header.add("Benchmark Mode");
            header.add("Metric");
            header.add("Sample Size");
            header.add("Statistic Type");
            header.add("Statistic Value");
            header.add("Statistical Margin of Error");
            header.add("Units");

            printer.printRecord(header);
            printer.printRecords(table);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private AggregationPolicy getPolicyOf(Result currentResult) {
        return (AggregationPolicy) getFieldContents(currentResult, "policy");
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

    // NaN messes up with I want to do with my spreadsheet.
    // Use zero in place of NaN.
    String doubleToString(Double value) {
        return value.isNaN() ? "0" : String.valueOf(value);
    }

    Object getFieldContents(Object object, String fieldName) {
        Class myClass = object.getClass();
        Field myField = null;
        try {
            myField = getField(myClass, fieldName);
            myField.setAccessible(true); //required if field is not normally accessible
            return myField.get(object);
        } catch (NoSuchFieldException | IllegalAccessException e) {
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



