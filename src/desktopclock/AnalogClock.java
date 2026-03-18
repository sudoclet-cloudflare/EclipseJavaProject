package desktopclock;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;

// This class represents a panel that knows how to draw a clock.
// It inherits basic drawing behavior from JPanel.
public class AnalogClock extends JPanel {

    // Constructor: runs when the clock panel is created
    public AnalogClock() {

        // Create a timer that fires every 1000 milliseconds (1 second)
        // Each time it fires, it calls repaint()
        // repaint() tells Swing: "please redraw this panel"
        Timer timer = new Timer(1000, e -> repaint());

        // Start the timer running
        timer.start();
    }

    // This method is called by Swing whenever the panel needs to be drawn
    @Override
    protected void paintComponent(Graphics g) {

        // Call the parent version first
        // This clears the background and prepares the drawing surface
        super.paintComponent(g);

        // Convert the Graphics object into a Graphics2D object
        // This gives us more advanced drawing features
        Graphics2D g2 = (Graphics2D) g;

        // Turn on anti-aliasing so lines look smooth instead of jagged
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Get the current size of the panel
        int width = getWidth();
        int height = getHeight();

        // Use the smaller dimension so the clock stays circular
        int size = Math.min(width, height) - 20;

        // Find the center point of the panel
        int centerX = width / 2;
        int centerY = height / 2;

        // Draw the clock face (a white circle with a black border)

        // Fill the circle
        g2.setColor(Color.WHITE);
        g2.fillOval(centerX - size / 2, centerY - size / 2, size, size);

        // Draw the outline
        g2.setColor(Color.BLACK);
        g2.drawOval(centerX - size / 2, centerY - size / 2, size, size);

        // Get the current system time
        LocalTime now = LocalTime.now();

        int hour = now.getHour() % 12;   // Convert to 12-hour format
        int minute = now.getMinute();
        int second = now.getSecond();

        // Convert time values into angles (in radians)
        // Subtract 90 degrees so 12 o'clock is at the top

        double secondAngle = Math.toRadians(second * 6 - 90);
        double minuteAngle = Math.toRadians(minute * 6 - 90);
        double hourAngle = Math.toRadians((hour + minute / 60.0) * 30 - 90);

        // Draw the three hands of the clock

        // Hour hand (short and thick)
        drawHand(g2, centerX, centerY, hourAngle, size / 3, 6);

        // Minute hand (longer, medium thickness)
        drawHand(g2, centerX, centerY, minuteAngle, size / 2 - 10, 4);

        // Second hand (long and thin)
        drawHand(g2, centerX, centerY, secondAngle, size / 2 - 5, 2);
    }

    // Helper function that draws a single clock hand
    private void drawHand(Graphics2D g2, int x, int y,
                          double angle, int length, int thickness) {

        // Compute the end point of the hand using basic trigonometry
        int xEnd = x + (int) (Math.cos(angle) * length);
        int yEnd = y + (int) (Math.sin(angle) * length);

        // Save the current line style
        Stroke oldStroke = g2.getStroke();

        // Set the thickness of the line
        g2.setStroke(new BasicStroke(thickness));

        // Draw the line from center to endpoint
        g2.drawLine(x, y, xEnd, yEnd);

        // Restore the previous line style
        g2.setStroke(oldStroke);
    }

    // Main method: this is where the program starts
    public static void main(String[] args) {

        // Create a window (frame)
        JFrame frame = new JFrame("Analog Clock");

        // Close the program when the window is closed
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Set window size
        frame.setSize(400, 400);

        // Center the window on screen
        frame.setLocationRelativeTo(null);

        // Add our clock panel into the window
        frame.add(new AnalogClock());

        // Make the window visible
        frame.setVisible(true);
    }
}