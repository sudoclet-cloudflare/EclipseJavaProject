package desktopclock;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.time.LocalTime;

/*
 * AnalogClock is a custom JPanel that draws an analog clock.
 *
 * This version continues the same overall design:
 *
 *   - Swing Timer for periodic repainting
 *   - custom painting in paintComponent()
 *   - cached layout data that is recomputed only when the panel size changes
 *
 * The main refinement in this pass is that tick-mark rendering is now more
 * internally consistent:
 *
 *   1. Tick strokes are cached as named constants instead of being recreated
 *      during every repaint.
 *
 *   2. Tick "type" values are named (THIN_TICK / THICK_TICK) instead of
 *      relying on unexplained numeric values.
 *
 * This keeps the code efficient while still avoiding the extra structural
 * machinery of introducing a separate TickMark class.
 */
public class AnalogClock extends JPanel {

    // -------------------------------------------------------------------------
    // Visual constants
    // -------------------------------------------------------------------------

    private static final int MARGIN = 20;

    private static final double HOUR_HAND_LENGTH_RATIO   = 0.50;
    private static final double MINUTE_HAND_LENGTH_RATIO = 0.75;
    private static final double SECOND_HAND_LENGTH_RATIO = 0.85;

    private static final int HOUR_HAND_THICKNESS   = 6;
    private static final int MINUTE_HAND_THICKNESS = 4;
    private static final int SECOND_HAND_THICKNESS = 2;

    private static final double NUMERAL_RADIUS_RATIO   = 0.80;
//    private static final double LONG_TICK_START_RATIO  = 0.82;
    private static final double LONG_TICK_START_RATIO  = 0.89;
    private static final double SHORT_TICK_START_RATIO = 0.90;
    private static final double TICK_END_RATIO         = 0.97;

    // -------------------------------------------------------------------------
    // Tick type constants
    // -------------------------------------------------------------------------

    /*
     * These named values are used in the cached tick data.
     * Naming them makes the data format easier to understand and avoids
     * unexplained comparisons like "== 2" in the drawing code.
     */
    private static final int THIN_TICK  = 1;
    private static final int THICK_TICK = 2;

    // -------------------------------------------------------------------------
    // Reusable drawing objects
    // -------------------------------------------------------------------------

    /*
     * These objects do not depend on current time or size, so they can be
     * created once and reused.
     */
    private static final Stroke FACE_STROKE = new BasicStroke(2f);

    private static final Stroke THIN_TICK_STROKE  = new BasicStroke(1f);
    private static final Stroke THICK_TICK_STROKE = new BasicStroke(2f);

