package com.placesplates.domain.post.repository;

import com.placesplates.domain.post.entity.PostCategory;

public interface PostCategoryCount {

	PostCategory getCategory();

	long getTotal();
}
