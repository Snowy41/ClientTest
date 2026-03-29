package com.hades.client.module.impl.render;

import com.hades.client.api.HadesAPI;
import com.hades.client.api.interfaces.IEntity;
import com.hades.client.event.EventHandler;
import com.hades.client.event.events.PacketEvent;
import com.hades.client.event.events.Render3DEvent;
import com.hades.client.event.events.TickEvent;
import com.hades.client.module.Module;
import com.hades.client.module.setting.ModeSetting;
import com.hades.client.module.setting.NumberSetting;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * HitParticles — spawns smooth, modern 3D particles on entity attacks.
 *
 * Modes:
 *   Star     — rounded cartoon star via Catmull-Rom spline (billboarded)
 *   Spark    — elongated streaks oriented along velocity (true 3D)
 *   Crescent — half-moon arcs that spin gracefully (billboarded)
 */
public class HitParticles extends Module {

    private final ModeSetting mode = new ModeSetting("Mode", "Star", "Star", "Spark", "Crescent");
    private final NumberSetting amount = new NumberSetting("Amount", 5.0, 1.0, 20.0, 1.0);
    private final NumberSetting scaleSetting = new NumberSetting("Scale", 0.35, 0.05, 1.5, 0.05);
    private final NumberSetting lifetimeSetting = new NumberSetting("Lifetime", 25.0, 10.0, 60.0, 1.0);

    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    // ── Pre-computed geometry caches ──
    private float[][] starVertices;
    private float[][] crescentInner;
    private float[][] crescentOuter;

    private static final int STAR_POINTS = 5;
    private static final int CURVE_SEGMENTS = 8;
    private static final int CRESCENT_SEGMENTS = 24;

    public HitParticles() {
        super("HitParticles", "Spawns smooth 3D particles on hit.", Category.RENDER, 0);
        register(mode);
        register(amount);
        register(scaleSetting);
        register(lifetimeSetting);
        precomputeShapes();
    }

    @Override
    public void onEnable() { particles.clear(); }

    @Override
    public void onDisable() { particles.clear(); }

    // ══════════════════════════════════════════════════════════
    // GEOMETRY PRE-COMPUTATION
    // ══════════════════════════════════════════════════════════

    private void precomputeShapes() {
        precomputeStar();
        precomputeCrescent();
    }

    private void precomputeStar() {
        double outerR = 0.48;
        double innerR = 0.20;
        int totalCtrl = STAR_POINTS * 2;
        float[][] controlPts = new float[totalCtrl][2];

        for (int i = 0; i < totalCtrl; i++) {
            double angle = i * Math.PI / STAR_POINTS - Math.PI / 2.0;
            double r = (i % 2 == 0) ? outerR : innerR;
            controlPts[i][0] = (float) (Math.cos(angle) * r);
            controlPts[i][1] = (float) (Math.sin(angle) * r);
        }

        int totalVerts = totalCtrl * CURVE_SEGMENTS;
        starVertices = new float[totalVerts][2];
        int idx = 0;

        for (int i = 0; i < totalCtrl; i++) {
            float[] p0 = controlPts[(i - 1 + totalCtrl) % totalCtrl];
            float[] p1 = controlPts[i];
            float[] p2 = controlPts[(i + 1) % totalCtrl];
            float[] p3 = controlPts[(i + 2) % totalCtrl];

            for (int s = 0; s < CURVE_SEGMENTS; s++) {
                float t = (float) s / CURVE_SEGMENTS;
                starVertices[idx] = catmullRom(p0, p1, p2, p3, t);
                idx++;
            }
        }
    }

