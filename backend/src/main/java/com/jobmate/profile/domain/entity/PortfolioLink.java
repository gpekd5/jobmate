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
@Table(name = "portfolio_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long ownerId;

    @Column(nullable = false, length = 300)
    private String url;

    @Column(length = 500)
    private String description;

    public static PortfolioLink create(String url, String description) {
        PortfolioLink link = new PortfolioLink();
        link.ownerId = 1;
        link.url = url;
        link.description = description;
        return link;
    }
}
