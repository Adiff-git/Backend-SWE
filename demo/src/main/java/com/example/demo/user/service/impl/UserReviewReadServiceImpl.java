package com.example.demo.user.service.impl;

import com.example.demo.media.dto.response.ImageAccessResponse;
import com.example.demo.user.dto.response.ReviewAuthorSummary;
import com.example.demo.user.entity.User;
import com.example.demo.user.enums.AccountStatus;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.UserReviewReadService;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class UserReviewReadServiceImpl implements UserReviewReadService {
    private final UserRepository users;
    @Override @Transactional(readOnly = true)
    public Map<Long, ReviewAuthorSummary> getReviewAuthors(Collection<Long> ids) {
        Map<Long, ReviewAuthorSummary> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        List<User> authors = users.findAllById(ids);
        // Return only safe identity and avatar references. The caller batches avatars together
        // with review media so the whole response uses one metadata query and one URL signer.
        for (User user : authors) {
            result.put(user.getId(), user.getAccountStatus() == AccountStatus.ACTIVE
                    ? new ReviewAuthorSummary(user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getUsername() : user.getDisplayName(), user.getProfileImageId() == null ? null : new ImageAccessResponse(user.getProfileImageId(), null))
                    : ReviewAuthorSummary.formerCustomer());
        }
        return result;
    }
}
