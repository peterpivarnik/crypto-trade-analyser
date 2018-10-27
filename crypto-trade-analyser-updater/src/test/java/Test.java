import com.webcerebrium.binance.api.BinanceApi;
import com.webcerebrium.binance.api.BinanceApiException;
import com.webcerebrium.binance.datatype.BinanceCandlestick;
import com.webcerebrium.binance.datatype.BinanceSymbol;

import java.util.List;

import static com.webcerebrium.binance.datatype.BinanceInterval.ONE_DAY;

public class Test {

    public static void main(String args[]) throws BinanceApiException {

        BinanceApi api = new BinanceApi();
        List<BinanceCandlestick> klines = api.klines(new BinanceSymbol("ETH_BTC"), ONE_DAY, 1, null);

        System.out.println("high: " + klines.get(0).getHigh());
        System.out.println("low: " + klines.get(0).getLow());
        System.out.println("open: " + klines.get(0).getOpen());
        System.out.println("close: " + klines.get(0).getClose());
        System.out.println("open time: " + klines.get(0).getOpenTime());
        System.out.println("close time: " + klines.get(0).getCloseTime());
    }
}
