package dev.egateza.termul.app.ui.streak;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Satu api yang hidup: menggambar {@link FlameLook} plus efek sementara (percikan, asap, pecahan es, guncangan, api
 * padam sampai hilang). Hanya dipakai di EDT.
 */
final class FlameActor {

    /** Respons klik pada api, untuk efek. */
    enum Effect { POKE, TAP, REVIVE, CRACK, SHATTER }

    private enum Kind { SPARK, SMOKE, SHARD, RING }

    private static final class Particle {
        Kind kind;
        double x, y, vx, vy, gravity, r, life, max, rot, spin, grow, width;
        Color color;
    }

    private static final double DIE_SECONDS = 1.6;

    private final List<Particle> particles = new ArrayList<>();
    private double shake;
    private double sparkDebt;
    private double smokeDebt;
    private double dyingT = -1;     // >= 0 selama/ setelah animasi padam
    private FlamePalette dyingFrom;
    private int dyingTongues;
    private boolean diedPuff;
    // posisi terakhir digambar, untuk efek klik
    private double lastX, lastBy, lastSize;

    boolean busy() {
        return !particles.isEmpty() || shake > 0 || dyingT >= 0 && dyingT < DIE_SECONDS;
    }

    /** Api padam perlahan (warna jadi abu-abu, mengecil, berasap) lalu hilang sampai {@link #relight()}. */
    void extinguish(FlamePalette from, int tongues) {
        dyingT = 0;
        dyingFrom = from;
        dyingTongues = tongues;
        diedPuff = false;
    }

    void relight() {
        dyingT = -1;
    }

    /**
     * @param t  detik (untuk goyangan api)
     * @param dt selang sejak frame sebelumnya (untuk partikel)
     */
    void paint(Graphics2D g, double cx, double by, double size, double t, double dt, FlameLook look) {
        shake = Math.max(0, shake - dt * 4);
        double x = cx + Math.sin(t * 70) * shake * size * .06;
        lastX = cx;
        lastBy = by;
        lastSize = size;
        boolean big = size >= 40;
        var random = ThreadLocalRandom.current();
        if (dyingT >= 0) {
            paintDying(g, x, by, size, t, dt);
        } else {
            switch (look.mode()) {
                case FROZEN -> {
                    FlamePainter.flame(g, x, by - size * .08, size * .78, t, FlamePalette.ICE, 0, 0);
                    FlamePainter.ice(g, x, by, size, look.cracks(), look.clicks(), t);
                }
                case GRAY -> {
                    FlamePainter.flame(g, x, by, size, t * .35, FlamePalette.GRAY, .3, 0);
                    smokeDebt += dt * (big ? 2.5 : .9);
                    while (smokeDebt > 1) {
                        smokeDebt--;
                        add(Kind.SMOKE, x + (random.nextDouble() - .5) * size * .1, by - size * .95,
                                (random.nextDouble() - .5) * size * .12, -size * .38, 0, Math.max(1.5, size * .06),
                                new Color(0x9A9EA4), 1.4 + random.nextDouble() * .6);
                    }
                }
                case ACTIVE -> paintActive(g, x, by, size, t, dt, look.tier(), big);
            }
        }
        paintParticles(g, dt);
    }

    private void paintActive(Graphics2D g, double x, double by, double size, double t, double dt, StreakTier tier,
                             boolean big) {
        var pal = tier.palette(t);
        if (tier == StreakTier.BARA) {
            FlamePainter.coal(g, x, by, size);
            FlamePainter.flame(g, x, by - size * .08, size * (.7 + Math.sin(t * 2.4) * .06), t, pal, .6, 0);
            return;
        }
        FlamePainter.flame(g, x, by, size, t, pal, 1, tier.tongues);
        if (tier.sparks > 0 && big) {
            var random = ThreadLocalRandom.current();
            sparkDebt += dt * tier.sparks * 5;
            while (sparkDebt > 1) {
                sparkDebt--;
                add(Kind.SPARK, x + (random.nextDouble() - .5) * size * .3, by - size * (.45 + random.nextDouble() * .3),
                        (random.nextDouble() - .5) * size * .25, -size * (.5 + random.nextDouble() * .6), 0,
                        Math.max(1, size * (.012 + random.nextDouble() * .018)),
                        random.nextDouble() < .6 ? pal.core() : pal.mid(), .5 + random.nextDouble() * .6);
            }
        }
        if (tier.stars && big) {
            for (int i = 0; i < 3; i++) {
                double s = Math.max(0, Math.sin(t * 2.2 + i * 2.1)) * size * .075;
                double a = i * 2.1 + .6;
                FlamePainter.star(g, x + Math.cos(a) * size * .52, by - size * .55 + Math.sin(a) * size * .4, s,
                        pal.core());
            }
        }
    }

    private void paintDying(Graphics2D g, double x, double by, double size, double t, double dt) {
        dyingT += dt;
        double p = Math.min(1, dyingT / DIE_SECONDS);
        if (p < 1) {
            var pal = dyingFrom.mix(FlamePalette.GRAY, Math.min(1, p * 1.8));
            if (p >= .35) {
                pal = new FlamePalette(pal.outer(), pal.mid(), pal.core(), null);
            }
            double s = size * Math.pow(1 - p, 1.4) * (1 + Math.sin(t * 28) * .12 * p);
            FlamePainter.flame(g, x, by, s, t, pal, 1 + p * 1.5, p < .3 ? dyingTongues : 0);
            var random = ThreadLocalRandom.current();
            smokeDebt += dt * (size >= 40 ? 9 : 3) * (.4 + p);
            while (smokeDebt > 1) {
                smokeDebt--;
                add(Kind.SMOKE, x + (random.nextDouble() - .5) * size * .2, by - Math.max(s, size * .15) * .9,
                        (random.nextDouble() - .5) * size * .2, -size * (.3 + random.nextDouble() * .3), 0,
                        Math.max(1.5, size * .07), new Color(0x8F9399), 1.2 + random.nextDouble() * .8);
            }
        } else if (!diedPuff) {
            diedPuff = true;
            puffAt(x, by - size * .1, size, 10);
        }
    }

