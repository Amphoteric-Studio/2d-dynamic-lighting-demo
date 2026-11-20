package com.dynami.lighting.demo;

import box2dLight.ConeLight;
import box2dLight.Light;
import box2dLight.PointLight;
import box2dLight.RayHandler;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * 2D dynamic lighting showcase using Box2D Lights (RayHandler)
 * with circular pointer light, clear cone beams, half-intensity lights,
 * and strict shadow occlusion on static blocks. Works on Desktop and Android.
 */
public class DynamicLightingDemo extends ApplicationAdapter {
    private static final float WORLD_WIDTH = 24f;
    private static final float WORLD_HEIGHT = 14f;

    private static final short CATEGORY_OBSTACLE = 0x0001;
    private static final short MASK_ALL = (short) 0xFFFF;

    private static final Color BG_COLOR = new Color(0.02f, 0.025f, 0.05f, 1f);
    private static final Color OBSTACLE_COLOR = new Color(0.16f, 0.20f, 0.30f, 1f);
    private static final Color OBSTACLE_EDGE_COLOR = new Color(0.55f, 0.68f, 0.92f, 0.90f);

    private enum LightingPreset {
        NIGHT_EXPLORATION,
        NEON_REACTOR,
        AMBER_FORGE
    }

    private static class StaticBlock {
        final float x;
        final float y;
        final float halfW;
        final float halfH;

        StaticBlock(float x, float y, float halfW, float halfH) {
            this.x = x;
            this.y = y;
            this.halfW = halfW;
            this.halfH = halfH;
        }

        boolean containsWithPadding(float px, float py, float pad) {
            return Math.abs(px - x) <= (halfW + pad) && Math.abs(py - y) <= (halfH + pad);
        }
    }

    private static class TouchButton {
        final float x;
        final float y;
        final float width;
        final float height;
        final String id;
        boolean pressed;

        TouchButton(float x, float y, float width, float height, String id) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.id = id;
        }

