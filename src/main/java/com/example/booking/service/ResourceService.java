package com.example.booking.service;

import com.example.booking.dto.*;
import com.example.booking.entity.BookableResource;
import com.example.booking.exception.ResourceNotFoundException;
import com.example.booking.repository.ResourceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResourceService {
    private final ResourceRepository repository;

    public ResourceService(ResourceRepository repository) {
        this.repository = repository;
    }

    public List<ResourceResponse> findAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public ResourceResponse findById(Long id) {
        return toResponse(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found: " + id)));
    }

    public ResourceResponse create(ResourceRequest request) {
        BookableResource resource = new BookableResource();
        apply(resource, request);
        return toResponse(repository.save(resource));
    }

    public ResourceResponse update(Long id, ResourceRequest request) {
        BookableResource resource = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found: " + id));
        apply(resource, request);
        return toResponse(repository.save(resource));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Resource not found: " + id);
        }
        repository.deleteById(id);
    }

    private void apply(BookableResource resource, ResourceRequest request) {
        resource.setName(request.name());
        resource.setDescription(request.description());
        resource.setType(request.type());
        resource.setPricePerHour(request.pricePerHour());
        resource.setAvailable(request.available());
    }

    private ResourceResponse toResponse(BookableResource r) {
        return new ResourceResponse(
                r.getId(), r.getName(), r.getDescription(), r.getType(),
                r.getPricePerHour(), r.getAvailable());
    }
}
