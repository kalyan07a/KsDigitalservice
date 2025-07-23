
package com.pdf.printer.realestate.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pdf.printer.realestate.dto.PropertyDto;
import com.pdf.printer.realestate.model.Property;
import com.pdf.printer.realestate.model.SaleStatus;
import com.pdf.printer.realestate.repository.PropertyRepository;
import com.pdf.printer.realestate.service.FileStorageService;
import com.pdf.printer.realestate.service.PropertyService;

@Service
@Transactional
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository repo;
    private final FileStorageService fileService;

    public PropertyServiceImpl(PropertyRepository repo, FileStorageService fileService) {
        this.repo = repo;
        this.fileService = fileService;
    }

    @Override
    public Property create(PropertyDto dto) {
        List<String> storedNames = fileService.store(dto.getImages());
        Property entity = Property.builder()
                .productId(dto.getProductId())
                .title(dto.getTitle())
                .location(dto.getLocation())
                .carpetArea(dto.getCarpetArea())
                .amount(dto.getAmount())
                .additionalInfo(dto.getAdditionalInfo())
                .imageNames(String.join(",", storedNames))
                .status(SaleStatus.ON_SALE)
                .build();
        return repo.save(entity);
    }

    @Override public List<Property> findAll() { return repo.findAll(); }

    @Override public List<Property> findByStatus(SaleStatus status) {
        return repo.findByStatus(status);
    }

    @Override
    public Property updateStatus(Long id, SaleStatus status) {
        Property p = repo.findById(id)
                         .orElseThrow(() -> new RuntimeException("Property not found"));
        p.setStatus(status);
        return repo.save(p);
    }
    @Override
    public void deleteById(Long id) {
        repo.deleteById(id);
    }
}
