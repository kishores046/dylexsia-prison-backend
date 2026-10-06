package edu.ai.dyslexiaprisonbackend.service.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import edu.ai.dyslexiaprisonbackend.model.result.SessionResult;
import edu.ai.dyslexiaprisonbackend.model.user.User;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PdfReportService {

    public byte[] generateStudentReportPdf(User student, List<SessionResult> sessions) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Fonts
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.GRAY);

            // Title
            Paragraph title = new Paragraph("Student Progress & Gaze Analysis Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            // Metadata
            String studentName = student.getUsername() != null ? student.getUsername() : student.getEmail();
            Paragraph meta = new Paragraph("Student Name: " + studentName + "\n" +
                    "Generated Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n" +
                    "Total Sessions Recorded: " + sessions.size(), bodyFont);
            meta.setSpacingAfter(15);
            document.add(meta);

            // Table of Session History
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.5f, 3f, 2f, 2.5f});

            // Table Header
            String[] headers = {"Session ID", "Date", "Risk Score", "Classification"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new Color(41, 128, 185));
                cell.setPadding(8);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            // Rows
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (SessionResult s : sessions) {
                PdfPCell c1 = new PdfPCell(new Phrase(s.getSessionId(), bodyFont));
                PdfPCell c2 = new PdfPCell(new Phrase(s.getTimestamp() != null ? s.getTimestamp().format(dateFormatter) : "N/A", bodyFont));
                PdfPCell c3 = new PdfPCell(new Phrase(String.format("%.2f", s.getRiskScore() != null ? s.getRiskScore() : 0.0), bodyFont));
                PdfPCell c4 = new PdfPCell(new Phrase(s.getClassification() != null ? s.getClassification().name() : "LOW", bodyFont));

                c1.setPadding(6);
                c2.setPadding(6);
                c3.setPadding(6);
                c4.setPadding(6);

                c1.setHorizontalAlignment(Element.ALIGN_CENTER);
                c2.setHorizontalAlignment(Element.ALIGN_CENTER);
                c3.setHorizontalAlignment(Element.ALIGN_CENTER);
                c4.setHorizontalAlignment(Element.ALIGN_CENTER);

                table.addCell(c1);
                table.addCell(c2);
                table.addCell(c3);
                table.addCell(c4);
            }

            document.add(table);

            // Supportive framing explanation
            Paragraph explanation = new Paragraph("\nSupportive Note: This report summarizes reading pattern gaze-tracking sessions for the student, supporting personalized learning and targeted interventions.", subtitleFont);
            explanation.setSpacingBefore(15);
            document.add(explanation);

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }

        return out.toByteArray();
    }
}