    private static final Stroke HOUR_STROKE = new BasicStroke(
            HOUR_HAND_THICKNESS, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private static final Stroke MINUTE_STROKE = new BasicStroke(
            MINUTE_HAND_THICKNESS, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private static final Stroke SECOND_STROKE = new BasicStroke(
            SECOND_HAND_THICKNESS, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    // -------------------------------------------------------------------------
    // Cached layout state
    // -------------------------------------------------------------------------

    /*
     * These values depend on panel size and are recomputed only when the panel
     * dimensions change.
     */
    private int cachedWidth  = -1;
    private int cachedHeight = -1;

    private int centerX;
    private int centerY;
    private int radius;

    /*
     * Each tick entry stores:
     *   [0] x1
     *   [1] y1
     *   [2] x2
     *   [3] y2
     *   [4] tick type (THIN_TICK or THICK_TICK)
     *
     * This keeps the cache compact while still separating layout calculation
     * from paint-time drawing.
     */
    private final int[][] tickData = new int[60][5];

    /*
     * Each numeral entry stores:
     *   [0] center x
     *   [1] center y
     *   [2] hour number
     */
    private final int[][] numeralCenters = new int[12][3];

    /*
     * The numeral font scales with clock size, so it is part of cached layout.
     */
    private Font numeralFont;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /*
     * A Swing Timer fits Swing's event-driven model better than a thread with
     * sleep(). It requests repaints through the UI event system instead of
     * bypassing it.
     *
     * I use a single repeating timer and set an initial delay so the first tick
     * lands close to the next second boundary.
     */
    public AnalogClock() {
        Timer timer = new Timer(1000, e -> repaint());

        int msPastSecond = (int) (System.currentTimeMillis() % 1000);
        int initialDelay = 1000 - msPastSecond;

        timer.setInitialDelay(initialDelay);
        timer.start();
    }

    // -------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        /*
         * Swing expects the superclass version to clear and prepare the drawing
         * surface correctly.
         */
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;

        /*
         * Smooth circles, hands, and text look better with anti-aliasing enabled.
         */
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        updateCachedLayoutIfNeeded();

        drawFace(g2);
        drawTicks(g2);
        drawNumerals(g2);
        drawHands(g2);
        drawCenterPivot(g2);
    }

    // -------------------------------------------------------------------------
    // Layout caching
    // -------------------------------------------------------------------------

    /*
     * The static geometry of the clock depends only on current panel size.
     * That makes it natural to recalculate only when the size changes.
     */
    private void updateCachedLayoutIfNeeded() {
        int width = getWidth();
        int height = getHeight();

        if (width == cachedWidth && height == cachedHeight) {
            return;
        }

        cachedWidth = width;
        cachedHeight = height;

        int size = Math.min(width, height) - MARGIN * 2;
        radius = size / 2;
        centerX = width / 2;
        centerY = height / 2;

        /*
         * Using the panel's current font as a base tends to fit better with the
         * host look-and-feel than hardcoding a specific family name.
         */
        int fontSize = Math.max(10, radius / 7);
        numeralFont = getFont().deriveFont(Font.BOLD, (float) fontSize);

        cacheTickData();
        cacheNumeralCenters();
    }

    /*
     * Precompute tick line endpoints for the current size.
     */
    private void cacheTickData() {
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6.0 - 90.0);

            boolean isHourMark = (i % 5 == 0);
            double startRatio = isHourMark ? LONG_TICK_START_RATIO : SHORT_TICK_START_RATIO;
            int tickType = isHourMark ? THICK_TICK : THIN_TICK;

            int x1 = centerX + (int) (Math.cos(angle) * radius * startRatio);
            int y1 = centerY + (int) (Math.sin(angle) * radius * startRatio);
            int x2 = centerX + (int) (Math.cos(angle) * radius * TICK_END_RATIO);
            int y2 = centerY + (int) (Math.sin(angle) * radius * TICK_END_RATIO);

            tickData[i][0] = x1;
            tickData[i][1] = y1;
            tickData[i][2] = x2;
            tickData[i][3] = y2;
            tickData[i][4] = tickType;
        }
    }

    /*
     * Precompute the center point for each numeral position.
     */
    private void cacheNumeralCenters() {
        for (int hour = 1; hour <= 12; hour++) {
            double angle = Math.toRadians(hour * 30.0 - 90.0);

            int x = centerX + (int) (Math.cos(angle) * radius * NUMERAL_RADIUS_RATIO);
            int y = centerY + (int) (Math.sin(angle) * radius * NUMERAL_RADIUS_RATIO);

            numeralCenters[hour - 1][0] = x;
            numeralCenters[hour - 1][1] = y;
            numeralCenters[hour - 1][2] = hour;
        }
    }

    // -------------------------------------------------------------------------
    // Static clock parts
    // -------------------------------------------------------------------------

    private void drawFace(Graphics2D g2) {
        g2.setColor(Color.WHITE);
        g2.fill(new Ellipse2D.Double(centerX - radius, centerY - radius,
                radius * 2.0, radius * 2.0));

        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(FACE_STROKE);
        g2.draw(new Ellipse2D.Double(centerX - radius, centerY - radius,
                radius * 2.0, radius * 2.0));
    }

