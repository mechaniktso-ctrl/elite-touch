package com.dsh.elitetouch;

/** Low-poly wireframe models, unit-ish scale. */
public final class Models {

    public static final class Model {
        public final double[][] v;
        public final int[][] e;
        public Model(double[][] v, int[][] e) { this.v = v; this.e = e; }
    }

    public static final Model COBRA = new Model(new double[][]{
            {0, 0, 1.35}, {-1.45, 0, -0.25}, {1.45, 0, -0.25},
            {0, 0.45, 0.25}, {0, -0.40, 0.25},
            {-0.85, 0, -1.05}, {0.85, 0, -1.05}, {0, 0.32, -1.0}, {0, -0.28, -1.0}
    }, new int[][]{
            {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 3}, {3, 2}, {2, 4}, {4, 1},
            {1, 5}, {2, 6}, {5, 6}, {5, 7}, {6, 7}, {5, 8}, {6, 8}, {7, 8},
            {1, 7}, {2, 7}, {1, 8}, {2, 8}
    });

    public static final Model KRAIT = new Model(new double[][]{
            {0, 0, 1.45}, {-0.95, 0, -0.30}, {0.95, 0, -0.30},
            {0, 0.34, -0.55}, {0, -0.34, -0.55}, {0, 0, -0.95}
    }, new int[][]{
            {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 3}, {3, 2}, {2, 4}, {4, 1},
            {1, 5}, {2, 5}, {3, 5}, {4, 5}
    });

    public static final Model VIPER = new Model(new double[][]{
            {0, 0, 1.55}, {-1.05, 0, 0.0}, {1.05, 0, 0.0},
            {0, 0.30, -0.60}, {0, -0.30, -0.60},
            {-0.50, 0, -0.95}, {0.50, 0, -0.95}, {0, 0, -1.25}
    }, new int[][]{
            {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 3}, {2, 3}, {1, 4}, {2, 4},
            {1, 5}, {2, 6}, {5, 7}, {6, 7}, {5, 6}, {3, 7}, {4, 7}, {1, 2}
    });

    public static final Model PYTHON = new Model(new double[][]{
            {0, 0, 1.85}, {-1.15, -0.35, -0.55}, {1.15, -0.35, -0.55},
            {1.15, 0.35, -0.55}, {-1.15, 0.35, -0.55}, {0, 0, -1.35}
    }, new int[][]{
            {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 2}, {2, 3}, {3, 4}, {4, 1},
            {1, 5}, {2, 5}, {3, 5}, {4, 5}
    });

    public static final Model SIDEWINDER = new Model(new double[][]{
            {0, 0, 1.25}, {-1.25, 0, -0.20}, {1.25, 0, -0.20},
            {0, 0.45, -0.40}, {0, 0, -1.05}
    }, new int[][]{
            {0, 1}, {0, 2}, {0, 3}, {1, 3}, {3, 2}, {1, 4}, {2, 4}, {3, 4}, {1, 2}
    });

    public static final Model MISSILE = new Model(new double[][]{
            {0, 0, 1.0}, {-0.35, 0, -0.6}, {0.35, 0, -0.6},
            {0, 0.3, -0.6}, {0, -0.3, -0.6}, {0, 0, -1.0}
    }, new int[][]{
            {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 5}, {2, 5}, {3, 5}, {4, 5}, {1, 2}, {3, 4}
    });

    public static final Model STATION;
    public static final Model ASTEROID;

    static {
        int n = 8;
        double[][] v = new double[n * 2 + 4][3];
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            v[i][0] = Math.cos(a);
            v[i][1] = Math.sin(a);
            v[i][2] = 0.55;
        }
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            v[n + i][0] = Math.cos(a) * 0.78;
            v[n + i][1] = Math.sin(a) * 0.78;
            v[n + i][2] = -0.55;
        }
        // docking slot on the front
        v[n * 2 + 0] = new double[]{-0.30, -0.11, 0.70};
        v[n * 2 + 1] = new double[]{0.30, -0.11, 0.70};
        v[n * 2 + 2] = new double[]{0.30, 0.11, 0.70};
        v[n * 2 + 3] = new double[]{-0.30, 0.11, 0.70};
        int[] e = new int[(n * 4 + 4) * 2];
        int k = 0;
        for (int i = 0; i < n; i++) {
            e[k++] = i; e[k++] = (i + 1) % n;
            e[k++] = n + i; e[k++] = n + (i + 1) % n;
            e[k++] = i; e[k++] = n + i;
        }
        e[k++] = n * 2; e[k++] = n * 2 + 1;
        e[k++] = n * 2 + 1; e[k++] = n * 2 + 2;
        e[k++] = n * 2 + 2; e[k++] = n * 2 + 3;
        e[k++] = n * 2 + 3; e[k++] = n * 2;
        int[][] edges = new int[k / 2][2];
        for (int i = 0; i < edges.length; i++) {
            edges[i][0] = e[i * 2];
            edges[i][1] = e[i * 2 + 1];
        }
        STATION = new Model(v, edges);

        // icosahedron
        double phi = (1 + Math.sqrt(5)) / 2;
        double[][] iv = {
                {-1, phi, 0}, {1, phi, 0}, {-1, -phi, 0}, {1, -phi, 0},
                {0, -1, phi}, {0, 1, phi}, {0, -1, -phi}, {0, 1, -phi},
                {phi, 0, -1}, {phi, 0, 1}, {-phi, 0, -1}, {-phi, 0, 1}
        };
        double len = Math.sqrt(1 + phi * phi);
        for (double[] p : iv) { p[0] /= len; p[1] /= len; p[2] /= len; }
        java.util.ArrayList<int[]> ies = new java.util.ArrayList<>();
        for (int i = 0; i < iv.length; i++) {
            for (int j = i + 1; j < iv.length; j++) {
                double dx = iv[i][0] - iv[j][0], dy = iv[i][1] - iv[j][1], dz = iv[i][2] - iv[j][2];
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 1.12) ies.add(new int[]{i, j});
            }
        }
        ASTEROID = new Model(iv, ies.toArray(new int[0][]));
    }
}
