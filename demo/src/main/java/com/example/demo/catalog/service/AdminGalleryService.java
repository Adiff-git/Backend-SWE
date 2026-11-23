package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.request.AdminGalleryRequests.Attach;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Attachment;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Gallery;
import java.util.List;
import java.util.UUID;

public interface AdminGalleryService {
    Gallery get(UUID product);
    Attachment attach(UUID product,Long actor,Attach request);
    Gallery primary(UUID product,UUID image,long version,long taxonomy);
    Gallery reorder(UUID product,long version,long taxonomy,List<UUID> order);
    void remove(UUID product,UUID image,long version,long taxonomy,UUID replacement);
}
