package com.oneshop.dto.request;

import com.oneshop.entity.ActiveStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin form of a Store: Store Finder metadata only, no coordinates or map data (Roadmap V2 7.3). */
public class StoreRequest {

    @NotBlank(message = "Vui lòng nhập mã chi nhánh")
    @Size(max = 30, message = "Mã tối đa 30 ký tự")
    private String code;

    @NotBlank(message = "Vui lòng nhập tên chi nhánh")
    @Size(max = 150, message = "Tên tối đa 150 ký tự")
    private String name;

    @NotBlank(message = "Vui lòng nhập địa chỉ")
    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String address;

    @NotBlank(message = "Vui lòng nhập tỉnh/thành")
    @Size(max = 100, message = "Tỉnh/thành tối đa 100 ký tự")
    private String provinceCity;

    @NotBlank(message = "Vui lòng nhập khu vực")
    @Size(max = 100, message = "Khu vực tối đa 100 ký tự")
    private String area;

    @Pattern(regexp = "^$|^[0-9+ ]{8,20}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Size(max = 200, message = "Giờ mở cửa tối đa 200 ký tự")
    private String openingHours;

    private boolean deliveryEnabled = true;

    private boolean pickupEnabled = true;

    @NotNull(message = "Vui lòng chọn trạng thái")
    private ActiveStatus status = ActiveStatus.ACTIVE;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getProvinceCity() {
        return provinceCity;
    }

    public void setProvinceCity(String provinceCity) {
        this.provinceCity = provinceCity;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    public boolean isDeliveryEnabled() {
        return deliveryEnabled;
    }

    public void setDeliveryEnabled(boolean deliveryEnabled) {
        this.deliveryEnabled = deliveryEnabled;
    }

    public boolean isPickupEnabled() {
        return pickupEnabled;
    }

    public void setPickupEnabled(boolean pickupEnabled) {
        this.pickupEnabled = pickupEnabled;
    }

    public ActiveStatus getStatus() {
        return status;
    }

    public void setStatus(ActiveStatus status) {
        this.status = status;
    }
}
