package com.psw.cta.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeastSquaresTest {

    @InjectMocks
    private LeastSquares leastSquares;

    @Test
    public void shouldCalculateCorrectSlopeFromLeastSquare() {
        List<BigDecimal> data = Arrays.asList(new BigDecimal("1"), new BigDecimal("5"), new BigDecimal("13"), new BigDecimal("21"), new BigDecimal("45"));

        double slope = leastSquares.getSlope(data);

        assertThat(slope).isEqualTo(10.4);
    }
}