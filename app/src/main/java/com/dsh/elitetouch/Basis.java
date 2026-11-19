package com.dsh.elitetouch;

/** Orthonormal camera / ship basis: right (+X), up (+Y), forward (+Z). */
public final class Basis {
    public final Vec3 right = new Vec3(1, 0, 0);
    public final Vec3 up = new Vec3(0, 1, 0);
    public final Vec3 fwd = new Vec3(0, 0, 1);

    public void identity() {
        right.set(1, 0, 0);
        up.set(0, 1, 0);
        fwd.set(0, 0, 1);
    }

    public void set(Basis b) {
        right.set(b.right);
        up.set(b.up);
        fwd.set(b.fwd);
    }

    public void rotateYaw(double a) {
        // turn right for positive angle
        Vec3.rotate(right, up, a);
        Vec3.rotate(fwd, up, a);
        orthonormalize();
    }

    public void rotatePitch(double a) {
        // nose up for positive angle
        Vec3 axis = new Vec3(-right.x, -right.y, -right.z);
        Vec3.rotate(up, axis, a);
        Vec3.rotate(fwd, axis, a);
        orthonormalize();
    }

    public void orthonormalize() {
        fwd.norm();
        // right = up x fwd
        double rx = up.y * fwd.z - up.z * fwd.y;
        double ry = up.z * fwd.x - up.x * fwd.z;
        double rz = up.x * fwd.y - up.y * fwd.x;
        right.set(rx, ry, rz);
        if (right.len2() < 0.04) {
            right.set(1, 0, 0);
            if (Math.abs(fwd.x) > 0.9) right.set(0, 0, 1);
            right.addScaled(fwd, -right.dot(fwd));
        }
        right.norm();
        // up = fwd x right
        double ux = fwd.y * right.z - fwd.z * right.y;
        double uy = fwd.z * right.x - fwd.x * right.z;
        double uz = fwd.x * right.y - fwd.y * right.x;
        up.set(ux, uy, uz).norm();
    }

    public Vec3 toWorld(Vec3 view) {
        return new Vec3(
                right.x * view.x + up.x * view.y + fwd.x * view.z,
                right.y * view.x + up.y * view.y + fwd.y * view.z,
                right.z * view.x + up.z * view.y + fwd.z * view.z);
    }

    public void rotateToward(Vec3 dir, double maxAngle) {
        Vec3 axis = Vec3.cross(fwd, dir);
        double s = axis.len();
        double dot = fwd.dot(dir);
        if (s < 1e-7) {
            if (dot > 0) return;
            // exactly opposite: nudge away first
            Vec3.rotate(fwd, up, 0.03);
            axis = Vec3.cross(fwd, dir);
            s = axis.len();
            if (s < 1e-7) return;
        }
        axis.mul(1.0 / s);
        double ang = Math.acos(Math.max(-1, Math.min(1, fwd.dot(dir))));
        double a = Math.min(ang, maxAngle);
        Vec3.rotate(fwd, axis, a);
        Vec3.rotate(up, axis, a);
        orthonormalize();
    }
}
