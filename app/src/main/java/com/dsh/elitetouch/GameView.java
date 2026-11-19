package com.dsh.elitetouch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Wireframe renderer, HUD and touch UI. */
public class GameView extends View {

    private static final int GREEN = 0xFF46FF9A;
    private static final int GREEN_DIM = 0xFF1B7F4B;
    private static final int AMBER = 0xFFFFC857;
    private static final int RED = 0xFFFF5C5C;
    private static final int CYAN = 0xFF7BE9FF;
    private static final int WHITE = 0xFFE8FFF2;
    private static final int BLACK = 0xFF020503;

    private static final int S_NORMAL = 0, S_PRIMARY = 1, S_DANGER = 2, S_ROUND = 3;

    private final Game game;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vigPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface mono = Typeface.create("monospace", Typeface.NORMAL);
    private final Typeface monoBold = Typeface.create("monospace", Typeface.BOLD);
    private final Vec3 ORIGIN = new Vec3();
    private final Basis ID = new Basis();
    private final Basis titleBasis = new Basis();
    private final List<Btn> buttons = new ArrayList<>();

    private float w, h, density, focal, cx, cy;
    private boolean running;
    private long lastNanos;
    private int lastState = -1;

    private int joyId = -1;
    private float joyBaseX, joyBaseY, joyX, joyY;
    private float joyHomeX, joyHomeY;
    private int fireId = -1;
    private Btn dockBtn;

    private int marketSel = 0;
    private int chartSel = 0;
    private float toast;

