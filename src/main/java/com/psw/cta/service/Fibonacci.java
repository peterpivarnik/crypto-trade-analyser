package com.psw.cta.service;

import static java.math.RoundingMode.CEILING;

import java.math.BigDecimal;

public class Fibonacci {
    public static final BigDecimal[] FIBONACCI_SEQUENCE = new BigDecimal[]{new BigDecimal("1"),
                                                                           new BigDecimal("1"),
                                                                           new BigDecimal("2"),
                                                                           new BigDecimal("3"),
                                                                           new BigDecimal("5"),
                                                                           new BigDecimal("8"),
                                                                           new BigDecimal("13"),
                                                                           new BigDecimal("21"),
                                                                           new BigDecimal("34"),
                                                                           new BigDecimal("55"),
                                                                           new BigDecimal("89"),
                                                                           new BigDecimal("144"),
                                                                           new BigDecimal("233"),
                                                                           new BigDecimal("377"),
                                                                           new BigDecimal("610"),
                                                                           new BigDecimal("987"),
                                                                           new BigDecimal("1597"),
                                                                           new BigDecimal("2584"),
                                                                           new BigDecimal("4181"),
                                                                           new BigDecimal("6765"),
                                                                           new BigDecimal("10946"),
                                                                           new BigDecimal("17711"),
                                                                           new BigDecimal("28657"),
                                                                           new BigDecimal("46368"),
                                                                           new BigDecimal("75025"),
                                                                           new BigDecimal("121393")};

    public static final BigDecimal GOLDEN_RATIO =
        FIBONACCI_SEQUENCE[FIBONACCI_SEQUENCE.length - 1].divide(FIBONACCI_SEQUENCE[FIBONACCI_SEQUENCE.length - 2], 8, CEILING);
    public static final BigDecimal HUNDREDTH_OF_GOLDEN_RATIO = GOLDEN_RATIO.divide(new BigDecimal("100"), 8, CEILING);

    private Fibonacci() {
    }
}
