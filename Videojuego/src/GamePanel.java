import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class GamePanel extends JPanel implements KeyListener {

    private static final int WIDTH  = 800;
    private static final int HEIGHT = 600;

    // Formation grid: 4 rows x 6 cols = 24 enemies
    private static final int COLS      = 6;
    private static final int ROWS      = 4;
    private static final int CELL_W    = 80;
    private static final int CELL_H    = 55;
    // Center the formation horizontally
    private static final int FORM_LEFT = WIDTH / 2 - (COLS - 1) * CELL_W / 2; // = 200
    private static final int FORM_TOP  = 70;
    // Max horizontal drift before bouncing
    private static final int MAX_OFF   = 150;

    private static final int STAR_COUNT = 70;

    // Game objects (volatile so EDT and game-loop thread see latest reference)
    private volatile MiNave                    miNave;
    private volatile List<NaveEnemiga>         enemies;
    private volatile List<Proyectil>           playerBullets;
    private volatile List<ProyectilEnemigo>    enemyBullets;

    // Game state
    private volatile int     score      = 0;
    private volatile int     lives      = 3;
    private volatile int     wave       = 1;
    private volatile boolean gameOver   = false;
    private volatile boolean wavePaused = false;
    private int wavePauseTimer = 0;

    // Player flash on hit
    private volatile boolean playerFlashing = false;
    private int flashTimer = 0;

    // Formation movement
    private double formOffX   = 0;
    private double formOffY   = 0;
    private double formDirX   = 1.0;
    private double formSpeed;
    private double descentStep;

    // Timing counters (ticks at 60 fps)
    private int diveTick;
    private int shootTick;
    private int diveInterval;
    private int shootInterval;

    // Scrolling stars
    private final int[] starX   = new int[STAR_COUNT];
    private final int[] starY   = new int[STAR_COUNT];
    private final int[] starSpd = new int[STAR_COUNT];

    private final Random rand = new Random();

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        initStars();
        newGame();
        startGameLoop();
    }

    // ── Initialisation ──────────────────────────────────────────────────────

    private void initStars() {
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i]   = rand.nextInt(WIDTH);
            starY[i]   = rand.nextInt(HEIGHT);
            starSpd[i] = 1 + rand.nextInt(3);
        }
    }

    private void startGameLoop() {
        Thread loop = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                repaint();
                try { Thread.sleep(16); }
                catch (InterruptedException e) { break; }
            }
        });
        loop.setDaemon(true);
        loop.start();
    }

    private synchronized void newGame() {
        score      = 0;
        lives      = 3;
        wave       = 1;
        gameOver   = false;
        wavePaused = false;
        startWave();
    }

    private synchronized void startWave() {
        // Stop old threads
        if (enemies != null) {
            synchronized (enemies) {
                for (NaveEnemiga e : enemies) { e.kill(); e.interrupt(); }
            }
        }
        if (playerBullets != null) {
            synchronized (playerBullets) {
                for (Proyectil p : playerBullets) p.deactivate();
            }
        }
        if (enemyBullets != null) {
            synchronized (enemyBullets) {
                for (ProyectilEnemigo p : enemyBullets) p.deactivate();
            }
        }
        if (miNave != null) miNave.interrupt();

        // Fresh lists
        playerBullets = Collections.synchronizedList(new ArrayList<>());
        enemyBullets  = Collections.synchronizedList(new ArrayList<>());
        enemies       = Collections.synchronizedList(new ArrayList<>());

        // Formation state
        formOffX     = 0;
        formOffY     = 0;
        formDirX     = 1.0;
        formSpeed    = 0.6 + wave * 0.22;    // faster each wave
        descentStep  = 14 + wave * 1.5;      // drops more each wave

        // Timing (shorter = harder)
        diveInterval  = Math.max(50,  190 - wave * 18);
        shootInterval = Math.max(30,  130 - wave * 12);
        diveTick  = 0;
        shootTick = 0;

        // Player ship
        miNave = new MiNave(this);
        miNave.start();

        // Enemy formation
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int sx = FORM_LEFT + c * CELL_W;
                int sy = FORM_TOP  + r * CELL_H;
                NaveEnemiga e = new NaveEnemiga(sx, sy, r);
                enemies.add(e);
                e.start();
            }
        }

        playerFlashing = false;
        flashTimer     = 0;
    }

    // ── Game-loop update ─────────────────────────────────────────────────────

    private void update() {
        if (gameOver) return;

        // Wave-clear pause
        if (wavePaused) {
            if (--wavePauseTimer <= 0) {
                wavePaused = false;
                wave++;
                startWave();
            }
            return;
        }

        // Scroll stars
        for (int i = 0; i < STAR_COUNT; i++) {
            if ((starY[i] += starSpd[i]) > HEIGHT) {
                starY[i] = 0;
                starX[i] = rand.nextInt(WIDTH);
            }
        }

        // Flash cooldown
        if (playerFlashing && --flashTimer <= 0) playerFlashing = false;

        // Count alive enemies
        int aliveCount = 0;
        synchronized (enemies) {
            for (NaveEnemiga e : enemies) if (e.estaVivo()) aliveCount++;
        }
        if (aliveCount == 0) {
            wavePaused     = true;
            wavePauseTimer = 160;
            return;
        }

        // Oscillate and descend formation
        formOffX += formSpeed * formDirX;
        if (formOffX > MAX_OFF) {
            formOffX  = MAX_OFF;
            formDirX  = -1;
            formOffY += descentStep;
        } else if (formOffX < -MAX_OFF) {
            formOffX  = -MAX_OFF;
            formDirX  = 1;
            formOffY += descentStep;
        }

        // Push formation positions into each enemy
        synchronized (enemies) {
            for (NaveEnemiga e : enemies) {
                if (!e.estaVivo()) continue;
                if (!e.isDiving()) {
                    int fx = e.getSlotX() + (int) formOffX;
                    int fy = e.getSlotY() + (int) formOffY;
                    e.updateFormPos(fx, fy);
                }
                // Formation reached player zone — instant kill
                if (e.getPosY() > HEIGHT - 70) {
                    e.kill();
                    hitPlayer();
                }
            }
        }

        // Trigger dive attack
        if (++diveTick >= diveInterval) {
            diveTick = 0;
            triggerDive();
        }

        // Trigger enemy shot
        if (++shootTick >= shootInterval) {
            shootTick = 0;
            enemyShoot();
        }
    }

    private void triggerDive() {
        List<NaveEnemiga> candidates = new ArrayList<>();
        synchronized (enemies) {
            for (NaveEnemiga e : enemies)
                if (e.estaVivo() && !e.isDiving()) candidates.add(e);
        }
        if (candidates.isEmpty()) return;
        int n = Math.min(1 + wave / 3, 3);
        for (int i = 0; i < n && !candidates.isEmpty(); i++) {
            NaveEnemiga diver = candidates.remove(rand.nextInt(candidates.size()));
            double speed = 2.6 + wave * 0.32;
            diver.startDive(miNave.getPosX(), speed);
        }
    }

    private void enemyShoot() {
        List<NaveEnemiga> alive = new ArrayList<>();
        synchronized (enemies) {
            for (NaveEnemiga e : enemies) if (e.estaVivo()) alive.add(e);
        }
        if (alive.isEmpty()) return;
        NaveEnemiga shooter = alive.get(rand.nextInt(alive.size()));
        ProyectilEnemigo p = new ProyectilEnemigo(
                shooter.getPosX(), shooter.getPosY() + 20, this);
        enemyBullets.add(p);
        new Thread(p).start();
    }

    // ── Public API for projectile threads ────────────────────────────────────

    public void addProyectil(Proyectil p)         { playerBullets.add(p); }
    public void removeProyectil(Proyectil p)       { playerBullets.remove(p); }
    public void removeEnemyBullet(ProyectilEnemigo p) { enemyBullets.remove(p); }

    /** Player bullet hits an enemy. */
    public void checkColision(Proyectil p) {
        if (!p.isActive()) return;
        synchronized (enemies) {
            for (NaveEnemiga e : enemies) {
                if (!e.estaVivo()) continue;
                int ex = e.getPosX(), ey = e.getPosY();
                int px = p.getPosX(), py = p.getPosY();
                if (px >= ex - 22 && px <= ex + 22 && py >= ey - 18 && py <= ey + 18) {
                    p.deactivate();
                    e.kill();
                    int pts = (e.getRow() == 0) ? 300 : (e.getRow() == 1) ? 200 : 100;
                    score += pts;
                    break;
                }
            }
        }
    }

    /** Enemy bullet hits the player. */
    public void checkEnemyBulletCollision(ProyectilEnemigo p) {
        if (gameOver || playerFlashing || !p.isActive()) return;
        int px = p.getPosX(), py = p.getPosY();
        MiNave mn = miNave;
        if (mn == null) return;
        int nx = mn.getPosX(), ny = mn.getPosY();
        if (px >= nx - 20 && px <= nx + 20 && py >= ny - 22 && py <= ny + 14) {
            p.deactivate();
            hitPlayer();
        }
    }

    private synchronized void hitPlayer() {
        if (gameOver || playerFlashing) return;
        playerFlashing = true;
        flashTimer     = 110;
        if (--lives <= 0) {
            lives    = 0;
            gameOver = true;
        }
    }

    // ── Painting ─────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        drawStars(g);

        if (gameOver) { drawGameOverScreen(g); return; }

        if (wavePaused) drawWaveCleared(g);

        // Enemies
        List<NaveEnemiga> snap = enemies;
        if (snap != null) {
            synchronized (snap) {
                for (NaveEnemiga e : snap) if (e.estaVivo()) drawEnemy(g, e);
            }
        }

        // Player (blinks when hit)
        if (!playerFlashing || (flashTimer / 6) % 2 == 0) drawMiNave(g);

        // Player bullets
        List<Proyectil> pb = playerBullets;
        if (pb != null) {
            synchronized (pb) {
                for (Proyectil p : pb) {
                    if (p.isActive()) {
                        g.setColor(Color.YELLOW);
                        g.fillRect(p.getPosX() - 2, p.getPosY(), 4, p.getLength());
                    }
                }
            }
        }

        // Enemy bullets
        List<ProyectilEnemigo> eb = enemyBullets;
        if (eb != null) {
            synchronized (eb) {
                for (ProyectilEnemigo p : eb) {
                    if (p.isActive()) {
                        g.setColor(new Color(255, 80, 80));
                        g.fillRect(p.getPosX() - 2, p.getPosY(), 4, p.getLength());
                    }
                }
            }
        }

        drawHUD(g);
    }

    private void drawStars(Graphics g) {
        for (int i = 0; i < STAR_COUNT; i++) {
            int b = 110 + starSpd[i] * 48;
            g.setColor(new Color(b, b, b));
            g.fillRect(starX[i], starY[i], starSpd[i], starSpd[i]);
        }
    }

    private void drawMiNave(Graphics g) {
        int x = miNave.getPosX();
        int y = miNave.getPosY();

        // Engine flame
        g.setColor(Color.ORANGE);
        int[] fx = { x - 8, x, x + 8 };
        int[] fy = { y + 14, y + 30, y + 14 };
        g.fillPolygon(fx, fy, 3);

        // Hull
        g.setColor(Color.CYAN);
        int[] sx = { x, x - 20, x + 20 };
        int[] sy = { y - 22, y + 14, y + 14 };
        g.fillPolygon(sx, sy, 3);

        // Cockpit
        g.setColor(Color.WHITE);
        g.fillOval(x - 5, y - 10, 10, 10);
    }

    private void drawEnemy(Graphics g, NaveEnemiga e) {
        int x = e.getPosX();
        int y = e.getPosY();
        Color c = e.getColor();

        // Side wings
        g.setColor(c.darker());
        g.fillRect(x - 30, y - 5, 12, 14);
        g.fillRect(x + 18, y - 5, 12, 14);

        // Main body
        g.setColor(c);
        g.fillRect(x - 18, y - 14, 36, 28);

        // Inner darker panel
        g.setColor(c.darker().darker());
        g.fillRect(x - 10, y - 7, 20, 14);

        // Eyes
        g.setColor(Color.RED);
        g.fillOval(x - 13, y - 8, 8, 8);
        g.fillOval(x + 5,  y - 8, 8, 8);

        // Mouth
        g.setColor(Color.BLACK);
        g.fillRect(x - 9, y + 5, 18, 4);

        // Gold boss gets antennae and a crown dot
        if (e.getRow() == 0) {
            g.setColor(Color.YELLOW);
            g.drawLine(x - 8, y - 14, x - 13, y - 27);
            g.drawLine(x + 8, y - 14, x + 13, y - 27);
            g.fillOval(x - 16, y - 32, 7, 7);
            g.fillOval(x + 9,  y - 32, 7, 7);
        }
    }

    private void drawHUD(Graphics g) {
        // Top bar
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, WIDTH, 42);

        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.setColor(Color.WHITE);
        g.drawString(String.format("SCORE: %06d", score), 10, 28);

        g.setColor(Color.YELLOW);
        String ws = "WAVE " + wave;
        FontMetrics fm = g.getFontMetrics();
        g.drawString(ws, (WIDTH - fm.stringWidth(ws)) / 2, 28);

        // Lives mini-ships
        g.setColor(Color.CYAN);
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        g.drawString("LIVES:", WIDTH - 135, 28);
        for (int i = 0; i < lives; i++) {
            int lx = WIDTH - 68 + i * 22;
            int[] sx = { lx, lx - 7, lx + 7 };
            int[] sy = { 10, 30, 30 };
            g.fillPolygon(sx, sy, 3);
        }

        // Bottom hint
        g.setColor(new Color(80, 80, 80));
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.drawString("A/D o Flechas: Mover  |  ESPACIO: Disparar", 10, HEIGHT - 6);
    }

    private void drawWaveCleared(Graphics g) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, HEIGHT / 2 - 55, WIDTH, 110);

        g.setFont(new Font("Monospaced", Font.BOLD, 32));
        g.setColor(Color.GREEN);
        String msg = "WAVE " + wave + " COMPLETADA!";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, (WIDTH - fm.stringWidth(msg)) / 2, HEIGHT / 2 - 8);

        g.setFont(new Font("Monospaced", Font.PLAIN, 18));
        g.setColor(Color.YELLOW);
        String nxt = "Preparando WAVE " + (wave + 1) + "...";
        fm = g.getFontMetrics();
        g.drawString(nxt, (WIDTH - fm.stringWidth(nxt)) / 2, HEIGHT / 2 + 28);
    }

    private void drawGameOverScreen(Graphics g) {
        g.setFont(new Font("Monospaced", Font.BOLD, 54));
        g.setColor(Color.RED);
        String go = "GAME OVER";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(go, (WIDTH - fm.stringWidth(go)) / 2, HEIGHT / 2 - 55);

        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        g.setColor(Color.WHITE);
        String sc = String.format("SCORE: %06d   WAVE: %d", score, wave);
        fm = g.getFontMetrics();
        g.drawString(sc, (WIDTH - fm.stringWidth(sc)) / 2, HEIGHT / 2 + 5);

        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        g.setColor(Color.YELLOW);
        String rst = "Presiona ENTER o ESPACIO para jugar de nuevo";
        fm = g.getFontMetrics();
        g.drawString(rst, (WIDTH - fm.stringWidth(rst)) / 2, HEIGHT / 2 + 50);
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (gameOver) {
            if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) newGame();
            return;
        }
        MiNave mn = miNave;
        if (mn == null) return;
        if (k == KeyEvent.VK_LEFT  || k == KeyEvent.VK_A) mn.setMovingLeft(true);
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) mn.setMovingRight(true);
        if (k == KeyEvent.VK_SPACE) mn.disparar();
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        MiNave mn = miNave;
        if (mn == null) return;
        if (k == KeyEvent.VK_LEFT  || k == KeyEvent.VK_A) mn.setMovingLeft(false);
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) mn.setMovingRight(false);
    }

    @Override public void keyTyped(KeyEvent e) {}
}
