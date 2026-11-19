package com.dsh.elitetouch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Simulation and game state. Rendering and input live in GameView. */
public class Game {

    public static final int TITLE = 0, FLIGHT = 1, HUB = 2, MARKET = 3, EQUIP = 4, CHART = 5,
            STATUS = 6, HELP = 7, ABOUT = 8, MENU = 9, DEAD = 10, JUMP = 11;

    public static final int CARGO_CAP = 20;
    public static final double FUEL_MAX = 7.0;
    public static final double FUEL_PRICE = 1.0;
    public static final int MISSILE_PRICE = 30;
    public static final int MISSILE_MAX = 4;
    public static final double MAX_SPEED = 800;
    public static final double ACCEL = 750;
    public static final double TURN_RATE = 1.6;
    public static final double STATION_SCALE = 420;
    public static final double DOCK_RANGE = 1500;

    public final SoundFx sound;
    private final SharedPreferences prefs;
    private final Random rng = new Random();

    public Galaxy galaxy;
    public long seed;
    public int state = TITLE;
    public int returnState = TITLE;
    public String commander = "JAMESON";

    public final Vec3 pos = new Vec3(0, 0, -3600);
    public final Vec3 stationPos = new Vec3();
    public final Vec3 planetPos = new Vec3();
    public final Vec3 sunPos = new Vec3();
    public final Basis basis = new Basis();
    public final Basis stationBasis = new Basis();
    public final List<Star> stars = new ArrayList<>();
    public final List<Enemy> enemies = new ArrayList<>();
    public final List<Bolt> bolts = new ArrayList<>();
    public final List<Mis> flying = new ArrayList<>();
    public final List<Particle> particles = new ArrayList<>();

    public double speed, targetSpeed, shield = 100, hull = 100, fuel = 7;
    public double laserHeat, fireCd, missileCd, sinceHit = 99, dockCooldown;
    public int credits = 250, missiles = 4, kills;
    public final int[] cargo = new int[10];
    public boolean heatLock, firing, soundOn = true;
    public double stickX, stickY;
    public String msg;
    public double msgTimer;
    public double stationDist, stationAngle;
    public boolean dockReady;
    public double stationSpin, spawnTimer, time;
    public Enemy target;
    public double jumpT;
    public int jumpTarget;
    public String jumpName = "";
    public double shake, flash;
    public double titleAngle;
    public double planetRadius = 14000;
    public double stationRadius = STATION_SCALE;

    public static class Star {
        public double x, y, z, b;
        public Star(double x, double y, double z, double b) {
            this.x = x; this.y = y; this.z = z; this.b = b;
        }
    }

    public static class Enemy {
        public final Vec3 pos = new Vec3();
        public final Basis b = new Basis();
        public double speed, turn, scale, radius, hull, hullMax, shield, shieldMax, fireCd;
        public int type, bounty;
        public String name;
        public boolean dead;
    }

    public static class Bolt {
        public final Vec3 pos = new Vec3();
        public final Vec3 vel = new Vec3();
        public double life, dmg;
        public boolean friendly;
    }

    public static class Mis {
        public final Vec3 pos = new Vec3();
        public final Vec3 vel = new Vec3();
        public Enemy target;
        public double life;
        public boolean dead;
    }

    public static class Particle {
        public final Vec3 pos = new Vec3();
        public final Vec3 vel = new Vec3();
        public double life, max;
        public int color;
    }

    public Game(Context ctx) {
        sound = new SoundFx(ctx);
        prefs = ctx.getSharedPreferences("elite_touch", Context.MODE_PRIVATE);
        soundOn = prefs.getBoolean("sound", true);
        sound.enabled = soundOn;
        seed = System.currentTimeMillis();
        galaxy = new Galaxy(seed);
        makeStars(seed, 0);
        enterSystem(0);
        state = TITLE;
    }

    // ------------------------------------------------------------------ scenes

    private void makeStars(long s, int sys) {
        stars.clear();
        Random r = new Random(s * 17 + sys * 131 + 5);
        for (int i = 0; i < 260; i++) {
            double u = r.nextDouble() * 2 - 1;
            double a = r.nextDouble() * Math.PI * 2;
            double q = Math.sqrt(1 - u * u);
            double rad = 26000 + r.nextDouble() * 64000;
            stars.add(new Star(Math.cos(a) * q * rad, u * rad, Math.sin(a) * q * rad,
                    0.25 + r.nextDouble() * 0.75));
        }
    }

