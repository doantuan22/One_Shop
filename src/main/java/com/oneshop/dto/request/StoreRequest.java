package com.oneshop.dto.request;

import com.oneshop.entity.ActiveStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin create/update of a Store and its Store Finder metadata. Stores are deactivated, never deleted (BR-17). */
public record StoreRequest(
        @NotBlank(message = "Vui lòng nhập mã chi nhánh") @Size(max = 30, message = "Mã tối đa 30 ký tự") String code,
        @NotBlank(message = "Vui lòng nhập tên chi nhánh") @Size(max = 150, message = "Tên tối đa 150 ký tự") String name,
        @NotBlank(message = "Vui lòng nhập địa chỉ") @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự") String address,
        @NotBlank(message = "Vui lòng nhập tỉnh/thành") @Size(max = 100, message = "Tỉnh/thành tối đa 100 ký tự") String provinceCity,
        @NotBlank(message = "Vui lòng nhập khu vực") @Size(max = 100, message = "Khu vực tối đa 100 ký tự") String area,
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự") String phone,
        @Size(max = 200, message = "Giờ mở cửa tối đa 200 ký tự") String openingHours,
        boolean deliveryEnabled,
        boolean pickupEnabled,
        @NotNull(message = "Vui lòng chọn trạng thái") ActiveStatus status) {
}
