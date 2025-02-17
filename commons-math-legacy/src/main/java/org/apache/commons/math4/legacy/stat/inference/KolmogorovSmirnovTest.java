/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.math4.legacy.stat.inference;

import java.math.RoundingMode;
import java.util.Arrays;

import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.numbers.combinatorics.BinomialCoefficientDouble;
import org.apache.commons.numbers.fraction.BigFraction;
import org.apache.commons.numbers.field.BigFractionField;
import org.apache.commons.math4.legacy.distribution.EnumeratedRealDistribution;
import org.apache.commons.math4.legacy.distribution.AbstractRealDistribution;
import org.apache.commons.math4.legacy.exception.InsufficientDataException;
import org.apache.commons.math4.legacy.exception.MathArithmeticException;
import org.apache.commons.math4.legacy.exception.MaxCountExceededException;
import org.apache.commons.math4.legacy.exception.NullArgumentException;
import org.apache.commons.math4.legacy.exception.NumberIsTooLargeException;
import org.apache.commons.math4.legacy.exception.OutOfRangeException;
import org.apache.commons.math4.legacy.exception.TooManyIterationsException;
import org.apache.commons.math4.legacy.exception.NotANumberException;
import org.apache.commons.math4.legacy.exception.util.LocalizedFormats;
import org.apache.commons.math4.legacy.linear.MatrixUtils;
import org.apache.commons.math4.legacy.linear.RealMatrix;
import org.apache.commons.math4.core.jdkmath.JdkMath;
import org.apache.commons.math4.legacy.core.MathArrays;
import org.apache.commons.math4.legacy.field.linalg.FieldDenseMatrix;

/**
 * Implementation of the <a href="http://en.wikipedia.org/wiki/Kolmogorov-Smirnov_test">
 * Kolmogorov-Smirnov (K-S) test</a> for equality of continuous distributions.
 * <p>
 * The K-S test uses a statistic based on the maximum deviation of the empirical distribution of
 * sample data points from the distribution expected under the null hypothesis. For one-sample tests
 * evaluating the null hypothesis that a set of sample data points follow a given distribution, the
 * test statistic is \(D_n=\sup_x |F_n(x)-F(x)|\), where \(F\) is the expected distribution and
 * \(F_n\) is the empirical distribution of the \(n\) sample data points. The distribution of
 * \(D_n\) is estimated using a method based on [1] with certain quick decisions for extreme values
 * given in [2].
 * </p>
 * <p>
 * Two-sample tests are also supported, evaluating the null hypothesis that the two samples
 * {@code x} and {@code y} come from the same underlying distribution. In this case, the test
 * statistic is \(D_{n,m}=\sup_t | F_n(t)-F_m(t)|\) where \(n\) is the length of {@code x}, \(m\) is
 * the length of {@code y}, \(F_n\) is the empirical distribution that puts mass \(1/n\) at each of
 * the values in {@code x} and \(F_m\) is the empirical distribution of the {@code y} values. The
 * default 2-sample test method, {@link #kolmogorovSmirnovTest(double[], double[])} works as
 * follows:
 * <ul>
 * <li>When the product of the sample sizes is less than 10000, the method presented in [4]
 * is used to compute the exact p-value for the 2-sample test.</li>
 * <li>When the product of the sample sizes is larger, the asymptotic
 * distribution of \(D_{n,m}\) is used. See {@link #approximateP(double, int, int)} for details on
 * the approximation.</li>
 * </ul><p>
 * For small samples (former case), if the data contains ties, random jitter is added
 * to the sample data to break ties before applying the algorithm above. Alternatively,
 * the {@link #bootstrap(double[],double[],int,boolean,UniformRandomProvider)}
 * method, modeled after <a href="http://sekhon.berkeley.edu/matching/ks.boot.html">ks.boot</a>
 * in the R Matching package [3], can be used if ties are known to be present in the data.
 * </p>
 * <p>
 * In the two-sample case, \(D_{n,m}\) has a discrete distribution. This makes the p-value
 * associated with the null hypothesis \(H_0 : D_{n,m} \ge d \) differ from \(H_0 : D_{n,m} \ge d \)
 * by the mass of the observed value \(d\). To distinguish these, the two-sample tests use a boolean
 * {@code strict} parameter. This parameter is ignored for large samples.
 * </p>
 * <p>
 * The methods used by the 2-sample default implementation are also exposed directly:
 * <ul>
 * <li>{@link #exactP(double, int, int, boolean)} computes exact 2-sample p-values</li>
 * <li>{@link #approximateP(double, int, int)} uses the asymptotic distribution The {@code boolean}
 * arguments in the first two methods allow the probability used to estimate the p-value to be
 * expressed using strict or non-strict inequality. See
 * {@link #kolmogorovSmirnovTest(double[], double[], boolean)}.</li>
 * </ul>
 * <p>
 * References:
 * <ul>
 * <li>[1] <a href="http://www.jstatsoft.org/v08/i18/"> Evaluating Kolmogorov's Distribution</a> by
 * George Marsaglia, Wai Wan Tsang, and Jingbo Wang</li>
 * <li>[2] <a href="http://www.jstatsoft.org/v39/i11/"> Computing the Two-Sided Kolmogorov-Smirnov
 * Distribution</a> by Richard Simard and Pierre L'Ecuyer</li>
 * <li>[3] Jasjeet S. Sekhon. 2011. <a href="http://www.jstatsoft.org/article/view/v042i07">
 * Multivariate and Propensity Score Matching Software with Automated Balance Optimization:
 * The Matching package for R</a> Journal of Statistical Software, 42(7): 1-52.</li>
 * <li>[4] Wilcox, Rand. 2012. Introduction to Robust Estimation and Hypothesis Testing,
 * Chapter 5, 3rd Ed. Academic Press.</li>
 * </ul>
 * <br>
 * Note that [1] contains an error in computing h, refer to <a
 * href="https://issues.apache.org/jira/browse/MATH-437">MATH-437</a> for details.
 *
 * @since 3.3
 */
public class KolmogorovSmirnovTest {
    /** pi^2. */
    private static final double PI_SQUARED = JdkMath.PI * JdkMath.PI;
    /**
     * Bound on the number of partial sums in {@link #ksSum(double, double, int)}.
     */
    private static final int MAXIMUM_PARTIAL_SUM_COUNT = 100000;
    /** Convergence criterion for {@link #ksSum(double, double, int)}. */
    private static final double KS_SUM_CAUCHY_CRITERION = 1e-20;
    /** Convergence criterion for the sums in {@link #pelzGood(double, int)}. */
    private static final double PG_SUM_RELATIVE_ERROR = 1e-10;
    /** 1/2. */
    private static final BigFraction ONE_HALF = BigFraction.of(1, 2);

