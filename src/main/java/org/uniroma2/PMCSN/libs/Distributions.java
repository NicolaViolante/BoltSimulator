package org.uniroma2.PMCSN.libs;


public class Distributions {

    final static double TINY = 1.0e-10;
    final static double SQRT2PI = 2.506628274631;	/* sqrt(2 * pi) */

    public static double exponential(double m, Rngs r) {
        /* ---------------------------------------------------
         * generate an Exponential random variate, use m > 0.0
         * ---------------------------------------------------
         */
        return (-m * Math.log(1.0 - r.random()));
    }

    public static double uniform(double a, double b, Rngs r) {
        /* --------------------------------------------
         * generate a Uniform random variate, use a < b
         * --------------------------------------------
         */
        return (a + (b - a) * r.random());
    }

    public static double cdfNormal(double m, double s, double x)
        /* ==============================================
         * NOTE: x and m can be any value, but s > 0.0
         * ==============================================
         */
    {
        double t = (x - m) / s;

        return (cdfStandard(t));
    }

    public static double idfNormal(double m, double s, double u)
        /* =======================================================
         * NOTE: m can be any value, but s > 0.0 and 0.0 < u < 1.0
         * =======================================================
         */
    {
        return (m + s * idfStandard(u));
    }

    public static double cdfStandard(double x)
        /* ===================================
         * NOTE: x can be any value
         * ===================================
         */
    {
        double t;

        t = inGamma(0.5, 0.5 * x * x);
        if (x < 0.0)
            return (0.5 * (1.0 - t));
        else
            return (0.5 * (1.0 + t));
    }

    public static double idfStandard(double u)
        /* ===================================
         * NOTE: 0.0 < u < 1.0
         * ===================================
         */
    {
        double t, x = 0.0;                    /* initialize to the mean, then  */

        do {                                  /* use Newton-Raphson iteration  */
            t = x;
            x = t + (u - cdfStandard(t)) / pdfStandard(t);
        } while (Math.abs(x - t) >= TINY);
        return (x);
    }

    public static double pdfStandard(double x)
        /* ===================================
         * NOTE: x can be any value
         * ===================================
         */
    {
        return (Math.exp(- 0.5 * x * x) / SQRT2PI);
    }

    public static double inGamma(double a, double x)
        /* ========================================================================
         * Evaluates the incomplete gamma function.
         * NOTE: use a > 0.0 and x >= 0.0
         *
         * The algorithm used to evaluate the incomplete gamma function is based on
         * Algorithm AS 32, J. Applied Statistics, 1970, by G. P. Bhattacharjee.
         * See also equations 6.5.29 and 6.5.31 in the Handbook of Mathematical
         * Functions, Abramowitz and Stegum (editors).  The absolute error is less
         * than 1e-10 for all non-negative values of x.
         * ========================================================================
         */
    {
        double t, sum, term, factor, f, g;
        double c[] = new double[2];
        double p[] = new double[3];
        double q[] = new double[3];
        long   n;

        if (x > 0.0)
            factor = Math.exp(-x + a * Math.log(x) - logGamma(a));
        else
            factor = 0.0;
        if (x < a + 1.0) {                 /* evaluate as an infinite series - */
            t    = a;                        /* A & S equation 6.5.29            */
            term = 1.0 / a;
            sum  = term;
            while (term >= TINY * sum) {     /* sum until 'term' is small */
                t++;
                term *= x / t;
                sum  += term;
            }
            return (factor * sum);
        }
        else {                             /* evaluate as a continued fraction - */
            p[0]  = 0.0;                     /* A & S eqn 6.5.31 with the extended */
            q[0]  = 1.0;                     /* pattern 2-a, 2, 3-a, 3, 4-a, 4,... */
            p[1]  = 1.0;                     /* - see also A & S sec 3.10, eqn (3) */
            q[1]  = x;
            f     = p[1] / q[1];
            n     = 0;
            do {                             /* recursively generate the continued */
                g  = f;                        /* fraction 'f' until two consecutive */
                n++;                           /* values are small                   */
                if ((n % 2) > 0) {
                    c[0] = ((double) (n + 1) / 2) - a;
                    c[1] = 1.0;
                }
                else {
                    c[0] = (double) n / 2;
                    c[1] = x;
                }
                p[2] = c[1] * p[1] + c[0] * p[0];
                q[2] = c[1] * q[1] + c[0] * q[0];
                if (q[2] != 0.0) {             /* rescale to avoid overflow */
                    p[0] = p[1] / q[2];
                    q[0] = q[1] / q[2];
                    p[1] = p[2] / q[2];
                    q[1] = 1.0;
                    f    = p[1];
                }
            } while ((Math.abs(f - g) >= TINY) || (q[1] != 1.0));
            return (1.0 - factor * f);
        }
    }

    public static double logGamma(double a)
        /* ========================================================================
         * LogGamma returns the natural log of the gamma function.
         * NOTE: use a > 0.0
         *
         * The algorithm used to evaluate the natural log of the gamma function is
         * based on an approximation by C. Lanczos, SIAM J. Numerical Analysis, B,
         * vol 1, 1964.  The constants have been selected to yield a relative error
         * which is less than 2.0e-10 for all positive values of the parameter a.
         * ========================================================================
         */
    {
        double s[] = new double[6];
        double sum, temp;
        int    i;

        s[0] =  76.180091729406 / a;
        s[1] = -86.505320327112 / (a + 1.0);
        s[2] =  24.014098222230 / (a + 2.0);
        s[3] =  -1.231739516140 / (a + 3.0);
        s[4] =   0.001208580030 / (a + 4.0);
        s[5] =  -0.000005363820 / (a + 5.0);
        sum  =   1.000000000178;
        for (i = 0; i < 6; i++)
            sum += s[i];
        temp = (a - 0.5) * Math.log(a + 4.5) - (a + 4.5) + Math.log(SQRT2PI * sum);
        return (temp);
    }
}