    public void enterSystem(int idx) {
        galaxy.current = idx;
        Random r = new Random(seed + idx * 7919L + 31);
        pos.set(0, 0, -3600);
        basis.identity();
        stationPos.set(0, 0, 0);
        stationBasis.identity();
        stationSpin = r.nextDouble() * 6.28;
        planetPos.set(24000 + r.nextInt(9000), -5000 + r.nextInt(9000), 40000 + r.nextInt(9000));
        sunPos.set(-48000 - r.nextInt(9000), 7000 + r.nextInt(8000), 50000 + r.nextInt(9000));
        planetRadius = 12000 + r.nextInt(5000);
        enemies.clear();
        bolts.clear();
        flying.clear();
        particles.clear();
        target = null;
        speed = 0;
        targetSpeed = 0.4;
        shield = Math.max(shield, 60);
        fireCd = 0;
        missileCd = 0;
        laserHeat = 0;
        heatLock = false;
        spawnTimer = 20;
        dockCooldown = 2;
        stationDist = 3600;
        stationAngle = 0;
        dockReady = false;
        msg = null;
        msgTimer = 0;
        makeStars(seed, idx);
    }

    public void newGame(String name) {
        commander = (name == null || name.trim().isEmpty()) ? "JAMESON" : name.trim().toUpperCase();
        seed = System.currentTimeMillis();
        galaxy = new Galaxy(seed);
        credits = 250;
        fuel = FUEL_MAX;
        missiles = 4;
        kills = 0;
        hull = 100;
        shield = 100;
        for (int i = 0; i < cargo.length; i++) cargo[i] = 0;
        enterSystem(0);
        state = FLIGHT;
        message("WELCOME COMMANDER " + commander + " - DOCK AT THE STATION TO TRADE", 6);
        save();
    }

    public void goTitle() {
        state = TITLE;
        titleAngle = 0;
    }

    // ------------------------------------------------------------------ update

    public void update(double dt) {
        if (dt > 0.05) dt = 0.05;
        time += dt;
        if (msgTimer > 0) msgTimer -= dt;
        if (state == FLIGHT) {
            updateFlight(dt);
        } else if (state == JUMP) {
            jumpT -= dt;
            if (jumpT <= 0) completeJump();
        } else if (state == DEAD) {
            updateParticles(dt);
            shake *= Math.pow(0.05, dt);
            flash = Math.max(0, flash - dt * 1.2);
        } else {
            titleAngle += dt * 0.2;
            shake = 0;
        }
    }

    private void updateFlight(double dt) {
        double yaw = stickX * TURN_RATE * dt;
        double pitch = -stickY * TURN_RATE * 0.85 * dt;
        basis.rotateYaw(yaw);
        basis.rotatePitch(pitch);

        double tgt = targetSpeed * MAX_SPEED;
        if (speed < tgt) speed = Math.min(tgt, speed + ACCEL * dt);
        else speed = Math.max(tgt, speed - ACCEL * dt * 1.4);
        pos.addScaled(basis.fwd, speed * dt);

        fireCd -= dt;
        missileCd -= dt;
        sinceHit += dt;
        laserHeat = Math.max(0, laserHeat - 0.40 * dt);
        if (heatLock && laserHeat < 0.30) heatLock = false;
        if (firing) tryFire();
        if (shield < 100 && sinceHit > 6) shield = Math.min(100, shield + 4.5 * dt);
        if (flash > 0) flash = Math.max(0, flash - dt * 2.2);
        shake *= Math.pow(0.03, dt);
        if (dockCooldown > 0) dockCooldown -= dt;

        stationSpin += dt * 0.12;
        stationBasis.identity();
        stationBasis.rotateYaw(stationSpin);
        stationBasis.rotatePitch(0.18);

        Vec3 toStation = Vec3.sub(stationPos, pos);
        stationDist = toStation.len();
        Vec3 sd = toStation.copy().norm();
        stationAngle = Math.acos(Math.max(-1, Math.min(1, basis.fwd.dot(sd))));
        dockReady = dockCooldown <= 0 && stationDist < DOCK_RANGE && stationAngle < 0.72;

        if (dockCooldown <= 0 && stationDist < 520 && stationAngle < 0.42) {
            dock();
            return;
        }

        updateEnemies(dt);
        updateBolts(dt);
        if (state != FLIGHT) return;
        updateMissiles(dt);
        updateParticles(dt);
        target = pickTarget();

        spawnTimer -= dt;
        int max = maxPirates();
        if (spawnTimer <= 0 && enemies.size() < max) {
            spawnEnemy();
            spawnTimer = 16 + rng.nextDouble() * 20;
        }
    }