    /**
     * When product of sample sizes exceeds this value, 2-sample K-S test uses asymptotic
     * distribution to compute the p-value.
     */
    private static final int LARGE_SAMPLE_PRODUCT = 10000;

    /**
     * Computes the <i>p-value</i>, or <i>observed significance level</i>, of a one-sample <a
     * href="http://en.wikipedia.org/wiki/Kolmogorov-Smirnov_test"> Kolmogorov-Smirnov test</a>
     * evaluating the null hypothesis that {@code data} conforms to {@code distribution}. If
     * {@code exact} is true, the distribution used to compute the p-value is computed using
     * extended precision. See {@link #cdfExact(double, int)}.
     *
     * @param distribution reference distribution
     * @param data sample being being evaluated
     * @param exact whether or not to force exact computation of the p-value
     * @return the p-value associated with the null hypothesis that {@code data} is a sample from
     *         {@code distribution}
     * @throws InsufficientDataException if {@code data} does not have length at least 2
     * @throws NullArgumentException if {@code data} is null
     */
    public double kolmogorovSmirnovTest(ContinuousDistribution distribution, double[] data, boolean exact) {
        return 1d - cdf(kolmogorovSmirnovStatistic(distribution, data), data.length, exact);
    }

    /**
     * Computes the one-sample Kolmogorov-Smirnov test statistic, \(D_n=\sup_x |F_n(x)-F(x)|\) where
     * \(F\) is the distribution (cdf) function associated with {@code distribution}, \(n\) is the
     * length of {@code data} and \(F_n\) is the empirical distribution that puts mass \(1/n\) at
     * each of the values in {@code data}.
     *
     * @param distribution reference distribution
     * @param data sample being evaluated
     * @return Kolmogorov-Smirnov statistic \(D_n\)
     * @throws InsufficientDataException if {@code data} does not have length at least 2
     * @throws NullArgumentException if {@code data} is null
     */
    public double kolmogorovSmirnovStatistic(ContinuousDistribution distribution, double[] data) {
        checkArray(data);
        final int n = data.length;
        final double nd = n;
        final double[] dataCopy = new double[n];
        System.arraycopy(data, 0, dataCopy, 0, n);
        Arrays.sort(dataCopy);
        double d = 0d;
        for (int i = 1; i <= n; i++) {
            final double yi = distribution.cumulativeProbability(dataCopy[i - 1]);
            final double currD = JdkMath.max(yi - (i - 1) / nd, i / nd - yi);
            if (currD > d) {
                d = currD;
            }
        }
        return d;
    }

    /**
     * Computes the <i>p-value</i>, or <i>observed significance level</i>, of a two-sample <a
     * href="http://en.wikipedia.org/wiki/Kolmogorov-Smirnov_test"> Kolmogorov-Smirnov test</a>
     * evaluating the null hypothesis that {@code x} and {@code y} are samples drawn from the same
     * probability distribution. Specifically, what is returned is an estimate of the probability
     * that the {@link #kolmogorovSmirnovStatistic(double[], double[])} associated with a randomly
     * selected partition of the combined sample into subsamples of sizes {@code x.length} and
     * {@code y.length} will strictly exceed (if {@code strict} is {@code true}) or be at least as
     * large as (if {@code strict} is {@code false}) as {@code kolmogorovSmirnovStatistic(x, y)}.
     *
     * @param x first sample dataset.
     * @param y second sample dataset.
     * @param strict whether or not the probability to compute is expressed as
     * a strict inequality (ignored for large samples).
     * @return p-value associated with the null hypothesis that {@code x} and
     * {@code y} represent samples from the same distribution.
     * @throws InsufficientDataException if either {@code x} or {@code y} does
     * not have length at least 2.
     * @throws NullArgumentException if either {@code x} or {@code y} is null.
     * @throws NotANumberException if the input arrays contain NaN values.
     *
     * @see #bootstrap(double[],double[],int,boolean,UniformRandomProvider)
     */
    public double kolmogorovSmirnovTest(double[] x, double[] y, boolean strict) {
        final long lengthProduct = (long) x.length * y.length;
        double[] xa = null;
        double[] ya = null;
        if (lengthProduct < LARGE_SAMPLE_PRODUCT && hasTies(x,y)) {
            xa = Arrays.copyOf(x, x.length);
            ya = Arrays.copyOf(y, y.length);
            fixTies(xa, ya);
        } else {
            xa = x;
            ya = y;
        }
        if (lengthProduct < LARGE_SAMPLE_PRODUCT) {
            return exactP(kolmogorovSmirnovStatistic(xa, ya), x.length, y.length, strict);
        }
        return approximateP(kolmogorovSmirnovStatistic(x, y), x.length, y.length);
    }

    /**
     * Computes the <i>p-value</i>, or <i>observed significance level</i>, of a two-sample <a
     * href="http://en.wikipedia.org/wiki/Kolmogorov-Smirnov_test"> Kolmogorov-Smirnov test</a>
     * evaluating the null hypothesis that {@code x} and {@code y} are samples drawn from the same
     * probability distribution. Assumes the strict form of the inequality used to compute the
     * p-value. See {@link #kolmogorovSmirnovTest(ContinuousDistribution, double[], boolean)}.
     *
     * @param x first sample dataset
     * @param y second sample dataset
     * @return p-value associated with the null hypothesis that {@code x} and {@code y} represent
     *         samples from the same distribution
     * @throws InsufficientDataException if either {@code x} or {@code y} does not have length at
     *         least 2
     * @throws NullArgumentException if either {@code x} or {@code y} is null
     */
    public double kolmogorovSmirnovTest(double[] x, double[] y) {
        return kolmogorovSmirnovTest(x, y, true);
    }

    /**
     * Computes the two-sample Kolmogorov-Smirnov test statistic, \(D_{n,m}=\sup_x |F_n(x)-F_m(x)|\)
     * where \(n\) is the length of {@code x}, \(m\) is the length of {@code y}, \(F_n\) is the
     * empirical distribution that puts mass \(1/n\) at each of the values in {@code x} and \(F_m\)
     * is the empirical distribution of the {@code y} values.
     *
     * @param x first sample
     * @param y second sample
     * @return test statistic \(D_{n,m}\) used to evaluate the null hypothesis that {@code x} and
     *         {@code y} represent samples from the same underlying distribution
     * @throws InsufficientDataException if either {@code x} or {@code y} does not have length at
     *         least 2
     * @throws NullArgumentException if either {@code x} or {@code y} is null
     */
    public double kolmogorovSmirnovStatistic(double[] x, double[] y) {
        return integralKolmogorovSmirnovStatistic(x, y)/((double)(x.length * (long)y.length));
    }

