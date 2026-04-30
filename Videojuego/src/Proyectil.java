import java.awt.Color;

public class Proyectil implements Runnable {

    private static final int SPEED  = 8;
    private static final int LENGTH = 12;

    private volatile int posX;
    private volatile int posY;
    private final Color color = Color.YELLOW;
    private volatile boolean active = true;
    private final GamePanel gamePanel;

    public Proyectil(int posX, int posY, GamePanel gamePanel) {
        this.posX      = posX;
        this.posY      = posY;
        this.gamePanel = gamePanel;
    }

    public void mover() {
        posY -= SPEED;
    }

    public int getPosX()     { return posX; }
    public int getPosY()     { return posY; }
    public int getLength()   { return LENGTH; }
    public Color getColor()  { return color; }
    public boolean isActive(){ return active; }
    public void deactivate() { active = false; }

    @Override
    public void run() {
        while (active && posY > -LENGTH) {
            mover();
            gamePanel.checkColision(this);
            try {
                Thread.sleep(8);
            } catch (InterruptedException e) {
                break;
            }
        }
        active = false;
        gamePanel.removeProyectil(this);
    }
}
