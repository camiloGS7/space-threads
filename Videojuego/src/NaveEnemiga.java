import java.awt.Color;

public class NaveEnemiga extends Nave {

    static final int PANEL_WIDTH  = 800;
    static final int PANEL_HEIGHT = 600;

    private final int slotX, slotY;
    private final int row;

    // state: 0 = FORMATION, 1 = DIVING
    private volatile int     state = 0;
    private volatile boolean alive = true;

    // Formation target (written by GamePanel, read by enemy thread)
    private volatile int formX, formY;

    // Dive physics (all volatile for cross-thread visibility)
    private volatile double diveX, diveY;
    private volatile double diveVX, diveVY;
    private volatile double divePhase;

    public NaveEnemiga(int slotX, int slotY, int row) {
        this.slotX = slotX;
        this.slotY = slotY;
        this.row   = row;
        this.posX  = slotX;
        this.posY  = slotY;
        this.formX = slotX;
        this.formY = slotY;
        this.color = rowColor(row);
        setDaemon(true);
    }

    private static Color rowColor(int r) {
        if (r == 0) return new Color(255, 215, 0);   // Gold (boss)
        if (r == 1) return new Color(255, 100, 0);   // Orange
        if (r == 2) return new Color(100, 100, 255); // Blue
        return new Color(200, 0, 200);                // Magenta
    }

    /** Called every tick by GamePanel while enemy is in formation. */
    public void updateFormPos(int fx, int fy) {
        formX = fx;
        formY = fy;
        if (state == 0) {
            posX = fx;
            posY = fy;
        }
    }

    /** Kick off a dive attack toward playerX. */
    public synchronized void startDive(int playerX, double speed) {
        if (state != 0 || !alive) return;
        diveX     = posX;
        diveY     = posY;
        double dx = (playerX - posX) + (Math.random() * 120 - 60);
        double dy = PANEL_HEIGHT + 60 - posY;
        double len = Math.sqrt(dx * dx + dy * dy);
        diveVX    = dx / len * speed;
        diveVY    = dy / len * speed * 0.55 + 1.5;
        divePhase = Math.random() * Math.PI * 2;
        state     = 1; // set last — acts as release for the reads above
    }

    @Override
    public void mover() {
        if (state == 1) {
            divePhase += 0.07;
            diveX += diveVX + Math.sin(divePhase) * 2.5;
            diveY += diveVY;
            posX = (int) diveX;
            posY = (int) diveY;
            if (posY > PANEL_HEIGHT + 60) {
                // Return from the top, snap back to formation slot
                posY  = -45;
                diveY = -45;
                diveX = formX;
                posX  = formX;
                state = 0;
            }
        }
    }

    public boolean estaVivo()  { return alive; }
    public void    kill()     { alive = false; }
    public boolean isDiving() { return state == 1; }
    public int     getSlotX() { return slotX; }
    public int     getSlotY() { return slotY; }
    public int     getRow()   { return row; }

    @Override
    public void run() {
        while (!isInterrupted() && alive) {
            mover();
            try { Thread.sleep(16); }
            catch (InterruptedException e) { interrupt(); }
        }
    }
}