    /**
     * Computes the two-sample Kolmogorov-Smirnov test statistic, \(D_{n,m}=\sup_x |F_n(x)-F_m(x)|\)
     * where \(n\) is the length of {@code x}, \(m\) is the length of {@code y}, \(F_n\) is the
     * empirical distribution that puts mass \(1/n\) at each of the values in {@code x} and \(F_m\)
     * is the empirical distribution of the {@code y} values. Finally \(n m D_{n,m}\) is returned
     * as long value.
     *
     * @param x first sample
     * @param y second sample
     * @return test statistic \(n m D_{n,m}\) used to evaluate the null hypothesis that {@code x} and
     *         {@code y} represent samples from the same underlying distribution
     * @throws InsufficientDataException if either {@code x} or {@code y} does not have length at
     *         least 2
     * @throws NullArgumentException if either {@code x} or {@code y} is null
     */
    private long integralKolmogorovSmirnovStatistic(double[] x, double[] y) {
        checkArray(x);
        checkArray(y);
        // Copy and sort the sample arrays
        final double[] sx = Arrays.copyOf(x, x.length);
        final double[] sy = Arrays.copyOf(y, y.length);
        Arrays.sort(sx);
        Arrays.sort(sy);
        final int n = sx.length;
        final int m = sy.length;

        int rankX = 0;
        int rankY = 0;
        long curD = 0L;

        // Find the max difference between cdf_x and cdf_y
        long supD = 0L;
        do {
            double z = Double.compare(sx[rankX], sy[rankY]) <= 0 ? sx[rankX] : sy[rankY];
            while(rankX < n && Double.compare(sx[rankX], z) == 0) {
                rankX += 1;
                curD += m;
            }
            while(rankY < m && Double.compare(sy[rankY], z) == 0) {
                rankY += 1;
                curD -= n;
            }
            if (curD > supD) {
                supD = curD;
            } else if (-curD > supD) {
                supD = -curD;
            }
        } while(rankX < n && rankY < m);
        return supD;
    }

    /**
     * Computes the <i>p-value</i>, or <i>observed significance level</i>, of a one-sample <a
     * href="http://en.wikipedia.org/wiki/Kolmogorov-Smirnov_test"> Kolmogorov-Smirnov test</a>
     * evaluating the null hypothesis that {@code data} conforms to {@code distribution}.
     *
     * @param distribution reference distribution
     * @param data sample being being evaluated
     * @return the p-value associated with the null hypothesis that {@code data} is a sample from
     *         {@code distribution}
     * @throws InsufficientDataException if {@code data} does not have length at least 2
     * @throws NullArgumentException if {@code data} is null
     */
    public double kolmogorovSmirnovTest(ContinuousDistribution distribution, double[] data) {
        return kolmogorovSmirnovTest(distribution, data, false);
    }

    /**
     * Performs a <a href="http://en.wikipedia.org/wiki/Kolmogorov-Smirnov_test"> Kolmogorov-Smirnov
     * test</a> evaluating the null hypothesis that {@code data} conforms to {@code distribution}.
     *
     * @param distribution reference distribution
     * @param data sample being being evaluated
     * @param alpha significance level of the test
     * @return true iff the null hypothesis that {@code data} is a sample from {@code distribution}
     *         can be rejected with confidence 1 - {@code alpha}
     * @throws InsufficientDataException if {@code data} does not have length at least 2
     * @throws NullArgumentException if {@code data} is null
     */
    public boolean kolmogorovSmirnovTest(ContinuousDistribution distribution, double[] data, double alpha) {
        if (alpha <= 0 || alpha > 0.5) {
            throw new OutOfRangeException(LocalizedFormats.OUT_OF_BOUND_SIGNIFICANCE_LEVEL, alpha, 0, 0.5);
        }
        return kolmogorovSmirnovTest(distribution, data) < alpha;
    }

    /**
     * Estimates the <i>p-value</i> of a two-sample
     * <a href="http://en.wikipedia.org/wiki/Kolmogorov-Smirnov_test">Kolmogorov-Smirnov test</a>
     * evaluating the null hypothesis that {@code x} and {@code y} are samples
     * drawn from the same probability distribution.
     * This method estimates the p-value by repeatedly sampling sets of size
     * {@code x.length} and {@code y.length} from the empirical distribution
     * of the combined sample.
     * When {@code strict} is true, this is equivalent to the algorithm implemented
     * in the R function {@code ks.boot}, described in <pre>
     * Jasjeet S. Sekhon. 2011. 'Multivariate and Propensity Score Matching
     * Software with Automated Balance Optimization: The Matching package for R.'
     * Journal of Statistical Software, 42(7): 1-52.
     * </pre>
     *
     * @param x First sample.
     * @param y Second sample.
     * @param iterations Number of bootstrap resampling iterations.
     * @param strict Whether or not the null hypothesis is expressed as a strict inequality.
     * @param rng RNG for creating the sampling sets.
     * @return the estimated p-value.
     */
    public double bootstrap(double[] x,
                            double[] y,
                            int iterations,
                            boolean strict,
                            UniformRandomProvider rng) {
        final int xLength = x.length;
        final int yLength = y.length;
        final double[] combined = new double[xLength + yLength];
        System.arraycopy(x, 0, combined, 0, xLength);
        System.arraycopy(y, 0, combined, xLength, yLength);
        final ContinuousDistribution.Sampler sampler = new EnumeratedRealDistribution(combined).createSampler(rng);
        final long d = integralKolmogorovSmirnovStatistic(x, y);
        int greaterCount = 0;
        int equalCount = 0;
        double[] curX;
        double[] curY;
        long curD;
        for (int i = 0; i < iterations; i++) {
            curX = AbstractRealDistribution.sample(xLength, sampler);
            curY = AbstractRealDistribution.sample(yLength, sampler);
            curD = integralKolmogorovSmirnovStatistic(curX, curY);
            if (curD > d) {
                greaterCount++;
            } else if (curD == d) {
                equalCount++;
            }
        }
        return strict ? greaterCount / (double) iterations :
            (greaterCount + equalCount) / (double) iterations;
    }

