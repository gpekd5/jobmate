package com.jobmate.profile.application.result;

import com.jobmate.profile.domain.entity.PortfolioLink;

public record PortfolioLinkResult(Long linkId, String url, String description) {

    public static PortfolioLinkResult from(PortfolioLink link) {
        return new PortfolioLinkResult(link.getId(), link.getUrl(), link.getDescription());
    }
}
