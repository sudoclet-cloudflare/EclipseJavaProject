package desktopclock;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;

public class AnalogClock extends JPanel {

    public AnalogClock() {
        Timer timer = new Timer(1000, e -> repaint());
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int size = Math.min(width, height) - 20;

        int centerX = width / 2;
        int centerY = height / 2;

        // Draw clock face
        g2.setColor(Color.WHITE);
        g2.fillOval(centerX - size / 2, centerY - size / 2, size, size);

        g2.setColor(Color.BLACK);
        g2.drawOval(centerX - size / 2, centerY - size / 2, size, size);

        // Get current time
        LocalTime now = LocalTime.now();
        int hour = now.getHour() % 12;
        int minute = now.getMinute();
        int second = now.getSecond();

        double secondAngle = Math.toRadians(second * 6 - 90);
        double minuteAngle = Math.toRadians(minute * 6 - 90);
        double hourAngle = Math.toRadians((hour + minute / 60.0) * 30 - 90);

        drawHand(g2, centerX, centerY, hourAngle, size / 3, 6);
        drawHand(g2, centerX, centerY, minuteAngle, size / 2 - 10, 4);
        drawHand(g2, centerX, centerY, secondAngle, size / 2 - 5, 2);
    }

    private void drawHand(Graphics2D g2, int x, int y,
                          double angle, int length, int thickness) {
        int xEnd = x + (int) (Math.cos(angle) * length);
        int yEnd = y + (int) (Math.sin(angle) * length);

        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(thickness));
        g2.drawLine(x, y, xEnd, yEnd);
        g2.setStroke(oldStroke);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Analog Clock");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 400);
        frame.setLocationRelativeTo(null);

        frame.add(new AnalogClock());
        frame.setVisible(true);
    }
}