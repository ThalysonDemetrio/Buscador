package br.com.buscador.history;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PriceChange(BigDecimal previousCost, BigDecimal currentCost) {

    public boolean isDrop() {
        return currentCost.compareTo(previousCost) < 0;
    }

    /** Negativo quando o preço caiu, positivo quando subiu. */
    public int percentage() {
        if (previousCost.signum() == 0) {
            return 0;
        }
        return currentCost.subtract(previousCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousCost, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
