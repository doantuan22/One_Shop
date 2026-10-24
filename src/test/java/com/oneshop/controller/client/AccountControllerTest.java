package com.oneshop.controller.client;

import com.oneshop.dto.request.UpdateAccountRequest;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.repository.CustomerAddressRepository;
import com.oneshop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountControllerTest {

    private UserRepository userRepository;
    private CustomerAddressRepository addressRepository;
    private MockMvc mockMvc;
    private com.oneshop.service.AccountProfileService profiles;

    private User sampleUser;
    private Principal mockPrincipal;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        addressRepository = mock(CustomerAddressRepository.class);
        profiles = mock(com.oneshop.service.AccountProfileService.class);
        AccountController controller = new AccountController(userRepository, addressRepository, profiles);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Role customerRole = new Role(RoleName.CUSTOMER);

        sampleUser = new User();
        sampleUser.setEmail("customer@oneshop.vn");
        sampleUser.setFullName("Nguyễn Văn Khách");
        sampleUser.setPhone("0912345678");
        sampleUser.setUserName("khachhang");
        sampleUser.setRole(customerRole);

        mockPrincipal = () -> "customer@oneshop.vn";

        when(userRepository.findByEmail("customer@oneshop.vn")).thenReturn(Optional.of(sampleUser));
        when(addressRepository.findByUserEmailOrderByDefaultAddressDescCreatedAtDesc("customer@oneshop.vn"))
                .thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("GET /account trả về view account/profile và đẩy thông tin người dùng vào model")
    void testGetProfile() throws Exception {
        mockMvc.perform(get("/account").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(view().name("account/profile"))
                .andExpect(model().attributeExists("accountUser"))
                .andExpect(model().attributeExists("updateAccountRequest"))
                .andExpect(model().attributeExists("addresses"));
    }

    @Test
    @DisplayName("POST /account cập nhật họ tên và số điện thoại thành công, redirect sang /account?success")
    void testUpdateProfileSuccess() throws Exception {
        mockMvc.perform(post("/account")
                        .principal(mockPrincipal)
                        .param("fullName", "Nguyễn Văn Đổi Tên")
                        .param("phone", "0987654321"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account?success"));

        verify(profiles).update(eq(mockPrincipal.getName()), argThat(r ->
                r.getFullName().equals("Nguyễn Văn Đổi Tên") && r.getPhone().equals("0987654321")));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("POST /account với họ tên để trống báo lỗi validation")
    void testUpdateProfileBlankNameError() throws Exception {
        mockMvc.perform(post("/account")
                        .principal(mockPrincipal)
                        .param("fullName", "")
                        .param("phone", "0987654321"))
                .andExpect(status().isOk())
                .andExpect(view().name("account/profile"))
                .andExpect(model().attributeHasFieldErrors("updateAccountRequest", "fullName"));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(profiles);
    }

    @Test
    @DisplayName("POST /account không cho phép sửa tên đăng nhập, dù có cố tình truyền param userName thì userName vẫn giữ nguyên")
    void testUpdateProfileCannotChangeUserName() throws Exception {
        mockMvc.perform(post("/account")
                        .principal(mockPrincipal)
                        .param("fullName", "Nguyễn Văn Khách")
                        .param("phone", "0912345678")
                        .param("userName", "hacker_username_doi_ten"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account?success"));

        assertEquals("khachhang", sampleUser.getUserName(), "Tên đăng nhập bắt buộc phải giữ nguyên không đổi");
        verify(profiles).update(eq(mockPrincipal.getName()), any(UpdateAccountRequest.class));
        verify(userRepository, never()).save(any());
    }
}
