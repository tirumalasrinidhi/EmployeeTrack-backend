package com.employeetrack.controller;

import com.employeetrack.entity.Payslip;
import com.employeetrack.service.PayslipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payslips")
@RequiredArgsConstructor
public class PayslipController {

    private final PayslipService payslipService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<List<Payslip>> getAllPayslips() {
        return ResponseEntity.ok(payslipService.getAllPayslips());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<List<Payslip>> getMyPayslips() {
        return ResponseEntity.ok(payslipService.getMyPayslips());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payslip> getPayslipById(@PathVariable Long id) {
        return ResponseEntity.ok(payslipService.getPayslipById(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadPayslip(@PathVariable Long id) {
        byte[] pdfBytes = payslipService.generatePayslipPdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "payslip-" + id + ".pdf");
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
            .headers(headers)
            .body(pdfBytes);
    }
}
