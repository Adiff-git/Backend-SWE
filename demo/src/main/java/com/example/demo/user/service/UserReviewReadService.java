package com.example.demo.user.service;

import com.example.demo.user.dto.response.ReviewAuthorSummary;
import java.util.Collection;
import java.util.Map;

public interface UserReviewReadService {
    Map<Long, ReviewAuthorSummary> getReviewAuthors(Collection<Long> ids);
}
