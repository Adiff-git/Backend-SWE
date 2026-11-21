package com.example.demo.review.service;
import com.example.demo.review.dto.request.ReviewListRequest;
import com.example.demo.review.dto.response.ReviewPage;
import java.util.UUID;
public interface ReviewReadService { ReviewPage getReviews(UUID product, ReviewListRequest request); }
