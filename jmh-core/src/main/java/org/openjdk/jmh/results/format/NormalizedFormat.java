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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
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
        if (results.isEmpty()) {
            return;
        }

        List<List<String>> table = new ArrayList<>();

        Set<String> allSecondaryMetricNames = results.stream()
                .map(x -> x.getSecondaryResults()
                        .keySet())
                .flatMap(Set::stream)
                .map(Outcome::trimPunctuation)
                .collect(Collectors.toSet());
        Collection<String> parameterNames = null;
        for (RunResult run : results) {

            //Primary results
            Outcome outcome = Outcome.from(run, run.getPrimaryResult());
            table.add(outcome.asRow());

            // Any RunResult will provide the parameter names.
            // Because all RunResults should have the parameters.
            parameterNames = outcome.getParameterNames();

            //Secondary Results
            Set<String> writtenSecondaryMetrics = new HashSet<>();
            run.getSecondaryResults()
                    .forEach((metricName, result) -> {
                        Outcome o = (Outcome.from(run, result));
                        table.add(o.asRow());
                        writtenSecondaryMetrics.add(o.getMetricName());
                    });

            //Missing results
            HashSet<String> missingMetrics = new HashSet<>(allSecondaryMetricNames);
            missingMetrics.removeAll(writtenSecondaryMetrics);
            missingMetrics.forEach(metric -> {
                Outcome o = MissingOutcome.from(run, metric);
                table.add(o.asRow());
            });
        }

        List<String> header = new ArrayList<>();
        header.add("Test");
        header.addAll(parameterNames);
        header.add("Metric");
        header.add("Sample Size");
        header.add("Statistic Type");
        header.add("Statistic Value");
        header.add("Statistical Margin of Error");
        header.add("Units");

        try {
            printer.printRecord(header);
            printer.printRecords(table);

            // DEBUG PRINT
            // table.forEach(r -> System.err.println(r.stream()
            // .collect(Collectors.joining(", "))));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Outcome {

        protected RunResult run;

        protected Result result;

        private Outcome(RunResult run, Result result) {
            this.run = run;
            this.result = result;
        }

        public static Outcome from(RunResult run, Result result) {
            return new Outcome(run, result);
        }

        public static String trimPunctuation(String input) {
            String goodChars = "^[^a-zA-Z]+";
            return input.replaceFirst(goodChars, "")
                    .replaceAll(goodChars, "");
        }

        public String getMarginOfError() {
            return doubleToString(result.getScoreError());
        }

        public String getMetricName() {
            return isPrimary() ?
                    run.getParams()
                            .getMode()
                            .longLabel() :
                    trimPunctuation(result.getLabel());
        }

        Collection<String> getParameterNames() {
            return run.getParams()
                    .getParamsKeys();
        }

        List<String> getParameterValues(Collection<String> parameterNames) {
            return parameterNames.stream()
                    .map(p -> run.getParams()
                            .getParam(p))
                    .collect(Collectors.toList());
        }

        public ResultRole getRole() {
            return result.getRole();
        }

        public String getSampleSize() {
            return String.valueOf(result.getSampleCount());
        }

        String getStatisticName() {
            // How the measurements from multiple iterations are summarized
            // as a single number.
            return getFieldContents(result, "policy").toString();
        }

        public String getStatisticValue() {
            return String.valueOf(result.getScore());
        }

        public String getTestName() {
            return run.getPrimaryResult()
                    .getLabel();
        }

        public String getUnits() {
            return result.getScoreUnit();
        }

        public boolean isPrimary() {
            return getRole().equals(ResultRole.PRIMARY);
        }

        public List<String> asRow() {
            List<String> row = new ArrayList<>();
            row.add(getTestName());
            row.addAll(getParameterValues(getParameterNames()));
            row.add(getMetricName());
            row.add(getSampleSize());
            row.add(getStatisticName());
            row.add(getStatisticValue());
            row.add(getMarginOfError());
            row.add(getUnits());
            return row;
        }

        String doubleToString(Double value) {
            return value.isNaN() ? "NA" : String.valueOf(value);
        }

        Object getFieldContents(Object object, String fieldName) {
            Class myClass = object.getClass();
            Field myField;
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

    public static class MissingOutcome extends Outcome {
        protected String metricName;

        private MissingOutcome(RunResult run, String missingMetric) {
            super(run, null);
            metricName = missingMetric;
        }

        public static Outcome from(RunResult run, String missingMetric) {
            return new MissingOutcome(run, missingMetric);
        }

        public String getMarginOfError() {
            return "NA";
        }

        public String getMetricName() {
            return metricName;
        }

        @Override
        public ResultRole getRole() {
            return ResultRole.OMITTED;
        }

        String getStatisticName() {
            return "NONE";
        }

        public String getSampleSize() {
            return "0";
        }

        public String getStatisticValue() {
            return "0";
        }

        public String getUnits() {
            return "NONE";
        }
    }
}





