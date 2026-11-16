package org.sigf.domain;
import java.util.List;
public record ReportGrid(List<String> columns,List<List<String>> rows) {}
