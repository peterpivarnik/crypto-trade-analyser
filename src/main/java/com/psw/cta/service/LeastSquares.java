package com.psw.cta.service;

import java.math.BigDecimal;
import java.util.List;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.stereotype.Component;

@Component
public class LeastSquares {

    public double getSlope(List<BigDecimal> data) {
        SimpleRegression simpleRegression = new SimpleRegression(true);
        double[][] regressionData = new double[data.size()][2];
        for (int i = 0; i < data.size(); i++) {
            regressionData[i][0] = i;
            regressionData[i][1] = data.get(i).doubleValue();
        }
        simpleRegression.addData(regressionData);
        return simpleRegression.getSlope();
    }
}
