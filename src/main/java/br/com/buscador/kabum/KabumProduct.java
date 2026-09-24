package br.com.buscador.kabum;

import java.math.BigDecimal;

public record KabumProduct(
        String code,
        String name,
        String friendlyName,
        BigDecimal price,
        BigDecimal priceWithDiscount,
        boolean available,
        String sellerName,
        boolean marketplace,
        String warranty
) {}
