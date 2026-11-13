package com.example.demo.behavior;
import java.util.List;
public record BehaviorPage(List<BehaviorEvent> items, int page, int size, long totalItems, long totalPages) { }
