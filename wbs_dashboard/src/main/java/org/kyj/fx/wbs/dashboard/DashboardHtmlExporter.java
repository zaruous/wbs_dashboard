package org.kyj.fx.wbs.dashboard;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

// WbsProjectManager에 정의된 ProjectMetadata 클래스를 사용한다고 가정합니다.
// 만약 별도의 파일이라면 import 문이 필요합니다.
// import com.example.yourpackage.WbsProjectManager.ProjectMetadata; // 예시 경로

public class DashboardHtmlExporter {

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 대시보드 데이터를 HTML 파일로 내보냅니다.
     *
     * @param metadata              프로젝트 메타데이터 객체
     * @param overallProgress       전체 프로젝트 진행률 (0.0 ~ 1.0)
     * @param taskStatusCounts      작업 상태별 개수 (예: "진행 예정", "진행 중", "완료" 키와 해당 작업 수)
     * @param pieChartImageBase64   파이 차트 스냅샷 이미지 (Base64 인코딩된 문자열)
     * @param assigneeLoadImageBase64 담당자별 작업 부하 요약 TextArea 스냅샷 이미지 (Base64 인코딩된 문자열)
     * @param outputFile            저장할 HTML 파일
     */
    public void exportDashboardToHtml(
            ProjectMetadata metadata,
            double overallProgress,
            Map<String, Long> taskStatusCounts,
            String pieChartImageBase64,
            String assigneeLoadImageBase64,
            File outputFile) {

        StringBuilder htmlBuilder = new StringBuilder();

        // HTML 기본 구조 및 스타일 시작
        htmlBuilder.append("<!DOCTYPE html>\n");
        htmlBuilder.append("<html lang=\"ko\">\n");
        htmlBuilder.append("<head>\n");
        htmlBuilder.append("    <meta charset=\"UTF-8\">\n");
        htmlBuilder.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        htmlBuilder.append("    <title>WBS 대시보드 보고서</title>\n");
        htmlBuilder.append("    <style>\n");
        htmlBuilder.append("        body { font-family: 'Malgun Gothic', sans-serif; margin: 20px; background-color: #2b2b2b; color: #e0e0e0; }\n");
        htmlBuilder.append("        .container { background-color: #3c3f41; padding: 20px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.5); max-width: 900px; margin: auto; }\n");
        htmlBuilder.append("        h1, h2 { color: #009688; border-bottom: 2px solid #00796b; padding-bottom: 5px;}\n");
        htmlBuilder.append("        table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }\n");
        htmlBuilder.append("        th, td { border: 1px solid #555; padding: 10px; text-align: left; }\n");
        htmlBuilder.append("        th { background-color: #4a4a4a; }\n");
        htmlBuilder.append("        .progress-bar-container { width: 100%; background-color: #555; border-radius: 4px; margin-bottom:10px; }\n");
        htmlBuilder.append("        .progress-bar { height: 24px; background-color: #007bff; border-radius: 4px; text-align: center; line-height: 24px; color: white; }\n");
        htmlBuilder.append("        .section { margin-bottom: 30px; }\n");
        htmlBuilder.append("        .summary-item { margin-bottom: 8px; }\n");
        htmlBuilder.append("        .chart-image { max-width: 100%; height: auto; border: 1px solid #555; border-radius: 4px; margin-top: 10px; }\n");
        htmlBuilder.append("        .footer { text-align: center; margin-top: 30px; font-size: 0.9em; color: #aaa; }\n");
        htmlBuilder.append("    </style>\n");
        htmlBuilder.append("</head>\n");
        htmlBuilder.append("<body>\n");
        htmlBuilder.append("    <div class=\"container\">\n");
        htmlBuilder.append("        <h1>WBS 대시보드 보고서</h1>\n");
        htmlBuilder.append("        <p class=\"summary-item\">보고서 생성일: ").append(LocalDate.now().format(dateFormatter)).append("</p>\n");

        // 프로젝트 메타데이터 섹션
        if (metadata != null) {
            htmlBuilder.append("        <div class=\"section\">\n");
            htmlBuilder.append("            <h2>프로젝트 정보</h2>\n");
            htmlBuilder.append("            <table>\n");
            htmlBuilder.append("                <tr><th>프로젝트명</th><td>").append(escapeHtml(metadata.getProjectName())).append("</td></tr>\n");
            String startDateStr = metadata.getProjectStartDate() != null ? metadata.getProjectStartDate().format(dateFormatter) : "N/A";
            String endDateStr = metadata.getProjectEndDate() != null ? metadata.getProjectEndDate().format(dateFormatter) : "N/A";
            htmlBuilder.append("                <tr><th>프로젝트 기간</th><td>").append(startDateStr).append(" ~ ").append(endDateStr).append("</td></tr>\n");
            htmlBuilder.append("                <tr><th>작성자</th><td>").append(escapeHtml(metadata.getAuthor())).append("</td></tr>\n");
            String lastModDateStr = metadata.getLastModifiedDate() != null ? metadata.getLastModifiedDate().format(dateFormatter) : "N/A";
            htmlBuilder.append("                <tr><th>마지막 수정일</th><td>").append(lastModDateStr).append("</td></tr>\n");
            htmlBuilder.append("            </table>\n");
            htmlBuilder.append("        </div>\n");
        }

        // 전체 진행률 섹션
        htmlBuilder.append("        <div class=\"section\">\n");
        htmlBuilder.append("            <h2>전체 프로젝트 진행률</h2>\n");
        String formattedOverallProgress = String.format("%.1f%%", overallProgress * 100);
        htmlBuilder.append("            <p class=\"summary-item\">").append(formattedOverallProgress).append("</p>\n");
        htmlBuilder.append("            <div class=\"progress-bar-container\">\n");
        htmlBuilder.append("                <div class=\"progress-bar\" style=\"width: ").append(formattedOverallProgress).append(";\">").append(formattedOverallProgress).append("</div>\n");
        htmlBuilder.append("            </div>\n");
        htmlBuilder.append("        </div>\n");

        // 작업 상태별 분포 섹션 (표 + 이미지)
        htmlBuilder.append("        <div class=\"section\">\n");
        htmlBuilder.append("            <h2>업무 상태별 분포</h2>\n");
        if (taskStatusCounts != null && !taskStatusCounts.isEmpty()) {
            htmlBuilder.append("            <table>\n");
            htmlBuilder.append("                <tr><th>상태</th><th>작업 수</th></tr>\n");
            taskStatusCounts.forEach((status, count) -> {
                htmlBuilder.append("                <tr><td>").append(escapeHtml(status)).append("</td><td>").append(count).append(" 건</td></tr>\n");
            });
            htmlBuilder.append("            </table>\n");
        }
        if (pieChartImageBase64 != null && !pieChartImageBase64.isEmpty()) {
            htmlBuilder.append("            <img src=\"data:image/png;base64,").append(pieChartImageBase64).append("\" alt=\"업무 상태 파이 차트\" class=\"chart-image\">\n");
        }
        htmlBuilder.append("        </div>\n");


        // 담당자별 작업 부하 요약 섹션 (텍스트 + 이미지)
        htmlBuilder.append("        <div class=\"section\">\n");
        htmlBuilder.append("            <h2>담당자별 작업 부하 (진행 중/예정)</h2>\n");
        if (assigneeLoadImageBase64 != null && !assigneeLoadImageBase64.isEmpty()) {
             htmlBuilder.append("            <img src=\"data:image/png;base64,").append(assigneeLoadImageBase64).append("\" alt=\"담당자별 작업 부하\" class=\"chart-image\">\n");
        }
        // If you still want the text version as a fallback or in addition:
        // String assigneeLoadSummary = params.get("assigneeLoadSummaryText"); // Assuming you pass this too
        // if (assigneeLoadSummary != null && !assigneeLoadSummary.trim().isEmpty()) {
        //    htmlBuilder.append("            <pre style=\"background-color: #424242; padding: 10px; border-radius: 4px; white-space: pre-wrap;\">").append(escapeHtml(assigneeLoadSummary)).append("</pre>\n");
        // }
        htmlBuilder.append("        </div>\n");
        
        htmlBuilder.append("        <div class=\"footer\">\n");
        htmlBuilder.append("            <p>WBS Project Management Tool - 자동 생성된 보고서</p>\n");
        htmlBuilder.append("        </div>\n");

        htmlBuilder.append("    </div>\n");
        htmlBuilder.append("</body>\n");
        htmlBuilder.append("</html>\n");

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            writer.write(htmlBuilder.toString());
            System.out.println("대시보드 HTML 보고서가 성공적으로 저장되었습니다: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("HTML 보고서 저장 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
