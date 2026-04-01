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
                .chart-shell { position: relative; }
                svg { width: 100%%; height: auto; background: rgba(15,23,42,0.7); border-radius: 16px; }
                .chart-tooltip {
                  position: absolute;
                  min-width: 180px;
                  max-width: 260px;
                  padding: 12px 14px;
                  border-radius: 12px;
                  border: 1px solid rgba(148,163,184,0.22);
                  background: rgba(15,23,42,0.96);
                  color: #e2e8f0;
                  box-shadow: 0 18px 40px rgba(15,23,42,0.45);
                  pointer-events: none;
                  opacity: 0;
                  transform: translate(-50%%, calc(-100%% - 14px));
                  transition: opacity 120ms ease;
                  z-index: 3;
                }
                .chart-tooltip.visible { opacity: 1; }
                .chart-tooltip::after {
                  content: '';
                  position: absolute;
                  left: 50%%;
                  bottom: -8px;
                  width: 12px;
                  height: 12px;
                  background: rgba(15,23,42,0.96);
                  border-right: 1px solid rgba(148,163,184,0.22);
                  border-bottom: 1px solid rgba(148,163,184,0.22);
                  transform: translateX(-50%%) rotate(45deg);
                }
                .chart-tooltip strong { display: block; margin-bottom: 6px; font-size: 13px; color: #f8fafc; }
                .chart-tooltip span { display: block; font-size: 12px; color: #cbd5e1; line-height: 1.45; }
                .chart-point { cursor: pointer; }
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
                  <div class="chart-shell">
                    %s
                    <div id="chart-tooltip" class="chart-tooltip" aria-hidden="true"></div>
                  </div>
                  <p class="note">Each point represents one recorded run for this language, dataset, and tool. Hover a point to inspect the exact week and metric value.</p>
                  <p class="footer">Source repo: %s</p>
                </section>
              </main>
              <script>
                (() => {
                  const tooltip = document.getElementById('chart-tooltip');
                  const chart = document.querySelector('.chart-shell');
                  if (!tooltip || !chart) {
                    return;
                  }

                  const hideTooltip = () => {
                    tooltip.classList.remove('visible');
                    tooltip.setAttribute('aria-hidden', 'true');
                  };

                  const showTooltip = (point) => {
                    const rect = chart.getBoundingClientRect();
                    const x = Number(point.dataset.x ?? '0');
                    const y = Number(point.dataset.y ?? '0');
                    const lines = (point.dataset.tooltip ?? '').split('|').map((line) => line.trim()).filter(Boolean);

                    if (lines.length === 0) {
                      hideTooltip();
                      return;
                    }

                    const headline = lines[0];
                    const details = lines.slice(1).map((line) => `<span>${line}</span>`).join('');
                    tooltip.innerHTML = `<strong>${headline}</strong>${details}`;

                    const tooltipWidth = tooltip.offsetWidth || 220;
                    const clampedX = Math.max(tooltipWidth / 2 + 8, Math.min(rect.width - tooltipWidth / 2 - 8, x));

                    tooltip.style.left = `${clampedX}px`;
                    tooltip.style.top = `${y}px`;
                    tooltip.classList.add('visible');
                    tooltip.setAttribute('aria-hidden', 'false');
                  };

                  chart.querySelectorAll('.chart-point').forEach((point) => {
                    point.addEventListener('mouseenter', () => showTooltip(point));
                    point.addEventListener('focus', () => showTooltip(point));
                    point.addEventListener('mouseleave', hideTooltip);
                    point.addEventListener('blur', hideTooltip);
                  });
                })();
              </script>
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
            markers.append("<circle class=\"chart-point\" tabindex=\"0\" cx=\"").append(x)
                .append("\" cy=\"").append(y)
                .append("\" r=\"").append(radius)
                .append("\" fill=\"").append(color)
                .append("\" stroke=\"").append(stroke)
                .append("\" stroke-width=\"").append(strokeWidth)
                .append("\" data-x=\"").append(x)
                .append("\" data-y=\"").append(y)
                .append("\" data-tooltip=\"").append(escapeHtml(points.get(index).tooltip()))
                .append("\">")
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
