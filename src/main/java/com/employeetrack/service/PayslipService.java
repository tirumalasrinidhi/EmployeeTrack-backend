package com.employeetrack.service;

import com.employeetrack.entity.Employee;
import com.employeetrack.entity.Payslip;
import com.employeetrack.entity.User;
import com.employeetrack.repository.EmployeeRepository;
import com.employeetrack.repository.PayslipRepository;
import com.employeetrack.repository.UserRepository;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.Month;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayslipService {

    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public List<Payslip> getAllPayslips() {
        return payslipRepository.findAll();
    }

    public List<Payslip> getMyPayslips() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        Employee emp = employeeRepository.findByUser(user)
            .orElseThrow(() -> new RuntimeException("Employee not found"));
        return payslipRepository.findByEmployee(emp);
    }

    public Payslip getPayslipById(Long id) {
        return payslipRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Payslip not found"));
    }

    public byte[] generatePayslipPdf(Long id) {
        Payslip payslip = getPayslipById(id);
        Employee emp = payslip.getEmployee();
        String monthName = Month.of(payslip.getMonth()).name();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Fonts
            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(15, 52, 96));
            Font subtitleFont = new Font(Font.HELVETICA, 12, Font.NORMAL, new Color(100, 100, 100));
            Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
            Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
            Font boldFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
            Font totalFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(15, 52, 96));

            // Header
            PdfPTable headerTable = new PdfPTable(1);
            headerTable.setWidthPercentage(100);
            PdfPCell headerCell = new PdfPCell();
            headerCell.setBackgroundColor(new Color(15, 52, 96));
            headerCell.setPadding(20);
            headerCell.setBorder(Rectangle.NO_BORDER);

            Paragraph companyName = new Paragraph("EMPLOYEETRACK", titleFont);
            companyName.setAlignment(Element.ALIGN_CENTER);
            Font whiteFont = new Font(Font.HELVETICA, 20, Font.BOLD, Color.WHITE);
            Paragraph companyNameWhite = new Paragraph("EMPLOYEETRACK", whiteFont);
            companyNameWhite.setAlignment(Element.ALIGN_CENTER);

            Font subWhite = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(173, 216, 230));
            Paragraph sub = new Paragraph("Employee Payroll Management System", subWhite);
            sub.setAlignment(Element.ALIGN_CENTER);

            Font payslipTitleFont = new Font(Font.HELVETICA, 14, Font.BOLD, Color.WHITE);
            Paragraph payslipTitle = new Paragraph("PAYSLIP", payslipTitleFont);
            payslipTitle.setAlignment(Element.ALIGN_CENTER);
            payslipTitle.setSpacingBefore(5);

            headerCell.addElement(companyNameWhite);
            headerCell.addElement(sub);
            headerCell.addElement(payslipTitle);
            headerTable.addCell(headerCell);
            document.add(headerTable);

            document.add(new Paragraph(" "));

            // Pay period
            Font periodFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(15, 52, 96));
            Paragraph period = new Paragraph("Pay Period: " + monthName + " " + payslip.getYear(), periodFont);
            period.setAlignment(Element.ALIGN_CENTER);
            document.add(period);

            document.add(new Paragraph(" "));

            // Employee details
            PdfPTable empTable = new PdfPTable(2);
            empTable.setWidthPercentage(100);
            empTable.setSpacingBefore(10);

            PdfPCell empHeaderCell = new PdfPCell(new Phrase("EMPLOYEE INFORMATION", headerFont));
            empHeaderCell.setColspan(2);
            empHeaderCell.setBackgroundColor(new Color(52, 152, 219));
            empHeaderCell.setPadding(8);
            empHeaderCell.setBorder(Rectangle.NO_BORDER);
            empTable.addCell(empHeaderCell);

            addRow(empTable, "Employee ID", emp.getEmployeeId(), boldFont, normalFont);
            addRow(empTable, "Name", emp.getUser().getName(), boldFont, normalFont);
            addRow(empTable, "Department", emp.getDepartment() != null ? emp.getDepartment().getName() : "N/A", boldFont, normalFont);
            addRow(empTable, "Designation", emp.getDesignation(), boldFont, normalFont);
            addRow(empTable, "Email", emp.getUser().getEmail(), boldFont, normalFont);

            document.add(empTable);
            document.add(new Paragraph(" "));

            // Salary details
            PdfPTable salaryTable = new PdfPTable(2);
            salaryTable.setWidthPercentage(100);
            salaryTable.setSpacingBefore(10);

            PdfPCell salHeaderCell = new PdfPCell(new Phrase("SALARY DETAILS", headerFont));
            salHeaderCell.setColspan(2);
            salHeaderCell.setBackgroundColor(new Color(39, 174, 96));
            salHeaderCell.setPadding(8);
            salHeaderCell.setBorder(Rectangle.NO_BORDER);
            salaryTable.addCell(salHeaderCell);

            addRow(salaryTable, "Basic Salary", "₹ " + String.format("%.2f", payslip.getBasicSalary()), boldFont, normalFont);
            addRow(salaryTable, "Allowances", "₹ " + String.format("%.2f", payslip.getAllowances()), boldFont, normalFont);
            addRow(salaryTable, "Gross Salary", "₹ " + String.format("%.2f", payslip.getBasicSalary() + payslip.getAllowances()), boldFont, normalFont);
            addRow(salaryTable, "Deductions", "₹ " + String.format("%.2f", payslip.getDeductions()), boldFont, normalFont);

            // Net salary row
            PdfPCell netLabelCell = new PdfPCell(new Phrase("NET SALARY", totalFont));
            netLabelCell.setBackgroundColor(new Color(241, 196, 15));
            netLabelCell.setPadding(10);
            netLabelCell.setBorder(Rectangle.NO_BORDER);

            PdfPCell netValueCell = new PdfPCell(new Phrase("₹ " + String.format("%.2f", payslip.getNetSalary()), totalFont));
            netValueCell.setBackgroundColor(new Color(241, 196, 15));
            netValueCell.setPadding(10);
            netValueCell.setBorder(Rectangle.NO_BORDER);

            salaryTable.addCell(netLabelCell);
            salaryTable.addCell(netValueCell);

            document.add(salaryTable);

            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));

            // Footer
            Font footerFont = new Font(Font.HELVETICA, 9, Font.ITALIC, new Color(150, 150, 150));
            Paragraph footer = new Paragraph("This is a computer-generated payslip and does not require a signature.", footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            Paragraph footerDate = new Paragraph("Generated by EmployeeTrack System", footerFont);
            footerDate.setAlignment(Element.ALIGN_CENTER);
            document.add(footerDate);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF: " + e.getMessage());
        }
    }

    private void addRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setPadding(8);
        labelCell.setBackgroundColor(new Color(245, 245, 245));
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(new Color(220, 220, 220));

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "N/A", valueFont));
        valueCell.setPadding(8);
        valueCell.setBorder(Rectangle.BOX);
        valueCell.setBorderColor(new Color(220, 220, 220));

        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}
