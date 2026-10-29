package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.FinancialCategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialCategoryRequest {

    @NotBlank(message = "El nombre de la categoría es obligatorio.")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
    private String name;

    @NotNull(message = "El tipo de categoría (INGRESO o GASTO) es obligatorio.")
    private FinancialCategoryType type;

    @NotBlank(message = "El color identificador es obligatorio.")
    @Size(max = 50, message = "El color no puede exceder 50 caracteres.")
    private String color;

    private Boolean isSalesDefault;
}
