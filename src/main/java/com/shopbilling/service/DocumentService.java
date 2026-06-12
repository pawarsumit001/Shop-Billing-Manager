package com.shopbilling.service;

import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.io.image.ImageDataFactory;
import com.shopbilling.model.Invoice;
import com.shopbilling.model.InvoiceItem;
import com.shopbilling.repository.AppSettingRepository;
import com.shopbilling.repository.InvoiceRepository;
import com.shopbilling.repository.ProductRepository;
import com.shopbilling.util.ProductFilters;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
    private final InvoiceRepository invoices;
    private final ProductRepository products;
    private final AppSettingRepository settings;

    @Value("${app.shop.name}")
    private String shopName;

    @Value("${app.shop.address}")
    private String shopAddress;

    @Value("${app.shop.gst}")
    private String gstNumber;

    public DocumentService(InvoiceRepository invoices, ProductRepository products, AppSettingRepository settings) {
        this.invoices = invoices;
        this.products = products;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public byte[] invoicePdf(Long invoiceId) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        PdfDocument pdf = new PdfDocument(new PdfWriter(out));
        try (Document document = new Document(pdf)) {
            document.setMargins(24, 24, 24, 24);
            String activeShopName = setting("shopName", shopName);
            String activeShopAddress = setting("shopAddress", shopAddress);
            String activeGstNumber = setting("gstNumber", gstNumber);
            String activeUpiId = setting("upiId", "");
            String activeLogo = setting("shopLogoUrl", "");
            String invoiceFooter = setting("invoiceFooter", "Thank you for your business.");
            DeviceRgb blue = new DeviceRgb(72, 146, 172);
            DeviceRgb deepBlue = new DeviceRgb(20, 88, 112);
            DeviceRgb red = new DeviceRgb(178, 76, 73);
            DeviceRgb ink = new DeviceRgb(28, 42, 39);
            DeviceRgb light = new DeviceRgb(246, 250, 249);
            DeviceRgb softBlue = new DeviceRgb(228, 245, 249);
            DeviceRgb line = new DeviceRgb(38, 55, 50);
            DeviceRgb green = new DeviceRgb(19, 94, 69);
            DeviceRgb white = new DeviceRgb(255, 255, 255);

            Table top = new Table(UnitValue.createPercentArray(new float[]{0.8f, 2.8f, 2})).useAllAvailableWidth();
            top.addCell(logoCell(activeLogo, activeShopName, blue));
            top.addCell(new Cell().setBorder(Border.NO_BORDER)
                    .add(new Paragraph(activeShopName).setBold().setFontSize(20).setFontColor(blue).setMargin(0))
                    .add(new Paragraph("").setBorderTop(new SolidBorder(blue, 2)).setMarginTop(2).setMarginBottom(4))
                    .add(new Paragraph("Address: " + safe(activeShopAddress)).setFontSize(8.5f).setFontColor(ink).setMargin(0))
                    .add(new Paragraph(gstLine(activeGstNumber)).setFontSize(8.5f).setFontColor(ink).setMargin(0))
                    .add(new Paragraph("UPI: " + (activeUpiId.isBlank() ? "Not configured" : activeUpiId)).setFontSize(8.5f).setFontColor(ink).setMargin(0))
                    .add(new Paragraph("Payment Mode: " + invoice.getPaymentMode()).setFontSize(8.5f).setFontColor(ink).setMargin(0)));
            top.addCell(new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT).setPaddingTop(20)
                    .add(new Paragraph("TAX INVOICE").setBold().setFontSize(22).setFontColor(red).setMargin(0))
                    .add(new Paragraph(statusText(invoice)).setBold().setFontSize(9).setFontColor(invoice.getDueAmount().signum() > 0 ? red : green).setMargin(0)));
            document.add(top);
            document.add(new Paragraph(" ").setMargin(2));

            Table info = new Table(UnitValue.createPercentArray(new float[]{2, 2, 2})).useAllAvailableWidth();
            info.addCell(boxCell("Bill To",
                    safe(invoice.getCustomerName()) + "\nMobile: " + safe(invoice.getMobileNumber()) + "\nAddress: " + safe(invoice.getAddress()),
                    line, light));
            info.addCell(boxCell("Deliver To",
                    safe(invoice.getCustomerName()) + "\n" + safe(invoice.getAddress()) + "\nAttention: " + safe(invoice.getCustomerName()),
                    line, light));
            info.addCell(boxCell("Invoice Details",
                    "Invoice No #: " + invoice.getId() + "\nDate: " + invoice.getCreatedAt().toLocalDate() + "\nCreated By: " + safe(invoice.getCreatedBy()) + "\nTerms: " + (invoice.getDueAmount().signum() > 0 ? "Due" : "Paid"),
                    line, white));
            document.add(info);
            Table paymentStrip = new Table(UnitValue.createPercentArray(new float[]{2, 2, 2})).useAllAvailableWidth();
            paymentStrip.addCell(infoCell("Payment Status", statusText(invoice), softBlue, deepBlue, line));
            paymentStrip.addCell(infoCell("Amount Due", "Rs " + invoice.getDueAmount().toPlainString(), invoice.getDueAmount().signum() > 0 ? materialDueBackground() : softBlue, invoice.getDueAmount().signum() > 0 ? red : green, line));
            paymentStrip.addCell(upiCell(activeUpiId, activeShopName, invoice, pdf, softBlue, deepBlue, line));
            document.add(paymentStrip);
            document.add(new Paragraph(" ").setMargin(2));

            addItemTables(document, invoice, activeShopName, activeShopAddress, blue, white, light, line);

            Table bottom = new Table(UnitValue.createPercentArray(new float[]{3, 2})).useAllAvailableWidth();
            bottom.addCell(new Cell().setBorder(new SolidBorder(line, 1)).setPadding(5)
                    .add(new Paragraph("Comments & Instructions").setBold().setFontSize(8.5f).setFontColor(ink).setMarginBottom(2))
                    .add(new Paragraph(invoiceFooter).setFontSize(7.5f).setMargin(0))
                    .add(new Paragraph("Keep this invoice for warranty, return, due-payment and service reference. For UPI payment, share screenshot with invoice number.").setFontSize(7.5f).setMargin(0))
                    .add(new Paragraph("Authorized Signature: ____________________").setFontSize(7.5f).setMarginTop(4).setMarginBottom(0)));
            Table summary = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
            summary.addCell(summaryLabel("Subtotal", light, line));
            summary.addCell(summaryValue(invoice.getSubtotal().toPlainString(), light, line));
            summary.addCell(summaryLabel("GST Total", light, line));
            summary.addCell(summaryValue(invoice.getGstTotal().toPlainString(), light, line));
            summary.addCell(summaryLabel("Discount", light, line));
            summary.addCell(summaryValue(invoice.getDiscount().toPlainString(), light, line));
            summary.addCell(summaryLabel("Paid", light, line));
            summary.addCell(summaryValue(invoice.getPaidAmount().toPlainString(), light, line));
            summary.addCell(summaryLabel("Due", light, line));
            summary.addCell(summaryValue(invoice.getDueAmount().toPlainString(), light, line));
            summary.addCell(summaryLabel("Total", green, line).setFontColor(ColorConstants.WHITE));
            summary.addCell(summaryValue(invoice.getTotal().toPlainString(), green, line).setFontColor(ColorConstants.WHITE));
            bottom.addCell(new Cell().setBorder(Border.NO_BORDER).add(summary));
            document.add(bottom);
            document.add(new Paragraph("Amount in words: " + amountInWords(invoice.getTotal()) + " only.")
                    .setBold()
                    .setFontSize(8)
                    .setMarginTop(3)
                    .setMarginBottom(2)
                    .setFontColor(ink));
            document.add(new Paragraph(" ").setMargin(2));
            Table terms = new Table(UnitValue.createPercentArray(new float[]{1})).useAllAvailableWidth();
            terms.addCell(new Cell().setBorder(new SolidBorder(line, 1)).setPadding(5)
                    .add(new Paragraph("Rules, Warranty & Terms").setBold().setFontSize(8.5f).setFontColor(ink).setMarginBottom(2))
                    .add(new Paragraph("1. Return/exchange and warranty claims require this invoice and are subject to shop/manufacturer policy.").setFontSize(7.2f).setMargin(0))
                    .add(new Paragraph("2. Credit/partial bills must be cleared on agreed date; due amount is payable to " + activeShopName + ".").setFontSize(7.2f).setMargin(0))
                    .add(new Paragraph("3. Please verify item, size, quantity, rate, tax and due amount before leaving the shop.").setFontSize(7.2f).setMargin(0)));
            document.add(terms);
            document.add(new Paragraph(invoiceFooterLine(activeShopName, activeShopAddress, activeGstNumber, activeUpiId))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(7)
                    .setFontColor(blue)
                    .setPaddingTop(4)
                    .setMargin(0));
            addPageNumbers(pdf, activeShopName, blue);
        }

        return out.toByteArray();
    }

    public byte[] stockExcel() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Stock");
            Row header = sheet.createRow(0);
            String[] headers = {"Name", "Category", "Size", "Purchase Price", "Selling Price", "Quantity", "Unit", "Barcode", "Image URL", "Supplier", "GST"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int rowIndex = 1;
            for (var product : products.findAll().stream().filter(ProductFilters::isValid).toList()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(product.getName());
                row.createCell(1).setCellValue(product.getCategory());
                row.createCell(2).setCellValue(product.getSize());
                row.createCell(3).setCellValue(product.getPurchasePrice().doubleValue());
                row.createCell(4).setCellValue(product.getSellingPrice().doubleValue());
                row.createCell(5).setCellValue(product.getQuantity().doubleValue());
                row.createCell(6).setCellValue(product.getUnit());
                row.createCell(7).setCellValue(product.getBarcode());
                row.createCell(8).setCellValue(product.getImageUrl());
                row.createCell(9).setCellValue(product.getSupplier());
                row.createCell(10).setCellValue(product.getGstPercent().doubleValue());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void addHeader(Table table, DeviceRgb background, String... values) {
        for (String value : values) {
            table.addHeaderCell(new Cell().setBackgroundColor(background).setFontColor(ColorConstants.WHITE)
                    .setBorder(new SolidBorder(background, 1)).setPadding(5)
                    .add(new Paragraph(value).setBold().setFontSize(8.5f).setMargin(0)));
        }
    }

    private void addItemTables(Document document, Invoice invoice, String shopName, String shopAddress, DeviceRgb header, DeviceRgb white, DeviceRgb light, DeviceRgb line) {
        int itemsPerPage = 15;
        int itemCount = invoice.getItems().size();
        for (int start = 0; start < itemCount; start += itemsPerPage) {
            if (start > 0) {
                document.add(new AreaBreak());
                addContinuationHeader(document, invoice, shopName, shopAddress, header, line);
            }
            Table table = new Table(UnitValue.createPercentArray(new float[]{0.7f, 4.8f, 1.1f, 1.3f, 1.1f, 1.5f}))
                    .useAllAvailableWidth();
            addHeader(table, header, "S.No", "Product / Size", "Qty", "Rate", "GST", "Amount");
            int end = Math.min(start + itemsPerPage, itemCount);
            for (int i = start; i < end; i++) {
                InvoiceItem item = invoice.getItems().get(i);
                DeviceRgb rowColor = i % 2 == 0 ? white : light;
                table.addCell(amountCell(String.valueOf(i + 1), rowColor, line));
                table.addCell(textCell(productDescription(item), rowColor, line));
                table.addCell(amountCell(item.getQuantity().toPlainString(), rowColor, line));
                table.addCell(amountCell("Rs " + item.getRate().toPlainString(), rowColor, line));
                table.addCell(amountCell(item.getGstPercent().toPlainString() + "%", rowColor, line));
                table.addCell(amountCell("Rs " + item.getLineTotal().toPlainString(), rowColor, line));
            }
            document.add(table);
        }
    }

    private void addContinuationHeader(Document document, Invoice invoice, String shopName, String shopAddress, DeviceRgb accent, DeviceRgb line) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{3, 2})).useAllAvailableWidth();
        header.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph(shopName).setBold().setFontSize(12).setFontColor(accent).setMargin(0))
                .add(new Paragraph(shopAddress).setFontSize(7).setMargin(0)));
        header.addCell(new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph("Invoice #" + invoice.getId()).setBold().setFontSize(9).setMargin(0))
                .add(new Paragraph("Customer: " + safe(invoice.getCustomerName())).setFontSize(7).setMargin(0)));
        document.add(header);
        document.add(new Paragraph("").setBorderTop(new SolidBorder(line, 0.5f)).setMarginTop(2).setMarginBottom(4));
    }

    private Cell logoCell(String logoPath, String shopName, DeviceRgb accent) {
        Cell cell = new Cell().setBorder(Border.NO_BORDER).setPaddingRight(6);
        try {
            if (logoPath != null && !logoPath.isBlank()) {
                Path path = Path.of(logoPath);
                if (Files.exists(path)) {
                    cell.add(new Image(ImageDataFactory.create(path.toAbsolutePath().toString())).setWidth(42).setHeight(42));
                    return cell;
                }
            }
        } catch (Exception ignored) {
            // Fall back to text logo if configured image is unavailable.
        }
        String initials = shopName == null || shopName.isBlank() ? "SB" : shopName.replaceAll("[^A-Za-z0-9 ]", "").trim();
        initials = initials.isBlank() ? "SB" : initials.substring(0, Math.min(2, initials.length())).toUpperCase();
        cell.setBackgroundColor(accent).setTextAlignment(TextAlignment.CENTER)
                .add(new Paragraph(initials).setFontColor(ColorConstants.WHITE).setBold().setFontSize(12).setMarginTop(10));
        return cell;
    }

    private void addPageNumbers(PdfDocument pdf, String shopName, DeviceRgb accent) {
        int total = pdf.getNumberOfPages();
        for (int pageNo = 1; pageNo <= total; pageNo++) {
            Rectangle pageSize = pdf.getPage(pageNo).getPageSize();
            Rectangle footer = new Rectangle(pageSize.getLeft() + 24, pageSize.getBottom() + 10, pageSize.getWidth() - 48, 14);
            Canvas canvas = new Canvas(new PdfCanvas(pdf.getPage(pageNo)), footer);
            canvas.add(new Paragraph(shopName + " | Page " + pageNo + " of " + total)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setFontSize(7)
                    .setFontColor(accent)
                    .setMargin(0));
            canvas.close();
        }
    }

    private Cell boxCell(String title, String body, DeviceRgb line, DeviceRgb background) {
        return new Cell().setBorder(new SolidBorder(line, 1)).setBackgroundColor(background).setPadding(6)
                .add(new Paragraph(title).setBold().setItalic().setFontSize(9).setMarginBottom(2))
                .add(new Paragraph(body).setFontSize(8).setMargin(0));
    }

    private Cell infoCell(String title, String body, DeviceRgb background, DeviceRgb accent, DeviceRgb line) {
        return new Cell().setBackgroundColor(background).setBorder(new SolidBorder(line, 1)).setPadding(6)
                .add(new Paragraph(title).setBold().setFontSize(8).setFontColor(accent).setMarginBottom(2))
                .add(new Paragraph(body).setFontSize(9).setMargin(0));
    }

    private Cell upiCell(String upiId, String shopName, Invoice invoice, PdfDocument pdf, DeviceRgb background, DeviceRgb accent, DeviceRgb line) {
        Cell cell = new Cell().setBackgroundColor(background).setBorder(new SolidBorder(line, 1)).setPadding(6);
        cell.add(new Paragraph("UPI Payment QR").setBold().setFontSize(8).setFontColor(accent).setMarginBottom(2));
        if (upiId == null || upiId.isBlank()) {
            cell.add(new Paragraph("Add UPI ID in Settings to print QR on invoice.").setFontSize(8).setMargin(0));
            return cell;
        }
        BarcodeQRCode qrCode = new BarcodeQRCode(upiLink(upiId, shopName, invoice));
        Image qrImage = new Image(qrCode.createFormXObject(ColorConstants.BLACK, pdf)).setWidth(44).setHeight(44);
        cell.add(qrImage);
        cell.add(new Paragraph(upiId).setFontSize(7.5f).setBold().setMargin(0));
        return cell;
    }

    private String upiLink(String upiId, String shopName, Invoice invoice) {
        java.math.BigDecimal payable = invoice.getDueAmount().signum() > 0
                ? invoice.getDueAmount()
                : invoice.getTotal();
        return "upi://pay?pa=" + encode(upiId)
                + "&pn=" + encode(shopName)
                + "&am=" + payable.max(java.math.BigDecimal.ZERO).toPlainString()
                + "&tn=" + encode("Invoice " + invoice.getId());
    }

    private String encode(String value) {
        return URLEncoder.encode(safe(value), StandardCharsets.UTF_8);
    }

    private String statusText(Invoice invoice) {
        return invoice.getDueAmount().signum() > 0
                ? "PARTIAL / DUE - Rs " + invoice.getDueAmount().toPlainString()
                : "PAID";
    }

    private DeviceRgb materialDueBackground() {
        return new DeviceRgb(255, 241, 238);
    }

    private Cell textCell(String value, DeviceRgb background, DeviceRgb line) {
        return new Cell().setBackgroundColor(background).setBorder(new SolidBorder(line, 1)).setPadding(3)
                .add(new Paragraph(safe(value)).setFontSize(8).setMargin(0));
    }

    private Cell amountCell(String value, DeviceRgb background, DeviceRgb line) {
        return new Cell().setTextAlignment(TextAlignment.RIGHT).setBackgroundColor(background)
                .setBorder(new SolidBorder(line, 1)).setPadding(3)
                .add(new Paragraph(value).setFontSize(8).setMargin(0));
    }

    private Cell summaryLabel(String label, DeviceRgb background, DeviceRgb line) {
        return new Cell().setBackgroundColor(background).setBorder(new SolidBorder(line, 1)).setPadding(5)
                .add(new Paragraph(label).setBold().setFontSize(8.5f).setMargin(0));
    }

    private Cell summaryValue(String value, DeviceRgb background, DeviceRgb line) {
        return new Cell().setTextAlignment(TextAlignment.RIGHT).setBackgroundColor(background)
                .setBorder(new SolidBorder(line, 1)).setPadding(5)
                .add(new Paragraph("Rs " + value).setBold().setFontSize(8.5f).setMargin(0));
    }

    private String productDescription(InvoiceItem item) {
        String name = safe(item.getProductName());
        if (item.getProduct() == null || item.getProduct().getSize() == null || item.getProduct().getSize().isBlank()) {
            return name;
        }
        String size = item.getProduct().getSize().trim();
        if (name.toLowerCase().contains(size.toLowerCase())) {
            return name;
        }
        return name + " (" + size + ")";
    }

    private String amountInWords(BigDecimal amount) {
        BigDecimal normalized = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        long rupees = normalized.longValue();
        int paise = normalized.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String words = toIndianWords(rupees) + " rupees";
        if (paise > 0) {
            words += " and " + toIndianWords(paise) + " paise";
        }
        return capitalize(words);
    }

    private String toIndianWords(long value) {
        if (value == 0) {
            return "zero";
        }
        String[] units = {"", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
                "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
                "eighteen", "nineteen"};
        String[] tens = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
        StringBuilder result = new StringBuilder();
        long crore = value / 10000000;
        value %= 10000000;
        long lakh = value / 100000;
        value %= 100000;
        long thousand = value / 1000;
        value %= 1000;
        long hundred = value / 100;
        long rest = value % 100;
        appendPart(result, crore, "crore", units, tens);
        appendPart(result, lakh, "lakh", units, tens);
        appendPart(result, thousand, "thousand", units, tens);
        appendPart(result, hundred, "hundred", units, tens);
        if (rest > 0) {
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(twoDigitWords(rest, units, tens));
        }
        return result.toString();
    }

    private void appendPart(StringBuilder result, long value, String label, String[] units, String[] tens) {
        if (value <= 0) {
            return;
        }
        if (result.length() > 0) {
            result.append(" ");
        }
        result.append(twoDigitWords(value, units, tens)).append(" ").append(label);
    }

    private String twoDigitWords(long value, String[] units, String[] tens) {
        if (value < 20) {
            return units[(int) value];
        }
        long ten = value / 10;
        long unit = value % 10;
        return tens[(int) ten] + (unit == 0 ? "" : " " + units[(int) unit]);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String setting(String key, String fallback) {
        return settings.findById(key).map(item -> item.getSettingValue()).filter(value -> !value.isBlank()).orElse(fallback);
    }

    private String gstLine(String gst) {
        return hasRealGst(gst) ? "GST: " + gst.trim() : "";
    }

    private String invoiceFooterLine(String shopName, String address, String gst, String upiId) {
        StringBuilder footer = new StringBuilder();
        appendFooterPart(footer, shopName);
        appendFooterPart(footer, address);
        if (hasRealGst(gst)) {
            appendFooterPart(footer, "GST: " + gst.trim());
        }
        if (upiId != null && !upiId.isBlank()) {
            appendFooterPart(footer, "UPI: " + upiId.trim());
        }
        return footer.toString();
    }

    private void appendFooterPart(StringBuilder footer, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (footer.length() > 0) {
            footer.append(" | ");
        }
        footer.append(value.trim());
    }

    private boolean hasRealGst(String gst) {
        return gst != null && !gst.isBlank() && !gst.equalsIgnoreCase("GST-NOT-SET");
    }
}
