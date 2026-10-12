package com.oneshop.dto.response;

import java.util.List;

/**
 * Everything the home page shows. An empty list hides its section.
 *
 * @param categoryLayout   "none", "single", "pair", "bento" (exactly three) or "grid" (four or more)
 * @param searchCategories up to three categories offered as search suggestions under the search bar
 * @param searchBrands     up to three brands offered as search suggestions
 */
public record HomeViewModel(List<HomeCategoryTile> categories, String categoryLayout, List<ProductCardResponse> products,
                            List<HomeBrandTile> brands, List<HomeCategoryTile> searchCategories,
                            List<HomeBrandTile> searchBrands, List<StoreResponse> stores) {
}