    private void precomputeCrescent() {
        // A crescent is a thick arc spanning ~200 degrees.
        // Built as two concentric arcs (inner/outer) rendered via TRIANGLE_STRIP.
        double arcStart = Math.toRadians(-100);
        double arcEnd = Math.toRadians(100);
        double outerR = 0.42;
        double innerR = 0.26;

        crescentOuter = new float[CRESCENT_SEGMENTS + 1][2];
        crescentInner = new float[CRESCENT_SEGMENTS + 1][2];

        for (int i = 0; i <= CRESCENT_SEGMENTS; i++) {
            double t = (double) i / CRESCENT_SEGMENTS;
            double angle = arcStart + (arcEnd - arcStart) * t;

            // Taper the thickness at the ends for that clean crescent shape
            double taperFactor = Math.sin(t * Math.PI); // 0 at ends, 1 at center
            double thickness = 0.08 + 0.08 * taperFactor;
            double curInnerR = outerR - thickness;

            crescentOuter[i][0] = (float) (Math.cos(angle) * outerR);
            crescentOuter[i][1] = (float) (Math.sin(angle) * outerR);
            crescentInner[i][0] = (float) (Math.cos(angle) * curInnerR);
            crescentInner[i][1] = (float) (Math.sin(angle) * curInnerR);
        }
    }

    private float[] catmullRom(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
        float tt = t * t;
        float ttt = tt * t;
        float q0 = -0.5f * ttt + tt - 0.5f * t;
        float q1 = 1.5f * ttt - 2.5f * tt + 1.0f;
        float q2 = -1.5f * ttt + 2.0f * tt + 0.5f * t;
        float q3 = 0.5f * ttt - 0.5f * tt;
        return new float[]{
                q0 * p0[0] + q1 * p1[0] + q2 * p2[0] + q3 * p3[0],
                q0 * p0[1] + q1 * p1[1] + q2 * p2[1] + q3 * p3[1]
        };
    }

    // ══════════════════════════════════════════════════════════
    // PACKET INTERCEPTION
    // ══════════════════════════════════════════════════════════

    @EventHandler
    public void onSend(PacketEvent.Send event) {
        if (!isEnabled()) return;
        Object packet = event.getPacket();

        if (HadesAPI.network.isC02Packet(packet)) {
            if ("ATTACK".equals(HadesAPI.network.getC02Action(packet))) {
                int entityId = HadesAPI.network.getPacketEntityId(packet);
                if (entityId != -1) {
                    IEntity target = null;
                    for (IEntity e : HadesAPI.world.getLoadedEntities()) {
                        if (e.getEntityId() == entityId) {
                            target = e;
                            break;
                        }
                    }
                    if (target != null) {
                        spawnParticles(target);
                    }
                }
            }
        }
    }

    private void spawnParticles(IEntity target) {
        double cx = target.getX();
        double cy = target.getY() + target.getHeight() * 0.6;
        double cz = target.getZ();

        int amt = amount.getValue().intValue();
        int maxLife = lifetimeSetting.getValue().intValue();
        String currentMode = mode.getValue();

        for (int i = 0; i < amt; i++) {
            double vx, vy, vz;

            if ("Spark".equals(currentMode)) {
                // Sparks fly fast and directional — biased outward
                double yaw = random.nextDouble() * Math.PI * 2;
                double speed = 0.15 + random.nextDouble() * 0.25;
                vx = Math.cos(yaw) * speed;
                vy = random.nextFloat() * 0.15 + 0.05;
                vz = Math.sin(yaw) * speed;
            } else {
                // Star / Crescent: softer, floaty
                vx = random.nextGaussian() * 0.08;
                vy = random.nextFloat() * 0.12 + 0.02;
                vz = random.nextGaussian() * 0.08;
            }

            double px = cx + random.nextGaussian() * 0.3;
            double py = cy + random.nextGaussian() * 0.4;
            double pz = cz + random.nextGaussian() * 0.3;

            particles.add(new Particle(px, py, pz, vx, vy, vz, maxLife, currentMode));
        }
    }

    // ══════════════════════════════════════════════════════════
    // PHYSICS TICK
    // ══════════════════════════════════════════════════════════

