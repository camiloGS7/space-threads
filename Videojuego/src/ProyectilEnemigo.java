public class ProyectilEnemigo implements Runnable {

    private static final int SPEED  = 5;
    private static final int LENGTH = 12;

    private volatile int     posX;
    private volatile int     posY;
    private volatile boolean active = true;
    private final GamePanel  gamePanel;

    public ProyectilEnemigo(int x, int y, GamePanel gp) {
        this.posX      = x;
        this.posY      = y;
        this.gamePanel = gp;
    }

    public int     getPosX()    { return posX; }
    public int     getPosY()    { return posY; }
    public int     getLength()  { return LENGTH; }
    public boolean isActive()   { return active; }
    public void    deactivate() { active = false; }

    @Override
    public void run() {
        while (active && posY < 620) {
            posY += SPEED;
            gamePanel.checkEnemyBulletCollision(this);
            try { Thread.sleep(16); }
            catch (InterruptedException e) { break; }
        }
        active = false;
        gamePanel.removeEnemyBullet(this);
    }
}