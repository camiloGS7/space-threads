import java.awt.Color;

public abstract class Nave extends Thread {
    protected volatile int posX;
    protected volatile int posY;
    protected Color color;

    public abstract void mover();

    public int getPosX() { return posX; }
    public int getPosY() { return posY; }
    public Color getColor() { return color; }
}
