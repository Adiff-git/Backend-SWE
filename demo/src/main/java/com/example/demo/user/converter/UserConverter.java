package com.example.demo.user.converter;
import com.example.demo.user.entity.User;
import com.example.demo.user.dto.response.PrivateUserResponse;
import com.example.demo.user.dto.response.PublicUserResponse;
import org.springframework.stereotype.Component;
@Component
public class UserConverter {
    public PrivateUserResponse toPrivate(User u) {
        PrivateUserResponse d=new PrivateUserResponse();
        d.setId(u.getId());
        d.setUsername(u.getUsername());
        d.setDisplayName(u.getDisplayName());
        d.setEmail(u.getEmail());
        d.setPhoneNumber(u.getPhoneNumber());
        d.setGender(u.getGender());
        d.setDateOfBirth(u.getDateOfBirth());
        d.setAccountStatus(u.getAccountStatus());
        d.setJoinedAt(u.getJoinedAt());
        return d;
    }
    public PublicUserResponse toPublic(User u) {
        PublicUserResponse d=new PublicUserResponse();
        d.setId(u.getId());
        d.setUsername(u.getUsername());
        d.setDisplayName(u.getDisplayName());
        d.setJoinedAt(u.getJoinedAt());
        return d;
    }
}
