package com.psw.cta.utils;

import java.math.BigDecimal;
import java.util.List;
import org.apache.commons.math3.stat.regression.SimpleRegression;

/**
 * Util for calculation least square data.
 */
public class LeastSquares {

    /**
     * Retirn slope for provided list of values.
     *
     * @param data Data for calculating regression
     * @return Regression
     */
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

    /**
     * Returns regression from provide points.
     *
     * @param x1 X value of point1
     * @param y1 Y value of point1
     * @param x2 X value of point2
     * @param y2 Y value of point2
     * @return Regression
     */
    public static SimpleRegression getRegression(double x1, double y1, double x2, double y2) {
        double[][] regressionData = new double[2][2];
        regressionData[0][0] = x1;
        regressionData[0][1] = y1;
        regressionData[1][0] = x2;
        regressionData[1][1] = y2;
        SimpleRegression simpleRegression = new SimpleRegression(true);
        simpleRegression.addData(regressionData);
        return simpleRegression;
    }
}