    /*
     * Tick layout is cached. Paint-time work is reduced to selecting the
     * correct prebuilt stroke and drawing the precomputed line.
     */
    private void drawTicks(Graphics2D g2) {
        Stroke oldStroke = g2.getStroke();
        Color oldColor = g2.getColor();

        g2.setColor(Color.DARK_GRAY);

        for (int i = 0; i < 60; i++) {
            Stroke tickStroke = (tickData[i][4] == THICK_TICK)
                    ? THICK_TICK_STROKE
                    : THIN_TICK_STROKE;

            g2.setStroke(tickStroke);
            g2.drawLine(tickData[i][0], tickData[i][1], tickData[i][2], tickData[i][3]);
        }

        g2.setStroke(oldStroke);
        g2.setColor(oldColor);
    }

    /*
     * Numeral centers are cached, but FontMetrics is still needed at paint time
     * because text is positioned by baseline rather than visual center.
     */
    private void drawNumerals(Graphics2D g2) {
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();

        g2.setFont(numeralFont);
        g2.setColor(Color.DARK_GRAY);

        FontMetrics fm = g2.getFontMetrics();

        for (int i = 0; i < 12; i++) {
            String text = String.valueOf(numeralCenters[i][2]);

            int textWidth = fm.stringWidth(text);
            int textAscent = fm.getAscent();

            int x = numeralCenters[i][0] - textWidth / 2;
            int y = numeralCenters[i][1] + textAscent / 2;

            g2.drawString(text, x, y);
        }

        g2.setFont(oldFont);
        g2.setColor(oldColor);
    }

    // -------------------------------------------------------------------------
    // Dynamic clock parts
    // -------------------------------------------------------------------------

    /*
     * Only the hands depend on current time, so this part remains dynamic.
     */
    private void drawHands(Graphics2D g2) {
        LocalTime now = LocalTime.now();

        int hour = now.getHour() % 12;
        int minute = now.getMinute();
        int second = now.getSecond();

        double secondAngle = Math.toRadians(second * 6.0 - 90.0);
        double minuteAngle = Math.toRadians(minute * 6.0 - 90.0);
        double hourAngle = Math.toRadians((hour + minute / 60.0) * 30.0 - 90.0);

        drawHand(g2, hourAngle, (int) (radius * HOUR_HAND_LENGTH_RATIO), HOUR_STROKE, Color.BLACK);
        drawHand(g2, minuteAngle, (int) (radius * MINUTE_HAND_LENGTH_RATIO), MINUTE_STROKE, Color.BLACK);
        drawHand(g2, secondAngle, (int) (radius * SECOND_HAND_LENGTH_RATIO), SECOND_STROKE, Color.RED);
    }

    /*
     * One helper is enough because the three hands differ only in parameters,
     * not in drawing logic.
     */
    private void drawHand(Graphics2D g2, double angle, int length, Stroke stroke, Color color) {
        int xEnd = centerX + (int) (Math.cos(angle) * length);
        int yEnd = centerY + (int) (Math.sin(angle) * length);

        Stroke oldStroke = g2.getStroke();
        Color oldColor = g2.getColor();

        g2.setStroke(stroke);
        g2.setColor(color);
        g2.drawLine(centerX, centerY, xEnd, yEnd);

        g2.setStroke(oldStroke);
        g2.setColor(oldColor);
    }

    /*
     * The pivot scales with radius so the center detail looks proportional at
     * different window sizes.
     */
    private void drawCenterPivot(Graphics2D g2) {
        Color oldColor = g2.getColor();

        int pivotRadius = Math.max(4, radius / 20);

        g2.setColor(Color.BLACK);
        g2.fill(new Ellipse2D.Double(centerX - pivotRadius, centerY - pivotRadius,
                pivotRadius * 2.0, pivotRadius * 2.0));

        g2.setColor(oldColor);
    }

    // -------------------------------------------------------------------------
    // Application entry point
    // -------------------------------------------------------------------------

    /*
     * Swing UI setup should run on the Event Dispatch Thread.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Analog Clock");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(400, 400);
            frame.setLocationRelativeTo(null);
            frame.add(new AnalogClock());
            frame.setVisible(true);
        });
    }
}