    private Bitmap scanBmp;
    private float dpScale;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!running) return;
            double dt = lastNanos == 0 ? 0.016 : (frameTimeNanos - lastNanos) / 1_000_000_000.0;
            lastNanos = frameTimeNanos;
            game.update(dt);
            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private static class Btn {
        String id, label;
        float x, y, w, h;
        int style;
        boolean enabled = true;

        Btn(String id, String label, float x, float y, float w, float h, int style) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.style = style;
        }

        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    public GameView(Context ctx) {
        super(ctx);
        game = new Game(ctx);
        density = getResources().getDisplayMetrics().density;
        dpScale = density;
        setBackgroundColor(BLACK);
        setKeepScreenOn(true);
        setFocusable(true);
        starPaint.setStyle(Paint.Style.STROKE);
        starPaint.setStrokeCap(Paint.Cap.ROUND);
        shadowPaint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.ROUND);
        tp.setTypeface(mono);
        tp.setLetterSpacing(0.02f);
    }

    // ---------------------------------------------------------------- lifecycle

    public void resumeGame() {
        if (running) return;
        running = true;
        lastNanos = 0;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    public void pauseGame() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        game.setFiring(false);
        game.setStick(0, 0);
        if (game.state != Game.TITLE && game.state != Game.DEAD) game.save();
    }

    public boolean back() {
        switch (game.state) {
            case Game.TITLE:
                return false;
            case Game.FLIGHT:
                game.state = Game.MENU;
                return true;
            case Game.MENU:
                game.state = Game.FLIGHT;
                return true;
            case Game.JUMP:
                return true;
            case Game.DEAD:
                game.goTitle();
                return true;
            case Game.HUB:
                game.launch();
                return true;
            case Game.MARKET:
            case Game.EQUIP:
            case Game.CHART:
            case Game.STATUS:
            case Game.HELP:
            case Game.ABOUT:
                game.state = game.returnState;
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        w = nw;
        h = nh;
        cx = w / 2f;
        cy = h / 2f;
        focal = h * 1.05f;
        joyHomeX = dp(92);
        joyHomeY = h - dp(92);
        if (joyId < 0) {
            joyBaseX = joyHomeX;
            joyBaseY = joyHomeY;
            joyX = joyBaseX;
            joyY = joyBaseY;
        }
        bgPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF06110C, 0xFF030805, 0xFF010302}, null, Shader.TileMode.CLAMP));
        vigPaint.setShader(new RadialGradient(cx, cy, Math.max(w, h) * 0.72f,
                new int[]{0x00000000, 0x00000000, 0x77000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        makeScanBitmap();
        rebuild();
    }

    private void makeScanBitmap() {
        if (w <= 0) return;
        int rows = 4;
        scanBmp = Bitmap.createBitmap(1, rows, Bitmap.Config.ARGB_8888);
        int[] px = new int[]{0x00000000, 0x14000000, 0x00000000, 0x14000000};
        scanBmp.setPixels(px, 0, 1, 0, 0, 1, rows);
        scanPaint.setShader(new BitmapShader(scanBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    private float dp(float v) {
        return v * dpScale;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final int action = e.getActionMasked();
        final int idx = e.getActionIndex();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = e.getX(idx), y = e.getY(idx);
            int id = e.getPointerId(idx);

            if (handleListTap(x, y)) return true;

            Btn b = hitButton(x, y);
            if (b != null) {
                if (b.id.equals("fire")) {
                    fireId = id;
                    game.setFiring(true);
                } else {
                    click();
                    activate(b);
                }
                return true;
            }
            if (game.state == Game.FLIGHT && x < w * 0.45f && y > h * 0.28f) {
                joyId = id;
                joyBaseX = x;
                joyBaseY = y;
                joyX = x;
                joyY = y;
                updateStick();
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                if (e.getPointerId(i) == joyId) {
                    joyX = e.getX(i);
                    joyY = e.getY(i);
                    updateStick();
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int id = e.getPointerId(idx);
            if (id == joyId) {
                joyId = -1;
                game.setStick(0, 0);
            }
            if (id == fireId) {
                fireId = -1;
                game.setFiring(false);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            joyId = -1;
            fireId = -1;
            game.setStick(0, 0);
            game.setFiring(false);
        }
        return true;
    }

    private void updateStick() {
        float max = dp(62);
        float dx = joyX - joyBaseX;
        float dy = joyY - joyBaseY;
        float len = (float) Math.hypot(dx, dy);
        if (len > max) {
            dx *= max / len;
            dy *= max / len;
            joyX = joyBaseX + dx;
            joyY = joyBaseY + dy;
        }
        float nx = dx / max;
        float ny = dy / max;
        if (Math.hypot(nx, ny) < 0.13) {
            nx = 0;
            ny = 0;
        }
        game.setStick(nx, ny);
    }

    private boolean handleListTap(float x, float y) {
        if (game.state == Game.MARKET) {
            for (int i = 0; i < Game.CARGO_CAP / 2; i++) {
                if (marketRow(i).contains(x, y)) {
                    marketSel = i;
                    click();
                    return true;
                }
            }
        }
        if (game.state == Game.CHART) {
            for (int i = 0; i < game.galaxy.sys.length; i++) {
                float[] p = chartPoint(i);
                if (Math.hypot(p[0] - x, p[1] - y) < dp(26)) {
                    chartSel = i;
                    click();
                    return true;
                }
            }
        }
        return false;
    }

    private Btn hitButton(float x, float y) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Btn b = buttons.get(i);
            if (b.enabled && b.contains(x, y)) return b;
        }
        return null;
    }

    private void click() {
        game.sound.play(SoundFx.CLICK);
        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void activate(Btn b) {
        switch (b.id) {
            case "new":
                promptNewGame();
                break;
            case "cont":
                if (!game.loadGame()) game.message("NO SAVED GAME");
                break;
            case "help":
                open(Game.HELP);
                break;
            case "about":
                open(Game.ABOUT);
                break;
            case "sound":
                game.setSound(!game.soundOn);
                break;
            case "menu":
                game.setFiring(false);
                game.setStick(0, 0);
                game.state = Game.MENU;
                break;
            case "msl":
                game.launchMissile();
                break;
            case "thr+":
                game.throttle(1);
                break;
            case "thr-":
                game.throttle(-1);
                break;
            case "dock":
                game.tryDock();
                break;
            case "market":
                marketSel = 0;
                open(Game.MARKET);
                break;
            case "equip":
                open(Game.EQUIP);
                break;
            case "chart":
                chartSel = game.galaxy.current;
                open(Game.CHART);
                break;
            case "status":
                open(Game.STATUS);
                break;
            case "save":
                game.save();
                game.message("GAME SAVED");
                break;
            case "launch":
                game.launch();
                break;
            case "back":
                back();
                break;
            case "buy1":
                game.buyCargo(marketSel, 1);
                break;
            case "buy10":
                game.buyCargo(marketSel, 10);
                break;
            case "sell1":
                game.sellCargo(marketSel, 1);
                break;
            case "sell10":
                game.sellCargo(marketSel, 10);
                break;
            case "fuel":
                game.buyFuel();
                break;
            case "bmsl":
                game.buyMissile();
                break;
            case "repair":
                game.repairHull();
                break;
            case "jump":
                game.requestJump(chartSel);
                if (game.state == Game.JUMP) {
                    // UI will follow automatically
                }
                break;
            case "resume":
                game.state = Game.FLIGHT;
                break;
            case "title":
                game.save();
                game.goTitle();
                break;
        }
    }

    private void open(int state) {
        game.returnState = game.state;
        game.state = state;
    }

    private void promptNewGame() {
        Activity a = (Activity) getContext();
        EditText et = new EditText(a);
        et.setText("JAMESON");
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        new AlertDialog.Builder(a)
                .setTitle("COMMANDER NAME")
                .setView(et)
                .setPositiveButton("START", (d, which) -> game.newGame(et.getText().toString()))
                .setNegativeButton("CANCEL", null)
                .show();
    }

    // ---------------------------------------------------------------- UI layout

    private void rebuild() {
        buttons.clear();
        if (w <= 0) return;
        float m = dp(14);
        switch (game.state) {
            case Game.TITLE: {
                float gap = dp(8);
                float bw = (w - 2 * m - 4 * gap) / 5f;
                float y = h - dp(70);
                add("new", "NEW GAME", m, y, bw, dp(44), S_PRIMARY);
                Btn cont = add("cont", "CONTINUE", m + (bw + gap), y, bw, dp(44), S_NORMAL);
                cont.enabled = game.hasSave();
                add("help", "HELP", m + 2 * (bw + gap), y, bw, dp(44), S_NORMAL);
                add("about", "ABOUT", m + 3 * (bw + gap), y, bw, dp(44), S_NORMAL);
                add("sound", "SOUND", m + 4 * (bw + gap), y, bw, dp(44), S_NORMAL);
                break;
            }
            case Game.FLIGHT: {
                add("menu", "MENU", w - dp(72), dp(52), dp(58), dp(26), S_NORMAL);
                add("thr+", "THR +", w - dp(60), h * 0.5f - dp(52), dp(52), dp(36), S_NORMAL);
                add("thr-", "THR -", w - dp(60), h * 0.5f - dp(10), dp(52), dp(36), S_NORMAL);
                add("msl", "MSL", w - dp(190), h - dp(108), dp(64), dp(64), S_ROUND);
                add("fire", "FIRE", w - dp(120), h - dp(132), dp(108), dp(108), S_ROUND);
                dockBtn = add("dock", "DOCK", w / 2f - dp(74), h - dp(158), dp(148), dp(38), S_PRIMARY);
                break;
            }
            case Game.HUB: {
                float px = dp(14), py = dp(52), pw = w * 0.50f, ph = h - dp(66);
                float gap = dp(8);
                float bw = (pw - dp(28) - gap) / 2f;
                float bx = px + dp(14), by = py + dp(92);
                add("market", "MARKET", bx, by, bw, dp(42), S_PRIMARY);
                add("equip", "EQUIPMENT", bx + bw + gap, by, bw, dp(42), S_NORMAL);
                add("chart", "CHART", bx, by + dp(50), bw, dp(42), S_NORMAL);
                add("status", "STATUS", bx + bw + gap, by + dp(50), bw, dp(42), S_NORMAL);
                add("save", "SAVE", bx, by + dp(100), bw, dp(42), S_NORMAL);
                add("launch", "LAUNCH", bx + bw + gap, by + dp(100), bw, dp(42), S_PRIMARY);
                break;
            }
            case Game.MARKET: {
                float gap = dp(6);
                float bw = (w - 2 * m - 4 * gap) / 5f;
                float y = h - dp(56);
                add("buy1", "BUY 1", m, y, bw, dp(40), S_NORMAL);
                add("buy10", "BUY 10", m + bw + gap, y, bw, dp(40), S_NORMAL);
                add("sell1", "SELL 1", m + 2 * (bw + gap), y, bw, dp(40), S_NORMAL);
                add("sell10", "SELL 10", m + 3 * (bw + gap), y, bw, dp(40), S_NORMAL);
                add("back", "BACK", m + 4 * (bw + gap), y, bw, dp(40), S_NORMAL);
                break;
            }
            case Game.EQUIP: {
                float bx = m, bw = w - 2 * m;
                add("fuel", "REFUEL", bx, dp(100), bw, dp(46), S_PRIMARY);
                add("bmsl", "MISSILE", bx, dp(156), bw, dp(46), S_NORMAL);
                add("repair", "REPAIR", bx, dp(212), bw, dp(46), S_NORMAL);
                add("back", "BACK", w - m - dp(110), h - dp(58), dp(110), dp(40), S_NORMAL);
                break;
            }
            case Game.CHART: {
                add("back", "BACK", m, h - dp(58), dp(110), dp(40), S_NORMAL);
                add("jump", "JUMP", w - m - dp(130), h - dp(58), dp(130), dp(40), S_PRIMARY);
                break;
            }
            case Game.STATUS:
            case Game.HELP:
            case Game.ABOUT: {
                add("back", "BACK", w - m - dp(110), h - dp(58), dp(110), dp(40), S_NORMAL);
                break;
            }
            case Game.MENU: {
                float pw = Math.min(w * 0.66f, dp(560));
                float px = (w - pw) / 2f, py = dp(24);
                float gap = dp(10);
                float bw = (pw - dp(36) - gap) / 2f;
                float bx = px + dp(18), by = py + dp(72);
                add("resume", "RESUME", bx, by, bw, dp(42), S_PRIMARY);
                add("chart", "CHART", bx + bw + gap, by, bw, dp(42), S_NORMAL);
                add("status", "STATUS", bx, by + dp(50), bw, dp(42), S_NORMAL);
                add("help", "HELP", bx + bw + gap, by + dp(50), bw, dp(42), S_NORMAL);
                add("about", "ABOUT", bx, by + dp(100), bw, dp(42), S_NORMAL);
                add("save", "SAVE", bx + bw + gap, by + dp(100), bw, dp(42), S_NORMAL);
                add("sound", "SOUND", bx, by + dp(150), bw, dp(42), S_NORMAL);
                add("title", "QUIT", bx + bw + gap, by + dp(150), bw, dp(42), S_DANGER);
                break;
            }
            case Game.DEAD: {
                float gap = dp(10);
                float bw = (w - 2 * m - gap) / 2f;
                add("new", "NEW GAME", m, h - dp(68), bw, dp(46), S_PRIMARY);
                add("title", "TITLE", m + bw + gap, h - dp(68), bw, dp(46), S_NORMAL);
                break;
            }
        }
        dockBtn = null;
        for (Btn b : buttons) if (b.id.equals("dock")) dockBtn = b;
    }

    private Btn add(String id, String label, float x, float y, float bw, float bh, int style) {
        Btn b = new Btn(id, label, x, y, bw, bh, style);
        buttons.add(b);
        return b;
    }

    private void updateDynamicButtons() {
        for (Btn b : buttons) {
            switch (b.id) {
                case "dock":
                    b.enabled = game.dockReady;
                    break;
                case "jump": {
                    double d = game.galaxy.distLy(game.galaxy.current, chartSel);
                    b.enabled = chartSel != game.galaxy.current && d <= game.fuel + 1e-6;
                    break;
                }
            }
        }
    }

    private String labelFor(Btn b) {
        switch (b.id) {
            case "msl":
                return "MSL " + game.missiles;
            case "sound":
                return game.soundOn ? "SOUND ON" : "SOUND OFF";
            case "fuel": {
                int cost = (int) Math.ceil((Game.FUEL_MAX - game.fuel) * Game.FUEL_PRICE);
                return game.fuel >= Game.FUEL_MAX - 0.01 ? "TANKS FULL" : "FILL TANKS   " + cost + " CR";
            }
            case "bmsl":
                return "BUY MISSILE   30 CR  (" + game.missiles + "/" + Game.MISSILE_MAX + ")";
            case "repair": {
                int cost = (int) Math.ceil((100 - game.hull) * 2.0);
                return game.hull >= 99.9 ? "HULL INTACT" : "REPAIR HULL   " + cost + " CR";
            }
            case "jump": {
                double d = game.galaxy.distLy(game.galaxy.current, chartSel);
                return String.format(Locale.US, "JUMP %.1f LY", d);
            }
            default:
                return b.label;
        }
    }

    // ---------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas c) {
        if (w <= 0 || h <= 0) return;
        if (game.state != lastState) {
            lastState = game.state;
            rebuild();
        }
        updateDynamicButtons();

        switch (game.state) {
            case Game.TITLE: drawTitle(c); break;
            case Game.FLIGHT: drawFlight(c); break;
            case Game.MENU: drawFlightScene(c, false); drawMenu(c); break;
            case Game.JUMP: drawJump(c); break;
            case Game.DEAD: drawDead(c); break;
            case Game.HUB: drawHub(c); break;
            case Game.MARKET: drawMarket(c); break;
            case Game.EQUIP: drawEquip(c); break;
            case Game.CHART: drawChart(c); break;
            case Game.STATUS: drawStatus(c); break;
            case Game.HELP: drawHelp(c); break;
            case Game.ABOUT: drawAbout(c); break;
        }

        if (scanBmp != null) c.drawRect(0, 0, w, h, scanPaint);
    }

    private void drawTitle(Canvas c) {
        c.drawRect(0, 0, w, h, bgPaint);
        titleBasis.identity();
        titleBasis.rotateYaw(Math.sin(game.titleAngle * 0.35) * 0.22);
        titleBasis.rotatePitch(0.10);
        drawStars(c, titleBasis, ORIGIN);

        Basis mb = new Basis();
        mb.rotateYaw(game.titleAngle * 0.55);
        mb.rotatePitch(0.18);
        drawModel(c, Models.COBRA, new Vec3(0, -dp(10), 1700), mb, ORIGIN, titleBasis,
                dp(380), 0x553BFF8F, dp(1.4f));

        tp.setLetterSpacing(0.22f);
        txt(c, "ELITE", cx, h * 0.34f, 76, GREEN, Paint.Align.CENTER, true);
        tp.setLetterSpacing(0.02f);
        txt(c, "T O U C H   C O M M A N D E R", cx, h * 0.34f + dp(26), 12, AMBER, Paint.Align.CENTER, true);
        txt(c, "A NATIVE TRIBUTE TO THE ZX SPECTRUM CLASSIC", cx, h * 0.34f + dp(44), 8, GREEN_DIM, Paint.Align.CENTER, false);

        String line = game.hasSave() ? "SAVED COMMANDER: " + game.commander : "NO SAVED COMMANDER";
        txt(c, line, cx, h * 0.62f, 10, 0xAA7BE9FF, Paint.Align.CENTER, false);
        txt(c, "DRAG LEFT SIDE TO STEER  ·  HOLD FIRE TO SHOOT", cx, h * 0.70f, 9, 0xAA46FF9A, Paint.Align.CENTER, false);

        drawButtons(c);
        drawMessage(c, h * 0.20f);
    }

    private void drawFlight(Canvas c) {
        drawFlightScene(c, true);
        drawHud(c);
        drawButtons(c);
        drawJoystick(c);
    }

    private void drawFlightScene(Canvas c, boolean withHud) {
        canvasRef = c;
        c.drawRect(0, 0, w, h, bgPaint);
        c.save();
        double sh = game.shake;
        if (sh > 0.1) {
            c.translate((float) (Math.sin(game.time * 91) * sh), (float) (Math.cos(game.time * 77) * sh));
        }
        drawStars(c, game.basis, game.pos);
        drawSun(c, game.basis, game.pos);
        drawPlanet(c, game.basis, game.pos);
        drawStation();
        drawEnemies();
        drawBolts();
        drawMissiles();
        drawParticles();
        c.restore();
        canvasRef = null;

        if (game.flash > 0) {
            fill.setColor(Color.argb((int) Math.min(120, game.flash * 170), 255, 40, 30));
            c.drawRect(0, 0, w, h, fill);
        }
        c.drawRect(0, 0, w, h, vigPaint);
    }

    // ---------------------------------------------------------------- space

    private void drawStars(Canvas c, Basis b, Vec3 cam) {
        for (Game.Star s : game.stars) {
            double rx = s.x - cam.x, ry = s.y - cam.y, rz = s.z - cam.z;
            double vz = rx * b.fwd.x + ry * b.fwd.y + rz * b.fwd.z;
            if (vz < 60) continue;
            double vx = rx * b.right.x + ry * b.right.y + rz * b.right.z;
            double vy = rx * b.up.x + ry * b.up.y + rz * b.up.z;
            float sx = cx + (float) (vx * focal / vz);
            float sy = cy - (float) (vy * focal / vz);
            if (sx < -4 || sx > w + 4 || sy < -4 || sy > h + 4) continue;
            int alpha = (int) (45 + 190 * s.b);
            starPaint.setColor((alpha << 24) | 0xC8E8D8);
            starPaint.setStrokeWidth(dp(1.1f + (float) s.b * 1.2f));
            c.drawPoint(sx, sy, starPaint);
        }
    }

    private void drawPlanet(Canvas c, Basis b, Vec3 cam) {
        Vec3 p = game.planetPos;
        double rx = p.x - cam.x, ry = p.y - cam.y, rz = p.z - cam.z;
        double vz = rx * b.fwd.x + ry * b.fwd.y + rz * b.fwd.z;
        if (vz < 100) return;
        double vx = rx * b.right.x + ry * b.right.y + rz * b.right.z;
        double vy = rx * b.up.x + ry * b.up.y + rz * b.up.z;
        float sx = cx + (float) (vx * focal / vz);
        float sy = cy - (float) (vy * focal / vz);
        double r = game.planetRadius * focal / vz;
        if (r < 1.5) return;
        float rr = (float) Math.min(r, w * 2.2);
        if (sx < -rr || sx > w + rr || sy < -rr || sy > h + rr) return;

        RadialGradient g = new RadialGradient(sx - rr * 0.35f, sy - rr * 0.35f, rr * 1.45f,
                new int[]{0xFF5FA889, 0xFF2B6254, 0xFF0B1D19},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(g);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(sx, sy, rr, paint);
        paint.setShader(null);

        c.save();
        Path clip = new Path();
        clip.addCircle(sx, sy, rr, Path.Direction.CW);
        c.clipPath(clip);
        shadowPaint.setColor(0xE0020503);
        c.drawCircle(sx + rr * 0.88f, sy - rr * 0.08f, rr * 1.08f, shadowPaint);
        c.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.3f));
        paint.setColor(0x5546FFB0);
        c.drawCircle(sx, sy, rr, paint);
    }

    private void drawSun(Canvas c, Basis b, Vec3 cam) {
        Vec3 p = game.sunPos;
        double rx = p.x - cam.x, ry = p.y - cam.y, rz = p.z - cam.z;
        double vz = rx * b.fwd.x + ry * b.fwd.y + rz * b.fwd.z;
        if (vz < 100) return;
        double vx = rx * b.right.x + ry * b.right.y + rz * b.right.z;
        double vy = rx * b.up.x + ry * b.up.y + rz * b.up.z;
        float sx = cx + (float) (vx * focal / vz);
        float sy = cy - (float) (vy * focal / vz);
        float rr = (float) Math.max(dp(3), 2200 * focal / vz);
        if (sx < -rr * 6 || sx > w + rr * 6 || sy < -rr * 6 || sy > h + rr * 6) return;

        RadialGradient g = new RadialGradient(sx, sy, rr * 7,
                new int[]{0x99FFF6D0, 0x33FFD27A, 0x00000000},
                new float[]{0f, 0.15f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(g);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(sx, sy, rr * 7, paint);
        paint.setShader(null);
        fill.setColor(0xFFFFF3CC);
        c.drawCircle(sx, sy, rr, fill);
    }

    private void drawStation() {
        if (dotView(game.stationPos) < 30) return;
        drawModel(canvasRef, Models.STATION, game.stationPos, game.stationBasis,
                game.pos, game.basis, Game.STATION_SCALE, 0xFF57D9A0, dp(1.8f));
    }

    private Canvas canvasRef;

    private void drawEnemies() {
        for (Game.Enemy e : game.enemies) {
            if (e.dead) continue;
            Models.Model m = modelFor(e.type);
            int col = e.type == 2 ? 0xFFFFB37A : (e.type == 1 ? 0xFFFF7A5C : 0xFFFF9A6A);
            drawModel(canvasRef, m, e.pos, e.b, game.pos, game.basis, e.scale, col, dp(1.7f));
        }
    }

    private Models.Model modelFor(int type) {
        switch (type) {
            case 1: return Models.VIPER;
            case 2: return Models.PYTHON;
            case 3: return Models.SIDEWINDER;
            default: return Models.KRAIT;
        }
    }

    private void drawBolts() {
        if (canvasRef == null) return;
        float[] p = new float[3];
        for (Game.Bolt b : game.bolts) {
            if (!project(b.pos, game.basis, game.pos, p)) continue;
            Vec3 tail = new Vec3(b.pos).addScaled(b.vel, -0.018);
            float[] q = new float[3];
            boolean hasTail = project(tail, game.basis, game.pos, q);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(b.friendly ? dp(2.4f) : dp(2.0f));
            paint.setColor(b.friendly ? 0xFFB4FF7A : 0xFFFF5A4A);
            if (hasTail) canvasRef.drawLine(q[0], q[1], p[0], p[1], paint);
            else canvasRef.drawPoint(p[0], p[1], paint);
        }
    }

    private void drawMissiles() {
        if (canvasRef == null) return;
        float[] p = new float[3], q = new float[3];
        for (Game.Mis m : game.flying) {
            if (m.dead) continue;
            if (!project(m.pos, game.basis, game.pos, p)) continue;
            Vec3 tail = new Vec3(m.pos).addScaled(m.vel, -0.03);
            boolean hasTail = project(tail, game.basis, game.pos, q);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2.2f));
            paint.setColor(AMBER);
            if (hasTail) canvasRef.drawLine(q[0], q[1], p[0], p[1], paint);
            fill.setColor(0xFFFFE6A8);
            canvasRef.drawCircle(p[0], p[1], dp(2), fill);
        }
    }

    private void drawParticles() {
        if (canvasRef == null) return;
        for (Game.Particle part : game.particles) {
            float[] p = new float[3];
            if (!project(part.pos, game.basis, game.pos, p)) continue;
            float a = (float) Math.max(0, Math.min(1, part.life / part.max));
            int col = (part.color & 0x00FFFFFF) | ((int) (a * 220) << 24);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.6f));
            paint.setColor(col);
            Vec3 tail = new Vec3(part.pos).addScaled(part.vel, -0.025);
            float[] q = new float[3];
            if (project(tail, game.basis, game.pos, q)) canvasRef.drawLine(q[0], q[1], p[0], p[1], paint);
            else canvasRef.drawPoint(p[0], p[1], paint);
        }
    }

    private double dotView(Vec3 world) {
        return (world.x - game.pos.x) * game.basis.fwd.x
                + (world.y - game.pos.y) * game.basis.fwd.y
                + (world.z - game.pos.z) * game.basis.fwd.z;
    }

    private boolean project(Vec3 world, Basis view, Vec3 cam, float[] out) {
        double rx = world.x - cam.x, ry = world.y - cam.y, rz = world.z - cam.z;
        double vz = rx * view.fwd.x + ry * view.fwd.y + rz * view.fwd.z;
        if (vz < 25) return false;
        double vx = rx * view.right.x + ry * view.right.y + rz * view.right.z;
        double vy = rx * view.up.x + ry * view.up.y + rz * view.up.z;
        out[0] = cx + (float) (vx * focal / vz);
        out[1] = cy - (float) (vy * focal / vz);
        out[2] = (float) vz;
        return true;
    }

    private void drawModel(Canvas c, Models.Model m, Vec3 center, Basis b, Vec3 cam, Basis view,
                           double scale, int color, float width) {
        double crx = center.x - cam.x, cry = center.y - cam.y, crz = center.z - cam.z;
        double cr = crx * view.right.x + cry * view.right.y + crz * view.right.z;
        double cu = crx * view.up.x + cry * view.up.y + crz * view.up.z;
        double cf = crx * view.fwd.x + cry * view.fwd.y + crz * view.fwd.z;

        double bxr = b.right.x * view.right.x + b.right.y * view.right.y + b.right.z * view.right.z;
        double byr = b.up.x * view.right.x + b.up.y * view.right.y + b.up.z * view.right.z;
        double bzr = b.fwd.x * view.right.x + b.fwd.y * view.right.y + b.fwd.z * view.right.z;
        double bxu = b.right.x * view.up.x + b.right.y * view.up.y + b.right.z * view.up.z;
        double byu = b.up.x * view.up.x + b.up.y * view.up.y + b.up.z * view.up.z;
        double bzu = b.fwd.x * view.up.x + b.fwd.y * view.up.y + b.fwd.z * view.up.z;
        double bxf = b.right.x * view.fwd.x + b.right.y * view.fwd.y + b.right.z * view.fwd.z;
        double byf = b.up.x * view.fwd.x + b.up.y * view.fwd.y + b.up.z * view.fwd.z;
        double bzf = b.fwd.x * view.fwd.x + b.fwd.y * view.fwd.y + b.fwd.z * view.fwd.z;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(color);

        for (int[] e : m.e) {
            double[] v1 = m.v[e[0]];
            double[] v2 = m.v[e[1]];
            double x1 = v1[0] * scale, y1 = v1[1] * scale, z1 = v1[2] * scale;
            double vx1 = cr + x1 * bxr + y1 * byr + z1 * bzr;
            double vy1 = cu + x1 * bxu + y1 * byu + z1 * bzu;
            double vz1 = cf + x1 * bxf + y1 * byf + z1 * bzf;
            double x2 = v2[0] * scale, y2 = v2[1] * scale, z2 = v2[2] * scale;
            double vx2 = cr + x2 * bxr + y2 * byr + z2 * bzr;
            double vy2 = cu + x2 * bxu + y2 * byu + z2 * bzu;
            double vz2 = cf + x2 * bxf + y2 * byf + z2 * bzf;
            if (vz1 < 25 || vz2 < 25) continue;
            float sx1 = cx + (float) (vx1 * focal / vz1);
            float sy1 = cy - (float) (vy1 * focal / vz1);
            float sx2 = cx + (float) (vx2 * focal / vz2);
            float sy2 = cy - (float) (vy2 * focal / vz2);
            c.drawLine(sx1, sy1, sx2, sy2, paint);
        }
    }

    // ---------------------------------------------------------------- HUD

    private void drawHud(Canvas c) {
        fill.setColor(0x77020805);
        c.drawRect(0, 0, w, dp(48), fill);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x333BFF8F);
        c.drawLine(0, dp(48), w, dp(48), paint);

        String sysName = game.galaxy.sys[game.galaxy.current].name;
        txt(c, sysName, dp(14), dp(19), 14, GREEN, Paint.Align.LEFT, true);
        txt(c, game.galaxy.govName(game.galaxy.current), dp(14), dp(33), 7.5f, GREEN_DIM, Paint.Align.LEFT, false);

        txt(c, "CR " + game.credits, w - dp(14), dp(19), 14, AMBER, Paint.Align.RIGHT, true);
        txt(c, String.format(Locale.US, "FUEL %.1f LY", game.fuel), w - dp(14), dp(34), 8, GREEN_DIM, Paint.Align.RIGHT, false);

        float bw = dp(88);
        float bx = cx - bw / 2f;
        bar(c, bx, dp(9), bw, dp(5), (float) game.shield / 100f, GREEN);
        bar(c, bx, dp(21), bw, dp(5), (float) game.hull / 100f, game.hull < 35 ? RED : AMBER);
        txt(c, "SHD", bx - dp(5), dp(14), 6.5f, GREEN_DIM, Paint.Align.RIGHT, false);
        txt(c, "HUL", bx - dp(5), dp(26), 6.5f, GREEN_DIM, Paint.Align.RIGHT, false);

        txt(c, String.format(Locale.US, "SPD %.2f", game.speed / Game.MAX_SPEED), dp(14), dp(61), 8.5f, GREEN, Paint.Align.LEFT, false);
        bar(c, dp(14), dp(65), dp(78), dp(4), (float) game.targetSpeed, 0xFF6BE8FF);
        txt(c, "LASER", dp(14), dp(79), 6.5f, GREEN_DIM, Paint.Align.LEFT, false);
        bar(c, dp(14), dp(82), dp(78), dp(4), (float) game.laserHeat, game.heatLock ? RED : AMBER);

        drawRadar(c);
        drawTarget(c);
        drawStationMarker(c);
        drawDockHint(c);
        drawMessage(c, h * 0.32f);
    }

    private void bar(Canvas c, float x, float y, float bw, float bh, float frac, int color) {
        frac = Math.max(0, Math.min(1, frac));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x55FFFFFF);
        c.drawRect(x, y, x + bw, y + bh, paint);
        fill.setColor(color);
        c.drawRect(x + dp(1), y + dp(1), x + dp(1) + (bw - dp(2)) * frac, y + bh - dp(1), fill);
    }

    private void drawRadar(Canvas c) {
        float rr = dp(40);
        float rx = cx;
        float ry = h - dp(64);
        paint.setStyle(Paint.Style.FILL);
        fill.setColor(0x66020805);
        c.drawCircle(rx, ry, rr, fill);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x773BFF8F);
        c.drawCircle(rx, ry, rr, paint);
        c.drawCircle(rx, ry, rr * 0.55f, paint);
        c.drawLine(rx - rr, ry, rx + rr, ry, paint);
        c.drawLine(rx, ry - rr, rx, ry + rr, paint);

        fill.setColor(RED);
        for (Game.Enemy e : game.enemies) {
            if (e.dead) continue;
            Vec3 d = Vec3.sub(e.pos, game.pos);
            float px = (float) (d.dot(game.basis.right));
            float pz = (float) (d.dot(game.basis.fwd));
            if (pz < -9000 || pz > 9000) continue;
            float px2 = px / 9000f * rr;
            float py2 = -pz / 9000f * rr;
            float len = (float) Math.hypot(px2, py2);
            if (len > rr - dp(3)) continue;
            c.drawCircle(rx + px2, ry + py2, dp(2.2f), fill);
        }
        Vec3 sd = Vec3.sub(game.stationPos, game.pos);
        float sx = (float) sd.dot(game.basis.right) / 9000f * rr;
        float sy = -(float) sd.dot(game.basis.fwd) / 9000f * rr;
        if (Math.hypot(sx, sy) < rr - dp(3)) {
            fill.setColor(GREEN);
            c.drawRect(rx + sx - dp(2.5f), ry + sy - dp(2.5f), rx + sx + dp(2.5f), ry + sy + dp(2.5f), fill);
        }
    }

    private void drawTarget(Canvas c) {
        Game.Enemy t = game.target;
        if (t == null || t.dead) return;
        float[] p = new float[3];
        boolean vis = project(t.pos, game.basis, game.pos, p)
                && p[0] > dp(20) && p[0] < w - dp(20) && p[1] > dp(54) && p[1] < h - dp(30);
        if (vis) {
            float r = Math.max(dp(13), (float) (t.scale * focal / p[2]));
            r = Math.min(r, dp(90));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.6f));
            paint.setColor(0xFFFFB05A);
            float k = r * 0.55f;
            c.drawLine(p[0] - r, p[1] - r, p[0] - r + k, p[1] - r, paint);
            c.drawLine(p[0] - r, p[1] - r, p[0] - r, p[1] - r + k, paint);
            c.drawLine(p[0] + r, p[1] - r, p[0] + r - k, p[1] - r, paint);
            c.drawLine(p[0] + r, p[1] - r, p[0] + r, p[1] - r + k, paint);
            c.drawLine(p[0] - r, p[1] + r, p[0] - r + k, p[1] + r, paint);
            c.drawLine(p[0] - r, p[1] + r, p[0] - r, p[1] + r - k, paint);
            c.drawLine(p[0] + r, p[1] + r, p[0] + r - k, p[1] + r, paint);
            c.drawLine(p[0] + r, p[1] + r, p[0] + r, p[1] + r - k, paint);

            float dist = (float) t.pos.dist(game.pos);
            txt(c, t.name + "  " + fmtDist(dist), p[0], p[1] - r - dp(6), 8, 0xFFFFC98A, Paint.Align.CENTER, true);
            float frac = (float) ((t.shield + t.hull) / Math.max(1, t.shieldMax + t.hullMax));
            bar(c, p[0] - r, p[1] + r + dp(4), r * 2, dp(3), frac, 0xFFFF8A5C);
        } else {
            Vec3 d = Vec3.sub(t.pos, game.pos).norm();
            String side = d.dot(game.basis.right) > 0 ? ">" : "<";
            String behind = d.dot(game.basis.fwd) < 0 ? " BEHIND" : "";
            txt(c, t.name + " " + fmtDist((float) t.pos.dist(game.pos)) + " " + side + behind,
                    cx, h - dp(196), 8, 0xCCFFB05A, Paint.Align.CENTER, false);
        }
    }

    private void drawStationMarker(Canvas c) {
        float[] p = new float[3];
        boolean vis = project(game.stationPos, game.basis, game.pos, p)
                && p[0] > dp(20) && p[0] < w - dp(20) && p[1] > dp(56) && p[1] < h - dp(40);
        float dist = (float) game.stationDist;
        if (vis) {
            float r = dp(5);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.2f));
            paint.setColor(0xAA57D9A0);
            Path path = new Path();
            path.moveTo(p[0], p[1] - r);
            path.lineTo(p[0] + r, p[1]);
            path.lineTo(p[0], p[1] + r);
            path.lineTo(p[0] - r, p[1]);
            path.close();
            c.drawPath(path, paint);
            txt(c, "STN " + fmtDist(dist), p[0], p[1] + dp(15), 7.5f, 0xAA57D9A0, Paint.Align.CENTER, false);
        } else if (dist < 12000) {
            Vec3 d = Vec3.sub(game.stationPos, game.pos).norm();
            String side = d.dot(game.basis.right) > 0 ? ">" : "<";
            txt(c, "STATION " + fmtDist(dist) + " " + side + (d.dot(game.basis.fwd) < 0 ? " BEHIND" : ""),
                    cx, h - dp(212), 8, 0xAA57D9A0, Paint.Align.CENTER, false);
        }
    }

    private void drawDockHint(Canvas c) {
        if (game.stationDist > 6000) return;
        String hint;
        int color;
        if (game.dockReady) {
            hint = "DOCKING ALIGNED — PRESS DOCK";
            color = GREEN;
        } else if (game.stationDist > Game.DOCK_RANGE) {
            hint = "STATION " + fmtDist((float) game.stationDist) + " — APPROACH TO DOCK";
            color = 0xAA7BE9FF;
        } else {
            hint = "ALIGN THE SHIP WITH THE STATION";
            color = 0xAA7BE9FF;
        }
        txt(c, hint, cx, h - dp(168), 8, color, Paint.Align.CENTER, false);
    }

    private void drawMessage(Canvas c, float y) {
        if (game.msgTimer <= 0 || game.msg == null) return;
        float alpha = (float) Math.min(1, game.msgTimer / 0.6);
        int a = (int) (alpha * 235);
        fill.setColor((a << 24) | 0x061009);
        tp.setTypeface(monoBold);
        tp.setTextSize(dp(10));
        tp.setLetterSpacing(0.02f);
        float tw = tp.measureText(game.msg);
        float pad = dp(14);
        c.drawRoundRect(cx - tw / 2 - pad, y - dp(13), cx + tw / 2 + pad, y + dp(6), dp(6), dp(6), fill);
        txt(c, game.msg, cx, y, 10, (a << 24) | 0xFFD98A, Paint.Align.CENTER, true);
    }

    private void drawJoystick(Canvas c) {
        float bx = joyId >= 0 ? joyBaseX : joyHomeX;
        float by = joyId >= 0 ? joyBaseY : joyHomeY;
        float kx = joyId >= 0 ? joyX : bx;
        float ky = joyId >= 0 ? joyY : by;
        float r = dp(58);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(joyId >= 0 ? 0x883BFF8F : 0x443BFF8F);
        c.drawCircle(bx, by, r, paint);
        c.drawCircle(bx, by, r * 0.35f, paint);
        fill.setColor(joyId >= 0 ? 0x553BFF8F : 0x223BFF8F);
        c.drawCircle(kx, ky, dp(24), fill);
        paint.setColor(0xAA3BFF8F);
        c.drawCircle(kx, ky, dp(24), paint);
    }

    // ---------------------------------------------------------------- buttons

    private void drawButtons(Canvas c) {
        for (Btn b : buttons) {
            drawButton(c, b);
        }
    }

    private void drawButton(Canvas c, Btn b) {
        String label = labelFor(b);
        boolean en = b.enabled;
        float rad = b.style == S_ROUND ? Math.min(b.w, b.h) / 2f : dp(7);
        int stroke;
        int bg;
        int textCol;
        if (!en) {
            stroke = 0x553A4A40;
            bg = 0x44101512;
            textCol = 0x665D7A69;
        } else if (b.style == S_PRIMARY) {
            stroke = GREEN;
            bg = 0x333BFF8F;
            textCol = 0xFFEAFFF2;
        } else if (b.style == S_DANGER) {
            stroke = RED;
            bg = 0x33FF5C5C;
            textCol = 0xFFFFDCDC;
        } else if (b.style == S_ROUND) {
            stroke = 0xFFFF7A5C;
            bg = 0x33FF5C3C;
            textCol = 0xFFFFE6D8;
        } else {
            stroke = 0xAA3BFF8F;
            bg = 0x66101813;
            textCol = 0xFFD8FFE6;
        }
        fill.setColor(bg);
        c.drawRoundRect(b.x, b.y, b.x + b.w, b.y + b.h, rad, rad, fill);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.3f));
        paint.setColor(stroke);
        c.drawRoundRect(b.x, b.y, b.x + b.w, b.y + b.h, rad, rad, paint);

        float size = b.style == S_ROUND ? 12 : 9.5f;
        if (b.id.equals("fire")) size = 14;
        if (b.id.equals("menu")) size = 8;
        txt(c, label, b.x + b.w / 2f, b.y + b.h / 2f + dp(size * 0.36f), size, textCol, Paint.Align.CENTER, true);
    }

    // ---------------------------------------------------------------- screens

    private void drawMenu(Canvas c) {
        fill.setColor(0xAA020503);
        c.drawRect(0, 0, w, h, fill);
        float pw = Math.min(w * 0.66f, dp(560));
        float px = (w - pw) / 2f, py = dp(24);
        float ph = h - dp(48);
        panel(c, px, py, pw, ph);
        txt(c, "PAUSED", px + dp(18), py + dp(30), 18, GREEN, Paint.Align.LEFT, true);
        txt(c, "COMMANDER " + game.commander + "   ·   " + game.rank(), px + dp(18), py + dp(46), 8, GREEN_DIM, Paint.Align.LEFT, false);
        txt(c, "CR " + game.credits + "   ·   " + game.galaxy.sys[game.galaxy.current].name, px + dp(18), py + dp(58), 8, AMBER, Paint.Align.LEFT, false);
        drawButtons(c);
        drawMessage(c, h - dp(30));
    }

    private void drawHub(Canvas c) {
        screenBackground(c);
        // huge station silhouette
        Basis sb = new Basis();
        sb.rotateYaw(game.time * 0.05);
        sb.rotatePitch(0.2);
        drawModel(c, Models.STATION, new Vec3(0, 0, 2400), sb, ORIGIN, ID, 1500, 0x223BFF8F, dp(1.2f));

        float px = dp(14), py = dp(52), pw = w * 0.50f, ph = h - dp(66);
        panel(c, px, py, pw, ph);
        txt(c, "DOCKED", px + dp(14), py + dp(28), 20, GREEN, Paint.Align.LEFT, true);
        String sn = game.galaxy.sys[game.galaxy.current].name + " STATION";
        txt(c, sn, px + dp(14), py + dp(44), 9, AMBER, Paint.Align.LEFT, false);
        c.drawLine(px + dp(14), py + dp(54), px + pw - dp(14), py + dp(54), linePaint(0x553BFF8F));
        drawButtons(c);

        float rx = px + pw + dp(10);
        float rw = w - rx - dp(14);
        panel(c, rx, py, rw, ph);
        float tx = rx + dp(14);
        float ty = py + dp(28);
        txt(c, "COMMANDER", tx, ty, 8, GREEN_DIM, Paint.Align.LEFT, false);
        txt(c, game.commander, tx, ty + dp(18), 14, WHITE, Paint.Align.LEFT, true);
        txt(c, "CREDITS   " + game.credits + " CR", tx, ty + dp(40), 9, AMBER, Paint.Align.LEFT, false);
        txt(c, "FUEL      " + String.format(Locale.US, "%.1f", game.fuel) + " LY", tx, ty + dp(54), 9, GREEN, Paint.Align.LEFT, false);
        txt(c, "CARGO     " + game.cargoUsed() + "/20 T", tx, ty + dp(68), 9, GREEN, Paint.Align.LEFT, false);
        txt(c, "HULL      " + (int) game.hull + "%", tx, ty + dp(82), 9, GREEN, Paint.Align.LEFT, false);
        txt(c, "RANK      " + game.rank(), tx, ty + dp(96), 9, GREEN, Paint.Align.LEFT, false);
        c.drawLine(tx, ty + dp(108), rx + rw - dp(14), ty + dp(108), linePaint(0x333BFF8F));
        txt(c, "SYSTEM", tx, ty + dp(126), 8, GREEN_DIM, Paint.Align.LEFT, false);
        txt(c, game.galaxy.sys[game.galaxy.current].name, tx, ty + dp(142), 12, WHITE, Paint.Align.LEFT, true);
        txt(c, game.galaxy.ecoName(game.galaxy.current), tx, ty + dp(158), 8, AMBER, Paint.Align.LEFT, false);
        txt(c, "TECH " + game.galaxy.sys[game.galaxy.current].tech + "   ·   " + game.galaxy.govName(game.galaxy.current), tx, ty + dp(172), 7.5f, GREEN_DIM, Paint.Align.LEFT, false);

        int eco = game.galaxy.sys[game.galaxy.current].eco;
        String ex = eco >= 4 ? "EXPORTS: FOOD, FURS, LIQUOR" : "EXPORTS: COMPUTERS, MACHINERY";
        txt(c, ex, tx, ty + dp(192), 7.5f, 0xAA7BE9FF, Paint.Align.LEFT, false);
        txt(c, "PRESS LAUNCH TO DEPART", tx, py + ph - dp(16), 7.5f, GREEN_DIM, Paint.Align.LEFT, false);
        drawMessage(c, h * 0.5f);
    }

    private void drawMarket(Canvas c) {
        screenBackground(c);
        float px = dp(14), py = dp(52), pw = w - dp(28), ph = h - dp(66);
        panel(c, px, py, pw, ph);
        txt(c, "MARKET", px + dp(14), py + dp(26), 18, GREEN, Paint.Align.LEFT, true);
        txt(c, game.galaxy.sys[game.galaxy.current].name, px + dp(14), py + dp(42), 8, GREEN_DIM, Paint.Align.LEFT, false);
        txt(c, "CR " + game.credits, px + pw - dp(14), py + dp(24), 11, AMBER, Paint.Align.RIGHT, true);
        txt(c, "CARGO " + game.cargoUsed() + "/20 T", px + pw - dp(14), py + dp(40), 8, GREEN_DIM, Paint.Align.RIGHT, false);

        for (int i = 0; i < 10; i++) {
            RectF r = marketRow(i);
            boolean sel = i == marketSel;
            fill.setColor(sel ? 0x443BFF8F : 0x22020805);
            c.drawRoundRect(r, dp(5), dp(5), fill);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(sel ? GREEN : 0x333BFF8F);
            c.drawRoundRect(r, dp(5), dp(5), paint);
            txt(c, Galaxy.COMMODITY[i], r.left + dp(8), r.centerY() + dp(4), 9.5f, sel ? WHITE : 0xCCD8FFE6, Paint.Align.LEFT, sel);
            String price = String.format(Locale.US, "%.1f", game.galaxy.buyPrice(i));
            txt(c, price, r.right - dp(54), r.centerY() + dp(4), 10, AMBER, Paint.Align.RIGHT, false);
            txt(c, "x" + game.cargo[i], r.right - dp(8), r.centerY() + dp(4), 9, GREEN_DIM, Paint.Align.RIGHT, false);
        }
        drawButtons(c);
        drawMessage(c, h*0.5f);
    }

    private RectF marketRow(int i) {
        float m = dp(14);
        float top = dp(100);
        float bottom = h - dp(68);
        float rh = (bottom - top) / 5f;
        float gap = dp(6);
        float cellW = (w - 2 * m - gap) / 2f;
        int col = i / 5;
        int row = i % 5;
        float x = m + col * (cellW + gap);
        float y = top + row * rh;
        return new RectF(x + dp(2), y + dp(2), x + cellW - dp(2), y + rh - dp(2));
    }

    private void drawEquip(Canvas c) {
        screenBackground(c);
        float m = dp(14);
        panel(c, m, dp(52), w - 2 * m, h - dp(66));
        txt(c, "EQUIPMENT", m + dp(14), dp(76), 18, GREEN, Paint.Align.LEFT, true);
        txt(c, "CR " + game.credits + "   ·   HULL " + (int) game.hull + "%   ·   FUEL " + String.format(Locale.US, "%.1f", game.fuel) + " LY", m + dp(14), dp(92), 8, AMBER, Paint.Align.LEFT, false);
        drawButtons(c);
        txt(c, "SHIELDS RECHARGE FREE WHILE DOCKED", m + dp(14), dp(280), 8, GREEN_DIM, Paint.Align.LEFT, false);
        drawMessage(c, h * 0.5f);
    }

    private void drawChart(Canvas c) {
        screenBackground(c);
        float m = dp(14);
        panel(c, m, dp(52), w - 2 * m, h - dp(66));
        txt(c, "GALACTIC CHART", m + dp(14), dp(74), 17, GREEN, Paint.Align.LEFT, true);
        txt(c, "FUEL " + String.format(Locale.US, "%.1f", game.fuel) + " LY", m + dp(14), dp(90), 8, AMBER, Paint.Align.LEFT, false);

        for (int i = 0; i < game.galaxy.sys.length; i++) {
            float[] p = chartPoint(i);
            boolean cur = i == game.galaxy.current;
            boolean sel = i == chartSel;
            double d = game.galaxy.distLy(game.galaxy.current, i);
            boolean reach = d <= game.fuel + 1e-6;
            int col = cur ? AMBER : (sel ? WHITE : (reach ? GREEN : 0xFF2F5C46));
            fill.setColor(col);
            if (cur) {
                float r = dp(3.2f);
                Path path = new Path();
                path.moveTo(p[0], p[1] - r);
                path.lineTo(p[0] + r, p[1]);
                path.lineTo(p[0], p[1] + r);
                path.lineTo(p[0] - r, p[1]);
                path.close();
                c.drawPath(path, fill);
            } else {
                c.drawCircle(p[0], p[1], sel ? dp(3.4f) : dp(2.2f), fill);
            }
            if (sel) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.2f));
                paint.setColor(0xAAFFFFFF);
                c.drawCircle(p[0], p[1], dp(8), paint);
            }
            txt(c, game.galaxy.sys[i].name, p[0] + dp(6), p[1] - dp(4), 6.8f, cur ? AMBER : (sel ? WHITE : 0x99A8D8BC), Paint.Align.LEFT, sel);
        }

        int s = chartSel;
        double d = game.galaxy.distLy(game.galaxy.current, s);
        float iy = h * 0.67f;
        txt(c, game.galaxy.sys[s].name, m + dp(16), iy, 15, WHITE, Paint.Align.LEFT, true);
        txt(c, game.galaxy.ecoName(s), m + dp(16), iy + dp(16), 8, AMBER, Paint.Align.LEFT, false);
        txt(c, "TECH " + game.galaxy.sys[s].tech + "  ·  " + game.galaxy.govName(s), m + dp(16), iy + dp(30), 7.5f, GREEN_DIM, Paint.Align.LEFT, false);
        String jump = s == game.galaxy.current ? "CURRENT SYSTEM"
                : String.format(Locale.US, "DISTANCE %.1f LY  ·  FUEL COST %.1f", d, d);
        txt(c, jump, m + dp(16), iy + dp(46), 8, d <= game.fuel ? 0xFF7BE9FF : RED, Paint.Align.LEFT, false);
        drawButtons(c);
        drawMessage(c, h * 0.5f);
    }

    private float[] chartPoint(int i) {
        Galaxy.Sys s = game.galaxy.sys[i];
        float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f;
        for (Galaxy.Sys q : game.galaxy.sys) {
            minX = Math.min(minX, q.cx);
            maxX = Math.max(maxX, q.cx);
            minY = Math.min(minY, q.cy);
            maxY = Math.max(maxY, q.cy);
        }
        RectF r = new RectF(dp(30), dp(100), w - dp(30), h * 0.62f);
        float sc = Math.min(r.width() / Math.max(1, maxX - minX), r.height() / Math.max(1, maxY - minY));
        float ox = r.centerX() - (minX + maxX) / 2f * sc;
        float oy = r.centerY() - (minY + maxY) / 2f * sc;
        return new float[]{ox + s.cx * sc, oy + s.cy * sc};
    }

    private void drawStatus(Canvas c) {
        screenBackground(c);
        float m = dp(14);
        panel(c, m, dp(52), w - 2 * m, h - dp(66));
        txt(c, "COMMANDER STATUS", m + dp(16), dp(78), 17, GREEN, Paint.Align.LEFT, true);
        float x1 = m + dp(20), x2 = w * 0.52f;
        float y = dp(108), step = dp(20);
        line(c, "NAME", game.commander, x1, y);
        line(c, "RANK", game.rank(), x1, y + step);
        line(c, "CREDITS", game.credits + " CR", x1, y + step * 2);
        line(c, "FUEL", String.format(Locale.US, "%.1f LY", game.fuel), x1, y + step * 3);
        line(c, "HULL", (int) game.hull + " %", x1, y + step * 4);
        line(c, "SHIELD", (int) game.shield + " %", x1, y + step * 5);
        line(c, "MISSILES", game.missiles + " / " + Game.MISSILE_MAX, x1, y + step * 6);
        line(c, "KILLS", "" + game.kills, x1, y + step * 7);

        txt(c, "CARGO HOLD " + game.cargoUsed() + "/20 T", x2, y, 9, AMBER, Paint.Align.LEFT, true);
        float cy2 = y + dp(20);
        for (int i = 0; i < 10; i++) {
            if (game.cargo[i] > 0) {
                txt(c, Galaxy.COMMODITY[i] + "   x" + game.cargo[i], x2, cy2, 8.5f, 0xFFD8FFE6, Paint.Align.LEFT, false);
                cy2 += dp(17);
            }
        }
        txt(c, "SYSTEM " + game.galaxy.sys[game.galaxy.current].name, x1, y + step * 8, 8, GREEN, Paint.Align.LEFT, true);
        txt(c, game.galaxy.ecoName(game.galaxy.current), x1, y + step * 9, 7, GREEN_DIM, Paint.Align.LEFT, false);
        drawButtons(c);
    }

    private void drawHelp(Canvas c) {
        screenBackground(c);
        float m = dp(14);
        panel(c, m, dp(52), w - 2 * m, h - dp(66));
        txt(c, "FLIGHT MANUAL", m + dp(16), dp(78), 17, GREEN, Paint.Align.LEFT, true);
        String[] lines = {
                "FLIGHT",
                "Drag the left side of the screen to steer.",
                "Hold FIRE for twin pulse lasers - mind the heat bar.",
                "MISSILE locks the ship nearest your crosshairs.",
                "THR +/- sets speed.  Dock when close and aligned.",
                "",
                "DOCKED",
                "MARKET: buy low, sell high.  Hold capacity 20 t.",
                "EQUIPMENT: refuel, buy missiles, repair the hull.",
                "CHART: touch a star, then JUMP.  Fuel cost = distance.",
                "LAUNCH returns to space.  Landing saves the game.",
                "",
                "TRADE TIP",
                "Agricultural worlds sell food and furs cheap;",
                "industrial worlds pay well for them.",
        };
        float y = dp(96);
        for (String s : lines) {
            if (s.isEmpty()) { y += dp(6); continue; }
            boolean head = s.equals("FLIGHT") || s.equals("DOCKED") || s.equals("TRADE TIP");
            txt(c, s, m + dp(18), y, head ? 9 : 8.2f, head ? AMBER : 0xFFD8FFE6, Paint.Align.LEFT, head);
            y += dp(head ? 18 : 15);
        }
        drawButtons(c);
    }

    private void drawAbout(Canvas c) {
        screenBackground(c);
        float m = dp(14);
        panel(c, m, dp(52), w - 2 * m, h - dp(66));
        txt(c, "ELITE TOUCH  v1.0", m + dp(16), dp(78), 17, GREEN, Paint.Align.LEFT, true);
        txt(c, "A NATIVE ANDROID TRIBUTE TO ELITE (FIREBIRD, 1985)", m + dp(16), dp(94), 7.5f, AMBER, Paint.Align.LEFT, false);
        String[] lines = {
                "Source disk: 'Elite (1999)(Firebird Software)[m Dimon][GS]'.",
                "It is a 1999 ZX Spectrum remake by Dimon that requires",
                "General Sound 512K hardware - it cannot boot on a",
                "stock Spectrum or an ordinary emulator.",
                "",
                "This game is an original touch-screen remake, not an",
                "emulator: new code, wireframe models and synthesized",
                "sound, inspired by the original's trading and combat.",
                "",
                "COMMANDER'S CREED",
                "Never fly without fuel.  Never dock at speed.",
                "Never trust a Python in an Anarchy system.",
        };
        float y = dp(116);
        for (String s : lines) {
            txt(c, s, m + dp(18), y, 8.2f, 0xFFCFE9DA, Paint.Align.LEFT, false);
            y += dp(15);
        }
        txt(c, "FLY SAFE, COMMANDER.", m + dp(18), y + dp(12), 9, GREEN, Paint.Align.LEFT, true);
        drawButtons(c);
    }

    private void drawDead(Canvas c) {
        c.drawRect(0, 0, w, h, bgPaint);
        drawStars(c, game.basis, game.pos);
        canvasRef = c;
        drawParticles();
        canvasRef = null;
        fill.setColor(0x66050000);
        c.drawRect(0, 0, w, h, fill);
        tp.setLetterSpacing(0.2f);
        txt(c, "GAME OVER", cx, h * 0.32f, 44, RED, Paint.Align.CENTER, true);
        tp.setLetterSpacing(0.02f);
        txt(c, "THE COBRA HAS BEEN DESTROYED", cx, h * 0.32f + dp(26), 9, 0xFFFFB0A0, Paint.Align.CENTER, false);
        txt(c, "COMMANDER " + game.commander + "  ·  RANK " + game.rank(), cx, h * 0.52f, 10, WHITE, Paint.Align.CENTER, false);
        txt(c, "KILLS " + game.kills + "  ·  CREDITS " + game.credits, cx, h * 0.52f + dp(18), 9, AMBER, Paint.Align.CENTER, false);
        drawButtons(c);
    }

    private void drawJump(Canvas c) {
        c.drawRect(0, 0, w, h, bgPaint);
        float progress = 1f - (float) (game.jumpT / 2.0);
        fill.setColor(0xFF061009);
        c.drawRect(0, 0, w, h, fill);
        starPaint.setStrokeWidth(dp(2));
        for (int i = 0; i < 90; i++) {
            double a = i * 2.399963;
            float r0 = dp(20) + (i % 13) * dp(3);
            float r1 = h * (0.15f + 0.85f * progress) * (0.4f + (i % 7) / 7f);
            int al = 40 + (int) (180 * progress);
            starPaint.setColor((al << 24) | 0x9FFFE0);
            c.drawLine(cx + (float) Math.cos(a) * r0, cy + (float) Math.sin(a) * r0,
                    cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, starPaint);
        }
        float glow = (float) Math.sin(progress * Math.PI);
        fill.setColor(Color.argb((int) (80 * glow), 90, 255, 190));
        c.drawRect(0, 0, w, h, fill);
        txt(c, "HYPERSPACE", cx, cy - dp(10), 26, GREEN, Paint.Align.CENTER, true);
        txt(c, "DESTINATION  " + game.jumpName, cx, cy + dp(16), 11, AMBER, Paint.Align.CENTER, true);
        txt(c, String.format(Locale.US, "WITCHSPACE STABLE  ·  %.1f LY", game.galaxy.distLy(game.galaxy.current, game.jumpTarget)),
                cx, cy + dp(34), 8, GREEN_DIM, Paint.Align.CENTER, false);
    }

    private void screenBackground(Canvas c) {
        c.drawRect(0, 0, w, h, bgPaint);
        drawStars(c, game.basis, game.pos);
        c.drawRect(0, 0, w, h, vigPaint);
    }

    private void panel(Canvas c, float x, float y, float pw, float ph) {
        fill.setColor(0xD906100A);
        c.drawRoundRect(x, y, x + pw, y + ph, dp(10), dp(10), fill);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(0x553BFF8F);
        c.drawRoundRect(x, y, x + pw, y + ph, dp(10), dp(10), paint);
    }

    private Paint linePaint(int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(color);
        return paint;
    }

    private void line(Canvas c, String label, String value, float x, float y) {
        txt(c, label, x, y, 7.5f, GREEN_DIM, Paint.Align.LEFT, false);
        txt(c, value, x + dp(86), y, 9, WHITE, Paint.Align.LEFT, true);
    }

    // ---------------------------------------------------------------- text helpers

    private void txt(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean bold) {
        tp.setTypeface(bold ? monoBold : mono);
        tp.setTextSize(dp(size));
        tp.setColor(color);
        tp.setTextAlign(align);
        c.drawText(s, x, y, tp);
    }

    private String fmtDist(float d) {
        if (d >= 1000) return String.format(Locale.US, "%.1fk", d / 1000f);
        return ((int) d) + "m";
    }
}