    private int maxPirates() {
        int g = galaxy.sys[galaxy.current].gov;
        if (g <= 1) return 3;
        if (g <= 3) return 2;
        return 1;
    }

    private Enemy pickTarget() {
        Enemy best = null;
        double bd = 8000;
        for (Enemy e : enemies) {
            if (e.dead) continue;
            double d = e.pos.dist(pos);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private Enemy pickTargetCone() {
        Enemy best = null;
        double bd = 8000;
        for (Enemy e : enemies) {
            if (e.dead) continue;
            Vec3 d = Vec3.sub(e.pos, pos);
            double dist = d.len();
            if (dist < 1e-3) continue;
            double ang = Math.acos(Math.max(-1, Math.min(1, basis.fwd.dot(d.copy().norm()))));
            if (ang < 0.75 && dist < bd) { bd = dist; best = e; }
        }
        return best;
    }

    private void tryFire() {
        if (fireCd > 0 || heatLock) return;
        fireCd = 0.15;
        laserHeat += 0.185;
        if (laserHeat >= 1) {
            laserHeat = 1;
            heatLock = true;
            message("LASER OVERHEATED");
        }
        for (int side = -1; side <= 1; side += 2) {
            Bolt b = new Bolt();
            b.pos.set(pos).addScaled(basis.fwd, 45).addScaled(basis.right, side * 9);
            Vec3 dir = new Vec3(basis.fwd).addScaled(basis.right, side * 0.004).norm();
            b.vel.set(dir).mul(3200);
            b.life = 0.85;
            b.friendly = true;
            b.dmg = 9;
            bolts.add(b);
        }
        sound.play(SoundFx.LASER);
        shake = Math.max(shake, 1.2);
    }

    private void fireEnemyBolt(Enemy e, double dist) {
        Vec3 aim = new Vec3(pos).addScaled(basis.fwd, speed * (dist / 2600.0) * 0.6);
        Vec3 dir = Vec3.sub(aim, e.pos).norm();
        dir.addScaled(new Vec3(rng.nextDouble() - 0.5, rng.nextDouble() - 0.5, rng.nextDouble() - 0.5), 0.035);
        dir.norm();
        Bolt b = new Bolt();
        b.pos.set(e.pos).addScaled(e.b.fwd, e.scale * 0.8);
        b.vel.set(dir).mul(2600);
        b.life = 1.4;
        b.friendly = false;
        b.dmg = 5 + rng.nextDouble() * 6;
        bolts.add(b);
        sound.play(SoundFx.LASER_ENEMY);
    }

    private void updateEnemies(double dt) {
        for (Enemy e : enemies) {
            if (e.dead) continue;
            Vec3 to = Vec3.sub(pos, e.pos);
            double dist = to.len();
            Vec3 dir = to.copy().norm();
            if (dist > 160) e.b.rotateToward(dir, e.turn * dt);

            double sp = e.speed;
            if (dist < 420) sp *= 0.55;
            e.pos.addScaled(e.b.fwd, sp * dt);

            if (dist < 160) {
                e.pos.addScaled(dir, -(160 - dist) * 1.5 * dt);
            }

            e.fireCd -= dt;
            if (e.fireCd <= 0 && dist < 2500 && dist > 90) {
                double ang = Math.acos(Math.max(-1, Math.min(1, e.b.fwd.dot(dir))));
                if (ang < 0.25) {
                    fireEnemyBolt(e, dist);
                    e.fireCd = 1.9 + rng.nextDouble() * 1.8;
                }
            }
        }
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext(); ) {
            Enemy e = it.next();
            if (e.dead) it.remove();
        }
    }

    private void updateBolts(double dt) {
        for (Iterator<Bolt> it = bolts.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            Vec3 old = b.pos.copy();
            b.pos.addScaled(b.vel, dt);
            b.life -= dt;
            boolean hit = false;
            if (b.friendly) {
                for (Enemy e : enemies) {
                    if (e.dead) continue;
                    double rr = e.radius + 8;
                    if (Vec3.segDist2(old, b.pos, e.pos) < rr * rr) {
                        damageEnemy(e, b.dmg);
                        hit = true;
                        break;
                    }
                }
                if (!hit) {
                    // asteroids are not simulated; shots fly on
                }
            } else {
                double rr = 22;
                if (Vec3.segDist2(old, b.pos, pos) < rr * rr) {
                    damagePlayer(b.dmg);
                    hit = true;
                }
            }
            if (hit || b.life <= 0) it.remove();
        }
    }

    private void updateMissiles(double dt) {
        for (Iterator<Mis> it = flying.iterator(); it.hasNext(); ) {
            Mis m = it.next();
            m.life -= dt;
            if (m.target == null || m.target.dead) m.target = pickTarget();
            if (m.target != null) {
                Vec3 dir = Vec3.sub(m.target.pos, m.pos);
                double d = dir.len();
                if (d < 90) {
                    m.dead = true;
                    explosion(m.pos, 22);
                    for (Enemy e : enemies) {
                        if (!e.dead && e.pos.dist(m.pos) < 140) damageEnemy(e, 110);
                    }
                    sound.play(SoundFx.EXPLODE);
                    it.remove();
                    continue;
                }
                dir.norm();
                Vec3 desired = dir.mul(1500);
                m.vel.addScaled(Vec3.sub(desired, m.vel), Math.min(1, dt * 3.2));
                if (m.vel.len() > 1600) m.vel.norm().mul(1600);
            }
            m.pos.addScaled(m.vel, dt);
            if (m.life <= 0) it.remove();
        }
    }

    private void updateParticles(double dt) {
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.life -= dt;
            p.pos.addScaled(p.vel, dt);
            p.vel.mul(0.985);
            if (p.life <= 0) it.remove();
        }
    }