    /**
     * Calculates \(P(D_n &lt; d)\) using the method described in [1] with quick decisions for extreme
     * values given in [2] (see above). The result is not exact as with
     * {@link #cdfExact(double, int)} because calculations are based on
     * {@code double} rather than {@link BigFraction}.
     *
     * @param d statistic
     * @param n sample size
     * @return \(P(D_n &lt; d)\)
     * @throws MathArithmeticException if algorithm fails to convert {@code h} to a
     * {@link BigFraction} in expressing {@code d} as
     * \((k - h) / m\) for integer {@code k, m} and \(0 \le h &lt; 1\)
     */
    public double cdf(double d, int n) {
        return cdf(d, n, false);
    }

    /**
     * Calculates {@code P(D_n < d)}. The result is exact in the sense that BigFraction/BigReal is
     * used everywhere at the expense of very slow execution time. Almost never choose this in real
     * applications unless you are very sure; this is almost solely for verification purposes.
     * Normally, you would choose {@link #cdf(double, int)}. See the class
     * javadoc for definitions and algorithm description.
     *
     * @param d statistic
     * @param n sample size
     * @return \(P(D_n &lt; d)\)
     * @throws MathArithmeticException if the algorithm fails to convert {@code h} to a
     * {@link BigFraction} in expressing {@code d} as
     * \((k - h) / m\) for integer {@code k, m} and \(0 \le h &lt; 1\)
     */
    public double cdfExact(double d, int n) {
        return cdf(d, n, true);
    }

    /**
     * Calculates {@code P(D_n < d)} using method described in [1] with quick decisions for extreme
     * values given in [2] (see above).
     *
     * @param d statistic
     * @param n sample size
     * @param exact whether the probability should be calculated exact using
     *        {@link BigFraction} everywhere at the expense of
     *        very slow execution time, or if {@code double} should be used convenient places to
     *        gain speed. Almost never choose {@code true} in real applications unless you are very
     *        sure; {@code true} is almost solely for verification purposes.
     * @return \(P(D_n &lt; d)\)
     * @throws MathArithmeticException if algorithm fails to convert {@code h} to a
     * {@link BigFraction} in expressing {@code d} as
     * \((k - h) / m\) for integer {@code k, m} and \(0 \le h &lt; 1\).
     */
    public double cdf(double d, int n, boolean exact) {
        final double ninv = 1 / ((double) n);
        final double ninvhalf = 0.5 * ninv;

        if (d <= ninvhalf) {
            return 0;
        } else if (ninvhalf < d && d <= ninv) {
            double res = 1;
            final double f = 2 * d - ninv;
            // n! f^n = n*f * (n-1)*f * ... * 1*x
            for (int i = 1; i <= n; ++i) {
                res *= i * f;
            }
            return res;
        } else if (1 - ninv <= d && d < 1) {
            return 1 - 2 * Math.pow(1 - d, n);
        } else if (1 <= d) {
            return 1;
        }
        if (exact) {
            return exactK(d, n);
        }
        if (n <= 140) {
            return roundedK(d, n);
        }
        return pelzGood(d, n);
    }

