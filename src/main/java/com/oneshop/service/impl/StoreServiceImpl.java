package com.oneshop.service.impl;

import com.oneshop.dto.response.StoreResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.mapper.StoreMapper;
import com.oneshop.repository.StaffStoreAssignmentRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.StoreService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StoreServiceImpl implements StoreService {

    private final StoreRepository storeRepository;
    private final StaffStoreAssignmentRepository assignmentRepository;
    private final StoreMapper storeMapper;

    public StoreServiceImpl(StoreRepository storeRepository, StaffStoreAssignmentRepository assignmentRepository,
                            StoreMapper storeMapper) {
        this.storeRepository = storeRepository;
        this.assignmentRepository = assignmentRepository;
        this.storeMapper = storeMapper;
    }

    @Override
    public List<StoreResponse> getActiveStores() {
        return storeRepository.findByStatusOrderByProvinceCityAscAreaAscNameAsc(ActiveStatus.ACTIVE).stream()
                .map(storeMapper::toResponse)
                .toList();
    }

    @Override
    public StoreResponse getStore(Long id) {
        return storeRepository.findById(id)
                .map(storeMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh #" + id));
    }

    @Override
    public List<StoreResponse> getAssignedStores(String staffEmail) {
        if (staffEmail == null) {
            return List.of();
        }
        return assignmentRepository.findActiveStoresByStaffEmail(staffEmail).stream()
                .map(storeMapper::toResponse)
                .toList();
    }

    @Override
    public StoreResponse requireAssignedStore(String staffEmail, Long storeId) {
        // Same answer whether the Store does not exist or belongs to someone else: nothing leaks about other Stores.
        return getAssignedStores(staffEmail).stream()
                .filter(store -> store.id().equals(storeId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Bạn không được phân công tại chi nhánh này"));
    }
}
