package com.innowise.orderservice.mapper;

import com.innowise.orderservice.dto.response.ItemResponse;
import com.innowise.orderservice.model.Item;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ItemMapper {

    ItemResponse toResponse(Item item);
}
