package com.psw.cta.utils;

import java.math.BigDecimal;
import java.util.List;
import org.apache.commons.math3.stat.regression.SimpleRegression;

public class LeastSquares {

    public static double getSlope(List<BigDecimal> data) {
        SimpleRegression simpleRegression = new SimpleRegression(true);
        double[][] regressionData = new double[data.size()][2];
        for (int i = 0; i < data.size(); i++) {
            regressionData[i][0] = i;
            regressionData[i][1] = data.get(i).doubleValue();
        }
        simpleRegression.addData(regressionData);
        return simpleRegression.getSlope();
    }

    public static SimpleRegression getRegression(double x1, double y1, double x2, double y2) {
        SimpleRegression simpleRegression = new SimpleRegression(true);
        double[][] regressionData = new double[2][2];
            regressionData[0][0] = x1;
            regressionData[0][1] = y1;
            regressionData[1][0] = x2;
            regressionData[1][1] = y2;
        simpleRegression.addData(regressionData);
        return simpleRegression;
    }
}
