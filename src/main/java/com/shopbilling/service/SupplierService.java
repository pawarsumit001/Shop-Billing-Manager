package com.shopbilling.service;

import com.shopbilling.dto.ApiDtos.SupplierDto;
import com.shopbilling.dto.ApiSupport;
import com.shopbilling.model.Supplier;
import com.shopbilling.repository.SupplierRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SupplierService {
    private final SupplierRepository suppliers;
    private final SupplierLedgerService supplierLedgerService;

    public SupplierService(SupplierRepository suppliers, SupplierLedgerService supplierLedgerService) {
        this.suppliers = suppliers;
        this.supplierLedgerService = supplierLedgerService;
    }

    public List<SupplierDto> findAll() {
        return suppliers.findAll().stream().map(supplierLedgerService::summary).toList();
    }

    public SupplierDto save(Supplier supplier) {
        if (supplier.getName() == null || supplier.getName().isBlank()) {
            throw new IllegalArgumentException("Supplier name required hai");
        }
        if (!ApiSupport.isValidIndianMobile(supplier.getMobileNumber())) {
            throw new IllegalArgumentException("Valid 10 digit mobile number enter karo");
        }
        supplier.setMobileNumber(ApiSupport.normalizeMobile(supplier.getMobileNumber()));
        return supplierLedgerService.summary(suppliers.save(supplier));
    }

    public boolean exists(Long id) {
        return suppliers.existsById(id);
    }

    public void delete(Long id) {
        suppliers.deleteById(id);
    }
}
