package com.devashish.learning.benchmarking.serivces.evaluation;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.devashish.learning.benchmarking.persistence.entities.BenchmarkTaskEntity;
import com.devashish.learning.benchmarking.models.evaluation.ToolEvaluationResponse;
import com.devashish.learning.benchmarking.repositories.BenchmarkTaskRepository;

@Service
public class BenchmarkTrendChartService {

    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter WEEK_FORMATTER = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);

    private final BenchmarkEvaluationService benchmarkEvaluationService;
    private final BenchmarkTaskRepository benchmarkTaskRepository;

    public BenchmarkTrendChartService(
        BenchmarkEvaluationService benchmarkEvaluationService,
        BenchmarkTaskRepository benchmarkTaskRepository
    ) {
        this.benchmarkEvaluationService = benchmarkEvaluationService;
        this.benchmarkTaskRepository = benchmarkTaskRepository;
    }

    public String renderWeekOverWeekChart(UUID runId, String language, String dataset, String tool) {
        ToolEvaluationResponse evaluation = benchmarkEvaluationService.getToolEvaluation(runId, language, dataset, tool);
        List<TrendPoint> precisionPoints = buildHistoricalSeries(language, dataset, tool, true);
        List<TrendPoint> recallPoints = buildHistoricalSeries(language, dataset, tool, false);

        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width,initial-scale=1" />
              <title>Week over week trend</title>
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; background: #0f172a; color: #e2e8f0; }
                main { max-width: 980px; margin: 0 auto; padding: 28px; }
                .card { background: linear-gradient(145deg, rgba(15,23,42,0.92), rgba(30,41,59,0.88)); border: 1px solid rgba(148,163,184,0.18); border-radius: 18px; padding: 20px; }
                .eyebrow { color: #67e8f9; font-size: 12px; font-weight: 800; letter-spacing: 0.08em; text-transform: uppercase; }
                h1 { margin: 8px 0 10px 0; font-size: 32px; }
                p { color: #cbd5e1; }
                .stats { display: grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: 12px; margin: 20px 0; }
                .stat { background: rgba(15,23,42,0.74); border: 1px solid rgba(148,163,184,0.16); border-radius: 14px; padding: 14px; }
                .stat strong { display: block; font-size: 24px; margin-top: 6px; }
                .legend { display: flex; gap: 16px; margin: 12px 0 20px 0; font-size: 14px; color: #cbd5e1; }
                .legend span::before { content: ''; display: inline-block; width: 10px; height: 10px; border-radius: 50%%; margin-right: 8px; }
                .legend .precision::before { background: #22d3ee; }
                .legend .recall::before { background: #4ade80; }
                svg { width: 100%%; height: auto; background: rgba(15,23,42,0.7); border-radius: 16px; }
                .note { margin-top: 10px; font-size: 13px; color: #94a3b8; }
                .footer { margin-top: 16px; font-size: 13px; color: #94a3b8; }
              </style>
            </head>
            <body>
              <main>
                <section class="card">
                  <div class="eyebrow">%s / %s</div>
                  <h1>%s week over week</h1>
                  <p>Simulated growth trend derived from the current precision and recall baseline for this tool.</p>
                  <div class="stats">
                    <div class="stat"><span>Detected</span><strong>%d</strong></div>
                    <div class="stat"><span>Precision</span><strong>%s</strong></div>
                    <div class="stat"><span>Recall</span><strong>%s</strong></div>
                  </div>
                  <div class="legend">
                    <span class="precision">Precision</span>
                    <span class="recall">Recall</span>
                  </div>
                  %s
                  <p class="note">Each point represents one recorded run for this language, dataset, and tool. Hover a point to inspect the exact week and metric value.</p>
                  <p class="footer">Source repo: %s</p>
                </section>
              </main>
            </body>
            </html>
            """.formatted(
                escapeHtml(language),
                escapeHtml(dataset),
                escapeHtml(tool),
                evaluation.detectedFindings(),
                formatPercent(evaluation.precision()),
                formatPercent(evaluation.recall()),
                renderChartSvg(precisionPoints, recallPoints),
                escapeHtml(evaluation.sourceRepoUrl())
            );
    }

    private List<TrendPoint> buildHistoricalSeries(String language, String dataset, String tool, boolean precisionMetric) {
        List<TrendPoint> points = new ArrayList<>();
        List<BenchmarkTaskEntity> tasks = benchmarkTaskRepository.findHistoricalTasksForChart(language, dataset, tool);
        for (BenchmarkTaskEntity task : tasks) {
            ToolEvaluationResponse taskEvaluation = benchmarkEvaluationService.evaluateHistoricalTask(task.getRun(), task);
            LocalDate runDate = task.getRun().getSubmittedAt().atZone(ZoneOffset.UTC).toLocalDate();
            double metricValue = precisionMetric ? taskEvaluation.precision() : taskEvaluation.recall();
            points.add(new TrendPoint(
                runDate.format(LABEL_FORMATTER),
                clamp(metricValue),
                "Week of " + runDate.format(WEEK_FORMATTER)
                    + " | Precision " + formatPercent(taskEvaluation.precision())
                    + " | Recall " + formatPercent(taskEvaluation.recall())
            ));
        }
        if (points.isEmpty()) {
            points.add(new TrendPoint(LocalDate.now(ZoneOffset.UTC).format(LABEL_FORMATTER), 0.0, "No recorded runs"));
        }
        return points;
    }

    private String renderChartSvg(List<TrendPoint> precisionPoints, List<TrendPoint> recallPoints) {
        int width = 900;
        int height = 360;
        int leftPadding = 54;
        int rightPadding = 28;
        int topPadding = 28;
        int bottomPadding = 54;
        int chartWidth = width - leftPadding - rightPadding;
        int chartHeight = height - topPadding - bottomPadding;

        String precisionPolyline = buildPolyline(precisionPoints, leftPadding, topPadding, chartWidth, chartHeight);
        String recallPolyline = buildPolyline(recallPoints, leftPadding, topPadding, chartWidth, chartHeight);
        String precisionMarkers = buildMarkers(precisionPoints, leftPadding, topPadding, chartWidth, chartHeight, "#22d3ee");
        String recallMarkers = buildMarkers(recallPoints, leftPadding, topPadding, chartWidth, chartHeight, "#4ade80");
        StringBuilder labels = new StringBuilder();

        for (int index = 0; index < precisionPoints.size(); index++) {
            int x = leftPadding + (int) Math.round(index * (chartWidth / (double) Math.max(1, precisionPoints.size() - 1)));
            labels.append("<text x=\"").append(x).append("\" y=\"").append(height - 18)
                .append("\" text-anchor=\"middle\" fill=\"#94a3b8\" font-size=\"12\">")
                .append(escapeHtml(precisionPoints.get(index).label())).append("</text>");
        }

        return """
            <svg viewBox="0 0 %d %d" role="img" aria-label="Week over week chart">
              <line x1="%d" y1="%d" x2="%d" y2="%d" stroke="#334155" stroke-width="1" />
              <line x1="%d" y1="%d" x2="%d" y2="%d" stroke="#334155" stroke-width="1" />
              <text x="14" y="%d" fill="#94a3b8" font-size="12">100%%</text>
              <text x="20" y="%d" fill="#94a3b8" font-size="12">50%%</text>
              <text x="26" y="%d" fill="#94a3b8" font-size="12">0%%</text>
              <polyline fill="none" stroke="#22d3ee" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" points="%s" />
              <polyline fill="none" stroke="#4ade80" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" points="%s" />
              %s
              %s
              %s
            </svg>
            """.formatted(
            width,
            height,
            leftPadding,
            topPadding,
            leftPadding,
            topPadding + chartHeight,
            leftPadding,
            topPadding + chartHeight,
            leftPadding + chartWidth,
            topPadding + chartHeight,
            topPadding + 4,
            topPadding + (chartHeight / 2) + 4,
            topPadding + chartHeight + 4,
            precisionPolyline,
            recallPolyline,
            precisionMarkers,
            recallMarkers,
            labels
        );
    }

    private String buildPolyline(List<TrendPoint> points, int leftPadding, int topPadding, int chartWidth, int chartHeight) {
        List<String> polylinePoints = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            int x = leftPadding + (int) Math.round(index * (chartWidth / (double) Math.max(1, points.size() - 1)));
            int y = topPadding + (int) Math.round((1.0 - points.get(index).value()) * chartHeight);
            polylinePoints.add(x + "," + y);
        }
        return String.join(" ", polylinePoints);
    }

    private String buildMarkers(List<TrendPoint> points, int leftPadding, int topPadding, int chartWidth, int chartHeight, String color) {
        StringBuilder markers = new StringBuilder();
        for (int index = 0; index < points.size(); index++) {
            int x = leftPadding + (int) Math.round(index * (chartWidth / (double) Math.max(1, points.size() - 1)));
            int y = topPadding + (int) Math.round((1.0 - points.get(index).value()) * chartHeight);
            int radius = index == points.size() - 1 ? 6 : 4;
            String stroke = index == points.size() - 1 ? "#ffffff" : color;
            int strokeWidth = index == points.size() - 1 ? 2 : 1;
            markers.append("<circle cx=\"").append(x)
                .append("\" cy=\"").append(y)
                .append("\" r=\"").append(radius)
                .append("\" fill=\"").append(color)
                .append("\" stroke=\"").append(stroke)
                .append("\" stroke-width=\"").append(strokeWidth)
                .append("\">")
                .append("<title>").append(escapeHtml(points.get(index).tooltip())).append("</title>")
                .append("</circle>");

            if (index == points.size() - 1) {
                markers.append("<text x=\"").append(x)
                    .append("\" y=\"").append(y - 12)
                    .append("\" text-anchor=\"middle\" fill=\"").append(color)
                    .append("\" font-size=\"12\" font-weight=\"700\">")
                    .append(escapeHtml(formatPercent(points.get(index).value())))
                    .append("</text>");
            }
        }
        return markers.toString();
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(0.99, value));
    }

    private String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private record TrendPoint(String label, double value, String tooltip) {}
}
