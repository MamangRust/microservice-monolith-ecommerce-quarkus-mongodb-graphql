package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.ShippingAddressDto.CreateShippingAddressRequest;
import com.sanedge.gateway.dto.ShippingAddressDto.CreateShippingAddressResponse;
import com.sanedge.gateway.dto.ShippingAddressDto.FindAllShippingResponse;
import com.sanedge.gateway.dto.ShippingAddressDto.FindByIdShippingResponse;
import com.sanedge.gateway.dto.ShippingAddressDto.SimpleStatusMessageResponse;
import com.sanedge.gateway.dto.ShippingAddressDto.UpdateShippingAddressRequest;
import com.sanedge.gateway.dto.ShippingAddressDto.UpdateShippingAddressResponse;
import io.smallrye.mutiny.Uni;

public interface ShippingAddressService {
    Uni<FindAllShippingResponse> listShippingAddresses(int page, int size, String search);
    Uni<FindByIdShippingResponse> getShippingAddressByOrder(String orderId);
    Uni<FindAllShippingResponse> listActiveShippingAddresses(int page, int size, String search);
    Uni<FindAllShippingResponse> listTrashedShippingAddresses(int page, int size, String search);
    Uni<FindByIdShippingResponse> getShippingAddress(String id);
    Uni<CreateShippingAddressResponse> createShippingAddress(CreateShippingAddressRequest body);
    Uni<UpdateShippingAddressResponse> updateShippingAddress(String id, UpdateShippingAddressRequest body);
    Uni<FindByIdShippingResponse> deleteShippingAddress(String id);
    Uni<FindByIdShippingResponse> restoreShippingAddress(String id);
    Uni<SimpleStatusMessageResponse> deleteShippingAddressPermanent(String id);
    Uni<SimpleStatusMessageResponse> restoreAllShippingAddresses();
    Uni<SimpleStatusMessageResponse> deleteAllShippingAddressesPermanent();
}
