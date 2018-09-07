package com.psw.cta.service;

import com.psw.cta.service.dto.CryptoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
class LoggingServiceImpl {

    @Async
    void log(List<CryptoDto> cryptoDtos) {
        String text = prepareText2h(cryptoDtos) + prepareText5h(cryptoDtos) + prepareText10h(cryptoDtos) + prepareText24h(
                cryptoDtos);
        log.info(text);
        final String fileName = "/home/peter/work/crypto/" + LocalDateTime.now().toString() + ".txt";
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "utf-8"))) {
            writer.write(text);
        } catch (IOException e) {
            log.error(e.getMessage());
            log.error(e.getClass().toString());
        }
    }

    private String prepareText2h(List<CryptoDto> cryptoDtos) {
        final String prefix = String.format(
                "\r\n\r\nResults for 2h cryptos\r\n%10s\t%20s\t%20s\t%20s\t%20s\t%20s\r\n",
                "symbol",
                "price",
                "priceToSell",
                "priceToSellPercentage",
                "sumDiffPercent",
                "weight");

        return cryptoDtos.stream()
                .filter(cryptoDto -> cryptoDto.getPriceToSellPercentage2h().compareTo(new BigDecimal("0.5")) > 0)
                .sorted(Comparator.comparing(CryptoDto::getSumDiffsPerc2h))
                .map(marketDto -> (String.format
                        ("%10s\t%20.8f\t%20.8f\t%20.8f\t%20.8f\t%20.8f\t",
                         marketDto.getBinanceExchangeSymbol().getSymbol().getSymbol(),
                         marketDto.getCurrentPrice(),
                         marketDto.getPriceToSell2h(),
                         marketDto.getPriceToSellPercentage2h(),
                         marketDto.getSumDiffsPerc2h(),
                         marketDto.getWeight2h())))
                .collect(Collectors.joining("\r\n", prefix, ""));
    }

    private String prepareText5h(List<CryptoDto> cryptoDtos) {
        final String prefix = String.format(
                "\r\n\r\nResult for 5h cryptos\r\n%10s\t%20s\t%20s\t%20s\t%20s\t%20s\r\n",
                "symbol",
                "price",
                "priceToSell",
                "priceToSellPercentage",
                "sumDiffPercent",
                "weight");

        return cryptoDtos.stream()
                .filter(cryptoDto -> cryptoDto.getPriceToSellPercentage5h().compareTo(new BigDecimal("0.5")) > 0)
                .sorted(Comparator.comparing(CryptoDto::getSumDiffsPerc5h))
                .map(marketDto -> (String.format
                        ("%10s\t%20.8f\t%20.8f\t%20.8f\t%20.8f\t%20.8f\t",
                         marketDto.getBinanceExchangeSymbol().getSymbol().getSymbol(),
                         marketDto.getCurrentPrice(),
                         marketDto.getPriceToSell5h(),
                         marketDto.getPriceToSellPercentage5h(),
                         marketDto.getSumDiffsPerc5h(),
                         marketDto.getWeight5h())))
                .collect(Collectors.joining("\r\n", prefix, ""));
    }

    private String prepareText10h(List<CryptoDto> cryptoDtos) {
        final String prefix = String.format(
                "\r\n\r\nResult for 10h cryptos\r\n%10s\t%20s\t%20s\t%20s\t%20s\t%20s\r\n",
                "symbol",
                "price",
                "priceToSell",
                "priceToSellPercentage",
                "sumDiffPercent",
                "weight");

        return cryptoDtos.stream()
                .filter(cryptoDto -> cryptoDto.getPriceToSellPercentage10h().compareTo(new BigDecimal("0.5")) > 0)
                .sorted(Comparator.comparing(CryptoDto::getSumDiffsPerc10h))
                .map(marketDto -> (String.format
                        ("%10s\t%20.8f\t%20.8f\t%20.8f\t%20.8f\t%20.8f\t",
                         marketDto.getBinanceExchangeSymbol().getSymbol().getSymbol(),
                         marketDto.getCurrentPrice(),
                         marketDto.getPriceToSell10h(),
                         marketDto.getPriceToSellPercentage10h(),
                         marketDto.getSumDiffsPerc10h(),
                         marketDto.getWeight10h())))
                .collect(Collectors.joining("\r\n", prefix, ""));
    }

    private String prepareText24h(List<CryptoDto> cryptoDtos) {
        final String prefix = String.format(
                "\r\n\r\nResult for 24h cryptos\r\n%10s\t%20s\t%20s\t%20s\t%20s\t%20s\r\n",
                "symbol",
                "price",
                "priceToSell",
                "priceToSellPercentage",
                "sumDiffPercent",
                "weight");

        return cryptoDtos.stream()
                .filter(cryptoDto -> cryptoDto.getPriceToSellPercentage24h().compareTo(new BigDecimal("0.5")) > 0)
                .sorted(Comparator.comparing(CryptoDto::getSumDiffsPerc24h))
                .map(marketDto -> (String.format
                        ("%10s\t%20.8f\t%20.8f\t%20.8f\t%20.8f\t%20.8f\t",
                         marketDto.getBinanceExchangeSymbol().getSymbol().getSymbol(),
                         marketDto.getCurrentPrice(),
                         marketDto.getPriceToSell24h(),
                         marketDto.getPriceToSellPercentage24h(),
                         marketDto.getSumDiffsPerc24h(),
                         marketDto.getWeight24h())))
                .collect(Collectors.joining("\r\n", prefix, ""));
    }

}
