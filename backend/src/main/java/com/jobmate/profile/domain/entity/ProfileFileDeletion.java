package com.jobmate.profile.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "profile_file_deletion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileFileDeletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String storageKey;

    public static ProfileFileDeletion of(String key) {
        ProfileFileDeletion deletion = new ProfileFileDeletion();
        deletion.storageKey = key;
        return deletion;
    }
}