    @EventHandler
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;

        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update();
            if (p.age >= p.maxLife) {
                it.remove();
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // RENDER (every frame, interpolated with partialTicks)
    // ══════════════════════════════════════════════════════════

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || particles.isEmpty()) return;

        float pt = event.getPartialTicks();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);

        double rpX = HadesAPI.renderer.getRenderPosX();
        double rpY = HadesAPI.renderer.getRenderPosY();
        double rpZ = HadesAPI.renderer.getRenderPosZ();

        float camYaw = HadesAPI.player.getYaw();
        float camPitch = HadesAPI.player.getPitch();

        for (Particle p : particles) {
            double ix = p.prevX + (p.x - p.prevX) * pt - rpX;
            double iy = p.prevY + (p.y - p.prevY) * pt - rpY;
            double iz = p.prevZ + (p.z - p.prevZ) * pt - rpZ;

            float lifeProgress = (float) p.age / p.maxLife;

            // ── Scale easing ──
            float scaleEase;
            if (lifeProgress < 0.15f) {
                float t = lifeProgress / 0.15f;
                scaleEase = 1f - (1f - t) * (1f - t) * (1f - t);
            } else {
                float t = (lifeProgress - 0.15f) / 0.85f;
                scaleEase = 1f - t * t;
            }

            // ── Alpha fade ──
            float alpha;
            if (lifeProgress < 0.6f) {
                alpha = 0.85f;
            } else {
                alpha = 0.85f * (1f - (lifeProgress - 0.6f) / 0.4f);
            }

            if ("Spark".equals(p.mode)) {
                renderSpark(p, pt, ix, iy, iz, scaleEase, alpha);
            } else {
                // Billboarded modes (Star, Crescent)
                float scale = scaleSetting.getValue().floatValue() * scaleEase;

                GL11.glPushMatrix();
                GL11.glTranslated(ix, iy, iz);
                GL11.glRotatef(-camYaw, 0f, 1f, 0f);
                GL11.glRotatef(camPitch, 1f, 0f, 0f);

                float interpSpin = (float) (p.prevSpin + (p.spin - p.prevSpin) * pt);
                GL11.glRotatef(interpSpin, 0f, 0f, 1f);
                GL11.glScalef(scale, scale, scale);

                if ("Star".equals(p.mode)) {
                    drawRoundedStar(p.gray, alpha);
                } else if ("Crescent".equals(p.mode)) {
                    drawCrescent(p.gray, alpha);
                }

                GL11.glPopMatrix();
            }
        }

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    // ══════════════════════════════════════════════════════════
    // SHAPE RENDERERS
    // ══════════════════════════════════════════════════════════

    // ── Star: Catmull-Rom splined rounded star ──

    private void drawRoundedStar(float gray, float alpha) {
        if (starVertices == null) return;

        GL11.glColor4f(gray, gray, gray, alpha);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(0f, 0f);
        for (float[] v : starVertices) {
            GL11.glVertex2f(v[0], v[1]);
        }
        GL11.glVertex2f(starVertices[0][0], starVertices[0][1]);
        GL11.glEnd();

        GL11.glColor4f(gray * 0.55f, gray * 0.55f, gray * 0.55f, alpha * 0.9f);
        GL11.glLineWidth(1.5f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (float[] v : starVertices) {
            GL11.glVertex2f(v[0], v[1]);
        }
        GL11.glEnd();
    }

    // ── Crescent: Tapered half-moon arc ──

    private void drawCrescent(float gray, float alpha) {
        if (crescentOuter == null || crescentInner == null) return;

        // Filled crescent body via triangle strip
        GL11.glColor4f(gray, gray, gray, alpha);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= CRESCENT_SEGMENTS; i++) {
            GL11.glVertex2f(crescentOuter[i][0], crescentOuter[i][1]);
            GL11.glVertex2f(crescentInner[i][0], crescentInner[i][1]);
        }
        GL11.glEnd();

        // Smooth outline
        GL11.glColor4f(gray * 0.5f, gray * 0.5f, gray * 0.5f, alpha * 0.85f);
        GL11.glLineWidth(1.2f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        // Outer edge forward
        for (int i = 0; i <= CRESCENT_SEGMENTS; i++) {
            GL11.glVertex2f(crescentOuter[i][0], crescentOuter[i][1]);
        }
        // Inner edge backward (closes the loop)
        for (int i = CRESCENT_SEGMENTS; i >= 0; i--) {
            GL11.glVertex2f(crescentInner[i][0], crescentInner[i][1]);
        }
        GL11.glEnd();
    }

    // ── Spark: Velocity-stretched 3D line ──

    private void renderSpark(Particle p, float pt, double ix, double iy, double iz, float scaleEase, float alpha) {
        // Interpolate current velocity for the stretch direction
        double ivx = p.prevVx + (p.vx - p.prevVx) * pt;
        double ivy = p.prevVy + (p.vy - p.prevVy) * pt;
        double ivz = p.prevVz + (p.vz - p.prevVz) * pt;

        double speed = Math.sqrt(ivx * ivx + ivy * ivy + ivz * ivz);
        if (speed < 0.001) return; // Too slow to render

        // Trail length scales with velocity — faster = longer streak
        double trailLength = speed * 4.0 * scaleSetting.getValue().doubleValue() * scaleEase;
        double tailX = ix - (ivx / speed) * trailLength;
        double tailY = iy - (ivy / speed) * trailLength;
        double tailZ = iz - (ivz / speed) * trailLength;

        // Thicker line for larger scale setting
        float lineWidth = 1.5f + scaleSetting.getValue().floatValue() * 2.0f;
        GL11.glLineWidth(lineWidth * scaleEase);

        // Draw the spark: bright head → faded tail
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(p.gray + 0.15f, p.gray + 0.15f, p.gray + 0.15f, alpha);
        GL11.glVertex3d(ix, iy, iz); // Head (bright)
        GL11.glColor4f(p.gray, p.gray, p.gray, alpha * 0.15f);
        GL11.glVertex3d(tailX, tailY, tailZ); // Tail (faded)
        GL11.glEnd();
    }

    // ══════════════════════════════════════════════════════════
    // PARTICLE DATA
    // ══════════════════════════════════════════════════════════

    private class Particle {
        double x, y, z;
        double prevX, prevY, prevZ;
        double vx, vy, vz;
        double prevVx, prevVy, prevVz; // For spark velocity interpolation
        double spin, prevSpin, spinSpeed;
        int age, maxLife;
        float gray;
        String mode;

        Particle(double x, double y, double z, double vx, double vy, double vz, int maxLife, String mode) {
            this.x = x; this.y = y; this.z = z;
            this.prevX = x; this.prevY = y; this.prevZ = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.prevVx = vx; this.prevVy = vy; this.prevVz = vz;
            this.mode = mode;

            this.spin = random.nextInt(360);
            this.prevSpin = spin;
            this.spinSpeed = (random.nextFloat() - 0.5) * 12.0;

            this.age = 0;
            this.maxLife = maxLife + random.nextInt(10);
            this.gray = 0.55f + random.nextFloat() * 0.20f;
        }

        void update() {
            prevX = x; prevY = y; prevZ = z;
            prevVx = vx; prevVy = vy; prevVz = vz;
            prevSpin = spin;

            x += vx; y += vy; z += vz;
            spin += spinSpeed;

            if ("Spark".equals(mode)) {
                // Sparks: stronger gravity, less air friction for fast streaks
                vy -= 0.025;
                vx *= 0.95;
                vy *= 0.95;
                vz *= 0.95;
            } else {
                // Star / Crescent: gentle, floaty
                vy -= 0.015;
                vx *= 0.92;
                vy *= 0.97;
                vz *= 0.92;
            }
            spinSpeed *= 0.96;
            age++;
        }
    }
}
