package com.dsh.elitetouch;

/** Procedural eight-galaxy-flavoured cluster of systems, prices and names. */
public final class Galaxy {

    public static final String[] COMMODITY = {
            "FOOD", "TEXTILES", "LIQUOR", "LUXURIES", "COMPUTERS",
            "MACHINERY", "ALLOYS", "FIREARMS", "FURS", "GEM-STONES"
    };
    public static final double[] BASE_PRICE = {6, 10, 18, 95, 88, 62, 38, 72, 58, 165};
    /** 0 = agricultural good, 1 = industrial good, 2 = neutral. */
    public static final int[] CATEGORY = {0, 0, 0, 2, 1, 1, 1, 1, 0, 2};
    public static final String[] ECO_NAME = {
            "RICH INDUSTRIAL", "AVERAGE INDUSTRIAL", "POOR INDUSTRIAL", "MAINLY INDUSTRIAL",
            "MAINLY AGRICULTURAL", "RICH AGRICULTURAL", "AVERAGE AGRICULTURAL", "POOR AGRICULTURAL"
    };
    public static final String[] GOV_NAME = {
            "ANARCHY", "FEUDAL", "MULTI-GOVERNMENT", "DICTATORSHIP",
            "COMMUNIST", "CONFEDERACY", "DEMOCRACY", "CORPORATE STATE"
    };
    public static final String[] NAMES = {
            "LAVE", "DISO", "LEESTI", "ZAONCE", "REORTE", "RIEDQUAT", "ORERVE",
            "TIONISLA", "AREXE", "XEXAOR", "ISINOR", "ONRIRA", "ENSORE", "USZAA",
            "ORRERE", "RITILA", "QUTIRI", "ARONAR", "ERLAGE", "CEERDI", "TEORGE",
            "ORESLE", "GEREGE", "AESBION", "ZAALELA", "GEXEIN", "VEEN", "ORORON"
    };

    public static final class Sys {
        public String name;
        public int cx, cy;
        public int eco;      // 0..7
        public int tech;     // 1..15
        public int gov;      // 0..7
        public final double[] price = new double[COMMODITY.length];
        public Sys(String n, int cx, int cy) { name = n; this.cx = cx; this.cy = cy; }
    }

    public final Sys[] sys = new Sys[NAMES.length];
    public int current = 0;

    public Galaxy(long seed) {
        int cols = 7;
        java.util.Random rnd = new java.util.Random(seed * 31 + 7);
        for (int i = 0; i < NAMES.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int x = 50 + col * 90 + rnd.nextInt(45) - 22;
            int y = 60 + row * 85 + rnd.nextInt(45) - 22;
            sys[i] = new Sys(NAMES[i], x, y);
            sys[i].eco = rnd.nextInt(8);
            sys[i].tech = 1 + rnd.nextInt(15);
            sys[i].gov = rnd.nextInt(8);
        }
        // A familiar starting pair: Lave is rich agricultural, Diso rich industrial.
        sys[0].eco = 5;
        sys[0].tech = 5;
        sys[0].gov = 6;
        sys[1].eco = 0;
        sys[1].tech = 7;
        sys[1].gov = 5;
        // Keep the classic first trade run within one tank of fuel.
        sys[1].cx = sys[0].cx + 35;
        sys[1].cy = sys[0].cy + 22;
        for (Sys s : sys) computePrices(s, seed);
    }

    private void computePrices(Sys s, long seed) {
        for (int c = 0; c < COMMODITY.length; c++) {
            double f = 1.0;
            int cat = CATEGORY[c];
            if (cat == 0) f = 1.0 - (s.eco - 3.5) * 0.12;
            else if (cat == 1) f = 1.0 + (s.eco - 3.5) * 0.12;
            double h = Math.sin(seed * 0.0013 + index(s) * 12.9898 + c * 78.233) * 43758.5453;
            h -= Math.floor(h);
            double jitter = 0.92 + h * 0.16;
            s.price[c] = Math.max(0.4, Math.round(BASE_PRICE[c] * f * jitter * 10.0) / 10.0);
        }
    }

    private int index(Sys s) {
        for (int i = 0; i < sys.length; i++) if (sys[i] == s) return i;
        return 0;
    }

    public double distLy(int a, int b) {
        double dx = sys[a].cx - sys[b].cx;
        double dy = sys[a].cy - sys[b].cy;
        double d = Math.sqrt(dx * dx + dy * dy) / 25.0;
        return Math.round(d * 10.0) / 10.0;
    }

    public double buyPrice(int c) {
        return sys[current].price[c];
    }

    public double sellPrice(int c) {
        return Math.round(sys[current].price[c] * 0.92 * 10.0) / 10.0;
    }

    public String govName(int i) { return GOV_NAME[sys[i].gov]; }
    public String ecoName(int i) { return ECO_NAME[sys[i].eco]; }
}
