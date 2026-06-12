package com.shopbilling.api;

import com.shopbilling.dto.ApiDtos.BootstrapDto;
import com.shopbilling.dto.ApiDtos.DuePaymentDto;
import com.shopbilling.dto.ApiDtos.InvoiceDto;
import com.shopbilling.dto.ApiDtos.PurchaseDto;
import com.shopbilling.dto.ApiDtos.ReportDto;
import com.shopbilling.dto.ApiDtos.ReturnDto;
import com.shopbilling.dto.ApiDtos.SoldItemDto;
import com.shopbilling.dto.ApiDtos.StockAdjustmentDto;
import com.shopbilling.dto.ApiDtos.SupplierClaimDto;
import com.shopbilling.dto.ApiDtos.SupplierDto;
import com.shopbilling.dto.ApiDtos.SupplierPaymentDto;
import com.shopbilling.dto.ApiDtos.SupplierSettlementDto;
import com.shopbilling.dto.ApiSupport;
import com.shopbilling.model.Customer;
import com.shopbilling.model.Invoice;
import com.shopbilling.model.Purchase;
import com.shopbilling.model.Supplier;
import com.shopbilling.model.SupplierPayment;
import com.shopbilling.model.SupplierSettlement;
import com.shopbilling.repository.AppUserRepository;
import com.shopbilling.repository.CustomerRepository;
import com.shopbilling.repository.DuePaymentRepository;
import com.shopbilling.repository.InvoiceRepository;
import com.shopbilling.repository.ProductRepository;
import com.shopbilling.repository.PurchaseRepository;
import com.shopbilling.repository.ReturnRecordRepository;
import com.shopbilling.repository.StockAdjustmentRepository;
import com.shopbilling.repository.SupplierRepository;
import com.shopbilling.repository.SupplierClaimRepository;
import com.shopbilling.repository.SupplierPaymentRepository;
import com.shopbilling.repository.SupplierSettlementRepository;
import com.shopbilling.service.AppSettingsService;
import com.shopbilling.util.ProductFilters;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalyticsApiController {
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final PurchaseRepository purchases;
    private final ReturnRecordRepository returns;
    private final StockAdjustmentRepository stockAdjustments;
    private final DuePaymentRepository duePayments;
    private final AppUserRepository users;
    private final AppSettingsService appSettings;
    private final SupplierClaimRepository supplierClaims;
    private final SupplierPaymentRepository supplierPayments;
    private final SupplierSettlementRepository supplierSettlements;

    public AnalyticsApiController(ProductRepository products, SupplierRepository suppliers, CustomerRepository customers,
                                  InvoiceRepository invoices, PurchaseRepository purchases, ReturnRecordRepository returns,
                                  StockAdjustmentRepository stockAdjustments, DuePaymentRepository duePayments,
                                  AppUserRepository users, AppSettingsService appSettings,
                                  SupplierClaimRepository supplierClaims, SupplierPaymentRepository supplierPayments,
                                  SupplierSettlementRepository supplierSettlements) {
        this.products = products;
        this.suppliers = suppliers;
        this.customers = customers;
        this.invoices = invoices;
        this.purchases = purchases;
        this.returns = returns;
        this.stockAdjustments = stockAdjustments;
        this.duePayments = duePayments;
        this.users = users;
        this.appSettings = appSettings;
        this.supplierClaims = supplierClaims;
        this.supplierPayments = supplierPayments;
        this.supplierSettlements = supplierSettlements;
    }

    @GetMapping("/reports")
    @Transactional
    public ReportDto reports(Principal principal) {
        return buildReports(canViewSales(principal));
    }

    @GetMapping("/bootstrap")
    @Transactional
    public BootstrapDto bootstrap(Principal principal) {
        boolean includeSales = canViewSales(principal);
        return new BootstrapDto(
                products.findAll().stream().filter(ProductFilters::isValid).toList(),
                suppliers.findAll().stream().map(this::supplierSummary).toList(),
                customers.findAll(),
                invoices.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(InvoiceDto::from).toList(),
                purchases.findAll(Sort.by(Sort.Direction.DESC, "purchaseDate")).stream().map(PurchaseDto::from).toList(),
                returns.findAll(Sort.by(Sort.Direction.DESC, "returnedAt")).stream().map(ReturnDto::from).toList(),
                stockAdjustments.findAll(Sort.by(Sort.Direction.DESC, "adjustedAt")).stream().map(StockAdjustmentDto::from).toList(),
                supplierClaims.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(SupplierClaimDto::from).toList(),
                includeSales ? duePayments.findAll(Sort.by(Sort.Direction.DESC, "paidAt")).stream().map(DuePaymentDto::from).toList() : List.of(),
                supplierPayments.findAll(Sort.by(Sort.Direction.DESC, "paidAt")).stream().map(SupplierPaymentDto::from).toList(),
                supplierSettlements.findAll(Sort.by(Sort.Direction.DESC, "settledAt")).stream().map(SupplierSettlementDto::from).toList(),
                buildReports(includeSales), appSettings.readSettings());
    }

    private ReportDto buildReports(boolean includeSales) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate yearStart = today.withDayOfYear(1);
        var dailyInvoices = invoices.findByCreatedAtBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        var weeklyInvoices = invoices.findByCreatedAtBetween(weekStart.atStartOfDay(), today.plusDays(1).atStartOfDay());
        var monthlyInvoices = invoices.findByCreatedAtBetween(monthStart.atStartOfDay(), today.plusDays(1).atStartOfDay());
        var yearlyInvoices = invoices.findByCreatedAtBetween(yearStart.atStartOfDay(), today.plusDays(1).atStartOfDay());
        BigDecimal dues = customers.findAll().stream().map(Customer::getDueAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, SoldItemDto> soldToday = new LinkedHashMap<>();
        dailyInvoices.forEach(invoice -> invoice.getItems().forEach(item -> {
            SoldItemDto old = soldToday.getOrDefault(item.getProductName(), new SoldItemDto(item.getProductName(), BigDecimal.ZERO, BigDecimal.ZERO));
            soldToday.put(item.getProductName(), new SoldItemDto(item.getProductName(), old.quantity().add(item.getQuantity()), old.amount().add(item.getLineTotal())));
        }));
        return new ReportDto(
                includeSales ? sumTotal(dailyInvoices) : BigDecimal.ZERO,
                includeSales ? sumTotal(weeklyInvoices) : BigDecimal.ZERO,
                includeSales ? sumTotal(monthlyInvoices) : BigDecimal.ZERO,
                includeSales ? sumTotal(yearlyInvoices) : BigDecimal.ZERO,
                includeSales ? sumGst(dailyInvoices) : BigDecimal.ZERO,
                includeSales ? sumGst(weeklyInvoices) : BigDecimal.ZERO,
                includeSales ? sumGst(monthlyInvoices) : BigDecimal.ZERO,
                includeSales ? sumGst(yearlyInvoices) : BigDecimal.ZERO,
                includeSales ? sumProfit(dailyInvoices) : BigDecimal.ZERO,
                includeSales ? sumProfit(weeklyInvoices) : BigDecimal.ZERO,
                includeSales ? sumProfit(monthlyInvoices) : BigDecimal.ZERO,
                includeSales ? sumProfit(yearlyInvoices) : BigDecimal.ZERO,
                includeSales ? dues : BigDecimal.ZERO,
                products.findAll().stream().filter(ProductFilters::isLowStock).toList(),
                includeSales ? customers.findAll() : List.of(),
                includeSales ? soldToday.values().stream().toList() : List.of());
    }

    private BigDecimal sumTotal(List<Invoice> data) {
        return data.stream().map(Invoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumGst(List<Invoice> data) {
        return data.stream().map(Invoice::getGstTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumProfit(List<Invoice> data) {
        return data.stream()
                .flatMap(invoice -> invoice.getItems().stream())
                .map(item -> ApiSupport.nvl(item.getRate()).subtract(ApiSupport.nvl(item.getPurchasePrice())).multiply(ApiSupport.nvl(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean canViewSales(Principal principal) {
        if (principal == null) {
            return false;
        }
        return users.findByUsername(principal.getName())
                .map(user -> user.getRole().name())
                .filter(role -> role.equals("OWNER") || role.equals("ADMIN"))
                .isPresent();
    }

    private SupplierDto supplierSummary(Supplier supplier) {
        var supplierPurchases = purchases.findBySupplierId(supplier.getId(), Sort.by(Sort.Direction.DESC, "purchaseDate"));
        BigDecimal totalPurchases = supplierPurchases.stream().map(Purchase::getTotal).map(ApiSupport::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = supplierPayments.findBySupplierId(supplier.getId(), Sort.unsorted()).stream()
                .map(SupplierPayment::getAmount).map(ApiSupport::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSettled = supplierSettlements.findBySupplierId(supplier.getId(), Sort.unsorted()).stream()
                .map(SupplierSettlement::getAmount).map(ApiSupport::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
        long productCount = supplierPurchases.stream()
                .map(Purchase::getProduct)
                .filter(java.util.Objects::nonNull)
                .map(product -> product.getId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        String lastPurchaseDate = supplierPurchases.isEmpty() ? "" : String.valueOf(supplierPurchases.get(0).getPurchaseDate());
        return SupplierDto.from(supplier, totalPurchases.subtract(totalSettled), totalPaid, productCount, lastPurchaseDate);
    }
}
