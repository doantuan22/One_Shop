package com.oneshop.service.impl;

import com.oneshop.dto.response.StoreResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.mapper.StoreMapper;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.StoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StoreServiceImpl implements StoreService {

    private final StoreRepository storeRepository;
    private final StoreMapper storeMapper;

    public StoreServiceImpl(StoreRepository storeRepository, StoreMapper storeMapper) {
        this.storeRepository = storeRepository;
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
}