    private void damageEnemy(Enemy e, double dmg) {
        e.shield -= dmg;
        if (e.shield < 0) {
            e.hull += e.shield;
            e.shield = 0;
        }
        if (e.hull <= 0) {
            e.dead = true;
            credits += e.bounty;
            kills++;
            explosion(e.pos, (int) (e.scale * 0.7));
            sound.play(SoundFx.EXPLODE);
            message("BOUNTY +" + e.bounty + " CR");
        } else {
            sound.play(SoundFx.HIT);
            explosion(e.pos, 3);
        }
    }

    public void damagePlayer(double dmg) {
        if (state != FLIGHT) return;
        sinceHit = 0;
        shield -= dmg;
        if (shield < 0) {
            hull += shield;
            shield = 0;
        }
        flash = 0.55;
        shake = 9;
        sound.play(SoundFx.HIT);
        if (hull <= 0) {
            hull = 0;
            die();
        }
    }

    private void die() {
        state = DEAD;
        firing = false;
        stickX = stickY = 0;
        explosion(new Vec3(pos).addScaled(basis.fwd, 60), 45);
        sound.play(SoundFx.EXPLODE);
        prefs.edit().remove("save").apply();
    }

    private void explosion(Vec3 at, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.pos.set(at);
            p.pos.add(new Vec3(rng.nextDouble() - 0.5, rng.nextDouble() - 0.5, rng.nextDouble() - 0.5)
                    .mul(count > 20 ? 60 : 12));
            p.vel.set(rng.nextDouble() - 0.5, rng.nextDouble() - 0.5, rng.nextDouble() - 0.5)
                    .norm().mul(40 + rng.nextDouble() * (count > 20 ? 520 : 160));
            p.life = p.max = 0.45 + rng.nextDouble() * (count > 20 ? 1.1 : 0.5);
            p.color = rng.nextInt(3) == 0 ? 0xFFFFD27A : (rng.nextBoolean() ? 0xFFFF8A3D : 0xFFFFE9B0);
            particles.add(p);
        }
    }

    private void spawnEnemy() {
        int type;
        double roll = rng.nextDouble();
        int g = galaxy.sys[galaxy.current].gov;
        if (g <= 1) type = roll < 0.45 ? 0 : (roll < 0.7 ? 3 : (roll < 0.9 ? 1 : 2));
        else if (g <= 3) type = roll < 0.5 ? 1 : (roll < 0.8 ? 0 : 3);
        else type = roll < 0.6 ? 0 : (roll < 0.9 ? 1 : 3);

        Enemy e = new Enemy();
        e.type = type;
        if (type == 0) {
            e.name = "KRAIT"; e.speed = 620; e.turn = 1.5; e.scale = 32;
            e.hull = 45; e.shield = 15; e.bounty = 80;
        } else if (type == 1) {
            e.name = "VIPER"; e.speed = 730; e.turn = 1.75; e.scale = 36;
            e.hull = 60; e.shield = 25; e.bounty = 140;
        } else if (type == 2) {
            e.name = "PYTHON"; e.speed = 370; e.turn = 0.7; e.scale = 62;
            e.hull = 120; e.shield = 40; e.bounty = 280;
        } else {
            e.name = "SIDEWINDER"; e.speed = 690; e.turn = 1.65; e.scale = 26;
            e.hull = 30; e.shield = 10; e.bounty = 60;
        }
        e.hullMax = e.hull;
        e.shieldMax = e.shield;
        e.radius = e.scale * 1.1;
        e.fireCd = 1.0 + rng.nextDouble() * 1.5;

        double u = rng.nextDouble() * 2 - 1;
        double a = rng.nextDouble() * Math.PI * 2;
        double q = Math.sqrt(1 - u * u);
        Vec3 dir = new Vec3(Math.cos(a) * q, u, Math.sin(a) * q);
        if (dir.dot(basis.fwd) < 0.15) dir.mul(-1);
        double dist = 3200 + rng.nextDouble() * 1800;
        e.pos.set(pos).addScaled(dir, dist);

        Vec3 face = Vec3.sub(pos, e.pos).norm();
        e.b.fwd.set(face);
        if (Math.abs(face.y) > 0.94) e.b.up.set(1, 0, 0);
        else e.b.up.set(0, 1, 0);
        e.b.orthonormalize();
        enemies.add(e);
    }

    // ------------------------------------------------------------------ actions

    public void setStick(double x, double y) {
        stickX = x;
        stickY = y;
    }

    public void setFiring(boolean f) {
        firing = f;
    }

    public void throttle(int dir) {
        targetSpeed = Math.max(0, Math.min(1, targetSpeed + dir * 0.2));
        if (state == FLIGHT) sound.play(SoundFx.CLICK);
    }

    public void tryDock() {
        if (state != FLIGHT) return;
        if (stationDist <= DOCK_RANGE && stationAngle <= 0.72) dock();
        else if (stationDist > DOCK_RANGE) message("STATION OUT OF RANGE");
        else message("ALIGN WITH STATION");
    }

    public void dock() {
        state = HUB;
        firing = false;
        speed = 0;
        targetSpeed = 0;
        shield = 100;
        stickX = stickY = 0;
        msg = null;
        msgTimer = 0;
        sound.play(SoundFx.DOCK);
        save();
    }

    public void launch() {
        state = FLIGHT;
        pos.set(0, 0, -3600);
        basis.identity();
        speed = 0;
        targetSpeed = 0.4;
        shield = 100;
        dockCooldown = 2.5;
        firing = false;
        message("LAUNCHED");
        save();
    }

    public void requestJump(int idx) {
        if (idx < 0 || idx >= galaxy.sys.length) return;
        if (idx == galaxy.current) {
            message("ALREADY IN THIS SYSTEM");
            return;
        }
        double d = galaxy.distLy(galaxy.current, idx);
        if (d > fuel + 1e-6) {
            message("INSUFFICIENT FUEL");
            return;
        }
        fuel -= d;
        jumpTarget = idx;
        jumpName = galaxy.sys[idx].name;
        jumpT = 2.0;
        state = JUMP;
        firing = false;
        stickX = stickY = 0;
        sound.play(SoundFx.JUMP);
    }

    private void completeJump() {
        enterSystem(jumpTarget);
        state = FLIGHT;
        message("ARRIVED AT " + galaxy.sys[galaxy.current].name, 4);
        save();
    }

    public void buyCargo(int c, int qty) {
        int used = cargoUsed();
        qty = Math.min(qty, CARGO_CAP - used);
        if (qty <= 0) { message("CARGO BAY FULL"); return; }
        double price = galaxy.buyPrice(c);
        int afford = (int) Math.floor(credits / Math.max(0.01, price));
        qty = Math.min(qty, afford);
        if (qty <= 0) { message("NOT ENOUGH CREDITS"); return; }
        cargo[c] += qty;
        credits -= (int) Math.round(price * qty);
        sound.play(SoundFx.CLICK);
    }

    public void sellCargo(int c, int qty) {
        qty = Math.min(qty, cargo[c]);
        if (qty <= 0) { message("NO CARGO"); return; }
        cargo[c] -= qty;
        credits += (int) Math.round(galaxy.sellPrice(c) * qty);
        sound.play(SoundFx.CLICK);
    }

    public void buyFuel() {
        if (fuel >= FUEL_MAX - 0.01) { message("FUEL TANKS FULL"); return; }
        int cost = (int) Math.ceil((FUEL_MAX - fuel) * FUEL_PRICE);
        if (credits < cost) { message("NEED " + cost + " CR"); return; }
        credits -= cost;
        fuel = FUEL_MAX;
        sound.play(SoundFx.CLICK);
    }

    public void buyMissile() {
        if (missiles >= MISSILE_MAX) { message("MISSILE RACKS FULL"); return; }
        if (credits < MISSILE_PRICE) { message("NOT ENOUGH CREDITS"); return; }
        credits -= MISSILE_PRICE;
        missiles++;
        sound.play(SoundFx.CLICK);
    }

    public void repairHull() {
        if (hull >= 99.9) { message("HULL ALREADY INTACT"); return; }
        int cost = (int) Math.ceil((100 - hull) * 2.0);
        if (credits < cost) { message("REPAIR COSTS " + cost + " CR"); return; }
        credits -= cost;
        hull = 100;
        sound.play(SoundFx.DOCK);
    }

    public void launchMissile() {
        if (state != FLIGHT) return;
        if (missileCd > 0) return;
        if (missiles <= 0) { message("NO MISSILES"); return; }
        Enemy t = pickTargetCone();
        if (t == null) { message("NO TARGET LOCK"); return; }
        missiles--;
        missileCd = 1.2;
        Mis m = new Mis();
        m.pos.set(pos).addScaled(basis.fwd, 40);
        m.vel.set(basis.fwd).mul(600);
        m.target = t;
        m.life = 9;
        flying.add(m);
        sound.play(SoundFx.MISSILE);
        message("MISSILE AWAY");
    }

    public int cargoUsed() {
        int n = 0;
        for (int v : cargo) n += v;
        return n;
    }

    public String rank() {
        int k = kills;
        if (k >= 50) return "ELITE";
        if (k >= 35) return "DEADLY";
        if (k >= 25) return "DANGEROUS";
        if (k >= 18) return "COMPETENT";
        if (k >= 12) return "ABOVE AVERAGE";
        if (k >= 8) return "AVERAGE";
        if (k >= 5) return "POOR";
        if (k >= 2) return "MOSTLY HARMLESS";
        return "HARMLESS";
    }

    public void message(String text) {
        message(text, 2.8);
    }

    public void message(String text, double seconds) {
        msg = text;
        msgTimer = seconds;
    }

    public void setSound(boolean on) {
        soundOn = on;
        sound.enabled = on;
        prefs.edit().putBoolean("sound", on).apply();
        if (on) sound.play(SoundFx.CLICK);
    }

    // ------------------------------------------------------------------ save

    public boolean hasSave() {
        return prefs.contains("save");
    }

    public void save() {
        if (state == TITLE || state == DEAD) return;
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            o.put("seed", seed);
            o.put("name", commander);
            o.put("credits", credits);
            o.put("fuel", fuel);
            o.put("missiles", missiles);
            o.put("kills", kills);
            o.put("hull", hull);
            o.put("sys", galaxy.current);
            JSONArray a = new JSONArray();
            for (int v : cargo) a.put(v);
            o.put("cargo", a);
            prefs.edit().putString("save", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public boolean loadGame() {
        try {
            String s = prefs.getString("save", null);
            if (s == null) return false;
            JSONObject o = new JSONObject(s);
            seed = o.getLong("seed");
            galaxy = new Galaxy(seed);
            commander = o.optString("name", "JAMESON");
            credits = o.optInt("credits", 250);
            fuel = o.optDouble("fuel", 7);
            missiles = o.optInt("missiles", 4);
            kills = o.optInt("kills", 0);
            hull = o.optDouble("hull", 100);
            JSONArray a = o.optJSONArray("cargo");
            for (int i = 0; i < cargo.length; i++) cargo[i] = a != null ? a.optInt(i, 0) : 0;
            int sys = o.optInt("sys", 0);
            enterSystem(sys);
            shield = 100;
            state = FLIGHT;
            message("SAVED GAME RESTORED");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void deleteSave() {
        prefs.edit().remove("save").apply();
    }
}
