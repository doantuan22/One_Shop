package com.oneshop.service;

import com.oneshop.dto.request.CategoryRequest;
import com.oneshop.dto.response.CategoryResponse;
import com.oneshop.dto.response.ImageUploadResult;
import com.oneshop.entity.Category;
import com.oneshop.entity.VisibilityStatus;
import com.oneshop.mapper.CatalogMapper;
import com.oneshop.mapper.ProductMapper;
import com.oneshop.repository.BrandRepository;
import com.oneshop.repository.CategoryRepository;
import com.oneshop.repository.ProductImageRepository;
import com.oneshop.repository.ProductRepository;
import com.oneshop.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                productRepository,
                productImageRepository,
                categoryRepository,
                brandRepository,
                cloudinaryService,
                new ProductMapper(),
                new CatalogMapper()
        );
    }

    private CategoryRequest createRequest(String name, String desc, VisibilityStatus status) {
        CategoryRequest req = new CategoryRequest();
        req.setName(name);
        req.setDescription(desc);
        req.setStatus(status);
        return req;
    }

    private Category createCategoryEntity(Long id, String name, String imageUrl, String imagePublicId) {
        Category c = new Category();
        if (id != null) {
            ReflectionTestUtils.setField(c, "id", id);
        }
        c.setName(name);
        c.setImageUrl(imageUrl);
        c.setImagePublicId(imagePublicId);
        c.setStatus(VisibilityStatus.ACTIVE);
        return c;
    }

    @Test
    void createCategory_withoutImage() {
        CategoryRequest request = createRequest("Skincare", "All skincare", VisibilityStatus.ACTIVE);
        when(categoryRepository.existsByName("Skincare")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 10L);
            return c;
        });

        CategoryResponse response = productService.createCategory(request, null);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Skincare");
        assertThat(response.imageUrl()).isNull();

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getImageUrl()).isNull();
        assertThat(captor.getValue().getImagePublicId()).isNull();
        verify(cloudinaryService, never()).uploadImage(any(), anyString());
    }

    @Test
    void createCategory_withImage_success() {
        CategoryRequest request = createRequest("Skincare", "All skincare", VisibilityStatus.ACTIVE);
        MockMultipartFile file = new MockMultipartFile("file", "cat.png", "image/png", new byte[]{1, 2, 3});

        when(categoryRepository.existsByName("Skincare")).thenReturn(false);
        when(cloudinaryService.uploadImage(file, "categories"))
                .thenReturn(new ImageUploadResult("https://res.cloudinary.com/demo/image/upload/v1/cat.png", "cat_pub_123"));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 10L);
            return c;
        });

        CategoryResponse response = productService.createCategory(request, file);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.imageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/cat.png");

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getImageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/cat.png");
        assertThat(captor.getValue().getImagePublicId()).isEqualTo("cat_pub_123");
    }

    @Test
    void createCategory_withImage_uploadFails_doesNotSaveCategory() {
        CategoryRequest request = createRequest("Skincare", "All skincare", VisibilityStatus.ACTIVE);
        MockMultipartFile file = new MockMultipartFile("file", "cat.png", "image/png", new byte[]{1, 2, 3});

        when(categoryRepository.existsByName("Skincare")).thenReturn(false);
        when(cloudinaryService.uploadImage(file, "categories")).thenThrow(new RuntimeException("Cloudinary network timeout"));

        assertThatThrownBy(() -> productService.createCategory(request, file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cloudinary network timeout");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_withoutNewImage_keepsExistingImage() {
        Category category = createCategoryEntity(5L, "Makeup", "https://res.cloudinary.com/demo/image/upload/v1/makeup.png", "makeup_pub_1");

        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameAndIdNot("Makeup Pro", 5L)).thenReturn(false);

        CategoryRequest request = createRequest("Makeup Pro", "Updated", VisibilityStatus.ACTIVE);
        CategoryResponse response = productService.updateCategory(5L, request, null);

        assertThat(response.name()).isEqualTo("Makeup Pro");
        assertThat(response.imageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/makeup.png");
        assertThat(category.getImagePublicId()).isEqualTo("makeup_pub_1");
        verify(cloudinaryService, never()).uploadImage(any(), anyString());
        verify(cloudinaryService, never()).deleteImage(anyString());
    }

    @Test
    void updateCategory_withNewImage_replacesAndDeletesOldOnCloudinary() {
        Category category = createCategoryEntity(5L, "Makeup", "https://res.cloudinary.com/demo/image/upload/v1/old.png", "old_pub_id");
        MockMultipartFile newFile = new MockMultipartFile("file", "new.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameAndIdNot("Makeup", 5L)).thenReturn(false);
        when(cloudinaryService.uploadImage(newFile, "categories"))
                .thenReturn(new ImageUploadResult("https://res.cloudinary.com/demo/image/upload/v2/new.jpg", "new_pub_id"));
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryRequest request = createRequest("Makeup", "Updated", VisibilityStatus.ACTIVE);
        CategoryResponse response = productService.updateCategory(5L, request, newFile);

        assertThat(response.imageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v2/new.jpg");
        assertThat(category.getImagePublicId()).isEqualTo("new_pub_id");

        // Old Cloudinary image deleted
        verify(cloudinaryService).deleteImage("old_pub_id");
    }

    @Test
    void updateCategory_withNewImage_whenOldImageHasNullPublicId_doesNotCallCloudinaryDelete() {
        Category category = createCategoryEntity(1L, "Skincare", "/images/site/categories/category-skincare.webp", null);
        MockMultipartFile newFile = new MockMultipartFile("file", "new.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameAndIdNot("Skincare", 1L)).thenReturn(false);
        when(cloudinaryService.uploadImage(newFile, "categories"))
                .thenReturn(new ImageUploadResult("https://res.cloudinary.com/demo/image/upload/v2/new.jpg", "new_pub_id"));
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryRequest request = createRequest("Skincare", "Updated", VisibilityStatus.ACTIVE);
        CategoryResponse response = productService.updateCategory(1L, request, newFile);

        assertThat(response.imageUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v2/new.jpg");
        // Must NOT call delete on Cloudinary because old public_id was null
        verify(cloudinaryService, never()).deleteImage(anyString());
    }

    @Test
    void updateCategory_withNewImage_whenDbSaveFails_cleansUpNewImageAndKeepsOld() {
        Category category = createCategoryEntity(5L, "Makeup", "https://res.cloudinary.com/demo/image/upload/v1/old.png", "old_pub_id");
        MockMultipartFile newFile = new MockMultipartFile("file", "new.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameAndIdNot("Makeup", 5L)).thenReturn(false);
        when(cloudinaryService.uploadImage(newFile, "categories"))
                .thenReturn(new ImageUploadResult("https://res.cloudinary.com/demo/image/upload/v2/new.jpg", "new_pub_id"));
        when(categoryRepository.saveAndFlush(any(Category.class))).thenThrow(new RuntimeException("DB write failure"));

        CategoryRequest request = createRequest("Makeup", "Updated", VisibilityStatus.ACTIVE);

        assertThatThrownBy(() -> productService.updateCategory(5L, request, newFile))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB write failure");

        // Cleans up newly uploaded image to prevent orphan
        verify(cloudinaryService).deleteImage("new_pub_id");
        // Old image is not deleted
        verify(cloudinaryService, never()).deleteImage("old_pub_id");
    }

    @Test
    void removeCategoryImage_whenPublicIdNotNull_deletesFromCloudinaryAndClearsDb() {
        Category category = createCategoryEntity(7L, "Bodycare", "https://res.cloudinary.com/demo/image/upload/v1/body.png", "body_pub_123");

        when(categoryRepository.findById(7L)).thenReturn(Optional.of(category));
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.removeCategoryImage(7L);

        assertThat(category.getImageUrl()).isNull();
        assertThat(category.getImagePublicId()).isNull();
        verify(cloudinaryService).deleteImage("body_pub_123");
        verify(categoryRepository).saveAndFlush(category);
    }

    @Test
    void removeCategoryImage_whenPublicIdNull_clearsDbWithoutCallingCloudinary() {
        Category category = createCategoryEntity(7L, "Bodycare", "/images/site/categories/category-bodycare.webp", null);

        when(categoryRepository.findById(7L)).thenReturn(Optional.of(category));
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.removeCategoryImage(7L);

        assertThat(category.getImageUrl()).isNull();
        assertThat(category.getImagePublicId()).isNull();
        verify(cloudinaryService, never()).deleteImage(anyString());
        verify(categoryRepository).saveAndFlush(category);
    }
}

