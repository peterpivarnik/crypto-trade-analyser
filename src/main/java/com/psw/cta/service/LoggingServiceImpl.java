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
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
class LoggingServiceImpl {

    @Async
    void log(List<CryptoDto> cryptoDtos) {
        String text = prepareText(cryptoDtos);
        log.info(text);
        final String fileName = "/home/peter/work/crypto/" + LocalDateTime.now().toString() + ".txt";
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "utf-8"))) {
            writer.write(text);
        } catch (IOException e) {
            log.error(e.getMessage());
            log.error(e.getClass().toString());
        }
    }

    private String prepareText(List<CryptoDto> cryptoDtos) {
        final String prefix = String.format(
                "\r\n%10s\t%20s\t%20s\t%20s\t%20s\t%20s\r\n",
                "symbol",
                "percent",
                "quoteVolume",
                "price",
                "priceToSell",
                "weight");

        return cryptoDtos.stream()
                .sorted(Comparator.comparing(CryptoDto::getWeight))
                .map(marketDto -> (String.format
                        ("%10s\t%20.8f\t%20s\t%20.8f\t%20.8f\t%20.8f\t",
                         marketDto.getBinanceExchangeSymbol().getSymbol().getSymbol(),
                         marketDto.getFifteenMinutesPercentageLoss(),
                         marketDto.getTicker24hr().get("quoteVolume"),
                         marketDto.getCurrentPrice(),
                         marketDto.getPriceToSell(),
                         marketDto.getWeight())))
                .collect(Collectors.joining("\r\n", prefix, ""));
    }

}
