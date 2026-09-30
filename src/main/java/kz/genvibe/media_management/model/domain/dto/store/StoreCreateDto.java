package kz.genvibe.media_management.model.domain.dto.store;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreCreateDto(
    @NotBlank @Size(min = 3, message = "{validation.store.name}")
    String name,
    String location,
    @Email String email
) { }
