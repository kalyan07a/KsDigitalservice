
package com.pdf.printer.realestate.service;

import java.util.List;
import com.pdf.printer.realestate.dto.PropertyDto;
import com.pdf.printer.realestate.model.Property;
import com.pdf.printer.realestate.model.SaleStatus;

public interface PropertyService {
    Property create(PropertyDto dto);
    List<Property> findAll();
    List<Property> findByStatus(SaleStatus status);
    Property updateStatus(Long id, SaleStatus status);
    void deleteById(Long id);
}
