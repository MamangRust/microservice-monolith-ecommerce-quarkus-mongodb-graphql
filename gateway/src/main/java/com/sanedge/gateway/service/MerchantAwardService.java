package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.MerchantAwardDto.CreateMerchantAwardRequest;
import com.sanedge.gateway.dto.MerchantAwardDto.CreateMerchantAwardResponse;
import com.sanedge.gateway.dto.MerchantAwardDto.FindAllMerchantAwardResponse;
import com.sanedge.gateway.dto.MerchantAwardDto.FindByIdMerchantAwardResponse;
import com.sanedge.gateway.dto.MerchantAwardDto.SimpleStatusMessageResponse;
import com.sanedge.gateway.dto.MerchantAwardDto.UpdateMerchantAwardRequest;
import com.sanedge.gateway.dto.MerchantAwardDto.UpdateMerchantAwardResponse;
import io.smallrye.mutiny.Uni;

public interface MerchantAwardService {
    Uni<FindAllMerchantAwardResponse> listMerchantAwards(int page, int size, String search);
    Uni<FindAllMerchantAwardResponse> listActiveMerchantAwards(int page, int size, String search);
    Uni<FindAllMerchantAwardResponse> listTrashedMerchantAwards(int page, int size, String search);
    Uni<FindByIdMerchantAwardResponse> getMerchantAward(String id);
    Uni<CreateMerchantAwardResponse> createMerchantAward(CreateMerchantAwardRequest body);
    Uni<UpdateMerchantAwardResponse> updateMerchantAward(String id, UpdateMerchantAwardRequest body);
    Uni<FindByIdMerchantAwardResponse> deleteMerchantAward(String id);
    Uni<FindByIdMerchantAwardResponse> restoreMerchantAward(String id);
    Uni<SimpleStatusMessageResponse> deleteMerchantAwardPermanent(String id);
    Uni<SimpleStatusMessageResponse> restoreAllMerchantAwards();
    Uni<SimpleStatusMessageResponse> deleteAllMerchantAwardsPermanent();
}
