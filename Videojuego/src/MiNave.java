import java.awt.Color;

public class MiNave extends Nave {

    private static final int PANEL_WIDTH = 800;
    private static final int SPEED = 5;

    private volatile boolean movingLeft  = false;
    private volatile boolean movingRight = false;

    private final GamePanel gamePanel;

    public MiNave(GamePanel gamePanel) {
        this.gamePanel = gamePanel;
        this.posX  = 400;
        this.posY  = 540;
        this.color = Color.CYAN;
        setDaemon(true);
    }

    public void setMovingLeft(boolean val)  { movingLeft  = val; }
    public void setMovingRight(boolean val) { movingRight = val; }

    @Override
    public void mover() {
        if (movingLeft  && posX > 25)               posX -= SPEED;
        if (movingRight && posX < PANEL_WIDTH - 25) posX += SPEED;
    }

    public void disparar() {
        Proyectil p = new Proyectil(posX, posY - 20, gamePanel);
        gamePanel.addProyectil(p);
        new Thread(p).start();
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            mover();
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                interrupt();
            }
        }
    }
}
