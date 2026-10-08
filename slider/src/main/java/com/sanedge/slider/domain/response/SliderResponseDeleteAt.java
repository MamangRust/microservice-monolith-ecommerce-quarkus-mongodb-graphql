package com.sanedge.slider.domain.response;

import com.sanedge.slider.entity.Slider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SliderResponseDeleteAt {
    private String id;
    private String name;
    private String image;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;

    public static SliderResponseDeleteAt from(Slider entity) {
        return SliderResponseDeleteAt.builder()
                .id(entity.id != null ? entity.id.toHexString() : null)
                .name(entity.getName())
                .image(entity.getImage())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .deletedAt(entity.getDeletedAt() != null ? entity.getDeletedAt().toString() : null)
                .build();
    }
}