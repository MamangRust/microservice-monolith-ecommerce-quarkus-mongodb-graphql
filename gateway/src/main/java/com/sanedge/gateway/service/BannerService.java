package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.BannerDto.CreateBannerRequest;
import com.sanedge.gateway.dto.BannerDto.CreateBannerResponse;
import com.sanedge.gateway.dto.BannerDto.FindAllBannerResponse;
import com.sanedge.gateway.dto.BannerDto.FindByIdBannerResponse;
import com.sanedge.gateway.dto.BannerDto.SimpleStatusMessageResponse;
import com.sanedge.gateway.dto.BannerDto.UpdateBannerRequest;
import com.sanedge.gateway.dto.BannerDto.UpdateBannerResponse;
import io.smallrye.mutiny.Uni;

public interface BannerService {
    Uni<FindAllBannerResponse> listBanners(int page, int size, String search);

    Uni<FindAllBannerResponse> listActiveBanners(int page, int size, String search);

    Uni<FindAllBannerResponse> listTrashedBanners(int page, int size, String search);

    Uni<FindByIdBannerResponse> getBanner(String id);

    Uni<CreateBannerResponse> createBanner(CreateBannerRequest body);

    Uni<UpdateBannerResponse> updateBanner(String id, UpdateBannerRequest body);

    Uni<FindByIdBannerResponse> deleteBanner(String id);

    Uni<FindByIdBannerResponse> restoreBanner(String id);

    Uni<SimpleStatusMessageResponse> deleteBannerPermanent(String id);

    Uni<SimpleStatusMessageResponse> restoreAllBanners();

    Uni<SimpleStatusMessageResponse> deleteAllBannersPermanent();
}