    /** Efek klik di posisi api terakhir digambar. */
    void react(Effect effect, FlamePalette pal) {
        if (lastSize <= 0) {
            return;
        }
        switch (effect) {
            case TAP -> {
                shake = .6;
                puffAt(lastX, lastBy - lastSize * .6, lastSize, 5);
            }
            case REVIVE -> {
                shake = .8;
                burst(pal, true);
            }
            case CRACK -> {
                shake = 1;
                shards(4, .05);
            }
            case SHATTER -> {
                shards(18, .1);
                burst(pal, true);
            }
            case POKE -> {
                shake = .3;
                burst(pal, false);
            }
        }
    }

    /** Ledakan percikan (dan cincin) dari tengah api. */
    void burst(FlamePalette pal, boolean ring) {
        if (lastSize <= 0) {
            return;
        }
        double cy = lastBy - lastSize * .45;
        var random = ThreadLocalRandom.current();
        if (ring) {
            var p = add(Kind.RING, lastX, cy, 0, 0, 0, lastSize * .2, pal.mid(), .6);
            p.grow = lastSize * .75;
            p.width = Math.max(2, lastSize * .06);
        }
        int n = lastSize < 40 ? 8 : 28;
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2, speed = lastSize * (.8 + random.nextDouble() * 1.4);
            add(Kind.SPARK, lastX, cy, Math.cos(a) * speed, Math.sin(a) * speed - lastSize * .4, lastSize * 1.2,
                    Math.max(1, lastSize * (.015 + random.nextDouble() * .025)),
                    random.nextBoolean() ? pal.core() : pal.mid(), .5 + random.nextDouble() * .5);
        }
    }

    private void puffAt(double x, double y, double size, int n) {
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            add(Kind.SMOKE, x + (random.nextDouble() - .5) * size * .3, y, (random.nextDouble() - .5) * size * .5,
                    -size * (.2 + random.nextDouble() * .4), 0, Math.max(2, size * .07), new Color(0xB4B8BD),
                    .7 + random.nextDouble() * .4);
        }
    }

    private void shards(int n, double scale) {
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2, speed = lastSize * (.6 + random.nextDouble() * 1.2);
            var p = add(Kind.SHARD, lastX + (random.nextDouble() - .5) * lastSize * .7,
                    lastBy - lastSize * (.2 + random.nextDouble() * .8), Math.cos(a) * speed,
                    -lastSize * (.4 + random.nextDouble() * 1.1), lastSize * 4,
                    Math.max(2, lastSize * scale * (.6 + random.nextDouble() * .8)), null, .7 + random.nextDouble() * .5);
            p.rot = random.nextDouble() * 6;
            p.spin = (random.nextDouble() - .5) * 14;
        }
    }

    private Particle add(Kind kind, double x, double y, double vx, double vy, double gravity, double r, Color color,
                         double life) {
        var p = new Particle();
        p.kind = kind;
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.gravity = gravity;
        p.r = r;
        p.color = color;
        p.life = life;
        p.max = life;
        particles.add(p);
        return p;
    }

    private void paintParticles(Graphics2D g, double dt) {
        if (particles.isEmpty()) {
            return;
        }
        Composite saved = g.getComposite();
        for (var it = particles.iterator(); it.hasNext(); ) {
            var p = it.next();
            p.life -= dt;
            if (p.life <= 0) {
                it.remove();
                continue;
            }
            p.vy += p.gravity * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            double a = p.life / p.max;
            switch (p.kind) {
                case SPARK -> {
                    g.setComposite(alpha(a));
                    g.setColor(p.color);
                    double r = p.r * (.5 + a * .5);
                    g.fill(new Ellipse2D.Double(p.x - r, p.y - r, r * 2, r * 2));
                }
                case SMOKE -> {
                    g.setComposite(alpha(a * .35));
                    g.setColor(p.color);
                    double r = p.r * (1.8 - a);
                    g.fill(new Ellipse2D.Double(p.x - r, p.y - r, r * 2, r * 2));
                }
                case SHARD -> {
                    p.rot += p.spin * dt;
                    g.setComposite(alpha(Math.min(1, a * 1.6)));
                    var shard = new Path2D.Double();
                    shard.moveTo(0, -p.r);
                    shard.lineTo(p.r * .7, p.r * .6);
                    shard.lineTo(-p.r * .6, p.r * .4);
                    shard.closePath();
                    var at = AffineTransform.getTranslateInstance(p.x, p.y);
                    at.rotate(p.rot);
                    var shape = at.createTransformedShape(shard);
                    g.setColor(new Color(206, 237, 255, 235));
                    g.fill(shape);
                    g.setColor(new Color(255, 255, 255, 242));
                    g.setStroke(new BasicStroke(1f));
                    g.draw(shape);
                }
                case RING -> {
                    g.setComposite(alpha(a));
                    g.setColor(p.color);
                    g.setStroke(new BasicStroke((float) Math.max(.5, p.width * a)));
                    double r = p.r + (1 - a) * p.grow;
                    g.draw(new Ellipse2D.Double(p.x - r, p.y - r, r * 2, r * 2));
                }
            }
        }
        g.setComposite(saved);
    }

    private static AlphaComposite alpha(double a) {
        return AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.clamp(a, 0, 1));
    }

    int particleCount() {
        return particles.size();
    }
}
