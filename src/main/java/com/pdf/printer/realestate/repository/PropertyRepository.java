
package com.pdf.printer.realestate.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.pdf.printer.realestate.model.Property;
import com.pdf.printer.realestate.model.SaleStatus;

public interface PropertyRepository extends JpaRepository<Property, Long> {
    List<Property> findByStatus(SaleStatus status);
}
