package com.oneshop.service.impl;

import com.oneshop.dto.request.StoreRequest;
import com.oneshop.dto.response.StoreResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.Store;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.mapper.StoreMapper;
import com.oneshop.repository.StaffStoreAssignmentRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.service.StoreService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

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
        return storeMapper.toResponse(store(id));
    }

    // ------------------------------------------------------------------ Client

    @Override
    public List<StoreResponse> findStores(String provinceCity, String area) {
        // a chain has a handful of Stores: filtering in memory keeps the three filter combinations in one place
        return storeRepository.findAllByOrderByProvinceCityAscAreaAscNameAsc().stream()
                .filter(store -> matches(provinceCity, store.getProvinceCity()))
                .filter(store -> matches(area, store.getArea()))
                .map(storeMapper::toResponse)
                .toList();
    }

    @Override
    public List<String> getProvinceCities() {
        return storeRepository.findAllByOrderByProvinceCityAscAreaAscNameAsc().stream()
                .map(Store::getProvinceCity).distinct().toList();
    }

    @Override
    public List<String> getAreas(String provinceCity) {
        return storeRepository.findAllByOrderByProvinceCityAscAreaAscNameAsc().stream()
                .filter(store -> matches(provinceCity, store.getProvinceCity()))
                .map(Store::getArea).distinct().toList();
    }

    private static boolean matches(String filter, String value) {
        return !StringUtils.hasText(filter) || filter.trim().equalsIgnoreCase(value);
    }

    @Override
    public Optional<StoreResponse> findSelectableStore(Long storeId) {
        if (storeId == null) {
            return Optional.empty();
        }
        return storeRepository.findByIdAndStatus(storeId, ActiveStatus.ACTIVE).map(storeMapper::toResponse);
    }

    @Override
    public StoreResponse requireSelectableStore(Long storeId) {
        return findSelectableStore(storeId)
                .orElseThrow(() -> new BadRequestException("storeId", "Chi nhánh không tồn tại hoặc đã ngừng hoạt động"));
    }

    // ------------------------------------------------------------------ Admin

    @Override
    public List<StoreResponse> getAllStores() {
        return storeRepository.findAllByOrderByProvinceCityAscAreaAscNameAsc().stream()
                .map(storeMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public StoreResponse createStore(StoreRequest request) {
        String code = request.getCode().trim();
        if (storeRepository.existsByCode(code)) {
            throw new BadRequestException("code", "Mã chi nhánh đã tồn tại");
        }
        Store store = new Store();
        apply(store, code, request);
        return storeMapper.toResponse(storeRepository.save(store));
    }

    @Override
    @Transactional
    public StoreResponse updateStore(Long id, StoreRequest request) {
        Store store = store(id);
        String code = request.getCode().trim();
        if (storeRepository.existsByCodeAndIdNot(code, id)) {
            throw new BadRequestException("code", "Mã chi nhánh đã tồn tại");
        }
        apply(store, code, request);
        return storeMapper.toResponse(store);
    }

    private static void apply(Store store, String code, StoreRequest request) {
        store.setCode(code);
        store.setName(request.getName().trim());
        store.setAddress(request.getAddress().trim());
        store.setProvinceCity(request.getProvinceCity().trim());
        store.setArea(request.getArea().trim());
        store.setPhone(StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null);
        store.setOpeningHours(StringUtils.hasText(request.getOpeningHours()) ? request.getOpeningHours().trim() : null);
        store.setDeliveryEnabled(request.isDeliveryEnabled());
        store.setPickupEnabled(request.isPickupEnabled());
        store.setStatus(request.getStatus());
    }

    private Store store(Long id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh #" + id));
    }

    // ------------------------------------------------------------------ Staff Store scope

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