        boolean contains(float wx, float wy) {
            return wx >= x && wx <= x + width && wy >= y && wy <= y + height;
        }
    }

    private World world;
    private RayHandler rayHandler;
    private OrthographicCamera camera;
    private Viewport viewport;
    private ShapeRenderer shapeRenderer;

    private float animTime = 0f;
    private LightingPreset currentPreset = LightingPreset.NIGHT_EXPLORATION;
    private boolean pointerLightActive = true;

    // Circular light following pointer/touch + animated & static lights
    private PointLight pointerLight;
    private PointLight pointerHotspot;
    private ConeLight leftScanner;
    private ConeLight rightScanner;
    private PointLight orbiterA;
    private PointLight orbiterB;
    private PointLight orbiterC;

    private final Array<PointLight> accentLights = new Array<>();
    private final Array<StaticBlock> allColliders = new Array<>();
    private final Array<StaticBlock> visibleBlocks = new Array<>();
    private final Array<TouchButton> touchButtons = new Array<>();

    private final Vector2 pointerWorld = new Vector2(WORLD_WIDTH * 0.5f, WORLD_HEIGHT * 0.55f);

    private TouchButton modeBtn;
    private TouchButton beamBtn;

    @Override
    public void create() {
        camera = new OrthographicCamera();
        viewport = new StretchViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        viewport.apply(true);

        shapeRenderer = new ShapeRenderer();
        world = new World(new Vector2(0f, 0f), false);

        setupRayHandler();
        createShadowBlocks();
        createLights();
        applyPreset(currentPreset);
        createTouchControls();
    }

    private void setupRayHandler() {
        RayHandler.setGammaCorrection(false);
        RayHandler.useDiffuseLight(false);

        rayHandler = new RayHandler(world);
        rayHandler.setBlurNum(1);
        rayHandler.setShadows(true);
        rayHandler.setAmbientLight(0.06f, 0.07f, 0.12f, 0.86f);
    }

    private void createShadowBlocks() {
        addStaticBlock(WORLD_WIDTH * 0.5f, 0.35f, WORLD_WIDTH * 0.5f, 0.35f, false);
        addStaticBlock(WORLD_WIDTH * 0.5f, WORLD_HEIGHT - 0.35f, WORLD_WIDTH * 0.5f, 0.35f, false);
        addStaticBlock(0.35f, WORLD_HEIGHT * 0.5f, 0.35f, WORLD_HEIGHT * 0.5f, false);
        addStaticBlock(WORLD_WIDTH - 0.35f, WORLD_HEIGHT * 0.5f, 0.35f, WORLD_HEIGHT * 0.5f, false);

        addStaticBlock(6.8f, 7.0f, 0.75f, 2.3f, true);
        addStaticBlock(17.2f, 7.0f, 0.75f, 2.3f, true);
        addStaticBlock(12.0f, 7.0f, 1.45f, 1.45f, true);
        addStaticBlock(12.0f, 10.6f, 2.5f, 0.50f, true);
        addStaticBlock(12.0f, 3.4f, 2.5f, 0.50f, true);
    }

    private void addStaticBlock(float x, float y, float halfW, float halfH, boolean visible) {
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.StaticBody;
        def.position.set(x, y);

        Body body = world.createBody(def);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(halfW, halfH);

        FixtureDef fDef = new FixtureDef();
        fDef.shape = shape;
        fDef.isSensor = false;
        fDef.filter.categoryBits = CATEGORY_OBSTACLE;
        fDef.filter.maskBits = MASK_ALL;

        body.createFixture(fDef);
        shape.dispose();

        StaticBlock block = new StaticBlock(x, y, halfW, halfH);
        allColliders.add(block);
        if (visible) {
            visibleBlocks.add(block);
        }
    }

    private void configureSolidOcclusion(Light light, boolean soft, float softness) {
        light.setXray(false);
        light.setSoft(soft);
        light.setSoftnessLength(softness);
    }

    private void createLights() {
        pointerLight = new PointLight(rayHandler, 200, Color.WHITE, 12.5f, WORLD_WIDTH * 0.5f, WORLD_HEIGHT * 0.55f);
        configureSolidOcclusion(pointerLight, true, 0.65f);

        pointerHotspot = new PointLight(rayHandler, 160, Color.WHITE, 6.0f, WORLD_WIDTH * 0.5f, WORLD_HEIGHT * 0.55f);
        configureSolidOcclusion(pointerHotspot, false, 0f);

        leftScanner = new ConeLight(rayHandler, 220, Color.WHITE, 18.5f, 2.0f, 7.0f, 0f, 34f);
        configureSolidOcclusion(leftScanner, true, 0.65f);

        rightScanner = new ConeLight(rayHandler, 220, Color.WHITE, 18.5f, WORLD_WIDTH - 2.0f, 7.0f, 180f, 34f);
        configureSolidOcclusion(rightScanner, true, 0.65f);

        orbiterA = new PointLight(rayHandler, 160, Color.WHITE, 13.0f, 8f, 7f);
        configureSolidOcclusion(orbiterA, true, 0.60f);

        orbiterB = new PointLight(rayHandler, 160, Color.WHITE, 13.0f, 16f, 7f);
        configureSolidOcclusion(orbiterB, true, 0.60f);

        orbiterC = new PointLight(rayHandler, 160, Color.WHITE, 12.0f, 12f, 10f);
        configureSolidOcclusion(orbiterC, true, 0.55f);

        float[][] accentCoords = {
            { 2.2f, 11.8f, 12.5f },
            { 21.8f, 11.8f, 12.5f },
            { 2.2f, 2.2f, 12.5f },
            { 21.8f, 2.2f, 12.5f }
        };

        accentLights.clear();
        for (float[] cfg : accentCoords) {
            PointLight light = new PointLight(rayHandler, 160, Color.WHITE, cfg[2], cfg[0], cfg[1]);
            configureSolidOcclusion(light, true, 0.55f);
            accentLights.add(light);
        }
    }

    private void applyPreset(LightingPreset preset) {
        currentPreset = preset;

        switch (preset) {
            case NIGHT_EXPLORATION:
                rayHandler.setAmbientLight(0.06f, 0.07f, 0.12f, 0.86f);
                pointerLight.setColor(1.0f, 0.94f, 0.68f, 0.42f);
                pointerHotspot.setColor(1.0f, 0.98f, 0.85f, 0.38f);
                pointerLight.setDistance(12.5f);
                pointerHotspot.setDistance(6.0f);
                leftScanner.setColor(0.25f, 0.86f, 1.0f, 0.42f);
                leftScanner.setDistance(18.5f);
                rightScanner.setColor(1.0f, 0.66f, 0.20f, 0.42f);
                rightScanner.setDistance(18.5f);
                orbiterA.setColor(0.28f, 0.92f, 1.0f, 0.41f);
                orbiterA.setDistance(13.0f);
                orbiterB.setColor(1.0f, 0.56f, 0.16f, 0.41f);
                orbiterB.setDistance(13.0f);
                orbiterC.setColor(0.78f, 0.45f, 1.0f, 0.39f);
                orbiterC.setDistance(12.0f);
                setAccentColors(
                    new Color(0.30f, 0.82f, 1.0f, 0.38f),
                    new Color(1.0f, 0.70f, 0.22f, 0.38f)
                );
                break;

            case NEON_REACTOR:
                rayHandler.setAmbientLight(0.05f, 0.04f, 0.10f, 0.88f);
                pointerLight.setColor(0.25f, 1.0f, 0.88f, 0.42f);
                pointerHotspot.setColor(0.82f, 1.0f, 0.95f, 0.38f);
                pointerLight.setDistance(13.0f);
                pointerHotspot.setDistance(6.2f);
                leftScanner.setColor(1.0f, 0.24f, 0.80f, 0.42f);
                leftScanner.setDistance(19.0f);
                rightScanner.setColor(0.18f, 0.95f, 1.0f, 0.42f);
                rightScanner.setDistance(19.0f);
                orbiterA.setColor(1.0f, 0.25f, 0.80f, 0.41f);
                orbiterA.setDistance(13.2f);
                orbiterB.setColor(0.20f, 1.0f, 0.86f, 0.41f);
                orbiterB.setDistance(13.2f);
                orbiterC.setColor(0.76f, 0.32f, 1.0f, 0.39f);
                orbiterC.setDistance(12.2f);
                setAccentColors(
                    new Color(0.98f, 0.24f, 0.78f, 0.38f),
                    new Color(0.18f, 0.95f, 1.0f, 0.38f)
                );
                break;

            case AMBER_FORGE:
            default:
                rayHandler.setAmbientLight(0.07f, 0.04f, 0.03f, 0.85f);
                pointerLight.setColor(1.0f, 0.86f, 0.42f, 0.42f);
                pointerHotspot.setColor(1.0f, 0.94f, 0.70f, 0.38f);
                pointerLight.setDistance(12.5f);
                pointerHotspot.setDistance(6.0f);
                leftScanner.setColor(1.0f, 0.36f, 0.10f, 0.42f);
                leftScanner.setDistance(18.5f);
                rightScanner.setColor(1.0f, 0.72f, 0.20f, 0.42f);
                rightScanner.setDistance(18.5f);
                orbiterA.setColor(1.0f, 0.40f, 0.12f, 0.41f);
                orbiterA.setDistance(13.0f);
                orbiterB.setColor(1.0f, 0.76f, 0.20f, 0.41f);
                orbiterB.setDistance(13.0f);
                orbiterC.setColor(0.98f, 0.28f, 0.10f, 0.39f);
                orbiterC.setDistance(12.0f);
                setAccentColors(
                    new Color(1.0f, 0.38f, 0.10f, 0.38f),
                    new Color(1.0f, 0.72f, 0.20f, 0.38f)
                );
                break;
        }
    }

    private void setAccentColors(Color evenColor, Color oddColor) {
        for (int i = 0; i < accentLights.size; i++) {
            accentLights.get(i).setColor(i % 2 == 0 ? evenColor : oddColor);
        }
    }

    private void cyclePreset() {
        LightingPreset[] values = LightingPreset.values();
        int next = (currentPreset.ordinal() + 1) % values.length;
        applyPreset(values[next]);
    }

    private void togglePointerLight() {
        pointerLightActive = !pointerLightActive;
        pointerLight.setActive(pointerLightActive);
        pointerHotspot.setActive(pointerLightActive);
    }

    private void createTouchControls() {
        float btnW = 2.6f;
        float btnH = 1.25f;
        float margin = 0.7f;
        float y = 1.0f;

        modeBtn = new TouchButton(margin, y, btnW, btnH, "MODE");
        beamBtn = new TouchButton(WORLD_WIDTH - margin - btnW, y, btnW, btnH, "BEAM");

        touchButtons.clear();
        touchButtons.add(modeBtn);
        touchButtons.add(beamBtn);
    }

    private void keepPointOutsideBlocks(Vector2 pt, float margin) {
        pt.x = MathUtils.clamp(pt.x, 0.6f, WORLD_WIDTH - 0.6f);
        pt.y = MathUtils.clamp(pt.y, 0.6f, WORLD_HEIGHT - 0.6f);

        for (int i = 0; i < allColliders.size; i++) {
            StaticBlock b = allColliders.get(i);
            if (b.containsWithPadding(pt.x, pt.y, margin)) {
                float dx = pt.x - b.x;
                float dy = pt.y - b.y;
                float overlapX = (b.halfW + margin) - Math.abs(dx);
                float overlapY = (b.halfH + margin) - Math.abs(dy);

                if (overlapX < overlapY) {
                    pt.x = b.x + (dx >= 0f ? (b.halfW + margin) : -(b.halfW + margin));
                } else {
                    pt.y = b.y + (dy >= 0f ? (b.halfH + margin) : -(b.halfH + margin));
                }
            }
        }
    }

    private void updateLights(float dt) {
        animTime += dt;

        for (int i = 0; i < touchButtons.size; i++) {
            touchButtons.get(i).pressed = false;
        }

        if (Gdx.input.isTouched() || Gdx.input.getX() != 0 || Gdx.input.getY() != 0) {
            viewport.unproject(pointerWorld.set(Gdx.input.getX(), Gdx.input.getY()));
            keepPointOutsideBlocks(pointerWorld, 0.25f);
        }

        for (int pointer = 0; pointer < 20; pointer++) {
            if (!Gdx.input.isTouched(pointer)) continue;
            Vector2 tp = viewport.unproject(new Vector2(Gdx.input.getX(pointer), Gdx.input.getY(pointer)));
            for (int i = 0; i < touchButtons.size; i++) {
                TouchButton btn = touchButtons.get(i);
                if (btn.contains(tp.x, tp.y)) {
                    btn.pressed = true;
                }
            }
        }

        boolean modeKey = Gdx.input.isKeyJustPressed(Input.Keys.M) || Gdx.input.isKeyJustPressed(Input.Keys.L);
        boolean beamKey = Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.B);
        boolean modeTouch = false;
        boolean beamTouch = false;

        if (Gdx.input.justTouched()) {
            Vector2 jt = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
            if (modeBtn.contains(jt.x, jt.y)) {
                modeTouch = true;
            }
            if (beamBtn.contains(jt.x, jt.y)) {
                beamTouch = true;
            }
        }

        if (modeKey || modeTouch) {
            cyclePreset();
        }
        if (beamKey || beamTouch) {
            togglePointerLight();
        }

        pointerLight.setPosition(pointerWorld.x, pointerWorld.y);
        pointerHotspot.setPosition(pointerWorld.x, pointerWorld.y);

        float cx = WORLD_WIDTH * 0.5f;
        float cy = WORLD_HEIGHT * 0.5f;

        leftScanner.setDirection(MathUtils.sin(animTime * 1.15f) * 42f);
        rightScanner.setDirection(180f + MathUtils.cos(animTime * 1.15f) * 42f);

        float radiusX = 7.2f;
        float radiusY = 3.8f;

        Vector2 posA = new Vector2(
            cx + MathUtils.cos(animTime * 0.9f) * radiusX,
            cy + MathUtils.sin(animTime * 1.5f) * radiusY
        );
        keepPointOutsideBlocks(posA, 0.25f);
        orbiterA.setPosition(posA.x, posA.y);

        Vector2 posB = new Vector2(
            cx + MathUtils.cos(animTime * 0.9f + MathUtils.PI) * radiusX,
            cy + MathUtils.sin(animTime * 1.5f + MathUtils.PI) * radiusY
        );
        keepPointOutsideBlocks(posB, 0.25f);
        orbiterB.setPosition(posB.x, posB.y);

        Vector2 posC = new Vector2(
            cx + MathUtils.sin(animTime * 1.2f) * 4.8f,
            cy + MathUtils.cos(animTime * 0.75f) * 4.2f
        );
        keepPointOutsideBlocks(posC, 0.25f);
        orbiterC.setPosition(posC.x, posC.y);

        for (int i = 0; i < accentLights.size; i++) {
            PointLight al = accentLights.get(i);
            al.setDistance(12.5f + MathUtils.sin(animTime * 2.4f + i * 1.4f) * 0.9f);
        }
    }

    private void drawBlocks() {
        camera.update();
        shapeRenderer.setProjectionMatrix(camera.combined);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(OBSTACLE_COLOR);
        for (int i = 0; i < visibleBlocks.size; i++) {
            StaticBlock b = visibleBlocks.get(i);
            shapeRenderer.rect(b.x - b.halfW, b.y - b.halfH, b.halfW * 2f, b.halfH * 2f);
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(OBSTACLE_EDGE_COLOR);
        for (int i = 0; i < visibleBlocks.size; i++) {
            StaticBlock b = visibleBlocks.get(i);
            shapeRenderer.rect(b.x - b.halfW, b.y - b.halfH, b.halfW * 2f, b.halfH * 2f);
        }
        shapeRenderer.end();
    }

    private void drawLuminousCores() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float pulse = 0.88f + 0.12f * MathUtils.sin(animTime * 4f);

        drawCoreGlow(orbiterA.getPosition().x, orbiterA.getPosition().y, orbiterA.getColor(), 0.20f * pulse);
        drawCoreGlow(orbiterB.getPosition().x, orbiterB.getPosition().y, orbiterB.getColor(), 0.20f * pulse);
        drawCoreGlow(orbiterC.getPosition().x, orbiterC.getPosition().y, orbiterC.getColor(), 0.17f * pulse);

        drawCoreGlow(leftScanner.getX(), leftScanner.getY(), leftScanner.getColor(), 0.19f);
        drawCoreGlow(rightScanner.getX(), rightScanner.getY(), rightScanner.getColor(), 0.19f);

        if (pointerLightActive) {
            drawCoreGlow(pointerWorld.x, pointerWorld.y, pointerLight.getColor(), 0.17f);
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawCoreGlow(float x, float y, Color c, float radius) {
        shapeRenderer.setColor(c.r, c.g, c.b, 0.18f);
        shapeRenderer.circle(x, y, radius * 1.5f, 20);
        shapeRenderer.setColor(1f, 1f, 1f, 0.42f);
        shapeRenderer.circle(x, y, radius * 0.45f, 16);
    }

    private void drawControls() {
        shapeRenderer.setProjectionMatrix(camera.combined);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < touchButtons.size; i++) {
            TouchButton btn = touchButtons.get(i);
            if ("BEAM".equals(btn.id)) {
                float r = pointerLightActive ? 1.0f : 0.45f;
                float g = pointerLightActive ? 0.85f : 0.45f;
                float b = pointerLightActive ? 0.35f : 0.50f;
                shapeRenderer.setColor(r, g, b, btn.pressed ? 0.55f : 0.25f);
            } else {
                shapeRenderer.setColor(1f, 1f, 1f, btn.pressed ? 0.45f : 0.20f);
            }
            shapeRenderer.rect(btn.x, btn.y, btn.width, btn.height);
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1f, 1f, 1f, 0.85f);
        drawButtonIcon(modeBtn, "MODE");
        drawButtonIcon(beamBtn, "BEAM");
        shapeRenderer.end();
    }

    private void drawButtonIcon(TouchButton btn, String id) {
        float cx = btn.x + btn.width * 0.5f;
        float cy = btn.y + btn.height * 0.5f;
        float r = Math.min(btn.width, btn.height) * 0.24f;

        if ("MODE".equals(id)) {
            shapeRenderer.circle(cx, cy, r, 20);
        } else {
            shapeRenderer.line(cx - r, cy, cx + r, cy);
            shapeRenderer.line(cx, cy - r, cx, cy + r);
        }
    }

    @Override
    public void render() {
        ScreenUtils.clear(BG_COLOR);
        updateLights(Gdx.graphics.getDeltaTime());

        drawBlocks();

        rayHandler.setCombinedMatrix(camera);
        rayHandler.updateAndRender();

        drawLuminousCores();
        drawControls();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        if (rayHandler != null) {
            rayHandler.dispose();
        }
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
        }
        if (world != null) {
            world.dispose();
        }
    }
}
