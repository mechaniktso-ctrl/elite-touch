package com.dsh.elitetouch;

/** Small mutable 3D vector. */
public final class Vec3 {
    public double x, y, z;

    public Vec3() {}

    public Vec3(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }

    public Vec3(Vec3 v) {
        this.x = v.x; this.y = v.y; this.z = v.z;
    }

    public Vec3 set(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
        return this;
    }

    public Vec3 set(Vec3 v) {
        this.x = v.x; this.y = v.y; this.z = v.z;
        return this;
    }

    public Vec3 add(Vec3 v) {
        x += v.x; y += v.y; z += v.z;
        return this;
    }

    public Vec3 add(double ax, double ay, double az) {
        x += ax; y += ay; z += az;
        return this;
    }

    public Vec3 addScaled(Vec3 v, double s) {
        x += v.x * s; y += v.y * s; z += v.z * s;
        return this;
    }

    public Vec3 sub(Vec3 v) {
        x -= v.x; y -= v.y; z -= v.z;
        return this;
    }

    public Vec3 mul(double s) {
        x *= s; y *= s; z *= s;
        return this;
    }

    public double dot(Vec3 v) {
        return x * v.x + y * v.y + z * v.z;
    }

    public Vec3 cross(Vec3 v) {
        double nx = y * v.z - z * v.y;
        double ny = z * v.x - x * v.z;
        double nz = x * v.y - y * v.x;
        x = nx; y = ny; z = nz;
        return this;
    }

    public double len2() {
        return x * x + y * y + z * z;
    }

    public double len() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double dist(Vec3 v) {
        double dx = x - v.x, dy = y - v.y, dz = z - v.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double dist2(Vec3 v) {
        double dx = x - v.x, dy = y - v.y, dz = z - v.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public Vec3 norm() {
        double l = len();
        if (l > 1e-9) { x /= l; y /= l; z /= l; }
        return this;
    }

    public Vec3 copy() {
        return new Vec3(x, y, z);
    }

    public static Vec3 sub(Vec3 a, Vec3 b) {
        return new Vec3(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    public static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }

    /** Squared distance from point p to segment ab. */
    public static double segDist2(Vec3 a, Vec3 b, Vec3 p) {
        double abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        double apx = p.x - a.x, apy = p.y - a.y, apz = p.z - a.z;
        double denom = abx * abx + aby * aby + abz * abz;
        double t = denom < 1e-9 ? 0 : (apx * abx + apy * aby + apz * abz) / denom;
        t = Math.max(0, Math.min(1, t));
        double dx = a.x + abx * t - p.x;
        double dy = a.y + aby * t - p.y;
        double dz = a.z + abz * t - p.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Rotate vector v around unit axis by angle (Rodrigues). */
    public static void rotate(Vec3 v, Vec3 axis, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        double dot = v.dot(axis);
        double cx = axis.y * v.z - axis.z * v.y;
        double cy = axis.z * v.x - axis.x * v.z;
        double cz = axis.x * v.y - axis.y * v.x;
        double nx = v.x * c + cx * s + axis.x * dot * (1 - c);
        double ny = v.y * c + cy * s + axis.y * dot * (1 - c);
        double nz = v.z * c + cz * s + axis.z * dot * (1 - c);
        v.x = nx; v.y = ny; v.z = nz;
    }
}
