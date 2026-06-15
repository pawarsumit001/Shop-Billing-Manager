package com.shopbilling.service;

import com.shopbilling.dto.ApiSupport;
import com.shopbilling.model.Customer;
import com.shopbilling.repository.CustomerRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {
    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    public List<Customer> findAll() {
        return customers.findAll();
    }

    public Customer save(Customer customer) {
        boolean hasName = customer.getName() != null && !customer.getName().isBlank();
        boolean hasMobile = customer.getMobileNumber() != null && !customer.getMobileNumber().isBlank();
        if (!hasName && !hasMobile) {
            throw new IllegalArgumentException("Customer name ya mobile required hai");
        }
        if (!ApiSupport.isValidIndianMobile(customer.getMobileNumber())) {
            throw new IllegalArgumentException("Valid 10 digit mobile number enter karo");
        }
        customer.setMobileNumber(ApiSupport.normalizeMobile(customer.getMobileNumber()));
        Customer existingByMobile = hasMobile
                ? customers.findFirstByMobileNumberOrderByIdAsc(customer.getMobileNumber()).orElse(null)
                : null;
        if (existingByMobile != null && !Objects.equals(existingByMobile.getId(), customer.getId())) {
            if (customer.getId() == null) {
                throw new IllegalArgumentException("Customer already exists. Search karke existing customer select karo");
            }
            throw new IllegalArgumentException("Ye mobile number dusre customer ke naam se already saved hai");
        }
        if (customer.getId() == null && !hasMobile) {
            Customer existingByName = customers.findFirstByNameIgnoreCaseOrderByIdAsc(customer.getName()).orElse(null);
            if (existingByName != null) {
                throw new IllegalArgumentException("Customer already exists. Search karke existing customer select karo");
            }
        }
        if (customer.getId() != null) {
            Customer existing = customers.findById(customer.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
            if (customer.getDueAmount() == null) {
                customer.setDueAmount(existing.getDueAmount());
            }
        }
        customer.setDueAmount(ApiSupport.nvl(customer.getDueAmount()));
        return customers.save(customer);
    }

    public boolean exists(Long id) {
        return customers.existsById(id);
    }

    public void delete(Long id) {
        Customer customer = customers.findById(id).orElseThrow();
        if (ApiSupport.nvl(customer.getDueAmount()).compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Customer ka due pending hai, delete nahi kar sakte");
        }
        customers.deleteById(id);
    }
}
