package com.shopbilling.repository;

import com.shopbilling.model.Customer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findFirstByMobileNumberOrderByIdAsc(String mobileNumber);
    Optional<Customer> findFirstByNameIgnoreCaseOrderByIdAsc(String name);
}
