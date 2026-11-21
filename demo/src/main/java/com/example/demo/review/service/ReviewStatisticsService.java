package com.example.demo.review.service;
import com.example.demo.review.dto.response.ProductRatingSummary;
import java.util.UUID;
public interface ReviewStatisticsService { ProductRatingSummary getSummary(UUID product); }