    /**
     * Calculates the exact value of {@code P(D_n < d)} using the method described in [1] (reference
     * in class javadoc above) and {@link BigFraction} (see above).
     *
     * @param d statistic
     * @param n sample size
     * @return the two-sided probability of \(P(D_n &lt; d)\)
     * @throws MathArithmeticException if algorithm fails to convert {@code h} to a
     * {@link BigFraction}.
     */
    private double exactK(double d, int n) {
        final int k = (int) Math.ceil(n * d);

        final FieldDenseMatrix<BigFraction> h;
        try {
            h = createExactH(d, n);
        } catch (ArithmeticException e) {
            throw new MathArithmeticException(LocalizedFormats.FRACTION);
        }

        final FieldDenseMatrix<BigFraction> hPower = h.pow(n);

        BigFraction pFrac = hPower.get(k - 1, k - 1);

        for (int i = 1; i <= n; ++i) {
            pFrac = pFrac.multiply(i).divide(n);
        }

        /*
         * BigFraction.doubleValue converts numerator to double and the denominator to double and
         * divides afterwards. That gives NaN quite easy. This does not (scale is the number of
         * digits):
         */
        return pFrac.bigDecimalValue(20, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Calculates {@code P(D_n < d)} using method described in [1] and doubles (see above).
     *
     * @param d statistic
     * @param n sample size
     * @return \(P(D_n &lt; d)\)
     */
    private double roundedK(double d, int n) {

        final int k = (int) Math.ceil(n * d);
        final RealMatrix h = this.createRoundedH(d, n);
        final RealMatrix hPower = h.power(n);

        double pFrac = hPower.getEntry(k - 1, k - 1);
        for (int i = 1; i <= n; ++i) {
            pFrac *= (double) i / (double) n;
        }

        return pFrac;
    }

    /**
     * Computes the Pelz-Good approximation for \(P(D_n &lt; d)\) as described in [2] in the class javadoc.
     *
     * @param d value of d-statistic (x in [2])
     * @param n sample size
     * @return \(P(D_n &lt; d)\)
     * @since 3.4
     */
    public double pelzGood(double d, int n) {
        // Change the variable since approximation is for the distribution evaluated at d / sqrt(n)
        final double sqrtN = JdkMath.sqrt(n);
        final double z = d * sqrtN;
        final double z2 = d * d * n;
        final double z4 = z2 * z2;
        final double z6 = z4 * z2;
        final double z8 = z4 * z4;

        // Eventual return value
        double ret = 0;

        // Compute K_0(z)
        double sum = 0;
        double increment = 0;
        double kTerm = 0;
        double z2Term = PI_SQUARED / (8 * z2);
        int k = 1;
        for (; k < MAXIMUM_PARTIAL_SUM_COUNT; k++) {
            kTerm = 2 * k - 1;
            increment = JdkMath.exp(-z2Term * kTerm * kTerm);
            sum += increment;
            if (increment <= PG_SUM_RELATIVE_ERROR * sum) {
                break;
            }
        }
        if (k == MAXIMUM_PARTIAL_SUM_COUNT) {
            throw new TooManyIterationsException(MAXIMUM_PARTIAL_SUM_COUNT);
        }
        ret = sum * JdkMath.sqrt(2 * JdkMath.PI) / z;

        // K_1(z)
        // Sum is -inf to inf, but k term is always (k + 1/2) ^ 2, so really have
        // twice the sum from k = 0 to inf (k = -1 is same as 0, -2 same as 1, ...)
        final double twoZ2 = 2 * z2;
        sum = 0;
        kTerm = 0;
        double kTerm2 = 0;
        for (k = 0; k < MAXIMUM_PARTIAL_SUM_COUNT; k++) {
            kTerm = k + 0.5;
            kTerm2 = kTerm * kTerm;
            increment = (PI_SQUARED * kTerm2 - z2) * JdkMath.exp(-PI_SQUARED * kTerm2 / twoZ2);
            sum += increment;
            if (JdkMath.abs(increment) < PG_SUM_RELATIVE_ERROR * JdkMath.abs(sum)) {
                break;
            }
        }
        if (k == MAXIMUM_PARTIAL_SUM_COUNT) {
            throw new TooManyIterationsException(MAXIMUM_PARTIAL_SUM_COUNT);
        }
        final double sqrtHalfPi = JdkMath.sqrt(JdkMath.PI / 2);
        // Instead of doubling sum, divide by 3 instead of 6
        ret += sum * sqrtHalfPi / (3 * z4 * sqrtN);

        // K_2(z)
        // Same drill as K_1, but with two doubly infinite sums, all k terms are even powers.
        final double z4Term = 2 * z4;
        final double z6Term = 6 * z6;
        z2Term = 5 * z2;
        final double pi4 = PI_SQUARED * PI_SQUARED;
        sum = 0;
        kTerm = 0;
        kTerm2 = 0;
        for (k = 0; k < MAXIMUM_PARTIAL_SUM_COUNT; k++) {
            kTerm = k + 0.5;
            kTerm2 = kTerm * kTerm;
            increment =  (z6Term + z4Term + PI_SQUARED * (z4Term - z2Term) * kTerm2 +
                    pi4 * (1 - twoZ2) * kTerm2 * kTerm2) * JdkMath.exp(-PI_SQUARED * kTerm2 / twoZ2);
            sum += increment;
            if (JdkMath.abs(increment) < PG_SUM_RELATIVE_ERROR * JdkMath.abs(sum)) {
                break;
            }
        }
        if (k == MAXIMUM_PARTIAL_SUM_COUNT) {
            throw new TooManyIterationsException(MAXIMUM_PARTIAL_SUM_COUNT);
        }
        double sum2 = 0;
        kTerm2 = 0;
        for (k = 1; k < MAXIMUM_PARTIAL_SUM_COUNT; k++) {
            kTerm2 = (double) k * k;
            increment = PI_SQUARED * kTerm2 * JdkMath.exp(-PI_SQUARED * kTerm2 / twoZ2);
            sum2 += increment;
            if (JdkMath.abs(increment) < PG_SUM_RELATIVE_ERROR * JdkMath.abs(sum2)) {
                break;
            }
        }
        if (k == MAXIMUM_PARTIAL_SUM_COUNT) {
            throw new TooManyIterationsException(MAXIMUM_PARTIAL_SUM_COUNT);
        }
        // Again, adjust coefficients instead of doubling sum, sum2
        ret += (sqrtHalfPi / n) * (sum / (36 * z2 * z2 * z2 * z) - sum2 / (18 * z2 * z));

        // K_3(z) One more time with feeling - two doubly infinite sums, all k powers even.
        // Multiply coefficient denominators by 2, so omit doubling sums.
        final double pi6 = pi4 * PI_SQUARED;
        sum = 0;
        double kTerm4 = 0;
        double kTerm6 = 0;
        for (k = 0; k < MAXIMUM_PARTIAL_SUM_COUNT; k++) {
            kTerm = k + 0.5;
            kTerm2 = kTerm * kTerm;
            kTerm4 = kTerm2 * kTerm2;
            kTerm6 = kTerm4 * kTerm2;
            increment = (pi6 * kTerm6 * (5 - 30 * z2) + pi4 * kTerm4 * (-60 * z2 + 212 * z4) +
                            PI_SQUARED * kTerm2 * (135 * z4 - 96 * z6) - 30 * z6 - 90 * z8) *
                    JdkMath.exp(-PI_SQUARED * kTerm2 / twoZ2);
            sum += increment;
            if (JdkMath.abs(increment) < PG_SUM_RELATIVE_ERROR * JdkMath.abs(sum)) {
                break;
            }
        }
        if (k == MAXIMUM_PARTIAL_SUM_COUNT) {
            throw new TooManyIterationsException(MAXIMUM_PARTIAL_SUM_COUNT);
        }
        sum2 = 0;
        for (k = 1; k < MAXIMUM_PARTIAL_SUM_COUNT; k++) {
            kTerm2 = (double) k * k;
            kTerm4 = kTerm2 * kTerm2;
            increment = (-pi4 * kTerm4 + 3 * PI_SQUARED * kTerm2 * z2) *
                    JdkMath.exp(-PI_SQUARED * kTerm2 / twoZ2);
            sum2 += increment;
            if (JdkMath.abs(increment) < PG_SUM_RELATIVE_ERROR * JdkMath.abs(sum2)) {
                break;
            }
        }
        if (k == MAXIMUM_PARTIAL_SUM_COUNT) {
            throw new TooManyIterationsException(MAXIMUM_PARTIAL_SUM_COUNT);
        }
        return ret + (sqrtHalfPi / (sqrtN * n)) * (sum / (3240 * z6 * z4) +
                + sum2 / (108 * z6));
    }

    /**
     * Creates {@code H} of size {@code m x m} as described in [1] (see above).
     *
     * @param d statistic
     * @param n sample size
     * @return H matrix
     * @throws NumberIsTooLargeException if fractional part is greater than 1.
     * @throws ArithmeticException if algorithm fails to convert {@code h} to a
     * {@link BigFraction}.
     */
    private FieldDenseMatrix<BigFraction> createExactH(double d,
                                                       int n) {
        final int k = (int) Math.ceil(n * d);
        final int m = 2 * k - 1;
        final double hDouble = k - n * d;
        if (hDouble >= 1) {
            throw new NumberIsTooLargeException(hDouble, 1.0, false);
        }
        BigFraction h = null;
        try {
            h = BigFraction.from(hDouble, 1e-20, 10000);
        } catch (final ArithmeticException e1) {
            try {
                h = BigFraction.from(hDouble, 1e-10, 10000);
            } catch (final ArithmeticException e2) {
                h = BigFraction.from(hDouble, 1e-5, 10000);
            }
        }
        final FieldDenseMatrix<BigFraction> hData = FieldDenseMatrix.create(BigFractionField.get(), m, m);

        /*
         * Start by filling everything with either 0 or 1.
         */
        for (int i = 0; i < m; ++i) {
            for (int j = 0; j < m; ++j) {
                if (i - j + 1 < 0) {
                    hData.set(i, j, BigFraction.ZERO);
                } else {
                    hData.set(i, j, BigFraction.ONE);
                }
            }
        }

        /*
         * Setting up power-array to avoid calculating the same value twice: hPowers[0] = h^1 ...
         * hPowers[m-1] = h^m
         */
        final BigFraction[] hPowers = new BigFraction[m];
        hPowers[0] = h;
        for (int i = 1; i < m; i++) {
            hPowers[i] = h.multiply(hPowers[i - 1]);
        }

        /*
         * First column and last row has special values (each other reversed).
         */
        for (int i = 0; i < m; ++i) {
            hData.set(i, 0,
                      hData.get(i, 0).subtract(hPowers[i]));
            hData.set(m - 1, i,
                      hData.get(m - 1, i).subtract(hPowers[m - i - 1]));
        }

        /*
         * [1] states: "For 1/2 < h < 1 the bottom left element of the matrix should be (1 - 2*h^m +
         * (2h - 1)^m )/m!" Since 0 <= h < 1, then if h > 1/2 is sufficient to check:
         */
        if (h.compareTo(ONE_HALF) > 0) {
            hData.set(m - 1, 0,
                      hData.get(m - 1, 0).add(h.multiply(2).subtract(1).pow(m)));
        }

        /*
         * Aside from the first column and last row, the (i, j)-th element is 1/(i - j + 1)! if i -
         * j + 1 >= 0, else 0. 1's and 0's are already put, so only division with (i - j + 1)! is
         * needed in the elements that have 1's. There is no need to calculate (i - j + 1)! and then
         * divide - small steps avoid overflows. Note that i - j + 1 > 0 <=> i + 1 > j instead of
         * j'ing all the way to m. Also note that it is started at g = 2 because dividing by 1 isn't
         * really necessary.
         */
        for (int i = 0; i < m; ++i) {
            for (int j = 0; j < i + 1; ++j) {
                if (i - j + 1 > 0) {
                    for (int g = 2; g <= i - j + 1; ++g) {
                        hData.set(i, j,
                                  hData.get(i, j).divide(g));
                    }
                }
            }
        }
        return hData;
    }

    /***
     * Creates {@code H} of size {@code m x m} as described in [1] (see above)
     * using double-precision.
     *
     * @param d statistic
     * @param n sample size
     * @return H matrix
     * @throws NumberIsTooLargeException if fractional part is greater than 1
     */
    private RealMatrix createRoundedH(double d, int n)
        throws NumberIsTooLargeException {

        final int k = (int) Math.ceil(n * d);
        final int m = 2 * k - 1;
        final double h = k - n * d;
        if (h >= 1) {
            throw new NumberIsTooLargeException(h, 1.0, false);
        }
        final double[][] hData = new double[m][m];

        /*
         * Start by filling everything with either 0 or 1.
         */
        for (int i = 0; i < m; ++i) {
            for (int j = 0; j < m; ++j) {
                if (i - j + 1 < 0) {
                    hData[i][j] = 0;
                } else {
                    hData[i][j] = 1;
                }
            }
        }

        /*
         * Setting up power-array to avoid calculating the same value twice: hPowers[0] = h^1 ...
         * hPowers[m-1] = h^m
         */
        final double[] hPowers = new double[m];
        hPowers[0] = h;
        for (int i = 1; i < m; ++i) {
            hPowers[i] = h * hPowers[i - 1];
        }

        /*
         * First column and last row has special values (each other reversed).
         */
        for (int i = 0; i < m; ++i) {
            hData[i][0] = hData[i][0] - hPowers[i];
            hData[m - 1][i] -= hPowers[m - i - 1];
        }

        /*
         * [1] states: "For 1/2 < h < 1 the bottom left element of the matrix should be (1 - 2*h^m +
         * (2h - 1)^m )/m!" Since 0 <= h < 1, then if h > 1/2 is sufficient to check:
         */
        if (Double.compare(h, 0.5) > 0) {
            hData[m - 1][0] += JdkMath.pow(2 * h - 1, m);
        }

        /*
         * Aside from the first column and last row, the (i, j)-th element is 1/(i - j + 1)! if i -
         * j + 1 >= 0, else 0. 1's and 0's are already put, so only division with (i - j + 1)! is
         * needed in the elements that have 1's. There is no need to calculate (i - j + 1)! and then
         * divide - small steps avoid overflows. Note that i - j + 1 > 0 <=> i + 1 > j instead of
         * j'ing all the way to m. Also note that it is started at g = 2 because dividing by 1 isn't
         * really necessary.
         */
        for (int i = 0; i < m; ++i) {
            for (int j = 0; j < i + 1; ++j) {
                if (i - j + 1 > 0) {
                    for (int g = 2; g <= i - j + 1; ++g) {
                        hData[i][j] /= g;
                    }
                }
            }
        }
        return MatrixUtils.createRealMatrix(hData);
    }

    /**
     * Verifies that {@code array} has length at least 2.
     *
     * @param array array to test
     * @throws NullArgumentException if array is null
     * @throws InsufficientDataException if array is too short
     */
    private void checkArray(double[] array) {
        if (array == null) {
            throw new NullArgumentException(LocalizedFormats.NULL_NOT_ALLOWED);
        }
        if (array.length < 2) {
            throw new InsufficientDataException(LocalizedFormats.INSUFFICIENT_OBSERVED_POINTS_IN_SAMPLE, array.length,
                                                2);
        }
    }

    /**
     * Computes \( 1 + 2 \sum_{i=1}^\infty (-1)^i e^{-2 i^2 t^2} \) stopping when successive partial
     * sums are within {@code tolerance} of one another, or when {@code maxIterations} partial sums
     * have been computed. If the sum does not converge before {@code maxIterations} iterations a
     * {@link TooManyIterationsException} is thrown.
     *
     * @param t argument
     * @param tolerance Cauchy criterion for partial sums
     * @param maxIterations maximum number of partial sums to compute
     * @return Kolmogorov sum evaluated at t
     * @throws TooManyIterationsException if the series does not converge
     */
    public double ksSum(double t, double tolerance, int maxIterations) {
        if (t == 0.0) {
            return 0.0;
        }

        // TODO: for small t (say less than 1), the alternative expansion in part 3 of [1]
        // from class javadoc should be used.

        final double x = -2 * t * t;
        int sign = -1;
        long i = 1;
        double partialSum = 0.5d;
        double delta = 1;
        while (delta > tolerance && i < maxIterations) {
            delta = JdkMath.exp(x * i * i);
            partialSum += sign * delta;
            sign *= -1;
            i++;
        }
        if (i == maxIterations) {
            throw new TooManyIterationsException(maxIterations);
        }
        return partialSum * 2;
    }

    /**
     * Given a d-statistic in the range [0, 1] and the two sample sizes n and m,
     * an integral d-statistic in the range [0, n*m] is calculated, that can be used for
     * comparison with other integral d-statistics. Depending whether {@code strict} is
     * {@code true} or not, the returned value divided by (n*m) is greater than
     * (resp greater than or equal to) the given d value (allowing some tolerance).
     *
     * @param d a d-statistic in the range [0, 1]
     * @param n first sample size
     * @param m second sample size
     * @param strict whether the returned value divided by (n*m) is allowed to be equal to d
     * @return the integral d-statistic in the range [0, n*m]
     */
    private static long calculateIntegralD(double d, int n, int m, boolean strict) {
        final double tol = 1e-12;  // d-values within tol of one another are considered equal
        long nm = n * (long)m;
        long upperBound = (long)JdkMath.ceil((d - tol) * nm);
        long lowerBound = (long)JdkMath.floor((d + tol) * nm);
        if (strict && lowerBound == upperBound) {
            return upperBound + 1L;
        } else {
            return upperBound;
        }
    }

    /**
     * Computes \(P(D_{n,m} &gt; d)\) if {@code strict} is {@code true}; otherwise \(P(D_{n,m} \ge
     * d)\), where \(D_{n,m}\) is the 2-sample Kolmogorov-Smirnov statistic. See
     * {@link #kolmogorovSmirnovStatistic(double[], double[])} for the definition of \(D_{n,m}\).
     * <p>
     * The returned probability is exact, implemented by unwinding the recursive function
     * definitions presented in [4] (class javadoc).
     * </p>
     *
     * @param d D-statistic value
     * @param n first sample size
     * @param m second sample size
     * @param strict whether or not the probability to compute is expressed as a strict inequality
     * @return probability that a randomly selected m-n partition of m + n generates \(D_{n,m}\)
     *         greater than (resp. greater than or equal to) {@code d}
     */
    public double exactP(double d, int n, int m, boolean strict) {
       return 1 - n(m, n, m, n, calculateIntegralD(d, m, n, strict), strict) /
           BinomialCoefficientDouble.value(n + m, m);
    }

    /**
     * Uses the Kolmogorov-Smirnov distribution to approximate \(P(D_{n,m} &gt; d)\) where \(D_{n,m}\)
     * is the 2-sample Kolmogorov-Smirnov statistic. See
     * {@link #kolmogorovSmirnovStatistic(double[], double[])} for the definition of \(D_{n,m}\).
     * <p>
     * Specifically, what is returned is \(1 - k(d \sqrt{mn / (m + n)})\) where \(k(t) = 1 + 2
     * \sum_{i=1}^\infty (-1)^i e^{-2 i^2 t^2}\). See {@link #ksSum(double, double, int)} for
     * details on how convergence of the sum is determined.
     * </p>
     *
     * @param d D-statistic value
     * @param n first sample size
     * @param m second sample size
     * @return approximate probability that a randomly selected m-n partition of m + n generates
     *         \(D_{n,m}\) greater than {@code d}
     */
    public double approximateP(double d, int n, int m) {
        return 1 - ksSum(d * JdkMath.sqrt(((double) m * (double) n) / ((double) m + (double) n)),
                         KS_SUM_CAUCHY_CRITERION, MAXIMUM_PARTIAL_SUM_COUNT);
    }

    /**
     * Fills a boolean array randomly with a fixed number of {@code true} values.
     * The method uses a simplified version of the Fisher-Yates shuffle algorithm.
     * By processing first the {@code true} values followed by the remaining {@code false} values
     * less random numbers need to be generated. The method is optimized for the case
     * that the number of {@code true} values is larger than or equal to the number of
     * {@code false} values.
     *
     * @param b boolean array
     * @param numberOfTrueValues number of {@code true} values the boolean array should finally have
     * @param rng random data generator
     */
    private static void fillBooleanArrayRandomlyWithFixedNumberTrueValues(final boolean[] b, final int numberOfTrueValues, final UniformRandomProvider rng) {
        Arrays.fill(b, true);
        for (int k = numberOfTrueValues; k < b.length; k++) {
            final int r = rng.nextInt(k + 1);
            b[(b[r]) ? r : k] = false;
        }
    }

    /**
     * Uses Monte Carlo simulation to approximate \(P(D_{n,m} &gt; d)\) where \(D_{n,m}\) is the
     * 2-sample Kolmogorov-Smirnov statistic. See
     * {@link #kolmogorovSmirnovStatistic(double[], double[])} for the definition of \(D_{n,m}\).
     * <p>
     * The simulation generates {@code iterations} random partitions of {@code m + n} into an
     * {@code n} set and an {@code m} set, computing \(D_{n,m}\) for each partition and returning
     * the proportion of values that are greater than {@code d}, or greater than or equal to
     * {@code d} if {@code strict} is {@code false}.
     * </p>
     *
     * @param d D-statistic value.
     * @param n First sample size.
     * @param m Second sample size.
     * @param iterations Number of random partitions to generate.
     * @param strict whether or not the probability to compute is expressed as a strict inequality
     * @param rng RNG used for generating the partitions.
     * @return proportion of randomly generated m-n partitions of m + n that result in \(D_{n,m}\)
     * greater than (resp. greater than or equal to) {@code d}.
     */
    public double monteCarloP(final double d,
                              final int n,
                              final int m,
                              final boolean strict,
                              final int iterations,
                              UniformRandomProvider rng) {
        return integralMonteCarloP(calculateIntegralD(d, n, m, strict), n, m, iterations, rng);
    }

    /**
     * Uses Monte Carlo simulation to approximate \(P(D_{n,m} >= d / (n * m))\)
     * where \(D_{n,m}\) is the 2-sample Kolmogorov-Smirnov statistic.
     * <p>
     * Here {@code d} is the D-statistic represented as long value.
     * The real D-statistic is obtained by dividing {@code d} by {@code n * m}.
     * See also {@link #monteCarloP(double,int,int,boolean,int,UniformRandomProvider)}.
     *
     * @param d Integral D-statistic.
     * @param n First sample size.
     * @param m Second sample size.
     * @param iterations Number of random partitions to generate.
     * @param rng RNG used for generating the partitions.
     * @return proportion of randomly generated m-n partitions of m + n that result in \(D_{n,m}\)
     * greater than or equal to {@code d / (n * m))}.
     */
    private double integralMonteCarloP(final long d,
                                       final int n,
                                       final int m,
                                       final int iterations,
                                       UniformRandomProvider rng) {
        // ensure that nn is always the max of (n, m) to require fewer random numbers
        final int nn = JdkMath.max(n, m);
        final int mm = JdkMath.min(n, m);
        final int sum = nn + mm;

        int tail = 0;
        final boolean[] b = new boolean[sum];
        for (int i = 0; i < iterations; i++) {
            fillBooleanArrayRandomlyWithFixedNumberTrueValues(b, nn, rng);
            long curD = 0L;
            for(int j = 0; j < b.length; ++j) {
                if (b[j]) {
                    curD += mm;
                    if (curD >= d) {
                        tail++;
                        break;
                    }
                } else {
                    curD -= nn;
                    if (curD <= -d) {
                        tail++;
                        break;
                    }
                }
            }
        }
        return (double) tail / iterations;
    }

    /**
     * If there are no ties in the combined dataset formed from x and y,
     * this method is a no-op.
     * If there are ties, a uniform random deviate in
     * is added to each value in x and y, and this method overwrites the
     * data in x and y with the jittered values.
     *
     * @param x First sample.
     * @param y Second sample.
     * @throw NotANumberException if any of the input arrays contain
     * a NaN value.
     */
    private static void fixTies(double[] x, double[] y) {
        if (hasTies(x, y)) {
            // Add jitter using a fixed seed (so same arguments always give same results),
            // low-initialization-overhead generator.
            final UniformRandomProvider rng = RandomSource.SPLIT_MIX_64.create(876543217L);

            // It is theoretically possible that jitter does not break ties, so repeat
            // until all ties are gone.  Bound the loop and throw MIE if bound is exceeded.
            int fixedRangeTrials = 10;
            int jitterRange = 10;
            int ct = 0;
            boolean ties = true;
            do {
                // Nudge the data using a fixed range.
                jitter(x, rng, jitterRange);
                jitter(y, rng, jitterRange);
                ties = hasTies(x, y);
                ++ct;
            } while (ties && ct < fixedRangeTrials);

            if (ties) {
                throw new MaxCountExceededException(fixedRangeTrials);
            }
        }
    }

    /**
     * Returns true iff there are ties in the combined sample formed from
     * x and y.
     *
     * @param x First sample.
     * @param y Second sample.
     * @return true if x and y together contain ties.
     * @throw InsufficientDataException if the input arrays only contain infinite values.
     * @throw NotANumberException if any of the input arrays contain a NaN value.
     */
    private static boolean hasTies(double[] x, double[] y) {
       final double[] values = MathArrays.unique(MathArrays.concatenate(x, y));

       // "unique" moves NaN to the head of the output array.
       if (Double.isNaN(values[0])) {
           throw new NotANumberException();
       }

       int infCount = 0;
       for (double v : values) {
           if (Double.isInfinite(v)) {
               ++infCount;
           }
       }
       if (infCount == values.length) {
           throw new InsufficientDataException();
       }

        return values.length != x.length + y.length;  // There are no ties.
    }

    /**
     * Adds random jitter to {@code data} using deviates sampled from {@code dist}.
     * <p>
     * Note that jitter is applied in-place - i.e., the array
     * values are overwritten with the result of applying jitter.</p>
     *
     * @param data input/output data array - entries overwritten by the method
     * @param rng probability distribution to sample for jitter values
     * @param ulp ulp used when generating random numbers
     * @throws NullPointerException if either of the parameters is null
     */
    private static void jitter(double[] data,
                               UniformRandomProvider rng,
                               int ulp) {
        final int range = ulp * 2;
        for (int i = 0; i < data.length; i++) {
            final double orig = data[i];
            if (Double.isInfinite(orig)) {
                continue; // Avoid NaN creation.
            }

            final int rand = rng.nextInt(range) - ulp;
            final double ulps = Math.ulp(orig);
            final double r = rand * ulps;

            data[i] += r;
        }
    }

    /**
     * The function C(i, j) defined in [4] (class javadoc), formula (5.5).
     * defined to return 1 if |i/n - j/m| <= c; 0 otherwise. Here c is scaled up
     * and recoded as a long to avoid rounding errors in comparison tests, so what
     * is actually tested is |im - jn| <= cmn.
     *
     * @param i first path parameter
     * @param j second path paramter
     * @param m first sample size
     * @param n second sample size
     * @param cmn integral D-statistic (see {@link #calculateIntegralD(double, int, int, boolean)})
     * @param strict whether or not the null hypothesis uses strict inequality
     * @return C(i,j) for given m, n, c
     */
    private static int c(int i, int j, int m, int n, long cmn, boolean strict) {
        if (strict) {
            return JdkMath.abs(i*(long)n - j*(long)m) <= cmn ? 1 : 0;
        }
        return JdkMath.abs(i*(long)n - j*(long)m) < cmn ? 1 : 0;
    }

    /**
     * The function N(i, j) defined in [4] (class javadoc).
     * Returns the number of paths over the lattice {(i,j) : 0 <= i <= n, 0 <= j <= m}
     * from (0,0) to (i,j) satisfying C(h,k, m, n, c) = 1 for each (h,k) on the path.
     * The return value is integral, but subject to overflow, so it is maintained and
     * returned as a double.
     *
     * @param i first path parameter
     * @param j second path parameter
     * @param m first sample size
     * @param n second sample size
     * @param cnm integral D-statistic (see {@link #calculateIntegralD(double, int, int, boolean)})
     * @param strict whether or not the null hypothesis uses strict inequality
     * @return number or paths to (i, j) from (0,0) representing D-values as large as c for given m, n
     */
    private static double n(int i, int j, int m, int n, long cnm, boolean strict) {
        /*
         * Unwind the recursive definition given in [4].
         * Compute n(1,1), n(1,2)...n(2,1), n(2,2)... up to n(i,j), one row at a time.
         * When n(i,*) are being computed, lag[] holds the values of n(i - 1, *).
         */
        final double[] lag = new double[n];
        double last = 0;
        for (int k = 0; k < n; k++) {
            lag[k] = c(0, k + 1, m, n, cnm, strict);
        }
        for (int k = 1; k <= i; k++) {
            last = c(k, 0, m, n, cnm, strict);
            for (int l = 1; l <= j; l++) {
                lag[l - 1] = c(k, l, m, n, cnm, strict) * (last + lag[l - 1]);
                last = lag[l - 1];
            }
        }
        return last;
    }
}
